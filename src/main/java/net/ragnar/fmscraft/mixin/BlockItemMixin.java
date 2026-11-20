package net.ragnar.fmscraft.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.fragment.LuckyStrike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block Barrier: no placing blocks. Every block item (seeds, string, scaffolding...) goes through here.
 * Also remembers ores players place, which don't count for Greed's lucky strike.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void fmscraft$blockBarrier(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (ModEffects.blocksPlacing(context.getPlayer())) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void fmscraft$rememberPlacedOre(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue().isAccepted()) LuckyStrike.onPlaced(context.getWorld(), context.getBlockPos());
    }
}
