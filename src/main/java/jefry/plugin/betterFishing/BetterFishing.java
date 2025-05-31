package jefry.plugin.betterFishing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterFishing extends JavaPlugin {

    private FishingManager fishingManager;
    private FishingListener fishingListener;
    private static FishingRodsConfig fishingRodsConfig;
    private static ScoreboardManager scoreboardManager;
    private FishingItemManager fishingItemManager;
    private FishingAdminManager fishingAdminManager;
    private FishingMinigameManager fishingMinigameManager;
    private PlayerInventoryManager playerInventoryManager; // Added
    private static Economy econ = null; // Added for Vault


    @Override
    public void onEnable() {
        saveDefaultConfig(); // Good to have at the top

        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault or compatible economy plugin found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Successfully hooked into Vault and an economy plugin.");


        fishingRodsConfig = new FishingRodsConfig(this);
        scoreboardManager = new ScoreboardManager(this); // Must be before PlayerInventoryManager if it uses levels
        fishingItemManager = new FishingItemManager(this);

        // Initialize PlayerInventoryManager AFTER scoreboardManager and econ
        playerInventoryManager = new PlayerInventoryManager(this, econ, scoreboardManager);

        fishingManager = new FishingManager(this, fishingRodsConfig, fishingItemManager);
        fishingMinigameManager = new FishingMinigameManager(this, fishingItemManager, scoreboardManager);
        fishingListener = new FishingListener(fishingItemManager, this, fishingMinigameManager, scoreboardManager);


        FishingRodCommand fishingRodCommand = new FishingRodCommand(
                this,
                fishingRodsConfig,
                fishingManager,
                fishingItemManager,
                playerInventoryManager
        );

        FishingRodTabCompleter tabCompleter = new FishingRodTabCompleter(fishingRodsConfig);

        PluginCommand command = getCommand("betterfishing"); // Changed from "fishrod" to "betterfishing" to match plugin.yml
        if (command != null) {
            command.setExecutor(fishingRodCommand);
            command.setTabCompleter(tabCompleter);
        } else {
            getLogger().severe("Command 'betterfishing' not found! Check your plugin.yml.");
        }

        // Register events
        getServer().getPluginManager().registerEvents(fishingManager, this);
        getServer().getPluginManager().registerEvents(fishingListener, this);
        getServer().getPluginManager().registerEvents(getFishingAdminManager(), this);
        getServer().getPluginManager().registerEvents(fishingMinigameManager, this);
        getServer().getPluginManager().registerEvents(playerInventoryManager, this); // Register new manager

        FishingRodUpgradeGUI upgradeGUI = new FishingRodUpgradeGUI(this, fishingRodsConfig);
        getServer().getPluginManager().registerEvents(upgradeGUI, this);

        getLogger().info("BetterFishing plugin has been enabled!");
    }


    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.saveData();
        }
        if (fishingMinigameManager != null) {
            // Use the method designed for shutdown to iterate safely
            fishingMinigameManager.clearAllMinigames("Plugin disabling.");
        }
        getLogger().info("BetterFishing plugin has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault plugin not found! Economy features will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("No economy provider found through Vault! Economy features will be disabled.");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
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

    public PlayerInventoryManager getPlayerInventoryManager() { // Added getter
        return playerInventoryManager;
    }
}