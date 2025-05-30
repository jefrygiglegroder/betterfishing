package jefry.plugin.betterFishing;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BetterFishing extends JavaPlugin {

    private FishingManager fishingManager;
    private FishingListener fishingListener;
    private static FishingRodsConfig fishingRodsConfig;
    private static ScoreboardManager scoreboardManager;
    private FishingItemManager fishingItemManager;
    private FishingAdminManager fishingAdminManager;
    private FishingMinigameManager fishingMinigameManager; // <<< NEW


    @Override
    public void onEnable() {
        // Save default config file if not exists
        saveDefaultConfig();

        // Initialize fishing rods configuration
        fishingRodsConfig = new FishingRodsConfig(this);

        // Initialize scoreboard manager
        scoreboardManager = new ScoreboardManager(this);

        // Initialize fishing item manager
        fishingItemManager = new FishingItemManager(this);

        // Initialize fishing manager
        fishingManager = new FishingManager(this, fishingRodsConfig, fishingItemManager);

        fishingMinigameManager = new FishingMinigameManager(this, fishingItemManager, scoreboardManager);

        fishingListener = new FishingListener(fishingItemManager, this, fishingMinigameManager, scoreboardManager);


        FishingRodCommand fishingRodCommand = new FishingRodCommand(
                this,
                fishingRodsConfig,
                fishingManager,
                fishingItemManager
        );

        FishingRodTabCompleter tabCompleter = new FishingRodTabCompleter(fishingRodsConfig);

        PluginCommand command = getCommand("betterfishing");
        if (command != null) {
            command.setExecutor(fishingRodCommand);
            command.setTabCompleter(tabCompleter);
        }

        // Register events
        getServer().getPluginManager().registerEvents(fishingManager, this);
        getServer().getPluginManager().registerEvents(fishingListener, this);
        getServer().getPluginManager().registerEvents(getFishingAdminManager(), this);
        getServer().getPluginManager().registerEvents(fishingMinigameManager, this); // <<< NEW: Register minigame manager for its events

        // Register Fishing Rod Upgrade GUI
        FishingRodUpgradeGUI upgradeGUI = new FishingRodUpgradeGUI(this, fishingRodsConfig);
        getServer().getPluginManager().registerEvents(upgradeGUI, this);

        getLogger().info("BetterFishing plugin has been enabled!");
    }


    @Override
    public void onDisable() {
        // Save scoreboard data when the plugin disables
        if (scoreboardManager != null) {
            scoreboardManager.saveData();
        }
        if (fishingMinigameManager != null) {
            fishingMinigameManager.cancelAllMinigamesForShutdown();
        }
        getLogger().info("BetterFishing plugin has been disabled!");
    }

    public static ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    public static FishingRodsConfig getFishingRodsConfig() {
        return fishingRodsConfig;
    }

    public FishingAdminManager getFishingAdminManager() {
        if (fishingAdminManager == null) {
            fishingAdminManager = new FishingAdminManager(this, getFishingRodsConfig(), getFishingItemManager());
        }
        return fishingAdminManager;
    }

    private FishingItemManager getFishingItemManager() {
        return fishingItemManager;
    }

    public FishingMinigameManager getFishingMinigameManager() {
        return fishingMinigameManager;
    }
}