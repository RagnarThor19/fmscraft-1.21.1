package net.ragnar.fmscraft.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModEffects {
    /**
     * Block Barrier: you can't place blocks (or empty buckets) while you have it, like being in adventure mode.
     * Given inside the Greed Vault so nobody pillars out or walls the statues off. Enforced in BlockItemMixin and
     * BucketItemMixin, on both sides, so there are no ghost blocks.
     */
    public static final RegistryEntry<StatusEffect> BLOCK_BARRIER = Registry.registerReference(Registries.STATUS_EFFECT,
            Identifier.of(FmsCraft.MOD_ID, "block_barrier"), new BlockBarrierEffect());

    public static void initialize() {
    }

    /** Whether this player is stopped from placing blocks right now. */
    public static boolean blocksPlacing(PlayerEntity player) {
        return player != null && player.hasStatusEffect(BLOCK_BARRIER);
    }

    private static class BlockBarrierEffect extends StatusEffect {
        BlockBarrierEffect() {
            super(StatusEffectCategory.HARMFUL, 0xA8343A);
        }
    }
}
