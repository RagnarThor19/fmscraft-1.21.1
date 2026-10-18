package net.ragnar.fmscraft.client;

import net.minecraft.util.math.MathHelper;

/**
 * The thick, nearly black fog and dim light down in a Greed Vault. The server says when you go down into it or climb back out
 * ({@link net.ragnar.fmscraft.network.VaultFogPayload}); the fog closes in and lifts over a couple of seconds.
 * Drawn by BackgroundRendererMixin (fog) and LightmapTextureManagerMixin (light).
 */
public final class VaultFog {
    public static final float RED = 0.045f, GREEN = 0.045f, BLUE = 0.05f;
    public static final float START = 0f, END = 12f;
    /** How much of the brightness setting the vault takes away (0-1), and how far every light level drops. */
    public static final float GLOOM = 0.7f, DIM = 0.15f;
    private static final float FADE_TICKS = 40f;

    private static boolean inside;
    private static float strength, prevStrength;

    private VaultFog() {
    }

    public static void set(boolean in) {
        inside = in;
    }

    public static void clear() {
        inside = false;
        strength = prevStrength = 0;
    }

    public static void tick() {
        prevStrength = strength;
        strength = MathHelper.clamp(strength + (inside ? 1 : -1) / FADE_TICKS, 0f, 1f);
    }

    /** 0 with no vault fog, 1 fully in it. */
    public static float strength(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevStrength, strength);
    }
}
