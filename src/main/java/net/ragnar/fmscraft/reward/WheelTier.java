package net.ragnar.fmscraft.reward;

import com.mojang.serialization.Codec;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

/** Tome tiers on the reward wheel, matching Ragnars Magic Mod's TomeTier (and its tooltip colors). */
public enum WheelTier implements StringIdentifiable {
    BEGINNER(0.50, Formatting.GREEN, 0x5E8A3C),
    ADVANCED(0.30, Formatting.AQUA, 0x3E8088),
    MASTER(0.20, Formatting.LIGHT_PURPLE, 0x7E5296);

    public static final Codec<WheelTier> CODEC = StringIdentifiable.createCodec(WheelTier::values);

    public final double chance;
    public final Formatting textColor;
    /** Dull slice color on the wheel. */
    public final int sliceColor;

    WheelTier(double chance, Formatting textColor, int sliceColor) {
        this.chance = chance;
        this.textColor = textColor;
        this.sliceColor = sliceColor;
    }

    /** {@code roll} in [0, 1): 50% beginner, 30% advanced, 20% master. */
    public static WheelTier fromRoll(double roll) {
        double acc = 0;
        for (WheelTier tier : values()) {
            acc += tier.chance;
            if (roll < acc) return tier;
        }
        return MASTER;
    }

    @Override
    public String asString() {
        return name().toLowerCase();
    }

    public String translationKey() {
        return "wheel.fmscraft.tier." + name().toLowerCase();
    }
}
