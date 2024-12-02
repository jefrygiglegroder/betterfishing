package jefry.plugin.betterFishing;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FishingRodTabCompleter implements TabCompleter {
    private final FishingRodsConfig fishingRodsConfig;

    // Base subcommands
    private final List<String> BASE_COMMANDS = Arrays.asList("give", "reload", "reset");
    // Reset subcommands
    private final List<String> RESET_OPTIONS = Arrays.asList("scoreboard");

    public FishingRodTabCompleter(FishingRodsConfig fishingRodsConfig) {
        this.fishingRodsConfig = fishingRodsConfig;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // Check permissions first
        if (!hasAnyPermission(sender)) {
            return completions;
        }

        if (args.length == 1) {
            // First argument - show available subcommands based on permissions
            List<String> availableCommands = new ArrayList<>();
            if (sender.hasPermission("betterfishing.give")) {
                availableCommands.add("give");
            }
            if (sender.hasPermission("betterfishing.reload")) {
                availableCommands.add("reload");
            }
            if (sender.hasPermission("betterfishing.admin")) {
                availableCommands.add("reset");
            }
            StringUtil.copyPartialMatches(args[0], availableCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("betterfishing.give")) {
                // Second argument for 'give' - show available rod tiers
                StringUtil.copyPartialMatches(args[1],
                        fishingRodsConfig.getAllFishingRods().keySet()
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toList()),
                        completions);
            } else if (args[0].equalsIgnoreCase("reset") && sender.hasPermission("betterfishing.admin")) {
                // Second argument for 'reset' - show reset options
                StringUtil.copyPartialMatches(args[1], RESET_OPTIONS, completions);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && !(sender instanceof Player)) {
            // Third argument for 'give' when used from console - show online players
            StringUtil.copyPartialMatches(args[2],
                    sender.getServer().getOnlinePlayers()
                            .stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()),
                    completions);
        }

        return completions;
    }

    private boolean hasAnyPermission(CommandSender sender) {
        return sender.hasPermission("betterfishing.give") ||
                sender.hasPermission("betterfishing.reload") ||
                sender.hasPermission("betterfishing.admin");
    }
}