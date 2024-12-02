package jefry.plugin.betterFishing;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class FishingRodCommand implements CommandExecutor {
    private final Plugin plugin;
    private final FishingRodsConfig fishingRodsConfig;
    private final FishingManager fishingManager;
    private final FishingItemManager fishingItemManager;

    public FishingRodCommand(Plugin plugin, FishingRodsConfig fishingRodsConfig,
                             FishingManager fishingManager, FishingItemManager fishingItemManager) {
        this.plugin = plugin;
        this.fishingRodsConfig = fishingRodsConfig;
        this.fishingManager = fishingManager;
        this.fishingItemManager = fishingItemManager;
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
            fishingRodsConfig.reloadConfig();
        } catch (Exception e) {
            success = false;
            sender.sendMessage("§cError reloading fishing rods configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading fishing rods configuration: " + e.getMessage());
        }

        try {
            fishingItemManager.loadConfiguration();
        } catch (Exception e) {
            success = false;
            sender.sendMessage("§cError reloading fish configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading fish configuration: " + e.getMessage());
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

        // Determine target player
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
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
        if (!fishingRodsConfig.getAllFishingRods().containsKey(rodTier)) {
            sender.sendMessage("§cInvalid rod tier. Available tiers: " +
                    String.join(", ", fishingRodsConfig.getAllFishingRods().keySet()));
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
        if (!sender.hasPermission("betterfishing.adminRod")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reset the scoreboard.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /betterfishing reset scoreboard");
            return true;
        }

        String resetType = args[1].toLowerCase();
        if (resetType.equals("scoreboard")) {
            // Add confirmation message
            sender.sendMessage(ChatColor.GOLD + "Resetting all player fishing data...");

            // Reset all data
            BetterFishing.getScoreboardManager().resetAllData();

            // Confirmation messages
            sender.sendMessage(ChatColor.GREEN + "All fishing data has been reset!");
            sender.sendMessage(ChatColor.GRAY + "• All levels reset to 1");
            sender.sendMessage(ChatColor.GRAY + "• All XP reset to 0");
            sender.sendMessage(ChatColor.GRAY + "• All catch counts reset");
            sender.sendMessage(ChatColor.GRAY + "• All records cleared");

            // Log the action
            plugin.getLogger().info(sender.getName() + " reset all fishing scoreboard data");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid reset type. Available types: scoreboard");
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
        if (sender.hasPermission("betterfishing.adminRod")) {
            sender.sendMessage("§b/betterfishing reset scoreboard §7- Reset all scoreboard data");
        }
    }
}