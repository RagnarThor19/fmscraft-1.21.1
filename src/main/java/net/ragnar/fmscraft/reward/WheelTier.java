package net.ragnar.fmscraft.reward;

import net.minecraft.util.Formatting;

/** Tome tiers on the reward wheel, matching Ragnars Magic Mod's TomeTier (and its tooltip colors). */
public enum WheelTier {
    BEGINNER(0.50, Formatting.GREEN, 0x5E8A3C),
    ADVANCED(0.30, Formatting.AQUA, 0x3E8088),
    MASTER(0.20, Formatting.LIGHT_PURPLE, 0x7E5296);

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

    public String translationKey() {
        return "wheel.fmscraft.tier." + name().toLowerCase();
    }
}
