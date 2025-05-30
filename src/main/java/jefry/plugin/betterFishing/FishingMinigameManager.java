package jefry.plugin.betterFishing;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FishingMinigameManager implements Listener {

    private final BetterFishing plugin;
    private final FishingItemManager fishingItemManager;
    private final ScoreboardManager scoreboardManager;

    private final Map<UUID, MinigameInstance> activeMinigames = new HashMap<>();

    private static final int BAR_LENGTH = 40;
    private static final String INDICATOR_CHAR = "▌";
    private static final String BAR_CHAR = "-";
    private static final String SWEET_SPOT_CHAR_OPEN = "[";
    private static final String SWEET_SPOT_CHAR_CLOSE = "]";

    private static final Map<String, DifficultySettings> RARITY_DIFFICULTY = new HashMap<>();

    static {
        RARITY_DIFFICULTY.put("COMMON", new DifficultySettings(8, 3, 12)); // Large sweet spot, slow speed, long timeout
        RARITY_DIFFICULTY.put("UNCOMMON", new DifficultySettings(6, 2, 10)); // Medium sweet spot, medium speed, medium timeout
        RARITY_DIFFICULTY.put("RARE", new DifficultySettings(5, 2, 8)); // Smaller sweet spot, faster speed, shorter timeout
        RARITY_DIFFICULTY.put("EPIC", new DifficultySettings(4, 1, 7)); // Small sweet spot, fast speed, short timeout
        RARITY_DIFFICULTY.put("LEGENDARY", new DifficultySettings(3, 1, 6)); // Very small sweet spot, very fast speed, very short timeout
        RARITY_DIFFICULTY.put("MYTHIC", new DifficultySettings(2, 1, 5)); // Tiny sweet spot, very fast speed, very short timeout

        RARITY_DIFFICULTY.put("DEFAULT", new DifficultySettings(6, 2, 10));
    }

    public FishingMinigameManager(BetterFishing plugin, FishingItemManager fishingItemManager, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.fishingItemManager = fishingItemManager;
        this.scoreboardManager = scoreboardManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startMinigame(Player player, Item caughtEntity, ItemStack potentialCatch) {
        if (activeMinigames.containsKey(player.getUniqueId())) {
            caughtEntity.remove();
            return;
        }

        MinigameInstance instance = new MinigameInstance(player, caughtEntity, potentialCatch);
        activeMinigames.put(player.getUniqueId(), instance);
        instance.start();
    }

    public boolean isPlayerInMinigame(Player player) {
        return activeMinigames.containsKey(player.getUniqueId());
    }

    public void cancelPlayerMinigame(Player player, boolean silent, String reason) {
        MinigameInstance instance = activeMinigames.remove(player.getUniqueId());
        if (instance != null) {
            instance.cancel(silent, reason);
        }
    }

    public void clearAllMinigames(String reason) {
        for (UUID playerId : new HashMap<>(activeMinigames).keySet()) { // Iterate over a copy
            MinigameInstance instance = activeMinigames.remove(playerId);
            if (instance != null) {
                instance.cancel(true, reason);
            }
        }
        activeMinigames.clear();
    }

    public void cancelAllMinigamesForShutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInMinigame(player)) {
                cancelPlayerMinigame(player, true, "Plugin disabling.");
            }
        }
        activeMinigames.clear();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerInMinigame(player)) {
            return;
        }
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            MinigameInstance instance = activeMinigames.get(player.getUniqueId());
            if (instance != null) {
                instance.attemptCatch();
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MinigameInstance instance = activeMinigames.remove(event.getPlayer().getUniqueId());
        if (instance != null) {
            instance.cancel(true, "You left the game.");
        }
    }

    private String extractRarity(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String loreLine : item.getItemMeta().getLore()) {
                String strippedLine = ChatColor.stripColor(loreLine);
                if (strippedLine.startsWith("Quality:")) {
                    return strippedLine.substring("Quality:".length()).trim().toUpperCase();
                }
            }
        }
        return "COMMON"; //
    }

    private static class DifficultySettings {
        final int sweetSpotSize;
        final int indicatorSpeedTicks;
        final int timeoutSeconds;

        DifficultySettings(int sweetSpotSize, int indicatorSpeedTicks, int timeoutSeconds) {
            this.sweetSpotSize = sweetSpotSize;
            this.indicatorSpeedTicks = indicatorSpeedTicks;
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    class MinigameInstance {
        private final Player player;
        private final Item caughtItemEntity;
        private final ItemStack potentialCustomCatch;
        private final DifficultySettings difficulty;
        private final String fishRarity;

        private BossBar bossBar;
        private BukkitTask barUpdateTask;
        private BukkitTask timeoutTask;

        private int indicatorPosition = 0;
        private boolean movingRight = true;
        private final int sweetSpotStart;

        public MinigameInstance(Player player, Item caughtItemEntity, ItemStack potentialCustomCatch) {
            this.player = player;
            this.caughtItemEntity = caughtItemEntity;
            this.potentialCustomCatch = potentialCustomCatch;
            this.fishRarity = extractRarity(potentialCustomCatch);
            this.difficulty = RARITY_DIFFICULTY.getOrDefault(fishRarity, RARITY_DIFFICULTY.get("DEFAULT"));
            this.sweetSpotStart = ThreadLocalRandom.current().nextInt(0, BAR_LENGTH - difficulty.sweetSpotSize + 1);
        }

        public void start() {
            // Create boss bar with rarity-specific color and title
            ChatColor rarityColor = getRarityColor(fishRarity);
            String title = rarityColor + "Reel it in! (" + fishRarity + ")";

            bossBar = Bukkit.createBossBar(title, getRarityBarColor(fishRarity), BarStyle.SOLID);
            bossBar.addPlayer(player);
            updateBarVisual();

            barUpdateTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (movingRight) {
                        indicatorPosition++;
                        if (indicatorPosition >= BAR_LENGTH - 1) {
                            movingRight = false;
                        }
                    } else {
                        indicatorPosition--;
                        if (indicatorPosition <= 0) {
                            movingRight = true;
                        }
                    }
                    updateBarVisual();
                }
            }.runTaskTimer(plugin, 0L, difficulty.indicatorSpeedTicks);

            // Set timeout based on rarity
            timeoutTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activeMinigames.get(player.getUniqueId()) == MinigameInstance.this) {
                        failMinigame("Timed out!");
                    }
                }
            }.runTaskLater(plugin, difficulty.timeoutSeconds * 20L);

            Sound startSound = getRarityStartSound(fishRarity);
            player.playSound(player.getLocation(), startSound, 0.5f, getRarityPitch(fishRarity));

            player.sendMessage(rarityColor + "A " + fishRarity.toLowerCase() + " fish! " + getDifficultyHint());
        }

        private void updateBarVisual() {
            if (bossBar == null || !player.isOnline()) {
                if(bossBar != null) bossBar.removeAll();
                cleanupTasks();
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BAR_LENGTH; i++) {
                boolean inSweetSpot = i >= sweetSpotStart && i < sweetSpotStart + difficulty.sweetSpotSize;
                String currentIndicator = (i == indicatorPosition) ? ChatColor.YELLOW + INDICATOR_CHAR : "";

                if (inSweetSpot) {
                    if (i == sweetSpotStart) sb.append(ChatColor.GREEN).append(SWEET_SPOT_CHAR_OPEN);
                    sb.append(currentIndicator.isEmpty() ? ChatColor.GREEN + BAR_CHAR : currentIndicator);
                    if (i == sweetSpotStart + difficulty.sweetSpotSize - 1) sb.append(ChatColor.GREEN).append(SWEET_SPOT_CHAR_CLOSE);
                } else {
                    sb.append(currentIndicator.isEmpty() ? ChatColor.GRAY + BAR_CHAR : currentIndicator);
                }
            }
            bossBar.setTitle(sb.toString());
        }

        public void attemptCatch() {
            if (indicatorPosition >= sweetSpotStart && indicatorPosition < sweetSpotStart + difficulty.sweetSpotSize) {
                winMinigame();
            } else {
                failMinigame("Missed!");
            }
        }

        private void winMinigame() {
            cleanupTasks();
            if (bossBar != null) {
                ChatColor rarityColor = getRarityColor(fishRarity);
                bossBar.setTitle(rarityColor + "Fish Caught! (" + fishRarity + ")");
                bossBar.setColor(BarColor.GREEN);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (bossBar != null) bossBar.removeAll();
                        bossBar = null;
                    }
                }.runTaskLater(plugin, 40L);
            }

            Sound successSound = getRaritySuccessSound(fishRarity);
            player.playSound(player.getLocation(), successSound, 0.7f, getRarityPitch(fishRarity));

            ChatColor rarityColor = getRarityColor(fishRarity);
            if (potentialCustomCatch.hasItemMeta() && potentialCustomCatch.getItemMeta().hasDisplayName()) {
                player.sendMessage(rarityColor + "You successfully reeled in a " + potentialCustomCatch.getItemMeta().getDisplayName() + rarityColor + "!");
            } else {
                player.sendMessage(rarityColor + "You successfully reeled in a " + fishRarity.toLowerCase() + " fish!");
            }

            caughtItemEntity.setItemStack(potentialCustomCatch);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (caughtItemEntity != null && !caughtItemEntity.isDead() && player.isOnline()) {
                        caughtItemEntity.teleport(player.getLocation().add(0, 1, 0));

                        player.getInventory().addItem(potentialCustomCatch);
                        caughtItemEntity.remove();

                        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.0f);

                        retractFishingRod();
                    }
                }
            }.runTaskLater(plugin, 10L);

            scoreboardManager.incrementTotalCatches(player);
            if (potentialCustomCatch.hasItemMeta() && potentialCustomCatch.getItemMeta().hasLore()) {
                String quality = "COMMON";
                double weight = 0.0;

                for (String loreLine : potentialCustomCatch.getItemMeta().getLore()) {
                    String strippedLine = ChatColor.stripColor(loreLine);
                    if (strippedLine.startsWith("Quality:")) {
                        quality = strippedLine.substring("Quality:".length()).trim().toUpperCase();
                    } else if (strippedLine.startsWith("Weight:")) {
                        try {
                            weight = Double.parseDouble(strippedLine.replaceAll("[^0-9.]", ""));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Could not parse weight from lore: " + loreLine);
                        }
                    }
                }
                if (quality.contains("LEGENDARY")) {
                    scoreboardManager.incrementLegendaryFish(player);
                }
                scoreboardManager.updateHeaviestCatch(player, weight);
                scoreboardManager.addXP(player, quality, weight);
            }
            activeMinigames.remove(player.getUniqueId());
        }

        private void failMinigame(String reason) {
            cleanupTasks();
            if (bossBar != null) {
                bossBar.setTitle(ChatColor.RED + "Fish Escaped! (" + reason + ")");
                bossBar.setColor(BarColor.RED);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (bossBar != null) bossBar.removeAll();
                        bossBar = null;
                    }
                }.runTaskLater(plugin, 60L);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f, 0.5f);
            player.sendMessage(ChatColor.RED + "The " + fishRarity.toLowerCase() + " fish got away! " + reason);
            caughtItemEntity.remove();

            new BukkitRunnable() {
                @Override
                public void run() {
                    retractFishingRod();
                }
            }.runTaskLater(plugin, 20L); // 1 second delay

            activeMinigames.remove(player.getUniqueId());
        }

        public void cancel(boolean silent, String reason) {
            cleanupTasks();
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
            if (caughtItemEntity != null && !caughtItemEntity.isDead()) {
                caughtItemEntity.remove();
            }
            if (!silent && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "Fishing minigame cancelled. " + reason);
            }
        }

        private void cleanupTasks() {
            if (barUpdateTask != null) {
                barUpdateTask.cancel();
                barUpdateTask = null;
            }
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
        }

        private void retractFishingRod() {
            player.getWorld().getEntities().stream()
                    .filter(entity -> entity.getType().name().equals("FISHING_HOOK"))
                    .filter(entity -> entity instanceof org.bukkit.entity.FishHook)
                    .filter(entity -> ((org.bukkit.entity.FishHook) entity).getShooter() == player)
                    .forEach(org.bukkit.entity.Entity::remove);
        }

        private ChatColor getRarityColor(String rarity) {
            switch (rarity) {
                case "COMMON": return ChatColor.WHITE;
                case "UNCOMMON": return ChatColor.GREEN;
                case "RARE": return ChatColor.BLUE;
                case "EPIC": return ChatColor.DARK_PURPLE;
                case "LEGENDARY": return ChatColor.GOLD;
                case "MYTHIC": return ChatColor.DARK_RED;
                default: return ChatColor.GRAY;
            }
        }

        private BarColor getRarityBarColor(String rarity) {
            switch (rarity) {
                case "COMMON": return BarColor.WHITE;
                case "UNCOMMON": return BarColor.GREEN;
                case "RARE": return BarColor.BLUE;
                case "EPIC": return BarColor.PURPLE;
                case "LEGENDARY": return BarColor.YELLOW;
                case "MYTHIC": return BarColor.RED;
                default: return BarColor.WHITE;
            }
        }

        private Sound getRarityStartSound(String rarity) {
            switch (rarity) {
                case "LEGENDARY":
                case "MYTHIC":
                    return Sound.ENTITY_ENDER_DRAGON_GROWL;
                case "EPIC":
                    return Sound.ENTITY_WITHER_SPAWN;
                case "RARE":
                    return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                default:
                    return Sound.ENTITY_FISHING_BOBBER_SPLASH;
            }
        }

        private Sound getRaritySuccessSound(String rarity) {
            switch (rarity) {
                case "MYTHIC":
                    return Sound.UI_TOAST_CHALLENGE_COMPLETE;
                case "LEGENDARY":
                    return Sound.ENTITY_PLAYER_LEVELUP;
                case "EPIC":
                    return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                default:
                    return Sound.ENTITY_PLAYER_LEVELUP;
            }
        }

        private float getRarityPitch(String rarity) {
            switch (rarity) {
                case "MYTHIC": return 2.0f;
                case "LEGENDARY": return 1.8f;
                case "EPIC": return 1.5f;
                case "RARE": return 1.3f;
                case "UNCOMMON": return 1.1f;
                default: return 1.0f;
            }
        }

        private String getDifficultyHint() {
            switch (fishRarity) {
                case "MYTHIC":
                    return "This will be nearly impossible!";
                case "LEGENDARY":
                    return "This will be extremely challenging!";
                case "EPIC":
                    return "This will be very difficult!";
                case "RARE":
                    return "This will be challenging!";
                case "UNCOMMON":
                    return "This should be manageable.";
                default:
                    return "This should be easy.";
            }
        }
    }
}