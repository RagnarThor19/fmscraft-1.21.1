package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.village.TradeOfferList;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Greed Fragments used to be sold by some Expert villagers; now they come from mining (LuckyStrike). Any villager
 * still offering one from before loses that trade when it's loaded.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void fmscraft$dropOldGreedTrade(NbtCompound nbt, CallbackInfo ci) {
        TradeOfferList offers = ((MerchantEntityAccessor) this).fmscraft$offers();
        if (offers != null) offers.removeIf(offer -> offer.getSellItem().isOf(ModItems.FRAGMENTS.get(Sin.GREED)));
    }
}
