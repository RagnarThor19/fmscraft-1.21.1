package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;

/**
 * The sky over RAGGIMEL's island ({@link net.ragnar.fmscraft.network.GreedSkyPayload}). Phase 1 is a bright, pale
 * blue noon; phase 2 is a dark blue night with stars and no sun. Night falls at once (it happens under the black
 * screen) and lifts slowly when he's beaten. Applied by ClientWorldMixin (sky, clouds, light), WorldRendererMixin
 * (stars instead of the sun) and BackgroundRendererMixin (fog).
 */
public final class GreedSky {
    public static final Vec3d DAY_SKY = new Vec3d(0.56, 0.80, 1.0), DAY_FOG = new Vec3d(0.76, 0.89, 1.0);
    public static final Vec3d NIGHT_SKY = new Vec3d(0.02, 0.04, 0.13), NIGHT_FOG = new Vec3d(0.03, 0.06, 0.17);
    public static final Vec3d NIGHT_CLOUDS = new Vec3d(0.12, 0.16, 0.30);
    /** How bright the sky lights the island at night (vanilla's own goes 0.2-1). */
    public static final float NIGHT_LIGHT = 0.3f;

    private static int phase;
    private static boolean inArena;
    private static float night, prevNight;

    private GreedSky() {
    }

    public static void set(int newPhase) {
        phase = newPhase;
        if (phase == 2) night = prevNight = 1;
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.world.getRegistryKey().equals(Arenas.key(Sin.GREED));
        if (!inArena) {
            phase = 0;
            night = prevNight = 0;
            return;
        }
        prevNight = night;
        night = MathHelper.clamp(night + (phase == 2 ? 0.1f : -1 / 80f), 0f, 1f);
    }

    public static void clear() {
        phase = 0;
        night = prevNight = 0;
    }

    /** Whether we're over the island (its sky is always the pale blue one, darkening by {@link #night}). */
    public static boolean active() {
        return inArena;
    }

    /** 0 by day, 1 at night. */
    public static float night(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevNight, night) : 0;
    }

    public static Vec3d mix(Vec3d day, Vec3d nightColor, float tickDelta) {
        return day.lerp(nightColor, night(tickDelta));
    }
}
