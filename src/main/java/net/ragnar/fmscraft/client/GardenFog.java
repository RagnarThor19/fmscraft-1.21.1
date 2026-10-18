package net.ragnar.fmscraft.client;

import net.minecraft.util.math.MathHelper;

/**
 * Inside Lust's Garden the twilight's grey fog takes on a faint dusty rose, like the light through the blossom. Only
 * the colour changes, not how far you can see: the statues come out of the same fog everywhere. The server says when
 * you go in or out ({@link net.ragnar.fmscraft.network.GardenFogPayload}); it fades over a couple of seconds.
 * Drawn by BackgroundRendererMixin.
 */
public final class GardenFog {
    public static final float RED = 0.64f, GREEN = 0.55f, BLUE = 0.62f;
    /** How far toward the rose the fog goes (0-1): a tint, not a new colour. */
    public static final float TINT = 0.5f;
    private static final float FADE_TICKS = 40f;

    private static boolean inside;
    private static float strength, prevStrength;

    private GardenFog() {
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

    public static float strength(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevStrength, strength) * TINT;
    }
}
