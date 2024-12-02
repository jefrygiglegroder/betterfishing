package jefry.plugin.betterFishing;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ScoreboardManager {
    private final BetterFishing plugin;
    private final File scoreboardConfigFile;
    private FileConfiguration scoreboardConfig;
    private final Map<UUID, Scoreboard> playerScoreboards;
    private final Map<UUID, Integer> totalCatchesMap;
    private final Map<UUID, Integer> legendaryFishMap;
    private final Map<UUID, Double> heaviestCatchMap;
    private final Map<UUID, Double> playerXPMap;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private BukkitRunnable updateTask;
    private static final long UPDATE_INTERVAL_TICKS = 2L;

    private static final double XP_MULTIPLIER_LEGENDARY = 5.0;
    private static final double XP_MULTIPLIER_EPIC = 3.0;
    private static final double XP_MULTIPLIER_RARE = 2.0;
    private static final double XP_MULTIPLIER_UNCOMMON = 1.5;
    private static final double XP_MULTIPLIER_COMMON = 1.0;

    private double baseXP;
    private double levelMultiplier;
    private Map<String, Double> xpMultipliers;

    public ScoreboardManager(BetterFishing plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new HashMap<>();
        this.totalCatchesMap = new HashMap<>();
        this.legendaryFishMap = new HashMap<>();
        this.heaviestCatchMap = new HashMap<>();
        this.playerXPMap = new HashMap<>();
        this.xpMultipliers = new HashMap<>();

        // Initialize config files
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.dataFile = new File(plugin.getDataFolder(), "scoreboard_data.yml");
        this.scoreboardConfigFile = new File(plugin.getDataFolder(), "scoreboard.yml");

        // Save default configs
        if (!scoreboardConfigFile.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create scoreboard_data.yml: " + e.getMessage());
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        this.scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardConfigFile);

        loadScoreboardConfig();
        loadData();
        startAutoUpdate(plugin);
    }

    private void loadScoreboardConfig() {
        this.baseXP = scoreboardConfig.getDouble("base_xp", 1000.0);
        this.levelMultiplier = scoreboardConfig.getDouble("level_multiplier", 1.2);

        // Load XP multipliers
        ConfigurationSection multipliers = scoreboardConfig.getConfigurationSection("xp_multipliers");
        if (multipliers != null) {
            for (String key : multipliers.getKeys(false)) {
                xpMultipliers.put(key.toLowerCase(), multipliers.getDouble(key));
            }
        }
    }

    public void reloadConfig() {
        scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardConfigFile);
        loadScoreboardConfig();
    }

    public void resetAllData() {
        // Clear all data maps
        totalCatchesMap.clear();
        legendaryFishMap.clear();
        heaviestCatchMap.clear();
        playerXPMap.clear();

        // Reset scoreboards for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Reset all stats to default values
            totalCatchesMap.put(playerId, 0);
            legendaryFishMap.put(playerId, 0);
            heaviestCatchMap.put(playerId, 0.0);
            playerXPMap.put(playerId, 0.0);

            // Update their scoreboard
            smoothUpdateScoreboard(player);

            // Notify player
            player.sendMessage(ChatColor.RED + "Your fishing data has been reset by an administrator.");
        }

        // Delete and recreate data file
        try {
            if (dataFile.exists()) {
                dataFile.delete();
            }
            dataFile.createNewFile();
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            saveData(); // Save empty data to file

            plugin.getLogger().info("Scoreboard data has been reset for all players");
        } catch (IOException e) {
            plugin.getLogger().severe("Error resetting scoreboard data: " + e.getMessage());
        }
    }

    private void startAutoUpdate(BetterFishing plugin) {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack heldItem = player.getInventory().getItemInMainHand();
                    UUID playerId = player.getUniqueId();

                    // Check if the player is holding a fishing rod
                    if (isFishingRod(heldItem)) {
                        // If scoreboard doesn't exist, create it
                        if (!playerScoreboards.containsKey(playerId)) {
                            createScoreboard(player);
                        }
                    }
                    // If not holding a fishing rod and has a scoreboard, remove it
                    else if (playerScoreboards.containsKey(playerId)) {
                        removeScoreboard(player);
                    }
                }
            }
        };

        // Reduced update frequency and added a slight delay
        updateTask.runTaskTimer(plugin, 20L, 20L); // Once per second
    }

    private boolean isFishingRod(ItemStack item) {
        return item != null && item.getType().name().contains("FISHING_ROD");
    }

    public void smoothUpdateScoreboard(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            Scoreboard scoreboard = playerScoreboards.get(playerId);

            if (scoreboard == null) {
                createScoreboard(player);
                return;
            }

            Objective objective = scoreboard.getObjective("fishing_stats");
            if (objective == null) {
                objective = scoreboard.registerNewObjective(
                        "fishing_stats",
                        "dummy",
                        ChatColor.AQUA + "Fishing Stats"
                );
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            int totalCatches = totalCatchesMap.getOrDefault(playerId, 0);
            int legendaryFish = legendaryFishMap.getOrDefault(playerId, 0);
            double heaviestCatch = heaviestCatchMap.getOrDefault(playerId, 0.0);
            double currentXP = playerXPMap.getOrDefault(playerId, 0.0);

            int fishingLevel = calculateFishingLevel(currentXP);
            double currentLevelTotal = getLevelThreshold(fishingLevel + 1) - getLevelThreshold(fishingLevel);
            double currentLevelProgress = currentXP - getLevelThreshold(fishingLevel);

            // Remove old scores to prevent lingering entries
            scoreboard.getEntries().forEach(entry -> scoreboard.resetScores(entry));

            // Create lines using unique color codes as identifiers
            String[] lines = new String[] {
                    // Level
                    ChatColor.YELLOW + "⚡ Level: " + ChatColor.GOLD + fishingLevel,

                    // XP
                    ChatColor.GRAY + "XP: " + ChatColor.WHITE +
                            String.format("%,.1f / %,.1f", currentLevelProgress, currentLevelTotal),

                    // Progress bar
                    ChatColor.GREEN + "Progress: " + generateProgressBar((int)currentLevelProgress, (int)currentLevelTotal),

                    // Spacer 1
                    ChatColor.DARK_GRAY + "----------------------",

                    // Catches
                    ChatColor.YELLOW + "Catches: " + ChatColor.WHITE + totalCatches,

                    // Legendary
                    ChatColor.LIGHT_PURPLE + "Legendary: " + ChatColor.WHITE + legendaryFish,

                    // Spacer 2
                    ChatColor.DARK_GRAY + "----------------------",

                    // Record
                    ChatColor.AQUA + "Record: " + ChatColor.WHITE + String.format("%,.2f kg", heaviestCatch)
            };

            // Set scores using color codes as unique identifiers
            for (int i = 0; i < lines.length; i++) {
                String entry = ChatColor.values()[i].toString();  // Use color code as unique identifier
                objective.getScore(entry).setScore(15 - i);

                Team team = scoreboard.getTeam("line" + i);
                if (team == null) {
                    team = scoreboard.registerNewTeam("line" + i);
                }
                team.addEntry(entry);

                // Split text if needed (Minecraft has a 64 character limit for team prefix/suffix)
                if (lines[i].length() > 64) {
                    team.setPrefix(lines[i].substring(0, 64));
                    team.setSuffix(lines[i].substring(64));
                } else {
                    team.setPrefix(lines[i]);
                    team.setSuffix("");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error updating scoreboard for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public int calculateFishingLevel(double totalXP) {
        int level = 1;
        double currentThreshold = baseXP;
        double totalNeededXP = currentThreshold;

        while (totalXP >= totalNeededXP) {
            level++;
            currentThreshold *= levelMultiplier;
            totalNeededXP += currentThreshold;
        }

        return Math.max(1, level);
    }

    private void updateScore(Objective objective, String key, String prefix, String value, int score) {
        Scoreboard scoreboard = objective.getScoreboard();
        Team team = scoreboard.getTeam(key);

        if (team == null) {
            team = scoreboard.registerNewTeam(key);
        }

        String entry = ChatColor.values()[score % ChatColor.values().length] + key;

        team.setPrefix(prefix);
        team.setSuffix(value);

        if (!team.hasEntry(entry)) {
            team.getEntries().forEach(team::removeEntry);
            team.addEntry(entry);
        }

        objective.getScore(entry).setScore(score);
    }

    private void clearUnusedEntries(Scoreboard scoreboard, Set<String> activeKeys) {
        for (String entry : scoreboard.getEntries()) {
            if (!activeKeys.contains(entry)) {
                scoreboard.resetScores(entry);
            }
        }
    }

    public void createScoreboard(Player player) {
        try {
            org.bukkit.scoreboard.ScoreboardManager bukkitManager = Bukkit.getScoreboardManager();
            if (bukkitManager == null) {
                plugin.getLogger().warning("Failed to get Scoreboard Manager for player " + player.getName());
                return;
            }

            Scoreboard scoreboard = bukkitManager.getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("fishing_stats", "dummy",
                    ChatColor.AQUA + "" + ChatColor.BOLD + "Fishing Stats");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            UUID playerId = player.getUniqueId();
            totalCatchesMap.putIfAbsent(playerId, 0);
            legendaryFishMap.putIfAbsent(playerId, 0);
            heaviestCatchMap.putIfAbsent(playerId, 0.0);
            playerXPMap.putIfAbsent(playerId, 0.0);

            playerScoreboards.put(playerId, scoreboard);
            player.setScoreboard(scoreboard);

            smoothUpdateScoreboard(player);
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating scoreboard for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateScoreboard(Player player) {
        smoothUpdateScoreboard(player);
    }

    public double getHeaviestCatch(Player player) {
        return heaviestCatchMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    public Double getPlayerXP(Player player) {
        return playerXPMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    private double getLevelThreshold(int level) {
        if (level <= 1) return 0;

        double totalXpNeeded = baseXP;
        double currentThreshold = baseXP;

        for (int i = 2; i < level; i++) {
            currentThreshold *= levelMultiplier;
            totalXpNeeded += currentThreshold;
        }

        return totalXpNeeded;
    }


    private String generateProgressBar(int current, int max) {
        final int BAR_LENGTH = 10;
        double progress = Math.min(1.0, Math.max(0.0, (double) current / max));
        int filledBars = (int) (progress * BAR_LENGTH);

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < filledBars) {
                bar.append(ChatColor.GREEN + "▮"); // Solid block for filled
            } else {
                bar.append(ChatColor.GRAY + "▯"); // Empty block
            }
        }

        return bar.toString();
    }
    public void incrementTotalCatches(Player player) {
        UUID playerId = player.getUniqueId();
        totalCatchesMap.merge(playerId, 1, Integer::sum);
        updateScoreboard(player);
    }

    public void incrementLegendaryFish(Player player) {
        UUID playerId = player.getUniqueId();
        legendaryFishMap.merge(playerId, 1, Integer::sum);
        updateScoreboard(player);
    }

    public void updateHeaviestCatch(Player player, double weight) {
        UUID playerId = player.getUniqueId();
        double currentHeaviest = heaviestCatchMap.getOrDefault(playerId, 0.0);
        if (weight > currentHeaviest) {
            heaviestCatchMap.put(playerId, weight);
            updateScoreboard(player);
        }
    }

    public void removeScoreboard(Player player) {
        UUID playerId = player.getUniqueId();
        playerScoreboards.remove(playerId);

        // Reset player's scoreboard to the default server scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void loadData() {
        for (String uuidStr : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = uuidStr + ".";
                totalCatchesMap.put(uuid, dataConfig.getInt(path + "totalCatches", 0));
                legendaryFishMap.put(uuid, dataConfig.getInt(path + "legendaryFish", 0));
                heaviestCatchMap.put(uuid, dataConfig.getDouble(path + "heaviestCatch", 0.0));
                playerXPMap.put(uuid, dataConfig.getDouble(path + "xp", 0.0));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in scoreboard_data.yml: " + uuidStr);
            }
        }
    }

    public void saveData() {
        for (UUID uuid : totalCatchesMap.keySet()) {
            String path = uuid.toString() + ".";
            dataConfig.set(path + "totalCatches", totalCatchesMap.get(uuid));
            dataConfig.set(path + "legendaryFish", legendaryFishMap.get(uuid));
            dataConfig.set(path + "heaviestCatch", heaviestCatchMap.get(uuid));
            dataConfig.set(path + "xp", playerXPMap.getOrDefault(uuid, 0.0));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save scoreboard data: " + e.getMessage());
        }
    }

    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        String path = uuid.toString() + ".";
        dataConfig.set(path + "totalCatches", totalCatchesMap.getOrDefault(uuid, 0));
        dataConfig.set(path + "legendaryFish", legendaryFishMap.getOrDefault(uuid, 0));
        dataConfig.set(path + "heaviestCatch", heaviestCatchMap.getOrDefault(uuid, 0.0));
        dataConfig.set(path + "xp", playerXPMap.getOrDefault(uuid, 0.0));

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player scoreboard data: " + e.getMessage());
        }
    }

    private double calculateXPGain(String quality, double weight) {
        double baseXP = weight * 100;
        String cleanQuality = ChatColor.stripColor(quality).toLowerCase();

        double multiplier = xpMultipliers.getOrDefault(cleanQuality, 1.0);

        double xpGain = baseXP * multiplier;
        plugin.getLogger().info("Calculated XP gain: " + xpGain + " (base XP: " + baseXP + ", quality: " + quality + ", multiplier: " + multiplier + ")");
        return xpGain;
    }

    public void addXP(Player player, String quality, double weight) {
        UUID playerId = player.getUniqueId();
        double currentXP = playerXPMap.getOrDefault(playerId, 0.0);
        double xpGain = calculateXPGain(quality, weight);

        double newXP = currentXP + xpGain;
        playerXPMap.put(playerId, newXP);
        plugin.getLogger().info("Player " + player.getName() + " gained " + xpGain + " XP. New total XP: " + newXP);

        player.sendMessage(
                ChatColor.GREEN + "+" + String.format("%.1f", xpGain) + " XP" +
                        ChatColor.GRAY + " (" + quality + ChatColor.GRAY + " fish, " +
                        String.format("%.2f", weight) + " kg)"
        );

        savePlayerData(player);
        updateScoreboard(player);
    }
}