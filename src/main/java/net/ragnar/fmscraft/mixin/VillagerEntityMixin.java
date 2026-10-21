package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.ragnar.fmscraft.fragment.GreedTrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a villager has its trades for a new level, it may add the Greed trade on top of them (GreedTrade). */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void fmscraft$greedTrade(CallbackInfo ci) {
        GreedTrade.maybeOffer((VillagerEntity) (Object) this);
    }
}
