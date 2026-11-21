package net.ragnar.fmscraft.fragment;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

/**
 * Greed: a lucky strike. Mining an ore now and then turns up a Greed Fragment too, dropped alongside what the ore
 * gives (never instead of it): {@link #GOLD_CHANCE} for gold ore, {@link #RARE_CHANCE} for diamond ore, emerald ore and
 * ancient debris, {@link #CHANCE} for redstone and lapis ore (the tags {@code fmscraft:lucky_strike_gold},
 * {@code fmscraft:lucky_strike_rare} and {@code fmscraft:lucky_strike}). Not nether gold ore.
 *
 * <p>Only ore the world made counts, mined properly: not in creative, not with a tool that can't harvest it, not with
 * Silk Touch, and not ore a player placed (so a block of ancient debris, which drops itself, can't be placed and mined
 * again and again). Placed ores are remembered in FmsWorldState (BlockItemMixin) until mined.
 * The roll happens in BlockMixin, after the block has dropped its own loot with the tool that broke it.
 */
public final class LuckyStrike {
    public static final float GOLD_CHANCE = 0.025f;
    public static final float RARE_CHANCE = 0.02f;
    public static final float CHANCE = 0.01f;
    public static final TagKey<net.minecraft.block.Block> GOLD_ORES =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of(FmsCraft.MOD_ID, "lucky_strike_gold"));
    public static final TagKey<net.minecraft.block.Block> RARE_ORES =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of(FmsCraft.MOD_ID, "lucky_strike_rare"));
    public static final TagKey<net.minecraft.block.Block> ORES =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of(FmsCraft.MOD_ID, "lucky_strike"));

    private LuckyStrike() {
    }

    public static float chance(BlockState state) {
        if (state.isIn(GOLD_ORES)) return GOLD_CHANCE;
        if (state.isIn(RARE_ORES)) return RARE_CHANCE;
        if (state.isIn(ORES)) return CHANCE;
        return 0;
    }

    /** A player put an ore block down: it won't count when mined. */
    public static void onPlaced(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld) || chance(world.getBlockState(pos)) == 0) return;
        FmsWorldState.get(serverWorld.getServer()).rememberPlacedOre(world.getRegistryKey(), pos);
    }

    /** A player has harvested a block with {@code tool} (it has already dropped its loot). Maybe a fragment too. */
    public static boolean onHarvested(World world, PlayerEntity player, BlockPos pos, BlockState state, ItemStack tool) {
        if (!(world instanceof ServerWorld serverWorld) || player == null) return false;
        float chance = chance(state);
        if (chance == 0) return false;
        // Forgotten as it's mined, so whatever is put there next is judged afresh
        if (FmsWorldState.get(serverWorld.getServer()).forgetPlacedOre(world.getRegistryKey(), pos)) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        if (silkTouch(serverWorld, tool)) return false;
        return roll(serverWorld, pos, chance, serverWorld.getRandom());
    }

    static boolean roll(ServerWorld world, BlockPos pos, float chance, Random random) {
        if (random.nextFloat() >= chance) return false;
        FragmentEffects.drop(world, Vec3d.ofCenter(pos), Sin.GREED, 1);
        return true;
    }

    private static boolean silkTouch(ServerWorld world, ItemStack tool) {
        return EnchantmentHelper.getLevel(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), tool) > 0;
    }
}
