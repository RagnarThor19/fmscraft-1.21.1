package net.ragnar.fmscraft.mixin;

import net.minecraft.advancement.criterion.BredAnimalsCriterion;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ragnar.fmscraft.fragment.LustTrigger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BredAnimalsCriterion.class)
public abstract class BredAnimalsCriterionMixin {
    // Fired by vanilla for every breeding credited to a player, the same hook "Two by Two" uses.
    // Frogs and turtles lay eggs instead of having a child, so fall back to the parent's type.
    @Inject(method = "trigger", at = @At("HEAD"))
    private void fmscraft$trackLust(ServerPlayerEntity player, AnimalEntity parent, AnimalEntity partner,
                                    @Nullable PassiveEntity child, CallbackInfo ci) {
        LustTrigger.onBred(player, child != null ? child.getType() : parent.getType());
    }
}
