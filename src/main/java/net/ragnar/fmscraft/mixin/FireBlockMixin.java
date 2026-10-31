package net.ragnar.fmscraft.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.ragnar.fmscraft.fight.Arenas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire in a boss arena just burns: it never spreads, never burns anything away and never goes out. ORNAMIX's
 * colosseum is lit with hundreds of fires next to wool awnings and wooden beams; without this the arena would burn down
 * during the fight, and the doFireTick game rule can't be set for one dimension. (All of fire's spreading, burning and
 * dying out happens in its scheduled tick; the arena is rebuilt before every fight anyway.)
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void fmscraft$tameArenaFire(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (Arenas.sinOf(world) != null) ci.cancel();
    }
}
