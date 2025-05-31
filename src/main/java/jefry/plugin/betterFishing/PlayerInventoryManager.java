package jefry.plugin.betterFishing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;

import java.util.*;

public class PlayerInventoryManager implements Listener {

    private final BetterFishing plugin;
    private final Economy economy;
    private final ScoreboardManager scoreboardManager;

    private final List<BackpackUpgrade> upgrades = new ArrayList<>();
    public static final int SLOTS_PER_UPGRADE = 9;
    public static final int MAX_PLAYER_INVENTORY_SLOTS = 36;
    public static final int HOTBAR_SLOTS = 9;

    private final NamespacedKey unlockedSlotsKey;
    private final NamespacedKey lockedSlotKey;

    private static final String UPGRADE_GUI_TITLE = "§5Backpack Upgrades";
    private final Map<UUID, Inventory> openUpgradeGUIs = new HashMap<>();

    public PlayerInventoryManager(BetterFishing plugin, Economy economy, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.scoreboardManager = scoreboardManager;
        this.unlockedSlotsKey = new NamespacedKey(plugin, "unlocked_inventory_slots");
        this.lockedSlotKey = new NamespacedKey(plugin, "locked_slot_marker");
        loadUpgradeConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadUpgradeConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection upgradeSection = config.getConfigurationSection("backpack_upgrades");
        if (upgradeSection == null) {
            plugin.getLogger().warning("Backpack upgrades section not found in config.yml! Using defaults.");
            upgrades.add(new BackpackUpgrade(1, 5, 10000, "Unlock 9 more slots (Tier 1)"));
            upgrades.add(new BackpackUpgrade(2, 15, 50000, "Unlock another 9 slots (Tier 2)"));
            upgrades.add(new BackpackUpgrade(3, 30, 150000, "Unlock final 9 slots (Tier 3)"));
            return;
        }

        for (String key : upgradeSection.getKeys(false)) {
            try {
                int tier = Integer.parseInt(key);
                int requiredLevel = upgradeSection.getInt(key + ".required_level");
                double cost = upgradeSection.getDouble(key + ".cost");
                String description = ChatColor.translateAlternateColorCodes('&', upgradeSection.getString(key + ".description", "Unlock more slots"));
                upgrades.add(new BackpackUpgrade(tier, requiredLevel, cost, description));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid tier number in backpack_upgrades: " + key);
            }
        }
        upgrades.sort(Comparator.comparingInt(BackpackUpgrade::getTier));
        plugin.getLogger().info("Loaded " + upgrades.size() + " backpack upgrades.");
    }

    public int getUnlockedSlots(Player player) {
        return player.getPersistentDataContainer().getOrDefault(unlockedSlotsKey, PersistentDataType.INTEGER, HOTBAR_SLOTS);
    }

    public void setUnlockedSlots(Player player, int slots) {
        int newSlots = Math.min(MAX_PLAYER_INVENTORY_SLOTS, Math.max(HOTBAR_SLOTS, slots));
        player.getPersistentDataContainer().set(unlockedSlotsKey, PersistentDataType.INTEGER, newSlots);
        updateInventoryBarriers(player);
    }

    public int getCurrentUpgradeTier(Player player) {
        int unlocked = getUnlockedSlots(player);
        if (unlocked <= HOTBAR_SLOTS) return 0;
        return (unlocked - HOTBAR_SLOTS) / SLOTS_PER_UPGRADE;
    }

    public BackpackUpgrade getNextUpgrade(Player player) {
        int currentTier = getCurrentUpgradeTier(player);
        for (BackpackUpgrade upgrade : upgrades) {
            if (upgrade.getTier() == currentTier + 1) {
                return upgrade;
            }
        }
        return null;
    }

    private boolean isLockedSlot(int slot, Player player) {
        if (slot < HOTBAR_SLOTS) return false;

        if (slot >= HOTBAR_SLOTS && slot < MAX_PLAYER_INVENTORY_SLOTS) {
            int unlockedSlots = getUnlockedSlots(player);
            return slot >= unlockedSlots;
        }

        return false;
    }

    private ItemStack createBarrierItem() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Locked Slot");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "This slot is locked!",
                    ChatColor.YELLOW + "Upgrade your backpack to unlock it."
            ));
            meta.getPersistentDataContainer().set(lockedSlotKey, PersistentDataType.BYTE, (byte) 1);
            barrier.setItemMeta(meta);
        }
        return barrier;
    }

    private boolean isBarrierItem(ItemStack item) {
        if (item == null || item.getType() != Material.BARRIER) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(lockedSlotKey, PersistentDataType.BYTE);
    }

    public void updateInventoryBarriers(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Inventory inventory = player.getInventory();
                int unlockedSlots = getUnlockedSlots(player);

                for (int i = HOTBAR_SLOTS; i < MAX_PLAYER_INVENTORY_SLOTS; i++) {
                    if (i >= unlockedSlots) {
                        // Slot is locked - place barrier if not already there
                        ItemStack currentItem = inventory.getItem(i);
                        if (currentItem == null || !isBarrierItem(currentItem)) {
                            // Only place barrier if slot is truly empty or doesn't contain a barrier
                            if (currentItem == null || currentItem.getType() == Material.AIR) {
                                inventory.setItem(i, createBarrierItem());
                            }
                        }
                    } else {
                        ItemStack currentItem = inventory.getItem(i);
                        if (isBarrierItem(currentItem)) {
                            inventory.setItem(i, null);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getView().getTitle().equals(UPGRADE_GUI_TITLE)) {
            handleUpgradeGUIClick(event);
            return;
        }

        // Only handle player inventory clicks
        if (event.getClickedInventory() != player.getInventory()) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        int slot = event.getSlot();

        ItemStack clickedItem = event.getCurrentItem();
        if (isBarrierItem(clickedItem)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "This inventory slot is locked! Upgrade your backpack to unlock it.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (isLockedSlot(slot, player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "This inventory slot is locked! Upgrade your backpack to unlock it.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getInventory().getItem(slot) == null || player.getInventory().getItem(slot).getType() == Material.AIR) {
                        player.getInventory().setItem(slot, createBarrierItem());
                    }
                }
            }.runTaskLater(plugin, 1L);
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != player.getInventory()) {
            ItemStack itemToMove = event.getCurrentItem();
            if (itemToMove != null && itemToMove.getType() != Material.AIR) {
                // Check if there's space in unlocked slots
                if (!hasSpaceInUnlockedSlots(player, itemToMove)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Not enough space in unlocked inventory slots!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= player.getInventory().getSize()) continue;

            int slot = rawSlot;
            if (event.getView().getType() != InventoryType.CRAFTING) {
                if (rawSlot < event.getView().getTopInventory().getSize()) continue;
                slot = rawSlot - event.getView().getTopInventory().getSize();
            }

            if (isLockedSlot(slot, player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Cannot drag items into locked slots! Upgrade your backpack.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }
    }

    private boolean hasSpaceInUnlockedSlots(Player player, ItemStack item) {
        Inventory inventory = player.getInventory();
        int unlockedSlots = getUnlockedSlots(player);

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }

        // Check unlocked main inventory slots
        for (int i = HOTBAR_SLOTS; i < unlockedSlots; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType() == Material.AIR || isBarrierItem(slot)) return true;
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.getPersistentDataContainer().has(unlockedSlotsKey, PersistentDataType.INTEGER)) {
            setUnlockedSlots(player, HOTBAR_SLOTS);
            player.sendMessage(ChatColor.GOLD + "Welcome! Your backpack currently has " + HOTBAR_SLOTS + " slots available.");
        } else {
            // Update barriers on join
            updateInventoryBarriers(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                updateInventoryBarriers(player);
            }
        }.runTaskLater(plugin, 2L);
    }

    public void openUpgradeGUI(Player player) {
        BackpackUpgrade nextUpgrade = getNextUpgrade(player);
        int currentUnlocked = getUnlockedSlots(player);

        Inventory gui = Bukkit.createInventory(null, 27, UPGRADE_GUI_TITLE);

        if (nextUpgrade != null) {
            ItemStack upgradeItem = new ItemStack(Material.ENDER_CHEST);
            ItemMeta meta = upgradeItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "Upgrade Backpack (Tier " + nextUpgrade.getTier() + ")");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + nextUpgrade.getDescription());
            lore.add(ChatColor.YELLOW + "Required Level: " + ChatColor.AQUA + nextUpgrade.getRequiredLevel());
            lore.add(ChatColor.YELLOW + "Cost: " + ChatColor.GOLD + String.format("%,.2f", nextUpgrade.getCost()));
            lore.add(" ");
            int fishingLevel = scoreboardManager.calculateFishingLevel(scoreboardManager.getPlayerXP(player));
            boolean canAfford = economy != null && economy.has(player, nextUpgrade.getCost());
            boolean levelMet = fishingLevel >= nextUpgrade.getRequiredLevel();

            if (levelMet && canAfford) {
                lore.add(ChatColor.GREEN + "Click to purchase!");
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, false);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                if (!levelMet) lore.add(ChatColor.RED + "Level not met! (Current: " + fishingLevel + ")");
                if (!canAfford) lore.add(ChatColor.RED + "Not enough money!");
                lore.add(ChatColor.GRAY + "You cannot afford this upgrade yet.");
            }
            meta.setLore(lore);
            upgradeItem.setItemMeta(meta);
            gui.setItem(13, upgradeItem);
        } else {
            ItemStack maxedItem = new ItemStack(Material.DIAMOND_BLOCK);
            ItemMeta meta = maxedItem.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Backpack Fully Upgraded!");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "You have unlocked all available slots."));
            maxedItem.setItemMeta(meta);
            gui.setItem(13, maxedItem);
        }

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Current Backpack Info");
        infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Unlocked Slots: " + ChatColor.YELLOW + currentUnlocked + " / " + MAX_PLAYER_INVENTORY_SLOTS,
                ChatColor.GRAY + "Current Tier: " + ChatColor.YELLOW + getCurrentUpgradeTier(player)
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        player.openInventory(gui);
        openUpgradeGUIs.put(player.getUniqueId(), gui);
    }

    public void handleUpgradeGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ENDER_CHEST && clickedItem.hasItemMeta() &&
                clickedItem.getItemMeta().getDisplayName().contains("Upgrade Backpack")) {

            BackpackUpgrade nextUpgrade = getNextUpgrade(player);
            if (nextUpgrade == null) {
                player.sendMessage(ChatColor.RED + "Error: No upgrade available or already maxed.");
                player.closeInventory();
                return;
            }

            int fishingLevel = scoreboardManager.calculateFishingLevel(scoreboardManager.getPlayerXP(player));
            if (fishingLevel < nextUpgrade.getRequiredLevel()) {
                player.sendMessage(ChatColor.RED + "You need to be fishing level " + nextUpgrade.getRequiredLevel() + " to purchase this upgrade.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                openUpgradeGUI(player);
                return;
            }

            if (economy == null || !economy.has(player, nextUpgrade.getCost())) {
                player.sendMessage(ChatColor.RED + "You do not have enough money! Cost: " + String.format("%,.2f", nextUpgrade.getCost()));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                openUpgradeGUI(player);
                return;
            }

            economy.withdrawPlayer(player, nextUpgrade.getCost());
            int currentSlots = getUnlockedSlots(player);
            setUnlockedSlots(player, currentSlots + SLOTS_PER_UPGRADE);

            player.sendMessage(ChatColor.GREEN + "Backpack upgraded! You now have " + getUnlockedSlots(player) + " slots available.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onUpgradeGUIClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(UPGRADE_GUI_TITLE)) {
            openUpgradeGUIs.remove(event.getPlayer().getUniqueId());
        }
    }

    public void adminSetSlots(Player target, int slots) {
        setUnlockedSlots(target, slots);
        target.sendMessage(ChatColor.GOLD + "Your backpack slots have been set to " + slots + " by an admin.");
        plugin.getLogger().info("Admin set " + target.getName() + "'s backpack slots to " + slots);
    }

    public void adminUnlockAll(Player target) {
        setUnlockedSlots(target, MAX_PLAYER_INVENTORY_SLOTS);
        target.sendMessage(ChatColor.GOLD + "All your backpack slots have been unlocked by an admin.");
        plugin.getLogger().info("Admin unlocked all backpack slots for " + target.getName());
    }

    private static class BackpackUpgrade {
        private final int tier;
        private final int requiredLevel;
        private final double cost;
        private final String description;

        public BackpackUpgrade(int tier, int requiredLevel, double cost, String description) {
            this.tier = tier;
            this.requiredLevel = requiredLevel;
            this.cost = cost;
            this.description = description;
        }

        public int getTier() { return tier; }
        public int getRequiredLevel() { return requiredLevel; }
        public double getCost() { return cost; }
        public String getDescription() { return description; }
    }
}