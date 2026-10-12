package net.ragnar.fmscraft.mixin;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
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
}
