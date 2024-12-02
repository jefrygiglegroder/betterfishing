package jefry.plugin.betterFishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FishingAdminManager implements Listener {
    private final BetterFishing plugin;
    private final FishingRodsConfig fishingRodsConfig;
    private final FishingItemManager fishingItemManager;
    private static final int ITEMS_PER_PAGE = 27;

    private final ConversationFactory conversationFactory;
    private final Map<UUID, Map<String, Object>> pendingConfiguration = new HashMap<>();

    // Tracking players creating/editing items
    private final Map<UUID, CreationContext> creationContexts = new HashMap<>();

    // Enumeration for item types
    public enum ItemType {
        ROD, PERMANENT_FISH, TEMPORARY_FISH
    }

    // Context for item creation/editing
    private static class CreationContext {
        ItemType type;
        String currentId;
        Inventory creationInventory;
        boolean isEditing;

        CreationContext(ItemType type, String currentId, boolean isEditing) {
            this.type = type;
            this.currentId = currentId;
            this.isEditing = isEditing;
        }
    }

    // Constructor remains the same
    public FishingAdminManager(BetterFishing plugin,
                               FishingRodsConfig fishingRodsConfig,
                               FishingItemManager fishingItemManager) {
        this.plugin = plugin;
        this.fishingRodsConfig = fishingRodsConfig;
        this.fishingItemManager = fishingItemManager;

        this.conversationFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withEscapeSequence("/cancel")
                .withTimeout(60)
                .thatExcludesNonPlayersWithMessage("Only players can use this command!");
    }

    // Rod Management UI
    public void openRodManagementUI(Player player, int page) {
        if (!player.hasPermission("betterfishing.admin.rods")) {
            player.sendMessage("§cYou do not have permission to manage fishing rods.");
            return;
        }

        List<FishingRodsConfig.FishingRodData> rods = new ArrayList<>(fishingRodsConfig.getAllFishingRods().values());
        int maxPages = (int) Math.ceil((double) rods.size() / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPages - 1));

        Inventory gui = Bukkit.createInventory(null, 54, "§9Fishing Rod Management (Page " + (page + 1) + ")");

        // Add rod items for current page
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, rods.size());

        for (int i = startIndex; i < endIndex; i++) {
            FishingRodsConfig.FishingRodData rod = rods.get(i);
            ItemStack rodItem = createRodDisplayItem(rod);
            gui.addItem(rodItem);
        }

        // Navigation and control buttons
        gui.setItem(48, createGuiItem(Material.ARROW, "§6Previous Page",
                page > 0 ? "Click to go to previous page" : "§cNo previous page"));
        gui.setItem(49, createGuiItem(Material.COMPASS, "§bPage " + (page + 1) + " of " + maxPages));
        gui.setItem(50, createGuiItem(Material.ARROW, "§6Next Page",
                page < maxPages - 1 ? "Click to go to next page" : "§cNo next page"));

        // Add Rod Button
        gui.setItem(53, createGuiItem(Material.EMERALD, "§a§lAdd New Rod",
                "§7Click to create a new fishing rod"));

        player.openInventory(gui);
    }

    // Fish Management UI
    public void openFishManagementUI(Player player, int page) {
        if (!player.hasPermission("betterfishing.admin.fish")) {
            player.sendMessage("§cYou do not have permission to manage fish.");
            return;
        }

        List<FishingItemManager.FishingItem> fishItems = new ArrayList<>(
                fishingItemManager.permanentItems.values());
        fishItems.addAll(fishingItemManager.temporaryItems.values());

        int maxPages = (int) Math.ceil((double) fishItems.size() / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPages - 1));

        Inventory gui = Bukkit.createInventory(null, 54, "§9Fish Item Management (Page " + (page + 1) + ")");

        // Add fish items for current page
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, fishItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            FishingItemManager.FishingItem fishItem = fishItems.get(i);
            ItemStack fishDisplayItem = createFishDisplayItem(fishItem);
            gui.addItem(fishDisplayItem);
        }

        // Navigation and control buttons
        gui.setItem(48, createGuiItem(Material.ARROW, "§6Previous Page",
                page > 0 ? "Click to go to previous page" : "§cNo previous page"));
        gui.setItem(49, createGuiItem(Material.COMPASS, "§bPage " + (page + 1) + " of " + maxPages));
        gui.setItem(50, createGuiItem(Material.ARROW, "§6Next Page",
                page < maxPages - 1 ? "Click to go to next page" : "§cNo next page"));

        // Add Fish Button
        gui.setItem(53, createGuiItem(Material.EMERALD, "§a§lAdd New Fish",
                "§7Click to create a new fish item"));

        player.openInventory(gui);
    }

    private ItemStack createRodDisplayItem(FishingRodsConfig.FishingRodData rod) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§9" + rod.getDisplayName());
        meta.setLore(Arrays.asList(
                "§7Weight Multiplier: §f" + rod.getBaseWeightMultiplier(),
                "§7Rarity Bonus: §f" + rod.getRarityBonus(),
                "§7Legendary Chance: §f" + rod.getLegendaryChance(),
                "§7Click to edit or delete"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFishDisplayItem(FishingItemManager.FishingItem fishItem) {
        ItemStack item = new ItemStack(fishItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§9" + fishItem.getDisplayName());
        meta.setLore(Arrays.asList(
                "§7Rarity: §f" + fishItem.getRarity(),
                "§7Weight Range: §f" + fishItem.getMinWeight() + "-" + fishItem.getMaxWeight(),
                "§7Catch Chance: §f" + fishItem.getCatchChance(),
                "§7Click to edit or delete"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.contains("Fishing Rod Management")) {
            handleRodManagementClick(event, player);
        } else if (title.contains("Fish Item Management")) {
            handleFishManagementClick(event, player);
        } else if (title.equals("§9Select Fish Type")) {
            handleFishTypeSelection(event, player);
        } else if (title.contains("Create New Fishing Rod") || title.contains("Edit/Delete Rod")) {
            handleRodCreationEditClick(event, player);
        } else if (title.contains("Edit/Delete Fish")) {
            handleFishEditDeleteClick(event, player);
        }
    }

    private void handleRodManagementClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.getCurrentItem() != null) {
            switch (event.getCurrentItem().getType()) {
                case ARROW:
                    int currentPage = Integer.parseInt(
                            event.getView().getTitle().replaceAll("[^0-9]", "")) - 1;

                    if (event.getSlot() == 48 && currentPage > 0) {
                        openRodManagementUI(player, currentPage - 1);
                    } else if (event.getSlot() == 50) {
                        openRodManagementUI(player, currentPage + 1);
                    }
                    break;

                case EMERALD:
                    // Open rod creation UI
                    openRodCreationUI(player);
                    break;

                case FISHING_ROD:
                    // Get rod name from display name
                    String rodName = event.getCurrentItem().getItemMeta().getDisplayName()
                            .replace("§9", "")
                            .toLowerCase()
                            .replace(" ", "_");

                    // Open rod edit/delete UI
                    openRodEditUI(player, rodName);
                    break;
            }
        }
    }

    private void handleFishManagementClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.getCurrentItem() != null) {
            switch (event.getCurrentItem().getType()) {
                case ARROW:
                    int currentPage = Integer.parseInt(
                            event.getView().getTitle().replaceAll("[^0-9]", "")) - 1;

                    if (event.getSlot() == 48 && currentPage > 0) {
                        openFishManagementUI(player, currentPage - 1);
                    } else if (event.getSlot() == 50) {
                        openFishManagementUI(player, currentPage + 1);
                    }
                    break;

                case EMERALD:
                    // Open fish type selection
                    openFishTypeSelectionUI(player);
                    break;

                default:
                    // Open fish details/edit UI
                    String fishName = event.getCurrentItem().getItemMeta().getDisplayName()
                            .replace("§9", "")
                            .toLowerCase()
                            .replace(" ", "_");

                    openFishEditUI(player, fishName);
                    break;
            }
        }
    }
    private void openFishTypeSelectionUI(Player player) {
        Inventory typeSelection = Bukkit.createInventory(null, 27, "§9Select Fish Type");

        // Permanent Fish
        typeSelection.setItem(11, createGuiItem(Material.COD, "§aPermanent Fish",
                "§7Create a permanent fishing item"));

        // Temporary Fish
        typeSelection.setItem(15, createGuiItem(Material.SALMON, "§aTemporary Fish",
                "§7Create a time-limited fishing item"));

        player.openInventory(typeSelection);
    }

    private void openRodCreationUI(Player player) {
        Inventory creationInventory = Bukkit.createInventory(null, 54, "§9Create New Fishing Rod");

        // ID Input
        creationInventory.setItem(10, createGuiItem(Material.NAME_TAG, "§bRod ID",
                "§7Click to set rod ID (lowercase, no spaces)"));

        // Display Name
        creationInventory.setItem(19, createGuiItem(Material.PAPER, "§bDisplay Name",
                "§7Click to set display name"));

        // Weight Multiplier
        creationInventory.setItem(28, createGuiItem(Material.IRON_INGOT, "§bWeight Multiplier",
                "§7Click to set base weight multiplier"));

        // Upgrades Allowed
        creationInventory.setItem(37, createGuiItem(Material.EXPERIENCE_BOTTLE, "§bUpgrades Allowed",
                "§7Click to set number of upgrades"));

        // Rarity Bonus
        creationInventory.setItem(16, createGuiItem(Material.GOLD_NUGGET, "§bRarity Bonus",
                "§7Click to set rarity upgrade chance"));

        // Legendary Chance
        creationInventory.setItem(25, createGuiItem(Material.DIAMOND, "§bLegendary Chance",
                "§7Click to set legendary item chance"));

        // Save Button
        creationInventory.setItem(53, createGuiItem(Material.EMERALD_BLOCK, "§a§lSave Rod",
                "§7Click to save the new fishing rod"));

        // Create and track creation context
        creationContexts.put(player.getUniqueId(),
                new CreationContext(ItemType.ROD, null, false));

        player.openInventory(creationInventory);
    }

    private void openRodEditUI(Player player, String rodKey) {
        Inventory editInventory = Bukkit.createInventory(null, 27, "§9Edit/Delete Rod: " + rodKey);

        // Edit Rod
        editInventory.setItem(11, createGuiItem(Material.WRITABLE_BOOK, "§bEdit Rod",
                "§7Modify rod configuration"));

        // Delete Rod
        editInventory.setItem(15, createGuiItem(Material.BARRIER, "§c§lDelete Rod",
                "§7Permanently remove this rod"));

        // Create and track edit context
        creationContexts.put(player.getUniqueId(),
                new CreationContext(ItemType.ROD, rodKey, true));

        player.openInventory(editInventory);
    }

    private void openFishEditUI(Player player, String fishId) {
        Inventory editInventory = Bukkit.createInventory(null, 27, "§9Edit/Delete Fish: " + fishId);

        // Edit Fish
        editInventory.setItem(11, createGuiItem(Material.WRITABLE_BOOK, "§bEdit Fish",
                "§7Modify fish configuration"));

        // Delete Fish
        editInventory.setItem(15, createGuiItem(Material.BARRIER, "§c§lDelete Fish",
                "§7Permanently remove this fish"));

        // Determine fish type (permanent or temporary)
        ItemType fishType = fishingItemManager.permanentItems.containsKey(fishId)
                ? ItemType.PERMANENT_FISH
                : ItemType.TEMPORARY_FISH;

        // Create and track edit context
        creationContexts.put(player.getUniqueId(),
                new CreationContext(fishType, fishId, true));

        player.openInventory(editInventory);
    }

    private void handleFishTypeSelection(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.getCurrentItem() != null) {
            switch (event.getCurrentItem().getType()) {
                case COD:
                    openFishCreationUI(player, ItemType.PERMANENT_FISH);
                    break;
                case SALMON:
                    openFishCreationUI(player, ItemType.TEMPORARY_FISH);
                    break;
            }
        }
    }

    private void openFishCreationUI(Player player, ItemType fishType) {
        Inventory creationInventory = Bukkit.createInventory(null, 54,
                "§9Create " + (fishType == ItemType.TEMPORARY_FISH ? "Temporary" : "Permanent") + " Fish");

        // ID Input
        creationInventory.setItem(10, createGuiItem(Material.NAME_TAG, "§bFish ID",
                "§7Click to set fish ID (lowercase, no spaces)"));

        // Display Name
        creationInventory.setItem(19, createGuiItem(Material.PAPER, "§bDisplay Name",
                "§7Click to set display name"));

        // Material
        creationInventory.setItem(28, createGuiItem(Material.COD, "§bMaterial",
                "§7Click to set fish material"));

        // Rarity
        creationInventory.setItem(37, createGuiItem(Material.GOLD_NUGGET, "§bRarity",
                "§7Click to set fish rarity"));

        // Weight Range
        creationInventory.setItem(16, createGuiItem(Material.IRON_INGOT, "§bWeight Range",
                "§7Click to set min and max weight"));

        // Catch Chance
        creationInventory.setItem(25, createGuiItem(Material.EXPERIENCE_BOTTLE, "§bCatch Chance",
                "§7Click to set catch probability"));

        // Locations
        creationInventory.setItem(34, createGuiItem(Material.MAP, "§bLocations",
                "§7Click to set available locations"));

        // Temporary Fish Specific - Duration
        if (fishType == ItemType.TEMPORARY_FISH) {
            creationInventory.setItem(43, createGuiItem(Material.CLOCK, "§bDuration",
                    "§7Click to set start and end dates"));
        }

        // Save Button
        creationInventory.setItem(53, createGuiItem(Material.EMERALD_BLOCK, "§a§lSave Fish",
                "§7Click to save the new fish"));

        // Create and track creation context
        creationContexts.put(player.getUniqueId(),
                new CreationContext(fishType, null, false));

        player.openInventory(creationInventory);
    }

    private void handleRodCreationEditClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        CreationContext context = creationContexts.get(player.getUniqueId());
        if (context == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        switch (clickedItem.getType()) {
            case NAME_TAG:
                startInputConversation(player, "Rod ID", "rod_id");
                break;
            case PAPER:
                startInputConversation(player, "Display Name", "display_name");
                break;
            case IRON_INGOT:
                startInputConversation(player, "Weight Multiplier (decimal)", "weight_multiplier");
                break;
            case EXPERIENCE_BOTTLE:
                startInputConversation(player, "Upgrades Allowed (number)", "upgrades_allowed");
                break;
            case GOLD_NUGGET:
                startInputConversation(player, "Rarity Bonus (decimal)", "rarity_bonus");
                break;
            case DIAMOND:
                startInputConversation(player, "Legendary Chance (decimal)", "legendary_chance");
                break;
            case EMERALD_BLOCK:
                saveRodConfiguration(player, context);
                break;
            case BARRIER:
                if (context.isEditing) {
                    deleteRodFromConfig(context.currentId);
                    player.sendMessage("§cFishing rod deleted successfully!");
                    player.closeInventory();
                }
                break;
        }
    }
    private void handleFishCreationEditClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        CreationContext context = creationContexts.get(player.getUniqueId());
        if (context == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        switch (clickedItem.getType()) {
            case NAME_TAG:
                startInputConversation(player, "Fish ID", "fish_id");
                break;
            case PAPER:
                startInputConversation(player, "Display Name", "display_name");
                break;
            case COD:
                startInputConversation(player, "Material (e.g., COD, SALMON)", "material");
                break;
            case GOLD_NUGGET:
                startInputConversation(player, "Rarity (COMMON, RARE, LEGENDARY)", "rarity");
                break;
            case IRON_INGOT:
                startInputConversation(player, "Minimum Weight", "min_weight");
                break;
            case EXPERIENCE_BOTTLE:
                startInputConversation(player, "Catch Chance (decimal)", "catch_chance");
                break;
            case EMERALD_BLOCK:
                saveFishConfiguration(player, context);
                break;
            case BARRIER:
                if (context.isEditing) {
                    deleteFishFromConfig(context.currentId,
                            context.type == ItemType.TEMPORARY_FISH);
                    player.sendMessage("§cFish item deleted successfully!");
                    player.closeInventory();
                }
                break;
        }
    }

    private void handleFishEditDeleteClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        CreationContext context = creationContexts.get(player.getUniqueId());
        if (context == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        switch (clickedItem.getType()) {
            case WRITABLE_BOOK:
                // Open fish creation/edit UI
                openFishCreationUI(player, context.type);
                break;
            case BARRIER:
                if (context.isEditing) {
                    boolean isTemporary = context.type == ItemType.TEMPORARY_FISH;
                    deleteFishFromConfig(context.currentId, isTemporary);
                    player.sendMessage("§cFish item deleted successfully!");
                    player.closeInventory();
                }
                break;
        }
    }

    private void startInputConversation(Player player, String prompt, String configKey) {
        Conversation conversation = conversationFactory
                .withFirstPrompt(new StringPrompt() {
                    @Override
                    public String getPromptText(ConversationContext context) {
                        return "§aEnter " + prompt + " (type /cancel to exit):";
                    }

                    @Override
                    public Prompt acceptInput(ConversationContext context, String input) {
                        if (input == null || input.trim().isEmpty()) {
                            player.sendMessage("§cInvalid input. Please try again.");
                            return this;
                        }

                        // Validate input based on config key
                        if (!validateInput(configKey, input)) {
                            player.sendMessage("§cInvalid input. Please try again.");
                            return this;
                        }

                        // Store input in pending configuration
                        pendingConfiguration
                                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                                .put(configKey, input);

                        player.sendMessage("§a" + prompt + " set successfully!");
                        return Prompt.END_OF_CONVERSATION;
                    }
                })
                .buildConversation(player);

        conversation.begin();
    }
    private boolean validateInput(String configKey, String input) {
        try {
            switch (configKey) {
                case "rod_id":
                    return input.matches("^[a-z0-9_]+$");
                case "display_name":
                    return input.length() > 0 && input.length() <= 50;
                case "weight_multiplier":
                case "rarity_bonus":
                case "legendary_chance":
                case "catch_chance":
                    return input.matches("^\\d+(\\.\\d+)?$");
                case "upgrades_allowed":
                    return input.matches("^\\d+$");
                case "material":
                    try {
                        Material.valueOf(input.toUpperCase());
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                case "rarity":
                    return input.matches("^(COMMON|RARE|LEGENDARY)$");
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Clean up creation context when inventory is closed
        creationContexts.remove(event.getPlayer().getUniqueId());
    }


    private void saveRodToConfig(String key, Map<String, Object> rodData) {
        // Load existing configuration
        File configFile = new File(plugin.getDataFolder(), "fishingrods.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Update or create rod section
        config.set("fishingrods." + key, rodData);

        try {
            config.save(configFile);
            // Reload configuration
            fishingRodsConfig.reloadConfig();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save rod configuration: " + e.getMessage());
        }
    }
    private void saveRodConfiguration(Player player, CreationContext context) {
        Map<String, Object> rodConfig = pendingConfiguration.get(player.getUniqueId());
        if (rodConfig == null || rodConfig.size() < 6) {
            player.sendMessage("§cPlease complete all rod configuration fields!");
            return;
        }

        String rodId = (String) rodConfig.get("rod_id");

        Map<String, Object> configData = new HashMap<>();
        configData.put("display_name", rodConfig.get("display_name"));
        configData.put("base_weight_multiplier",
                Double.parseDouble((String) rodConfig.get("weight_multiplier")));
        configData.put("upgrades_allowed",
                Integer.parseInt((String) rodConfig.get("upgrades_allowed")));
        configData.put("rarity_bonus",
                Double.parseDouble((String) rodConfig.get("rarity_bonus")));
        configData.put("legendary_chance",
                Double.parseDouble((String) rodConfig.get("legendary_chance")));

        saveRodToConfig(rodId, configData);
        player.sendMessage("§aRod configuration saved successfully!");
        player.closeInventory();
        pendingConfiguration.remove(player.getUniqueId());
    }
    private void saveFishToConfig(String key, Map<String, Object> fishData, boolean isTemporary) {
        // Load existing configuration
        File configFile = new File(plugin.getDataFolder(), "fish-config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Determine section based on temporary or permanent
        String section = isTemporary ? "temporary_items" : "permanent_items";

        // Update or create fish section
        config.set(section + "." + key, fishData);

        try {
            config.save(configFile);
            // Reload configuration
            fishingItemManager.loadConfiguration();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save fish configuration: " + e.getMessage());
        }
    }

    private void saveFishConfiguration(Player player, CreationContext context) {
        Map<String, Object> fishConfig = pendingConfiguration.get(player.getUniqueId());
        if (fishConfig == null || fishConfig.size() < 6) {
            player.sendMessage("§cPlease complete all fish configuration fields!");
            return;
        }

        String fishId = (String) fishConfig.get("fish_id");

        Map<String, Object> configData = new HashMap<>();
        configData.put("display_name", fishConfig.get("display_name"));
        configData.put("material", fishConfig.get("material"));
        configData.put("rarity", fishConfig.get("rarity"));
        configData.put("min_weight",
                Double.parseDouble((String) fishConfig.get("min_weight")));
        configData.put("catch_chance",
                Double.parseDouble((String) fishConfig.get("catch_chance")));

        boolean isTemporary = context.type == ItemType.TEMPORARY_FISH;
        saveFishToConfig(fishId, configData, isTemporary);
        player.sendMessage("§aFish configuration saved successfully!");
        player.closeInventory();
        pendingConfiguration.remove(player.getUniqueId());
    }
    private Map<String, Object> validateAndPrepareSaveRod(CreationContext context, Player player) {
        // Validate rod ID
        if (context.currentId == null) {
            player.sendMessage("§cPlease set a rod ID first!");
            return null;
        }

        // Prepare rod data
        Map<String, Object> rodData = new HashMap<>();

        // Example data points - you'll need to implement specific input methods
        rodData.put("display_name", "Example Rod"); // Placeholder - implement actual input
        rodData.put("base_weight_multiplier", 1.0); // Placeholder
        rodData.put("upgrades_allowed", 3); // Placeholder
        rodData.put("rarity_bonus", 0.1); // Placeholder
        rodData.put("legendary_chance", 0.01); // Placeholder

        return rodData;
    }

    private Map<String, Object> validateAndPrepareSaveFish(CreationContext context, Player player) {
        // Validate fish ID
        if (context.currentId == null) {
            player.sendMessage("§cPlease set a fish ID first!");
            return null;
        }

        // Prepare fish data
        Map<String, Object> fishData = new HashMap<>();

        // Example data points - you'll need to implement specific input methods
        fishData.put("display_name", "Example Fish"); // Placeholder
        fishData.put("material", "COD"); // Placeholder
        fishData.put("rarity", "COMMON"); // Placeholder
        fishData.put("min_weight", 0.1); // Placeholder
        fishData.put("max_weight", 5.0); // Placeholder
        fishData.put("catch_chance", 0.05); // Placeholder

        // If temporary fish, add duration
        if (context.type == ItemType.TEMPORARY_FISH) {
            fishData.put("start_date", System.currentTimeMillis());
            fishData.put("end_date", System.currentTimeMillis() + 86400000); // 24 hours from now
        }

        return fishData;
    }

    private void deleteRodFromConfig(String rodId) {
        File configFile = new File(plugin.getDataFolder(), "fishingrods.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Remove rod from configuration
        config.set("fishingrods." + rodId, null);

        try {
            config.save(configFile);
            // Reload configuration
            fishingRodsConfig.reloadConfig();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete rod configuration: " + e.getMessage());
        }
    }

    private void deleteFishFromConfig(String fishId, boolean isTemporary) {
        File configFile = new File(plugin.getDataFolder(), "fish-config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Determine section based on temporary or permanent
        String section = isTemporary ? "temporary_items" : "permanent_items";

        // Remove fish from configuration
        config.set(section + "." + fishId, null);

        try {
            config.save(configFile);
            // Reload configuration
            fishingItemManager.loadConfiguration();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete fish configuration: " + e.getMessage());
        }
    }
}