package jefry.plugin.betterFishing;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class FishingManager implements Listener {
    private final BetterFishing plugin;
    private final FishingRodsConfig fishingRodsConfig;

    // Enhanced item sets for catch types
    private final Set<Material> TREASURE_ITEMS = EnumSet.of(
            Material.BOW, Material.ENCHANTED_BOOK, Material.FISHING_ROD,
            Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE
    );

    private final Set<Material> JUNK_ITEMS = EnumSet.of(
            Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
            Material.ROTTEN_FLESH, Material.STICK, Material.STRING, Material.BONE,
            Material.INK_SAC, Material.TRIPWIRE_HOOK
    );

    private Map<String, Double> TIER_WEIGHT_MULTIPLIERS;


    private final Set<UUID> playersWithRod = new HashSet<>();
    private final FishingItemManager fishingItemManager;


    public FishingManager(BetterFishing plugin, FishingRodsConfig fishingRodsConfig, FishingItemManager fishingItemManager) {
        this.plugin = plugin;
        this.fishingRodsConfig = fishingRodsConfig;
        this.fishingItemManager = fishingItemManager;
        this.TIER_WEIGHT_MULTIPLIERS = generateTierWeightMultipliers(fishingRodsConfig.getAllFishingRods());
    }


    private Map<String, Double> generateTierWeightMultipliers(Map<String, FishingRodsConfig.FishingRodData> availableRods) {
        Map<String, Double> tierMultipliers = new HashMap<>();

        // Always ensure starter has a base multiplier of 1.0
        tierMultipliers.put("starter", 1.0);

        // Sort rods by their base weight multiplier to determine progression
        List<Map.Entry<String, FishingRodsConfig.FishingRodData>> sortedRods = new ArrayList<>(availableRods.entrySet());
        sortedRods.sort(Comparator.comparing(entry -> entry.getValue().getBaseWeightMultiplier()));

        // Generate multipliers based on rod progression
        for (int i = 0; i < sortedRods.size(); i++) {
            String tier = sortedRods.get(i).getKey();

            // Skip starter which is already set
            if ("starter".equals(tier)) continue;

            // Linear progression of multipliers based on base weight multiplier
            double baseMultiplier = sortedRods.get(i).getValue().getBaseWeightMultiplier();
            tierMultipliers.put(tier, baseMultiplier);
        }

        return tierMultipliers;
    }

    public ItemStack createCustomRod(String tier) {
        FishingRodsConfig.FishingRodData rodData = fishingRodsConfig.getFishingRod(tier.toLowerCase());

        if (rodData == null) {
            // Fallback to starter rod if not found
            rodData = fishingRodsConfig.getFishingRod("starter");
        }

        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(rodData.getDisplayName());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            // Apply enchantments
            for (Map.Entry<Enchantment, Integer> enchant : rodData.getEnchantments().entrySet()) {
                meta.addEnchant(enchant.getKey(), enchant.getValue(), true);
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7Weight Multiplier: §f" + rodData.getBaseWeightMultiplier() + "x");
            lore.add("§7Catch Chance Bonus: §f+5%");
            lore.add("§7Upgrades: §f0/" + rodData.getUpgradesAllowed());

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            rod.setItemMeta(meta);
        }

        return rod;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore() && !playersWithRod.contains(player.getUniqueId())) {
            ItemStack starterRod = createCustomRod("Starter");
            player.getInventory().addItem(starterRod);
            playersWithRod.add(player.getUniqueId());
        }
    }

}