package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;
import net.ragnar.fmscraft.boss.wrath.OrnamixTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;

/**
 * The sky over ORNAMIX's colosseum ({@link net.ragnar.fmscraft.network.WrathSkyPayload}). Phase 1 is a bright blood-red
 * noon; Ragemode turns it almost black red, with no sun, a dimmer light and dark red fog closing in round the pit. It
 * turns at once (under the black screen) and lifts back to red slowly when he's beaten. Applied by ClientWorldMixin
 * (sky, clouds, light), WorldRendererMixin (no sun in Ragemode) and BackgroundRendererMixin (fog).
 * <p>
 * In Ragemode the world also blinks dark on every hit of Return to Zero's bass ({@link OrnamixTimeline#blink}), timed
 * from the song's own clock on this client.
 */
public final class WrathSky {
    public static final Vec3d RED_SKY = new Vec3d(0.86, 0.16, 0.08), RED_FOG = new Vec3d(1.0, 0.42, 0.24);
    public static final Vec3d RAGE_SKY = new Vec3d(0.09, 0.0, 0.0), RAGE_FOG = new Vec3d(0.15, 0.015, 0.01);
    public static final Vec3d RED_CLOUDS = new Vec3d(0.75, 0.32, 0.25), RAGE_CLOUDS = new Vec3d(0.2, 0.03, 0.02);
    /** How bright the sky lights the arena in Ragemode (vanilla's own goes 0.2-1). */
    public static final float RAGE_LIGHT = 0.28f;
    /** In Ragemode the fog closes in to this (start, end): the far side of the pit is a dark red haze. */
    public static final float RAGE_FOG_START = 6f, RAGE_FOG_END = 62f;

    private static int phase;
    private static boolean inArena;
    private static float rage, prevRage;

    private WrathSky() {
    }

    public static void set(int newPhase) {
        phase = newPhase;
        if (phase == 2) rage = prevRage = 1;
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.world.getRegistryKey().equals(Arenas.key(Sin.WRATH));
        if (!inArena) {
            phase = 0;
            rage = prevRage = 0;
            return;
        }
        prevRage = rage;
        rage = MathHelper.clamp(rage + (phase == 2 ? 0.1f : -1 / 80f), 0f, 1f);
    }

    public static void clear() {
        phase = 0;
        rage = prevRage = 0;
    }

    /** Whether we're in the colosseum (its sky is always the red one, darkening by {@link #rage}). */
    public static boolean active() {
        return inArena;
    }

    /** 0 under the red sky, 1 in Ragemode. */
    public static float rage(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevRage, rage) : 0;
    }

    public static Vec3d mix(Vec3d red, Vec3d rageColor, float tickDelta) {
        return red.lerp(rageColor, rage(tickDelta));
    }

    /** How dark the bass has blinked the world right now, 0-1. */
    public static float blink(float tickDelta) {
        if (!inArena || phase != 2) return 0;
        double seconds = ClientBossMusic.secondsInto(OrnamixEntity.PHASE_TWO.soundId(), tickDelta);
        return seconds < 0 ? 0 : OrnamixTimeline.blink(seconds);
    }

    /** The blink: the screen dips dark for a moment on the beat. */
    public static void render(DrawContext ctx, RenderTickCounter counter) {
        float b = blink(counter.getTickDelta(true));
        if (b <= 0.005f) return;
        ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), ((int) (b * 255) << 24) | 0x0A0000);
    }
}
