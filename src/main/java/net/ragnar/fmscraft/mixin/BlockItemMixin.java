package net.ragnar.fmscraft.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.ragnar.fmscraft.effect.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block Barrier: no placing blocks. Every block item (seeds, string, scaffolding...) goes through here. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void fmscraft$blockBarrier(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (ModEffects.blocksPlacing(context.getPlayer())) cir.setReturnValue(ActionResult.FAIL);
    }
}
