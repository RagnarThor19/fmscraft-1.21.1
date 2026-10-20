package net.ragnar.fmscraft.effect;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModEffects {
    /**
     * Block Barrier: you can't place or break blocks (or empty buckets) while you have it, like being in adventure mode.
     * Given inside the Greed Vault so nobody pillars out or walls the statues off, and in the Den of Wrath. Enforced in
     * BlockItemMixin, BucketItemMixin and PlayerEntityMixin, on both sides, so there are no ghost blocks.
     */
    public static final RegistryEntry<StatusEffect> BLOCK_BARRIER = Registry.registerReference(Registries.STATUS_EFFECT,
            Identifier.of(FmsCraft.MOD_ID, "block_barrier"), new BarrierEffect(0xA8343A));
    /**
     * Item Barrier: you can't use any item but food while you have it (no bows, pearls, potions, tomes, rockets...).
     * Given inside Lust's Garden. Using blocks (doors, chests, levers, a key in its lock) still works. Enforced in
     * ItemStackMixin, on both sides.
     */
    public static final RegistryEntry<StatusEffect> ITEM_BARRIER = Registry.registerReference(Registries.STATUS_EFFECT,
            Identifier.of(FmsCraft.MOD_ID, "item_barrier"), new BarrierEffect(0x8E5C9E));

    public static void initialize() {
    }

    /** Whether this player is stopped from placing blocks right now. */
    public static boolean blocksPlacing(PlayerEntity player) {
        return player != null && player.hasStatusEffect(BLOCK_BARRIER);
    }

    /** Whether this player is stopped from breaking blocks right now. */
    public static boolean blocksBreaking(PlayerEntity player) {
        return player != null && player.hasStatusEffect(BLOCK_BARRIER);
    }

    /** Whether this player is stopped from using {@code stack} right now. Food is always allowed. */
    public static boolean blocksUsing(PlayerEntity player, ItemStack stack) {
        return player != null && player.hasStatusEffect(ITEM_BARRIER) && !stack.isEmpty() && !stack.contains(DataComponentTypes.FOOD);
    }

    private static class BarrierEffect extends StatusEffect {
        BarrierEffect(int color) {
            super(StatusEffectCategory.HARMFUL, color);
        }
    }
}
