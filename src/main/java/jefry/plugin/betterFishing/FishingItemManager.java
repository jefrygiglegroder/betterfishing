package jefry.plugin.betterFishing;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class FishingItemManager {
    private final Plugin plugin;
    private FileConfiguration config;
    final Map<String, FishingItem> permanentItems = new HashMap<>();
    final Map<String, TemporaryFishingItem> temporaryItems = new HashMap<>();
    private final Map<String, List<Biome>> locationBiomes = new HashMap<>();
    private final Map<String, RaritySettings> raritySettings = new HashMap<>();
    private final Random random = new Random();

    public FishingItemManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    public void loadConfiguration() {
        // Save default config if it doesn't exist
        File configFile = new File(plugin.getDataFolder(), "fish-config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("fish-config.yml", false);
        }

        // Load configuration
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load locations
        loadLocations();

        // Load rarity settings
        loadRaritySettings();

        // Load permanent items
        loadPermanentItems();

        // Load temporary items
        loadTemporaryItems();
    }

    private void loadLocations() {
        ConfigurationSection locationSection = config.getConfigurationSection("locations");
        if (locationSection != null) {
            for (String locationName : locationSection.getKeys(false)) {
                List<String> biomeNames = locationSection.getStringList(locationName + ".biomes");
                try {
                    List<Biome> biomes = biomeNames.stream()
                            .map(name -> {
                                try {
                                    return Biome.valueOf(name.toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Invalid biome name: " + name + " in location: " + locationName);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!biomes.isEmpty()) {
                        locationBiomes.put(locationName, biomes);
                        plugin.getLogger().info("Loaded " + biomes.size() + " biomes for location: " + locationName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading biomes for location " + locationName + ": " + e.getMessage());
                }
            }
        }
    }
    private void loadRaritySettings() {
        ConfigurationSection raritySection = config.getConfigurationSection("rarity_settings");
        if (raritySection != null) {
            for (String rarity : raritySection.getKeys(false)) {
                ConfigurationSection settings = raritySection.getConfigurationSection(rarity);
                if (settings != null) {
                    raritySettings.put(rarity, new RaritySettings(
                            settings.getString("color", "§7"),
                            settings.getInt("xp_reward", 0),
                            settings.getDouble("weight_multiplier", 1.0)
                    ));
                }
            }
        }
    }

    private void loadPermanentItems() {
        ConfigurationSection itemSection = config.getConfigurationSection("permanent_items");
        if (itemSection != null) {
            for (String itemId : itemSection.getKeys(false)) {
                ConfigurationSection section = itemSection.getConfigurationSection(itemId);
                if (section != null) {
                    FishingItem item = new FishingItem(
                            itemId,
                            Material.valueOf(section.getString("item", "COD")),
                            section.getString("display_name", "Fish"),
                            section.getString("rarity", "COMMON"),
                            section.getStringList("locations"),
                            section.getDouble("weight_range.min", 1.0),
                            section.getDouble("weight_range.max", 10.0),
                            section.getDouble("catch_chance", 100.0),
                            section.getStringList("lore")
                    );
                    permanentItems.put(itemId, item);
                }
            }
        }
    }

    private void loadTemporaryItems() {
        ConfigurationSection itemSection = config.getConfigurationSection("temporary_items");
        if (itemSection != null) {
            for (String itemId : itemSection.getKeys(false)) {
                ConfigurationSection section = itemSection.getConfigurationSection(itemId);
                if (section != null) {
                    TemporaryFishingItem item = new TemporaryFishingItem(
                            itemId,
                            Material.valueOf(section.getString("item", "COD")),
                            section.getString("display_name", "Fish"),
                            section.getString("rarity", "COMMON"),
                            section.getStringList("locations"),
                            section.getDouble("weight_range.min", 1.0),
                            section.getDouble("weight_range.max", 10.0),
                            section.getDouble("catch_chance", 100.0),
                            section.getStringList("lore"),
                            parseDateTime(section.getString("duration.start")),
                            parseDateTime(section.getString("duration.end"))
                    );
                    temporaryItems.put(itemId, item);
                }
            }
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(dateStr, formatter);
    }

    public ItemStack generateCatch(Player player, ItemStack fishingRod) {
        // Get player's current biome
        Biome playerBiome = player.getLocation().getBlock().getBiome();
        plugin.getLogger().info("Player biome: " + playerBiome.name());

        // Combine all available items based on location and time
        List<FishingItem> availableItems = new ArrayList<>();

        // Add permanent items
        List<FishingItem> permanentItemsInLocation = permanentItems.values().stream()
                .filter(item -> isValidLocation(item, playerBiome))
                .collect(Collectors.toList());

        plugin.getLogger().info("Permanent items in location: " + permanentItemsInLocation.size());
        availableItems.addAll(permanentItemsInLocation);

        // Add temporary items that are currently active
        LocalDateTime now = LocalDateTime.now();
        List<FishingItem> temporaryItemsInLocation = temporaryItems.values().stream()
                .filter(item -> item.isActive(now))
                .filter(item -> isValidLocation(item, playerBiome))
                .collect(Collectors.toList());

        plugin.getLogger().info("Temporary items in location: " + temporaryItemsInLocation.size());
        availableItems.addAll(temporaryItemsInLocation);

        // Print out available item details
        for (FishingItem item : availableItems) {
            plugin.getLogger().info("Available item: " + item.getDisplayName() +
                    ", Locations: " + item.getLocations());
        }

        // Select an item based on catch chances
        FishingItem selectedItem = selectItem(availableItems);
        if (selectedItem == null) {
            // Fallback to a default fish if no valid items
            plugin.getLogger().warning("No valid items found for biome: " + playerBiome);
            return new ItemStack(Material.COD);
        }

        // Generate the caught item
        return createCaughtItem(selectedItem, player, fishingRod);
    }

    private boolean isValidLocation(FishingItem item, Biome playerBiome) {
        // If it's a temporary fishing item, check biome
        if (item instanceof TemporaryFishingItem) {
            boolean isValid = item.getLocations().stream()
                    .anyMatch(location -> {
                        boolean containsBiome = locationBiomes.containsKey(location) &&
                                locationBiomes.get(location).contains(playerBiome);
                        if (containsBiome) {
                            plugin.getLogger().info("Location match found: " + location +
                                    " contains biome " + playerBiome);
                        }
                        return containsBiome;
                    });

            if (!isValid) {
                plugin.getLogger().info("No location match for " + playerBiome +
                        ". Item locations: " + item.getLocations());
            }
            return isValid;
        }

        // For permanent items, always return true (no biome restriction)
        return true;
    }
    private FishingItem selectItem(List<FishingItem> availableItems) {
        if (availableItems.isEmpty()) return null;

        double totalChance = availableItems.stream()
                .mapToDouble(FishingItem::getCatchChance)
                .sum();

        double roll = random.nextDouble() * totalChance;
        double currentSum = 0;

        for (FishingItem item : availableItems) {
            currentSum += item.getCatchChance();
            if (roll <= currentSum) {
                return item;
            }
        }

        return availableItems.get(0);
    }

    private ItemStack createCaughtItem(FishingItem fishingItem, Player player, ItemStack fishingRod) {
        ItemStack item = new ItemStack(fishingItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get rod attributes
            String rodTier = getTierFromRod(fishingRod);
            FishingRodsConfig.FishingRodData rodData = BetterFishing.getFishingRodsConfig().getFishingRod(rodTier);

            // Get rod base weight multiplier (if no rod found, default to 1.0)
            double rodBaseMultiplier = rodData != null ? rodData.getBaseWeightMultiplier() : 1.0;

            // Set display name with rarity
            RaritySettings rarityInfo = getRarityWithRodBonus(fishingItem.getRarity(), rodData);
            String colorCode = rarityInfo != null ? rarityInfo.getColor() : "§f";
            meta.setDisplayName(colorCode + fishingItem.getDisplayName());

            // Generate weight with rod multiplier
            double baseWeight = generateWeight(fishingItem);

            // Apply multipliers: base weight * rod multiplier * rarity multiplier
            double rarityMultiplier = rarityInfo != null ? rarityInfo.getWeightMultiplier() : 1.0;
            double finalWeight = baseWeight * rodBaseMultiplier * rarityMultiplier;

            // Create lore
            List<String> lore = new ArrayList<>();
            lore.add("§7Weight: §f" + String.format("%.2f", finalWeight) + " kg");
            lore.add("§7Quality: " + colorCode + fishingItem.getRarity());
            lore.addAll(fishingItem.getLore());
            lore.add("§7Caught by: §f" + player.getName());

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
    private RaritySettings getRarityWithRodBonus(String baseRarity, FishingRodsConfig.FishingRodData rodData) {
        if (rodData == null) return raritySettings.get(baseRarity);

        // Apply legendary chance from rod
        if (rodData.getLegendaryChance() > 0 && Math.random() < rodData.getLegendaryChance()) {
            return raritySettings.get("LEGENDARY");
        }

        // Apply rarity bonus from rod
        double rarityBonus = rodData.getRarityBonus();
        if (rarityBonus <= 0) return raritySettings.get(baseRarity);

        // Rarity upgrade chance based on rod bonus
        String[] rarityTiers = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
        int currentTier = Arrays.asList(rarityTiers).indexOf(baseRarity);
        if (currentTier < 0) return raritySettings.get(baseRarity);

        // Chance to upgrade rarity based on rod bonus
        if (Math.random() < rarityBonus && currentTier < rarityTiers.length - 1) {
            return raritySettings.get(rarityTiers[currentTier + 1]);
        }

        return raritySettings.get(baseRarity);
    }
    private String getTierFromRod(ItemStack rod) {
        if (rod == null || !rod.hasItemMeta() || !rod.getItemMeta().hasDisplayName()) {
            return "starter";
        }

        String displayName = rod.getItemMeta().getDisplayName();
        return displayName.split(" ")[0].replaceAll("§.", "").toLowerCase();
    }

    private double generateWeight(FishingItem item) {
        return item.getMinWeight() + (random.nextDouble() * (item.getMaxWeight() - item.getMinWeight()));
    }

    // Inner classes for data structure
    public static class FishingItem {
        private final String id;
        private final Material material;
        private final String displayName;
        private final String rarity;
        private final List<String> locations;
        private final double minWeight;
        private final double maxWeight;
        private final double catchChance;
        private final List<String> lore;

        public FishingItem(String id, Material material, String displayName, String rarity,
                           List<String> locations, double minWeight, double maxWeight,
                           double catchChance, List<String> lore) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.rarity = rarity;
            this.locations = locations;
            this.minWeight = minWeight;
            this.maxWeight = maxWeight;
            this.catchChance = catchChance;
            this.lore = lore;
        }

        // Getters
        public String getId() { return id; }
        public Material getMaterial() { return material; }
        public String getDisplayName() { return displayName; }
        public String getRarity() { return rarity; }
        public List<String> getLocations() { return locations; }
        public double getMinWeight() { return minWeight; }
        public double getMaxWeight() { return maxWeight; }
        public double getCatchChance() { return catchChance; }
        public List<String> getLore() { return lore; }
    }

    public static class TemporaryFishingItem extends FishingItem {
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        public TemporaryFishingItem(String id, Material material, String displayName,
                                    String rarity, List<String> locations, double minWeight,
                                    double maxWeight, double catchChance, List<String> lore,
                                    LocalDateTime startTime, LocalDateTime endTime) {
            super(id, material, displayName, rarity, locations, minWeight, maxWeight, catchChance, lore);
            this.startTime = startTime;
            this.endTime = endTime;
        }
        // Getters for start and end times
        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        // Check if the item is active
        public boolean isActive(LocalDateTime currentTime) {
            return (startTime == null || !currentTime.isBefore(startTime)) &&
                    (endTime == null || !currentTime.isAfter(endTime));
        }
    }

    public static class RaritySettings {
        private final String color;
        private final int xpReward;
        private final double weightMultiplier;

        public RaritySettings(String color, int xpReward, double weightMultiplier) {
            this.color = color;
            this.xpReward = xpReward;
            this.weightMultiplier = weightMultiplier;
        }

        // Getters
        public String getColor() {
            return color;
        }

        public int getXpReward() {
            return xpReward;
        }

        public double getWeightMultiplier() {
            return weightMultiplier;
        }
    }
}