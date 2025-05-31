package jefry.plugin.betterFishing;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin; // Ensure this import is used

public class FishingRodCommand implements CommandExecutor {
    private final Plugin plugin; // Use Plugin interface type
    private final FishingRodsConfig fishingRodsConfig;
    private final FishingManager fishingManager;
    private final FishingItemManager fishingItemManager;
    private final PlayerInventoryManager playerInventoryManager; // Added

    public FishingRodCommand(Plugin plugin, FishingRodsConfig fishingRodsConfig,
                             FishingManager fishingManager, FishingItemManager fishingItemManager,
                             PlayerInventoryManager playerInventoryManager) { // Added PlayerInventoryManager
        this.plugin = plugin;
        this.fishingRodsConfig = fishingRodsConfig;
        this.fishingManager = fishingManager;
        this.fishingItemManager = fishingItemManager;
        this.playerInventoryManager = playerInventoryManager; // Assign it
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "give":
                return handleGive(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "backpack": // New subcommand for GUI
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can open the backpack upgrade GUI.");
                    return true;
                }
                playerInventoryManager.openUpgradeGUI((Player) sender);
                return true;
            case "adminbackpack": // New admin subcommands
                return handleAdminBackpack(sender, args);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("betterfishing.reload")) {
            sender.sendMessage("§cYou do not have permission to reload the configurations.");
            return true;
        }

        boolean success = true;
        try {
            plugin.reloadConfig();
            if (fishingRodsConfig != null) fishingRodsConfig.reloadConfig();
            if (fishingItemManager != null) fishingItemManager.loadConfiguration();
            if (plugin instanceof BetterFishing) {
                PlayerInventoryManager pim = ((BetterFishing) plugin).getPlayerInventoryManager();
                if (pim != null) {
                }
            }


        } catch (Exception e) {
            success = false;
            sender.sendMessage("§cError reloading configurations: " + e.getMessage());
            plugin.getLogger().severe("Error reloading configurations: " + e.getMessage());
            e.printStackTrace();
        }


        if (success) {
            sender.sendMessage("§aBetterFishing configurations have been reloaded successfully!");
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterfishing.give")) {
            sender.sendMessage("§cYou do not have permission to give fishing rods.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /betterfishing give <tier> [player]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cPlease specify a player name when using this command from console.");
                return true;
            }
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return true;
        }

        String rodTier = args[1].toLowerCase();
        if (fishingRodsConfig == null || !fishingRodsConfig.getAllFishingRods().containsKey(rodTier)) {
            sender.sendMessage("§cInvalid rod tier. FishingRodsConfig not loaded or tier not found.");
            if (fishingRodsConfig != null) {
                sender.sendMessage("§cAvailable tiers: " + String.join(", ", fishingRodsConfig.getAllFishingRods().keySet()));
            }
            return true;
        }

        ItemStack rod = fishingManager.createCustomRod(rodTier);
        target.getInventory().addItem(rod);

        sender.sendMessage("§aGave " + rodTier + " fishing rod to " + target.getName());
        if (!sender.equals(target)) {
            target.sendMessage("§aYou received a " + rodTier + " fishing rod!");
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterfishing.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reset the scoreboard.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /betterfishing reset scoreboard");
            return true;
        }

        String resetType = args[1].toLowerCase();
        if (resetType.equals("scoreboard")) {
            sender.sendMessage(ChatColor.GOLD + "Resetting all player fishing data...");
            BetterFishing.getScoreboardManager().resetAllData();
            sender.sendMessage(ChatColor.GREEN + "All fishing data has been reset!");
            sender.sendMessage(ChatColor.GRAY + "• All levels reset to 1");
            sender.sendMessage(ChatColor.GRAY + "• All XP reset to 0");
            sender.sendMessage(ChatColor.GRAY + "• All catch counts reset");
            sender.sendMessage(ChatColor.GRAY + "• All records cleared");
            plugin.getLogger().info(sender.getName() + " reset all fishing scoreboard data");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid reset type. Available types: scoreboard");
        return true;
    }

    private boolean handleAdminBackpack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterfishing.admin.backpack")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage player backpacks.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bf adminbackpack <setslots|unlockall> <player> [slots_amount]");
            return true;
        }

        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' not found.");
            return true;
        }

        if (action.equals("setslots")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /bf adminbackpack setslots <player> <amount>");
                return true;
            }
            try {
                int slots = Integer.parseInt(args[3]);
                // Fixed validation: slots should be between HOTBAR_SLOTS and MAX_PLAYER_INVENTORY_SLOTS
                if (slots < PlayerInventoryManager.HOTBAR_SLOTS || slots > PlayerInventoryManager.MAX_PLAYER_INVENTORY_SLOTS) {
                    sender.sendMessage(ChatColor.RED + "Slots must be between " + PlayerInventoryManager.HOTBAR_SLOTS + " and " + PlayerInventoryManager.MAX_PLAYER_INVENTORY_SLOTS + ".");
                    return true;
                }
                playerInventoryManager.adminSetSlots(target, slots);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s backpack slots to " + slots + ".");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid slot amount: " + args[3]);
            }
        } else if (action.equals("unlockall")) {
            playerInventoryManager.adminUnlockAll(target);
            sender.sendMessage(ChatColor.GREEN + "Unlocked all backpack slots for " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown admin backpack action. Use 'setslots' or 'unlockall'.");
        }
        return true;
    }


    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6BetterFishing Commands:");
        if (sender.hasPermission("betterfishing.reload")) {
            sender.sendMessage("§b/betterfishing reload §7- Reload all fishing configurations");
        }
        if (sender.hasPermission("betterfishing.give")) {
            sender.sendMessage("§b/betterfishing give <tier> [player] §7- Give a fishing rod to a player");
        }
        if (sender.hasPermission("betterfishing.admin")) { // General admin for reset
            sender.sendMessage("§b/betterfishing reset scoreboard §7- Reset all scoreboard data");
        }
        if (sender.hasPermission("betterfishing.backpack.use")) { // Player command for backpack
            sender.sendMessage("§b/betterfishing backpack §7- Open backpack upgrade GUI.");
        }
        if (sender.hasPermission("betterfishing.admin.backpack")) {
            sender.sendMessage("§b/betterfishing adminbackpack setslots <player> <amount> §7- Set player's unlocked slots.");
            sender.sendMessage("§b/betterfishing adminbackpack unlockall <player> §7- Unlock all slots for a player.");
        }
    }
}