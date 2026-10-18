package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.ragnar.fmscraft.effect.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item Barrier: nothing but food can be used, in the air, on a block or on a mob. Blocks you use (a chest, a lever,
 * the lock) are handled by the block before the item gets a say, so they still work.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fmscraft$itemBarrier(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (ModEffects.blocksUsing(user, self)) cir.setReturnValue(TypedActionResult.fail(self));
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void fmscraft$itemBarrierOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (ModEffects.blocksUsing(context.getPlayer(), (ItemStack) (Object) this)) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void fmscraft$itemBarrierOnEntity(PlayerEntity user, LivingEntity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (ModEffects.blocksUsing(user, (ItemStack) (Object) this)) cir.setReturnValue(ActionResult.FAIL);
    }
}
