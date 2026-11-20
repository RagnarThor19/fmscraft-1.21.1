package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantEntity.class)
public interface MerchantEntityAccessor {
    /** The trades as they are, without filling them in for a villager that has none yet (as getOffers would). */
    @Accessor("offers")
    TradeOfferList fmscraft$offers();
}
