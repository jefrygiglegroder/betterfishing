package jefry.plugin.betterFishing;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingListener implements Listener {
    private final FishingItemManager fishingItemManager;
    private final BetterFishing plugin;
    private final FishingMinigameManager fishingMinigameManager;
    private final ScoreboardManager scoreboardManager;

    // <<< MODIFIED CONSTRUCTOR
    public FishingListener(FishingItemManager fishingItemManager, BetterFishing plugin, FishingMinigameManager fishingMinigameManager, ScoreboardManager scoreboardManager) {
        this.fishingItemManager = fishingItemManager;
        this.plugin = plugin;
        this.fishingMinigameManager = fishingMinigameManager;
        this.scoreboardManager = scoreboardManager; // Store it
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        if (!(event.getCaught() instanceof Item)) {
            return;
        }

        Player player = event.getPlayer();
        Item caughtItemEntity = (Item) event.getCaught();
        ItemStack fishingRod = player.getInventory().getItemInMainHand();

        if (fishingMinigameManager.isPlayerInMinigame(player)) {
            event.setCancelled(true);
            caughtItemEntity.remove();
            return;
        }

        ItemStack potentialCustomCatch = fishingItemManager.generateCatch(player, fishingRod);

        fishingMinigameManager.startMinigame(player, caughtItemEntity, potentialCustomCatch);

        event.setCancelled(true);

    }
}