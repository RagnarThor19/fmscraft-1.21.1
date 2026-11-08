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
import net.ragnar.fmscraft.network.SlothSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The sky over RICKY's city, on each client ({@link SlothSkyPayload}).
 * <ul>
 *   <li><b>The sunset</b> (phase 1): vanilla's own, the dimension's clock standing at 12500: the sun low in the west,
 *       the sky gold and red over the flooded streets. Nothing is drawn here.</li>
 *   <li><b>The dark</b> (Super Saiyan): the sunset drains away over three seconds to a black sky full of glowing red
 *       lights, big and small, twinkling and turning slowly, with a grey haze settling over the city and the light
 *       going dim. It lifts back to the sunset when he's beaten.</li>
 * </ul>
 * Applied by WorldRendererMixin (the sky, no clouds in the dark), ClientWorldMixin (sky colour, light) and
 * BackgroundRendererMixin (the haze).
 */
public final class SlothSky {
    public static final Vec3d BLACK = Vec3d.ZERO, HAZE = new Vec3d(0.31, 0.3, 0.31);
    public static final float DARK_LIGHT = 0.2f, FOG_START = 24f, FOG_END = 130f;
    /** How many red lights there are, and how far out they're drawn (in the sky's own units). */
    private static final int LIGHTS = 1100;
    private static final float DISTANCE = 100f;

    private static boolean inArena;
    private static int phase = 1, ticks;
    private static float dark, prevDark;

    private SlothSky() {
    }

    public static void receive(SlothSkyPayload payload) {
        phase = payload.phase();
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.world.getRegistryKey().equals(Arenas.key(Sin.SLOTH));
        if (!inArena) {
            clear();
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        prevDark = dark;
        dark = MathHelper.clamp(dark + (phase == 2 ? 1 / 60f : -1 / 80f), 0f, 1f);
    }

    public static void clear() {
        inArena = false;
        phase = 1;
        dark = prevDark = 0;
    }

    public static boolean active() {
        return inArena;
    }

    /** 0 at sunset, 1 in the dark. */
    public static float dark(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevDark, dark) : 0f;
    }

    public static Vec3d skyColor(Vec3d vanilla, float tickDelta) {
        return vanilla.lerp(BLACK, dark(tickDelta));
    }

    public static Vec3d fogColor(Vec3d vanilla, float tickDelta) {
        return vanilla.lerp(HAZE, dark(tickDelta));
    }

    public static float light(float vanilla, float tickDelta) {
        return MathHelper.lerp(dark(tickDelta), vanilla, DARK_LIGHT);
    }

    /** Fog start and end, given what vanilla (or Blindness, water...) set: never thinner than that. */
    public static float fogStart(float vanilla, float tickDelta) {
        return Math.min(vanilla, MathHelper.lerp(dark(tickDelta), vanilla, FOG_START));
    }

    public static float fogEnd(float vanilla, float tickDelta) {
        return Math.min(vanilla, MathHelper.lerp(dark(tickDelta), vanilla, FOG_END));
    }

    /** Whether vanilla's sky (the sunset) is drawn: until it's half gone. */
    public static boolean vanillaSky(float tickDelta) {
        return dark(tickDelta) < 0.5f;
    }

    /** The dark in place of the sky: a black dome going grey towards the horizon, and the red lights over it. */
    public static void renderDark(Matrix4f matrix4f, float tickDelta) {
        float d = dark(tickDelta);
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int rings = 12, segments = 32;
        for (int r = 0; r < rings; r++) {
            float e0 = -0.35f + r * (MathHelper.HALF_PI + 0.35f) / rings, e1 = -0.35f + (r + 1) * (MathHelper.HALF_PI + 0.35f) / rings;
            for (int s = 0; s < segments; s++) {
                float a0 = s * MathHelper.TAU / segments, a1 = (s + 1) * MathHelper.TAU / segments;
                domeVertex(buffer, m, a0, e0, d);
                domeVertex(buffer, m, a0, e1, d);
                domeVertex(buffer, m, a1, e1, d);
                domeVertex(buffer, m, a1, e0, d);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        renderLights(matrix4f, tickDelta);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** A point of the dome: the haze's grey at the horizon, black from a little above it. */
    private static void domeVertex(BufferBuilder buffer, Matrix4f m, float angle, float elevation, float alpha) {
        float up = MathHelper.sin(elevation), out = MathHelper.cos(elevation);
        float t = MathHelper.clamp((up + 0.05f) / 0.4f, 0f, 1f);
        t = t * t * (3 - 2 * t);
        float r = (float) HAZE.x * (1 - t), g = (float) HAZE.y * (1 - t), b = (float) HAZE.z * (1 - t);
        buffer.vertex(m, MathHelper.cos(angle) * out * DISTANCE * 1.2f, up * DISTANCE * 1.2f, MathHelper.sin(angle) * out * DISTANCE * 1.2f).color(r, g, b, alpha);
    }

    /** The red lights, fading in with the dark (over the sunset while it drains away, then on their own). */
    public static void renderLights(Matrix4f matrix4f, float tickDelta) {
        float d = dark(tickDelta);
        if (d <= 0) return;
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        float time = ticks + tickDelta;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.006f));
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Random random = Random.create(12500L);
        for (int i = 0; i < LIGHTS; i++) {
            // Anywhere over the horizon, a few just under it (seen over the haze)
            double y = random.nextDouble() * 1.12 - 0.12, a = random.nextDouble() * MathHelper.TAU, h = Math.sqrt(Math.max(0, 1 - y * y));
            Vector3f dir = new Vector3f((float) (Math.cos(a) * h), (float) y, (float) (Math.sin(a) * h));
            boolean big = random.nextInt(14) == 0;
            float size = big ? 0.9f + random.nextFloat() * 0.9f : 0.18f + random.nextFloat() * 0.35f;
            float twinkle = 0.55f + 0.45f * MathHelper.sin(time * (0.03f + random.nextFloat() * 0.08f) + random.nextFloat() * 6.3f);
            float red = 0.75f + random.nextFloat() * 0.25f, green = random.nextFloat() * 0.18f, blue = random.nextFloat() * 0.08f;
            float alpha = d * twinkle * (y < 0.05 ? 0.6f : 1f);
            light(buffer, m, dir, size, red, green, blue, alpha);
            // The big ones glow: a soft halo round them
            if (big) light(buffer, m, dir, size * 3.2f, red, green * 0.5f, blue, alpha * 0.22f);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** A square of light facing the middle, {@code size} across, out along {@code dir}. */
    private static void light(BufferBuilder buffer, Matrix4f m, Vector3f dir, float size, float r, float g, float b, float a) {
        Vector3f c = new Vector3f(dir).mul(DISTANCE);
        Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize().mul(size * 0.5f);
        Vector3f up = new Vector3f(dir).cross(side).normalize().mul(size * 0.5f);
        buffer.vertex(m, c.x - side.x - up.x, c.y - side.y - up.y, c.z - side.z - up.z).color(r, g, b, a);
        buffer.vertex(m, c.x + side.x - up.x, c.y + side.y - up.y, c.z + side.z - up.z).color(r, g, b, a);
        buffer.vertex(m, c.x + side.x + up.x, c.y + side.y + up.y, c.z + side.z + up.z).color(r, g, b, a);
        buffer.vertex(m, c.x - side.x + up.x, c.y - side.y + up.y, c.z - side.z + up.z).color(r, g, b, a);
    }
}
