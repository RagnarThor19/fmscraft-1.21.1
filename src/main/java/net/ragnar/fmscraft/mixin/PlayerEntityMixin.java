package net.ragnar.fmscraft.mixin;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.fight.DeathChests;
import net.ragnar.fmscraft.fragment.GluttonyTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    // HEAD, so the stack is still intact before eating shrinks it
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void fmscraft$checkGluttony(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
        GluttonyTrigger.onEat((PlayerEntity) (Object) this, stack);
    }

    /** While a fallen fighter's open screens are emptied, what they'd drop goes to their death chests instead. */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void fmscraft$deathChestCapture(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (DeathChests.capture((PlayerEntity) (Object) this, stack)) cir.setReturnValue(null);
    }

    /** Block Barrier: no breaking blocks either, the way adventure mode does it, so the client never breaks them either. */
    @Inject(method = "isBlockBreakingRestricted", at = @At("HEAD"), cancellable = true)
    private void fmscraft$blockBarrier(World world, BlockPos pos, GameMode gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (ModEffects.blocksBreaking((PlayerEntity) (Object) this)) cir.setReturnValue(true);
    }
}
