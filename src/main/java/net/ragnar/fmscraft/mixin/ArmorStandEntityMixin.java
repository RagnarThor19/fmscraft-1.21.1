package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.ragnar.fmscraft.fragment.PrideTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandEntityMixin {
    // Covers players, dispensers and commands: every way armor ends up on a stand goes through equipStack
    @Inject(method = "equipStack", at = @At("TAIL"))
    private void fmscraft$checkPride(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        PrideTrigger.onEquipmentChanged((ArmorStandEntity) (Object) this);
    }
}
