package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The sky over RAGGIMEL's island and his manor ({@link net.ragnar.fmscraft.network.GreedSkyPayload}). Phase 1 is a
 * bright, pale blue noon. Phase 2 is navy, with great black holes hanging in it everywhere: each a disc of pure black
 * with a thin bright edge, a tilted ring of pale light whirling round it and the navy darkened round it, as if they
 * drank the light. It turns over the three seconds he calls the tide, and lifts slowly when he's beaten. Applied by
 * ClientWorldMixin (sky, clouds, light), WorldRendererMixin (the holes, faint stars, no sun) and BackgroundRendererMixin
 * (fog).
 */
public final class GreedSky {
    public static final Vec3d DAY_SKY = new Vec3d(0.56, 0.80, 1.0), DAY_FOG = new Vec3d(0.76, 0.89, 1.0);
    public static final Vec3d NIGHT_SKY = new Vec3d(0.04, 0.09, 0.32), NIGHT_FOG = new Vec3d(0.05, 0.10, 0.28);
    public static final Vec3d NIGHT_CLOUDS = new Vec3d(0.12, 0.17, 0.38);
    /** How bright the sky lights the island at night (vanilla's own goes 0.2-1). */
    public static final float NIGHT_LIGHT = 0.32f;
    /** How many black holes, and how far out the sky is drawn. */
    public static final int HOLES = 17;
    private static final float DISTANCE = 100f;

    private static int phase, ticks;
    private static boolean inArena;
    private static float night, prevNight;

    private GreedSky() {
    }

    public static void set(int newPhase) {
        phase = newPhase;
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.world.getRegistryKey().equals(Arenas.key(Sin.GREED));
        if (!inArena) {
            phase = 0;
            night = prevNight = 0;
            return;
        }
        ticks++;
        prevNight = night;
        night = MathHelper.clamp(night + (phase == 2 ? 1 / 60f : -1 / 80f), 0f, 1f);
    }

    public static void clear() {
        phase = 0;
        night = prevNight = 0;
    }

    /** Whether we're over the island or the manor (their sky is always the pale blue one, darkening by {@link #night}). */
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

    /** The black holes, faded in by the night. */
    public static void renderHoles(Matrix4f matrix4f, float tickDelta) {
        float n = night(tickDelta);
        if (n <= 0) return;
        float time = ticks + tickDelta;
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.004f));
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        for (int pass = 0; pass < 4; pass++) {
            // 0: the dark round each (normal blend), 1: the ring of light (adding), 2: the black, 3: the bright edge (adding)
            if (pass == 1 || pass == 3) {
                RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                        GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            Random random = Random.create(70300L);
            for (int i = 0; i < HOLES; i++) {
                double y = 0.02 + random.nextDouble() * 0.9, a = random.nextDouble() * MathHelper.TAU, h = Math.sqrt(1 - y * y);
                Vector3f dir = new Vector3f((float) (Math.cos(a) * h), (float) y, (float) (Math.sin(a) * h));
                float size = random.nextInt(4) == 0 ? 14f + random.nextFloat() * 10f : 5f + random.nextFloat() * 7f;
                float tilt = random.nextFloat() * MathHelper.TAU, flat = 0.25f + random.nextFloat() * 0.25f;
                float spin = (random.nextBoolean() ? 1 : -1) * (0.004f + random.nextFloat() * 0.006f);
                float pulse = 1f + 0.03f * MathHelper.sin(time * 0.05f + i);
                Vector3f c = new Vector3f(dir).mul(DISTANCE);
                Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
                Vector3f up = new Vector3f(dir).cross(side).normalize();
                float r = size * pulse;
                switch (pass) {
                    case 0 -> annulus(buffer, m, c, side, up, r, r * 2.3f, 1f, 1f, 0.01f, 0.03f, 0.08f, 0.55f * n, 0f);
                    case 1 -> {
                        // Tilted and flattened, whirling: the bright inner edge of it, fading out
                        Vector3f s2 = new Vector3f(side).rotateAxis(tilt + time * spin, dir.x, dir.y, dir.z);
                        Vector3f u2 = new Vector3f(dir).cross(s2).normalize();
                        annulus(buffer, m, c, s2, u2, r * 1.15f, r * 2.1f, 1f, flat, 0.62f, 0.72f, 1.0f, 0.5f * n, 0f);
                        annulus(buffer, m, c, s2, u2, r * 1.05f, r * 1.3f, 1f, flat, 0.9f, 0.92f, 1.0f, 0.55f * n, 0.15f * n);
                    }
                    case 2 -> annulus(buffer, m, c, side, up, 0, r, 1f, 1f, 0, 0, 0, n, n);
                    default -> annulus(buffer, m, c, side, up, r * 0.97f, r * 1.07f, 1f, 1f, 0.75f, 0.85f, 1.0f, 0.8f * n, 0f);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /**
     * A ring round {@code c} on the sky, from {@code inner} to {@code outer} across ({@code sx}/{@code sy} squash it into an
     * ellipse), coloured {@code r g b}, {@code innerAlpha} at its inside fading to {@code outerAlpha} at its outside.
     */
    private static void annulus(BufferBuilder buffer, Matrix4f m, Vector3f c, Vector3f side, Vector3f up, float inner, float outer,
                                float sx, float sy, float r, float g, float b, float innerAlpha, float outerAlpha) {
        int segments = 40;
        for (int k = 0; k < segments; k++) {
            float a0 = k * MathHelper.TAU / segments, a1 = (k + 1) * MathHelper.TAU / segments;
            vertex(buffer, m, c, side, up, a0, inner, sx, sy, r, g, b, innerAlpha);
            vertex(buffer, m, c, side, up, a1, inner, sx, sy, r, g, b, innerAlpha);
            vertex(buffer, m, c, side, up, a1, outer, sx, sy, r, g, b, outerAlpha);
            vertex(buffer, m, c, side, up, a0, outer, sx, sy, r, g, b, outerAlpha);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f m, Vector3f c, Vector3f side, Vector3f up, float a, float radius,
                               float sx, float sy, float r, float g, float b, float alpha) {
        float x = MathHelper.cos(a) * radius * sx, y = MathHelper.sin(a) * radius * sy;
        buffer.vertex(m, c.x + side.x * x + up.x * y, c.y + side.y * x + up.y * y, c.z + side.z * x + up.z * y).color(r, g, b, alpha);
    }
}
