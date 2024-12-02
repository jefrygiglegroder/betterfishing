package jefry.plugin.betterFishing;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public class FishingRodsConfig {
    private final Plugin plugin;
    private FileConfiguration fishingRodsConfig;
    private File fishingRodsConfigFile;
    private Map<String, FishingRodData> fishingRods = new HashMap<>();

    public FishingRodsConfig(Plugin plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    private void setupConfig() {
        fishingRodsConfigFile = new File(plugin.getDataFolder(), "fishingrods.yml");
        if (!fishingRodsConfigFile.exists()) {
            plugin.getLogger().info("Creating fishingrods.yml with default configuration...");
            plugin.saveResource("fishingrods.yml", false);
        }
        reloadConfig();
    }

    public void reloadConfig() {
        fishingRodsConfig = YamlConfiguration.loadConfiguration(fishingRodsConfigFile);
        loadFishingRods();
    }

    private void loadFishingRods() {
        fishingRods.clear();

        ConfigurationSection rodsSection = fishingRodsConfig.getConfigurationSection("fishingrods");
        if (rodsSection == null) {
            plugin.getLogger().warning("No fishing rods found in configuration!");
            return;
        }

        for (String rodKey : rodsSection.getKeys(false)) {
            String path = "fishingrods." + rodKey;

            String displayName = fishingRodsConfig.getString(path + ".display-name");
            double baseWeightMultiplier = fishingRodsConfig.getDouble(path + ".weight-multiplier", 1.0);
            int upgradesAllowed = fishingRodsConfig.getInt(path + ".upgrades-allowed", 3);

            // New attributes for catch modification
            double rarityBonus = fishingRodsConfig.getDouble(path + ".rarity-bonus", 0.0);
            double legendaryChance = fishingRodsConfig.getDouble(path + ".legendary-chance", 0.0);

            Map<Enchantment, Integer> enchantments = new HashMap<>();
            List<Map<String, Integer>> enchantmentList =
                    (List<Map<String, Integer>>) fishingRodsConfig.getList(path + ".enchantments");

            if (enchantmentList != null) {
                for (Map<String, Integer> enchMap : enchantmentList) {
                    for (Map.Entry<String, Integer> entry : enchMap.entrySet()) {
                        Enchantment enchantment = getEnchantmentByName(entry.getKey());
                        if (enchantment != null) {
                            enchantments.put(enchantment, entry.getValue());
                        }
                    }
                }
            }

            FishingRodData rodData = new FishingRodData(
                    rodKey,
                    displayName,
                    upgradesAllowed,
                    baseWeightMultiplier,
                    rarityBonus,
                    legendaryChance,
                    enchantments
            );
            fishingRods.put(rodKey, rodData);
        }
    }

    private Enchantment getEnchantmentByName(String name) {
        switch (name.toLowerCase()) {
            case "lure": return Enchantment.LURE;
            case "luck-of-the-sea": return Enchantment.LUCK_OF_THE_SEA;
            case "unbreaking": return Enchantment.UNBREAKING;
            case "mending": return Enchantment.MENDING;
            default: return null;
        }
    }

    public FishingRodData getFishingRod(String rodKey) {
        return fishingRods.get(rodKey);
    }

    public Map<String, FishingRodData> getAllFishingRods() {
        return fishingRods;
    }

    public static class FishingRodData {
        private final String key;
        private final String displayName;
        private final int upgradesAllowed;
        private final double baseWeightMultiplier;
        private final double rarityBonus;
        private final double legendaryChance;
        private final Map<Enchantment, Integer> enchantments;

        public FishingRodData(String key, String displayName,
                              int upgradesAllowed, double baseWeightMultiplier,
                              double rarityBonus, double legendaryChance,
                              Map<Enchantment, Integer> enchantments) {
            this.key = key;
            this.displayName = displayName;
            this.upgradesAllowed = upgradesAllowed;
            this.baseWeightMultiplier = baseWeightMultiplier;
            this.rarityBonus = rarityBonus;
            this.legendaryChance = legendaryChance;
            this.enchantments = enchantments;
        }

        // Getters
        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public int getUpgradesAllowed() { return upgradesAllowed; }
        public Map<Enchantment, Integer> getEnchantments() { return enchantments; }

        // Updated getters for catch modification attributes
        public double getBaseWeightMultiplier() { return baseWeightMultiplier; }
        public double getRarityBonus() { return rarityBonus; }
        public double getLegendaryChance() { return legendaryChance; }
    }
}