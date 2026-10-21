package net.ragnar.fmscraft.fragment;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Greed: a Greed Fragment bought from a villager, at a greedy price. Now and then ({@link #CHANCE}) a villager of any
 * trade, reaching {@link #LEVEL} (Expert), adds it to its trades, on top of the usual two for that level, so it never
 * takes the place of one. Each such villager asks one of three prices (32 diamonds, 8 emerald blocks or 16 gold
 * blocks), never more than one. Added by VillagerEntityMixin after the villager fills its trades for a level.
 */
public final class GreedTrade {
    public static final float CHANCE = 0.2f;
    /** Expert. */
    public static final int LEVEL = 4;
    /** Bought this often before the villager has to restock, as any trade does. */
    public static final int MAX_USES = 3;
    private static final int EXPERIENCE = 30;
    /** As vanilla's dearest trades: curing the villager or Hero of the Village takes a little off. */
    private static final float PRICE_MULTIPLIER = 0.05f;

    /** The three prices a villager may ask, as (item, count). */
    private static final Item[] PRICE_ITEMS = {Items.DIAMOND, Items.EMERALD_BLOCK, Items.GOLD_BLOCK};
    private static final int[] PRICE_COUNTS = {32, 8, 16};

    private GreedTrade() {
    }

    /** Called once a villager has filled its trades for a new level: maybe it adds the Greed trade. */
    public static void maybeOffer(VillagerEntity villager) {
        VillagerData data = villager.getVillagerData();
        if (data.getLevel() != LEVEL || data.getProfession() == VillagerProfession.NONE || data.getProfession() == VillagerProfession.NITWIT) return;
        Random random = villager.getRandom();
        if (random.nextFloat() >= CHANCE || offersGreed(villager)) return;
        villager.getOffers().add(create(random));
    }

    /** One of the three, picked at random. */
    public static TradeOffer create(Random random) {
        int i = random.nextInt(PRICE_ITEMS.length);
        return new TradeOffer(new TradedItem(PRICE_ITEMS[i], PRICE_COUNTS[i]), new ItemStack(ModItems.FRAGMENTS.get(Sin.GREED)),
                MAX_USES, EXPERIENCE, PRICE_MULTIPLIER);
    }

    public static boolean offersGreed(VillagerEntity villager) {
        for (TradeOffer offer : villager.getOffers()) {
            if (offer.getSellItem().isOf(ModItems.FRAGMENTS.get(Sin.GREED))) return true;
        }
        return false;
    }
}
