package jefry.plugin.betterFishing;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingListener implements Listener {
    private final FishingItemManager fishingItemManager;
    private final BetterFishing plugin;
    private final ScoreboardManager scoreboardManager;

    public FishingListener(FishingItemManager fishingItemManager, BetterFishing plugin) {
        this.fishingItemManager = fishingItemManager;
        this.plugin = plugin;
        this.scoreboardManager = plugin.getScoreboardManager();
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        // Check if the event represents catching a fish
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        // Ensure the caught entity is an Item
        if (!(event.getCaught() instanceof Item)) {
            return;
        }

        Player player = event.getPlayer();
        Item caughtItem = (Item) event.getCaught();
        ItemStack fishingRod = player.getInventory().getItemInMainHand();

        // Generate a custom catch using FishingItemManager
        ItemStack generatedCatch = fishingItemManager.generateCatch(player, fishingRod);

        // Set the caught item to the generated catch
        caughtItem.setItemStack(generatedCatch);

        // Update player stats
        scoreboardManager.incrementTotalCatches(player);

        // Handle fish attributes like weight and quality
        if (generatedCatch.getItemMeta() != null && generatedCatch.getItemMeta().getLore() != null) {
            String quality = generatedCatch.getItemMeta().getLore().stream()
                    .filter(line -> line.contains("Quality:"))
                    .findFirst()
                    .orElse("Quality: Common");

            double weight = generatedCatch.getItemMeta().getLore().stream()
                    .filter(line -> line.contains("Weight:"))
                    .map(line -> {
                        try {
                            return Double.parseDouble(line.split("§f")[1].replace(" kg", ""));
                        } catch (Exception e) {
                            return 0.0;
                        }
                    })
                    .findFirst()
                    .orElse(0.0);

            // Check if it's a legendary fish and increment legendary stats
            if (quality.contains("Legendary")) {
                scoreboardManager.incrementLegendaryFish(player);
            }

            // Update player's heaviest catch if applicable
            scoreboardManager.updateHeaviestCatch(player, weight);

            // Add XP based on the fish's quality and weight
            scoreboardManager.addXP(player, quality, weight);
        }
    }
}
