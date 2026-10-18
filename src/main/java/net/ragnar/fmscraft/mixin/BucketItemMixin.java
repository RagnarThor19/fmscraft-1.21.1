package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.ragnar.fmscraft.effect.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block Barrier: no pouring out water or lava either (a water column is a way up). */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
    private void fmscraft$blockBarrier(PlayerEntity player, World world, BlockPos pos, BlockHitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        if (ModEffects.blocksPlacing(player)) cir.setReturnValue(false);
    }
}
