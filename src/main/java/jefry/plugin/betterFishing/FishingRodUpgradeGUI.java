package jefry.plugin.betterFishing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FishingRodUpgradeGUI implements Listener {
    private final BetterFishing plugin;
    private final FishingRodsConfig fishingRodsConfig;
    private final FishingRodUpgradeGUI.EconomyHandler economyHandler;

    public FishingRodUpgradeGUI(BetterFishing plugin, FishingRodsConfig fishingRodsConfig) {
        this.plugin = plugin;
        this.fishingRodsConfig = fishingRodsConfig;
        this.economyHandler = new EconomyHandler(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if ((event.getAction() == Action.RIGHT_CLICK_AIR ||
                event.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                player.isSneaking() &&
                itemInHand.getType() == Material.FISHING_ROD) {
            ItemMeta meta = itemInHand.getItemMeta();
            if (meta != null && meta.hasLore()) {
                openOptionsGUI(player);
                event.setCancelled(true);
            }
        }
    }

    private void openOptionsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6Fishing Options");

        // Admin Panel Option
        ItemStack adminPanel = createGuiItem(Material.BOOK,
                "§b§lAdmin Panel",
                "§7Manage fish and fishing rods",
                "§7Only accessible to staff members");

        // Chat Settings Option
        ItemStack chatSettings = createGuiItem(Material.PAPER,
                "§a§lChat Settings",
                "§7Configure fishing-related chat messages",
                "§7Customize your fishing experience");

        // Sell All Fish Button
        ItemStack sellButton = createGuiItem(Material.EMERALD,
                "§e§lSell All Fish",
                "§7Sell all caught fish in your inventory");

        // Place items in specific slots
        gui.setItem(11, adminPanel);
        gui.setItem(13, chatSettings);
        gui.setItem(15, sellButton);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() == null) ||
                !event.getView().getTitle().equals("§6Fishing Options")) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        switch (clickedItem.getType()) {
            case BOOK:
                if (player.hasPermission("betterfishing.admin")) {
                    openAdminPanel(player);
                } else {
                    player.sendMessage("§cYou do not have permission to access the Admin Panel.");
                }
                break;
            case PAPER:
                openChatSettings(player);
                break;
            case EMERALD:
                economyHandler.sellAllFish(player);
                break;
        }
    }

    private void openAdminPanel(Player player) {
        if (!player.hasPermission("betterfishing.admin")) {
            player.sendMessage("§cYou do not have permission to access the Admin Panel.");
            return;
        }

        Inventory adminGui = Bukkit.createInventory(null, 27, "§b§lFishing Admin Panel");

        // Manage Fish Items
        ItemStack manageFish = createGuiItem(Material.COD,
                "§a§lManage Fish Items",
                "§7Add, edit, or remove fish items",
                "§7Requires betterfishing.admin.fish permission");

        // Manage Fishing Rods
        ItemStack manageRods = createGuiItem(Material.FISHING_ROD,
                "§9§lManage Fishing Rods",
                "§7Add, edit, or modify fishing rods",
                "§7Requires betterfishing.admin.rods permission");

        // Place items
        adminGui.setItem(11, manageFish);
        adminGui.setItem(15, manageRods);

        player.openInventory(adminGui);
    }

    @EventHandler
    public void onAdminPanelClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() == null) ||
                !event.getView().getTitle().equals("§b§lFishing Admin Panel")) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        switch (clickedItem.getType()) {
            case COD:
                if (player.hasPermission("betterfishing.admin.fish")) {
                    // Open fish management UI (first page)
                    plugin.getFishingAdminManager().openFishManagementUI(player, 0);
                } else {
                    player.sendMessage("§cYou do not have permission to manage fish items.");
                }
                break;
            case FISHING_ROD:
                if (player.hasPermission("betterfishing.admin.rods")) {
                    // Open rod management UI (first page)
                    plugin.getFishingAdminManager().openRodManagementUI(player, 0);
                } else {
                    player.sendMessage("§cYou do not have permission to manage fishing rods.");
                }
                break;
        }
    }
    private void openChatSettings(Player player) {
        Inventory chatGui = Bukkit.createInventory(null, 27, "§a§lFishing Chat Settings");

        // Toggle Fish Catch Announcements
        ItemStack catchAnnouncements = createGuiItem(Material.BELL,
                "§6§lCatch Announcements",
                "§7Toggle fish catch messages",
                "§7Current: §aEnabled");

        // Toggle Rare Fish Highlights
        ItemStack rareHighlights = createGuiItem(Material.NAME_TAG,
                "§d§lRare Fish Highlights",
                "§7Show special messages for rare catches",
                "§7Current: §aEnabled");

        // Place items
        chatGui.setItem(11, catchAnnouncements);
        chatGui.setItem(15, rareHighlights);

        player.openInventory(chatGui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // Existing sell functionality moved to a separate inner class
    private static class EconomyHandler {
        private final BetterFishing plugin;
        private final Economy economy;

        public EconomyHandler(BetterFishing plugin) {
            this.plugin = plugin;
            this.economy = setupEconomy();
        }

        private Economy setupEconomy() {
            RegisteredServiceProvider<Economy> economyProvider =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);

            return economyProvider != null ? economyProvider.getProvider() : null;
        }

        public void sellAllFish(Player player) {
            if (economy == null) {
                player.sendMessage("§c[BetterFishing] Economy system is not set up.");
                return;
            }

            double totalSellValue = 0;
            ItemStack[] contents = player.getInventory().getContents();
            List<Integer> fishSlots = new ArrayList<>();

            // Calculate fish sell value (reusing existing logic from previous implementation)
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && isCaughtFish(item)) {
                    double weight = extractWeight(item);
                    double basePrice = calculateFishPrice(item, weight);
                    totalSellValue += basePrice;
                    fishSlots.add(i);
                }
            }

            // Sell fish and give money
            if (totalSellValue > 0) {
                economy.depositPlayer(player, totalSellValue);

                // Remove fish from inventory
                for (int slot : fishSlots) {
                    player.getInventory().setItem(slot, null);
                }

                player.sendMessage("§a§lSold all fish for $" + String.format("%.2f", totalSellValue));
            } else {
                player.sendMessage("§cNo fish to sell!");
            }
        }

        private boolean isCaughtFish(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasLore()) return false;

            List<String> lore = meta.getLore();
            return lore != null && lore.stream().anyMatch(line -> line.contains("Weight:"));
        }

        private double extractWeight(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasLore()) return 1.0;

            for (String line : meta.getLore()) {
                if (line.contains("Weight:")) {
                    try {
                        return Double.parseDouble(line.split(":")[1].trim().replace(" kg", ""));
                    } catch (NumberFormatException e) {
                        return 1.0;
                    }
                }
            }
            return 1.0;
        }

        private double calculateFishPrice(ItemStack item, double weight) {
            double basePrice = 10; // Base price per kg

            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.contains("Quality:")) {
                        if (line.contains("Legendary")) basePrice *= 5;
                        else if (line.contains("Epic")) basePrice *= 3;
                        else if (line.contains("Rare")) basePrice *= 2;
                        else if (line.contains("Uncommon")) basePrice *= 1.5;
                        break;
                    }
                }
            }

            return basePrice * weight;
        }
    }
}