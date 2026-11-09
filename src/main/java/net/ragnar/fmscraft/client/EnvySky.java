package net.ragnar.fmscraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.EnvyArena;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
import net.ragnar.fmscraft.boss.envy.GummssTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The void round GUMMSS's arena, on each client. No sky: deep green fog (the dimension uses the Nether's effects, so
 * there's no sun, moon, stars or clouds, only the fog colour) closing in on the black, and a dim light.
 * <p>
 * And the eyes ({@link EnvyArena#eyes()}), the vanilla-pixel version of the eye GUMMSS was designed with
 * (tools/textures/envy_eye_textures.py): huge ones hanging out in the void that turn to follow you and blink, and small
 * ones on the pillars staring in at the middle. They open one by one when he arrives and close when he dies. During
 * his Evil Eye they all widen and turn a sickly green, the fog brightens and closes in, and the edges of the screen
 * throb green.
 * <p>
 * His Covet (half health): the song is turned down to nothing and back ({@link ClientBossMusic#duck}), and the void
 * blinks: the light goes out and comes back, faster and faster, only the eyes left in the black, now and then a flare
 * of green. While the four of him cast, it stays darker, the eyes widen and sicken and the edges of the screen throb.
 * After it the void is awake: the fog breathes, the eyes drift and twitch, bloodshot, more of them open just past the
 * rim, a great eye opens in the fog overhead, the dark blinks by itself now and then, and things move out in it.
 * <p>
 * Applied by ClientWorldMixin (light), BackgroundRendererMixin (fog) and WorldRendererMixin (no sky at all).
 */
public final class EnvySky {
    public static final Vec3d FOG = new Vec3d(0.03, 0.10, 0.04), STARE_FOG = new Vec3d(0.10, 0.28, 0.07);
    /** The fog breathing in, once he's awake; the green flare of his Covet. */
    public static final Vec3d AWAKE_FOG = new Vec3d(0.07, 0.17, 0.05), FLARE_FOG = new Vec3d(0.22, 0.55, 0.16);
    public static final float FOG_START = 16f, FOG_END = 130f, STARE_FOG_START = 6f, STARE_FOG_END = 80f;
    /** How bright the void lights the arena (vanilla's own goes 0.2-1), and how dark it goes when it blinks. */
    public static final float LIGHT = 0.42f, BLINK_LIGHT = 0.03f;
    private static final Identifier EYE = Identifier.of(FmsCraft.MOD_ID, "textures/environment/envy_eye.png");
    private static final float EYE_ASPECT = 32f / 48f;
    /** The great eye that opens overhead once he's awake: how high over the middle, and how wide. */
    private static final double GREAT_EYE_HEIGHT = 72, GREAT_EYE_WIDTH = 110;
    private static final SoundEvent[] WHISPERS = {SoundEvents.AMBIENT_CAVE.value(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(),
            SoundEvents.ENTITY_WARDEN_AMBIENT, SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS,
            SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value()};

    private static boolean inArena;
    private static float open, prevOpen, stare, prevStare;
    private static float dark, prevDark, flare, prevFlare, dread, prevDread, awake, prevAwake;
    private static int ticks, nextWhisper = 400;
    private static List<EnvyArena.Eye> eyes, awakeEyes;

    private EnvySky() {
    }

    public static void tick(MinecraftClient client) {
        boolean was = inArena;
        inArena = client.world != null && client.player != null && client.world.getRegistryKey().equals(Arenas.key(Sin.ENVY));
        if (!inArena) {
            if (was) clear();
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        GummssEntity boss = null;
        for (GummssEntity g : client.world.getEntitiesByClass(GummssEntity.class, client.player.getBoundingBox().expand(220), e -> e.isAlive())) {
            boss = g;
        }
        prevOpen = open;
        prevStare = stare;
        prevDark = dark;
        prevFlare = flare;
        prevDread = dread;
        prevAwake = awake;
        open = MathHelper.clamp(open + (boss != null ? 1 / 70f : -1 / 90f), 0f, 1f);
        stare = MathHelper.clamp(stare + (boss != null && boss.isStaring() ? 0.08f : -0.05f), 0f, 1f);
        awake = MathHelper.clamp(awake + (boss != null && boss.isAwake() ? 1 / 140f : -1 / 60f), 0f, 1f);

        int covet = boss != null ? boss.covetTicks() : 0, impact = boss != null ? boss.covetImpact() : 0;
        float darkTo = 0, flareTo = 0, dreadTo = 0;
        int fade = GummssTimeline.COVET_FADE;
        if (covet > 0 && covet < fade) {
            // The void blinking: off and on, faster and faster, only the eyes left in the black; now and then a flare
            float u = covet / (float) fade;
            int step = ticks / (u < 0.5f ? 3 : 2);
            darkTo = hash(step) < 0.3f + 0.45f * u ? 1f : 0f;
            if (hash(step * 31 + 7) < 0.05f + 0.06f * u) flareTo = 1f;
        } else if (covet >= fade && (impact <= 0 || covet < impact)) {
            float u = impact > fade ? MathHelper.clamp((covet - fade) / (float) (impact - fade), 0, 1) : 0;
            // Darker while they cast, still blinking (more as it comes), flaring on the beat of its heart
            int step = ticks / 3;
            darkTo = hash(step) < 0.15f + 0.3f * u ? 1f : 0.3f + 0.35f * u;
            if (hash(step * 17 + 3) < 0.03f + 0.07f * u) flareTo = 0.8f;
            dreadTo = 0.35f + 0.65f * u;
        } else if (covet >= fade) {
            // Down: the light comes back in the blast
            flareTo = covet < impact + 4 ? 1f : 0f;
        } else if (awake > 0) {
            // Awake: every so often the void blinks by itself, a few quick flickers
            int cycle = (ticks + 200) % 460;
            if (cycle < 16 && hash(ticks / 2 + 991) < 0.65f) darkTo = 0.9f * awake;
        }
        dark = darkTo > dark ? Math.min(darkTo, dark + 0.6f) : Math.max(darkTo, dark - 0.35f);
        flare = Math.max(flareTo, flare * 0.72f);
        dread = dreadTo > dread ? Math.min(dreadTo, dread + 0.05f) : Math.max(dreadTo, dread - 0.04f);

        // The song: turned down to nothing as he goes, and back up to its height as it lands
        float volume = covet <= 0 ? 1f : covet >= fade && impact <= 0 ? 0f : GummssTimeline.covetVolume(covet, impact);
        ClientBossMusic.duck(volume);

        // Awake: things moving out in the dark
        if (awake > 0.5f && --nextWhisper <= 0) {
            var r = client.world.random;
            nextWhisper = 180 + r.nextInt(320);
            double a = r.nextDouble() * MathHelper.TAU, d = 14 + r.nextDouble() * 12;
            Vec3d at = client.player.getPos().add(Math.cos(a) * d, -2 + r.nextDouble() * 8, Math.sin(a) * d);
            client.world.playSound(at.x, at.y, at.z, WHISPERS[r.nextInt(WHISPERS.length)], SoundCategory.AMBIENT,
                    0.7f + r.nextFloat() * 0.5f, 0.45f + r.nextFloat() * 0.3f, false);
        }
    }

    public static void clear() {
        open = prevOpen = stare = prevStare = 0;
        dark = prevDark = flare = prevFlare = dread = prevDread = awake = prevAwake = 0;
        ClientBossMusic.duck(1f);
    }

    public static boolean active() {
        return inArena;
    }

    /** 0 normally, 1 at the height of his Evil Eye. */
    public static float stare(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevStare, stare) : 0;
    }

    private static float dark(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevDark, dark);
    }

    private static float flare(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevFlare, flare);
    }

    /** 0 normally, rising to 1 as his Covet comes down. */
    private static float dread(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevDread, dread) : 0;
    }

    /** 0 until his Covet, then 1: the void awake. */
    private static float awake(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevAwake, awake);
    }

    /** The void breathing, once he's awake: 0-1, in and out every few seconds. */
    private static float breath(float tickDelta) {
        return (MathHelper.sin((ticks + tickDelta) * 0.045f) * 0.5f + 0.5f) * awake(tickDelta);
    }

    public static float light(float tickDelta) {
        float light = LIGHT - 0.06f * breath(tickDelta);
        light = MathHelper.lerp(dark(tickDelta), light, BLINK_LIGHT);
        return Math.min(1f, light + 0.45f * flare(tickDelta));
    }

    public static Vec3d fog(float tickDelta) {
        float s = stare(tickDelta);
        // The stare throbs with a heartbeat
        float beat = s > 0 ? (MathHelper.sin((ticks + tickDelta) * 0.52f) * 0.5f + 0.5f) * 0.3f : 0;
        Vec3d fog = FOG.lerp(AWAKE_FOG, breath(tickDelta) * 0.8f);
        fog = fog.lerp(STARE_FOG, Math.min(1f, s * (0.7f + beat)));
        fog = fog.lerp(Vec3d.ZERO, dark(tickDelta));
        return fog.lerp(FLARE_FOG, flare(tickDelta));
    }

    public static float fogStart(float tickDelta) {
        float start = MathHelper.lerp(stare(tickDelta), FOG_START, STARE_FOG_START);
        return MathHelper.lerp(Math.max(dread(tickDelta), awake(tickDelta) * 0.5f), start, 6f);
    }

    public static float fogEnd(float tickDelta) {
        float end = MathHelper.lerp(stare(tickDelta), FOG_END, STARE_FOG_END);
        // Breathing in and out, once he's awake
        end -= 18f * breath(tickDelta);
        return MathHelper.lerp(dread(tickDelta) * 0.4f, end, 105f);
    }

    // ---- The eyes ----

    public static void renderEyes(WorldRenderContext context) {
        if (!inArena) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        float opened = MathHelper.lerp(tickDelta, prevOpen, open);
        if (opened <= 0) return;
        if (eyes == null) eyes = EnvyArena.eyes();
        if (awakeEyes == null) awakeEyes = EnvyArena.awakeEyes();
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getEntityTranslucent(EYE));
        float time = ticks + tickDelta, aw = awake(tickDelta), fl = flare(tickDelta);
        // Widening and sickening in his Evil Eye and as his Covet comes down; bloodshot once he's awake
        float s = Math.max(stare(tickDelta), dread(tickDelta));
        float r = MathHelper.lerp(s, 0.88f, 0.62f), g = MathHelper.lerp(s, 0.9f, 1.0f), b = MathHelper.lerp(s, 0.86f, 0.55f);
        r = MathHelper.lerp(aw * 0.7f, r, 1.0f);
        g = MathHelper.lerp(aw * 0.7f, g, 0.74f);
        b = MathHelper.lerp(aw * 0.7f, b, 0.66f);
        // In the flare of green, every one of them shuts
        float flinch = 1f - 0.92f * fl;
        for (EnvyArena.Eye eye : eyes) drawEye(matrices, vertices, cam, eye, opened, time, s, aw, flinch, r, g, b);
        if (aw > 0) {
            for (EnvyArena.Eye eye : awakeEyes) drawEye(matrices, vertices, cam, eye, aw, time, s, aw, flinch, r, g, b);
            drawGreatEye(matrices, vertices, cam, aw, time, flinch, r, g, b);
        }
        buffers.draw();
    }

    private static void drawEye(MatrixStack matrices, VertexConsumer vertices, Vec3d cam, EnvyArena.Eye eye, float opened, float time,
                                float wide, float aw, float flinch, float r, float g, float b) {
        // Each opens in its own time, and blinks now and then (and twitches, once he's awake)
        float stagger = (eye.seed() >>> 8 & 0xFF) / 255f;
        float lid = MathHelper.clamp(opened * 1.8f - stagger * 0.8f, 0f, 1f);
        if (lid <= 0) return;
        lid *= blink(eye.seed(), time) * (1 + 0.18f * wide) * flinch;
        if (aw > 0) lid *= MathHelper.lerp(aw, 1f, twitch(eye.seed(), time));
        if (lid <= 0.01f) return;
        float w = (float) eye.width(), h = w * EYE_ASPECT * lid;
        double x = eye.x(), y = eye.y(), z = eye.z();
        if (aw > 0 && eye.turns()) {
            // Drifting, slowly, as if they were alive
            float seed = (eye.seed() & 1023) / 97f;
            y += MathHelper.sin(time * 0.017f + seed) * 2.5f * aw;
            x += MathHelper.cos(time * 0.011f + seed * 1.3f) * 1.5f * aw;
        }
        matrices.push();
        matrices.translate(x - cam.x, y - cam.y, z - cam.z);
        if (eye.turns()) {
            Vec3d to = cam.subtract(x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(to.x, to.z)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -Math.atan2(to.y, to.horizontalLength())));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-eye.yaw()));
        }
        quad(matrices, vertices, w / 2, h / 2, r, g, b);
        matrices.pop();
    }

    /** Overhead, in the fog: one great eye, opening slowly once he's awake, looking down at all of you, turning. */
    private static void drawGreatEye(MatrixStack matrices, VertexConsumer vertices, Vec3d cam, float aw, float time, float flinch,
                                     float r, float g, float b) {
        float lid = MathHelper.clamp((aw - 0.25f) / 0.75f, 0f, 1f);
        lid *= blink(0x5EE5L << 20, time * 0.4f) * flinch;
        if (lid <= 0.01f) return;
        float w = (float) GREAT_EYE_WIDTH * (1 + 0.03f * MathHelper.sin(time * 0.05f)), h = w * EYE_ASPECT * lid;
        matrices.push();
        matrices.translate(EnvyArena.CENTER.x - cam.x, EnvyArena.FLOOR_Y + GREAT_EYE_HEIGHT - cam.y, EnvyArena.CENTER.z - cam.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.08f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        quad(matrices, vertices, w / 2, h / 2, r, g, b);
        matrices.pop();
    }

    private static void quad(MatrixStack matrices, VertexConsumer vertices, float hw, float hh, float r, float g, float b) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        vertex(vertices, m, entry, -hw, -hh, 0, 1, r, g, b, light);
        vertex(vertices, m, entry, hw, -hh, 1, 1, r, g, b, light);
        vertex(vertices, m, entry, hw, hh, 1, 0, r, g, b, light);
        vertex(vertices, m, entry, -hw, hh, 0, 0, r, g, b, light);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, MatrixStack.Entry entry, float x, float y, float u, float t,
                               float r, float g, float b, int light) {
        v.vertex(m, x, y, 0).color(r, g, b, 1f).texture(u, t).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
    }

    /** 1 while the eye is open, dipping to almost 0 for a quick blink every few seconds. */
    static float blink(long seed, float time) {
        int period = 90 + (int) ((seed >>> 20) & 127);
        float t = (time + (seed & 255)) % period;
        if (t > 7) return 1f;
        float x = Math.abs(t - 3.5f) / 3.5f;
        return 0.06f + 0.94f * x * x;
    }

    /** Awake: now and then an eye flutters, half-shut and open again, two or three times in a second. */
    static float twitch(long seed, float time) {
        int period = 60 + (int) ((seed >>> 32) & 63);
        int cycle = (int) Math.floor((time + ((seed >>> 12) & 511)) / period);
        if (hash(cycle * 13 + (int) (seed & 0xFFFF)) > 0.35f) return 1f;
        float t = (time + ((seed >>> 12) & 511)) % period;
        if (t > 14) return 1f;
        return 0.55f + 0.45f * Math.abs(MathHelper.cos(t * 0.9f));
    }

    /** A steady pseudo-random 0-1 for {@code n}: the same on every client. */
    static float hash(int n) {
        int x = n * 0x27d4eb2d;
        x ^= x >>> 15;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        return (x >>> 8) / (float) (1 << 24);
    }

    // ---- The Evil Eye's throb at the edges of the screen (and his Covet's) ----

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        float tickDelta = counter.getTickDelta(true);
        float s = Math.max(stare(tickDelta), dread(tickDelta) * 0.9f);
        if (s <= 0.01f) return;
        // Faster as the Covet comes down, like the heartbeat
        float speed = 0.52f + 0.5f * dread(tickDelta);
        float beat = MathHelper.sin((ticks + tickDelta) * speed) * 0.5f + 0.5f;
        int a = (int) (s * (90 + 70 * beat));
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int edge = Math.max(24, Math.min(w, h) / 5), color = 0x1E5A14;
        int solid = (a << 24) | color, none = color;
        ctx.fillGradient(0, 0, w, edge, solid, none);
        ctx.fillGradient(0, h - edge, w, h, none, solid);
        // Sides: a few strips stepping out, as fillGradient only runs top to bottom
        for (int i = 0; i < 8; i++) {
            int strip = edge / 8, alpha = a * (8 - i) / 8;
            ctx.fill(i * strip, 0, (i + 1) * strip, h, (alpha << 24) | color);
            ctx.fill(w - (i + 1) * strip, 0, w - i * strip, h, (alpha << 24) | color);
        }
    }
}
