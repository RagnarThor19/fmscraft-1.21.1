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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.EnvyArena;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
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
 * Applied by ClientWorldMixin (light), BackgroundRendererMixin (fog) and WorldRendererMixin (no sky at all).
 */
public final class EnvySky {
    public static final Vec3d FOG = new Vec3d(0.03, 0.10, 0.04), STARE_FOG = new Vec3d(0.10, 0.28, 0.07);
    public static final float FOG_START = 16f, FOG_END = 130f, STARE_FOG_START = 6f, STARE_FOG_END = 80f;
    /** How bright the void lights the arena (vanilla's own goes 0.2-1). */
    public static final float LIGHT = 0.42f;
    private static final Identifier EYE = Identifier.of(FmsCraft.MOD_ID, "textures/environment/envy_eye.png");
    private static final float EYE_ASPECT = 32f / 48f;

    private static boolean inArena;
    private static float open, prevOpen, stare, prevStare;
    private static int ticks;
    private static List<EnvyArena.Eye> eyes;

    private EnvySky() {
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.player != null && client.world.getRegistryKey().equals(Arenas.key(Sin.ENVY));
        if (!inArena) {
            clear();
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
        open = MathHelper.clamp(open + (boss != null ? 1 / 70f : -1 / 90f), 0f, 1f);
        stare = MathHelper.clamp(stare + (boss != null && boss.isStaring() ? 0.08f : -0.05f), 0f, 1f);
    }

    public static void clear() {
        open = prevOpen = stare = prevStare = 0;
    }

    public static boolean active() {
        return inArena;
    }

    /** 0 normally, 1 at the height of his Evil Eye. */
    public static float stare(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevStare, stare) : 0;
    }

    public static Vec3d fog(float tickDelta) {
        float s = stare(tickDelta);
        // The stare throbs with a heartbeat
        float beat = s > 0 ? (MathHelper.sin((ticks + tickDelta) * 0.52f) * 0.5f + 0.5f) * 0.3f : 0;
        return FOG.lerp(STARE_FOG, Math.min(1f, s * (0.7f + beat)));
    }

    public static float fogStart(float tickDelta) {
        return MathHelper.lerp(stare(tickDelta), FOG_START, STARE_FOG_START);
    }

    public static float fogEnd(float tickDelta) {
        return MathHelper.lerp(stare(tickDelta), FOG_END, STARE_FOG_END);
    }

    // ---- The eyes ----

    public static void renderEyes(WorldRenderContext context) {
        if (!inArena) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        float opened = MathHelper.lerp(tickDelta, prevOpen, open);
        if (opened <= 0) return;
        if (eyes == null) eyes = EnvyArena.eyes();
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getEntityTranslucent(EYE));
        float s = stare(tickDelta), time = ticks + tickDelta;
        float r = MathHelper.lerp(s, 0.88f, 0.62f), g = MathHelper.lerp(s, 0.9f, 1.0f), b = MathHelper.lerp(s, 0.86f, 0.55f);
        for (EnvyArena.Eye eye : eyes) {
            // Each opens in its own time, and blinks now and then
            float stagger = (eye.seed() >>> 8 & 0xFF) / 255f;
            float lid = MathHelper.clamp(opened * 1.8f - stagger * 0.8f, 0f, 1f);
            if (lid <= 0) continue;
            lid *= blink(eye.seed(), time) * (1 + 0.18f * s);
            float w = (float) eye.width(), h = w * EYE_ASPECT * lid;
            matrices.push();
            matrices.translate(eye.x() - cam.x, eye.y() - cam.y, eye.z() - cam.z);
            if (eye.turns()) {
                Vec3d to = cam.subtract(eye.x(), eye.y(), eye.z());
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(to.x, to.z)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -Math.atan2(to.y, to.horizontalLength())));
            } else {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-eye.yaw()));
            }
            Matrix4f m = matrices.peek().getPositionMatrix();
            MatrixStack.Entry entry = matrices.peek();
            int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            float hw = w / 2, hh = h / 2;
            vertex(vertices, m, entry, -hw, -hh, 0, 1, r, g, b, light);
            vertex(vertices, m, entry, hw, -hh, 1, 1, r, g, b, light);
            vertex(vertices, m, entry, hw, hh, 1, 0, r, g, b, light);
            vertex(vertices, m, entry, -hw, hh, 0, 0, r, g, b, light);
            matrices.pop();
        }
        buffers.draw();
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

    // ---- The Evil Eye's throb at the edges of the screen ----

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        float s = stare(counter.getTickDelta(true));
        if (s <= 0.01f) return;
        float beat = MathHelper.sin((ticks + counter.getTickDelta(true)) * 0.52f) * 0.5f + 0.5f;
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
