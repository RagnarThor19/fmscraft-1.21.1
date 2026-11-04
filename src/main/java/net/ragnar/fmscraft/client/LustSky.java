package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.lust.MugenTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.network.LustSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;

/**
 * The sky over MUGEN's compound, on each client ({@link LustSkyPayload}).
 * <ul>
 *   <li><b>Phase 1</b>: a deep violet night, stars, and a great full moon low over the hall to the west (the way
 *       everyone faces as they arrive), a faint violet halo round it. Purple haze, a dim light.</li>
 *   <li><b>Phase 2</b>: a blood-red sky and an eclipse where the moon was: a black disc in a ragged ring of red fire
 *       (tools/textures/mugen_textures.py), throbbing slowly. Few stars, darker, the red haze closing in. It turns at
 *       once (under the black screen) and lifts back to the moonlit night when he's beaten.</li>
 *   <li><b>The Blackout</b>: everything goes black round you, and you see four blocks
 *       ({@link MugenTimeline#BLACKOUT_SIGHT}); it closes in fast and opens again slowly.</li>
 * </ul>
 * Applied by WorldRendererMixin (the sky itself, no clouds), ClientWorldMixin (sky colour, light) and
 * BackgroundRendererMixin (fog).
 */
public final class LustSky {
    public static final Vec3d NIGHT_SKY = new Vec3d(0.13, 0.05, 0.22), NIGHT_FOG = new Vec3d(0.12, 0.06, 0.18);
    public static final Vec3d ECLIPSE_SKY = new Vec3d(0.24, 0.02, 0.03), ECLIPSE_FOG = new Vec3d(0.17, 0.01, 0.02);
    public static final float NIGHT_LIGHT = 0.5f, ECLIPSE_LIGHT = 0.36f;
    /** In the eclipse the red haze closes in to this (start, end). */
    public static final float ECLIPSE_FOG_START = 14f, ECLIPSE_FOG_END = 110f;
    /** How far the moon (and the eclipse) sits from straight overhead, towards the west: 38 degrees over the horizon. */
    private static final float MOON_ANGLE = 52f;
    private static final Identifier MOON = Identifier.ofVanilla("textures/environment/moon_phases.png");
    private static final Identifier SUN = Identifier.ofVanilla("textures/environment/sun.png");
    private static final Identifier CORONA = Identifier.of(FmsCraft.MOD_ID, "textures/environment/eclipse.png");
    private static final Identifier DISC = Identifier.of(FmsCraft.MOD_ID, "textures/environment/eclipse_disc.png");

    private static boolean inArena;
    private static int phase = 1, blackout, ticks;
    private static float eclipse, prevEclipse, dark, prevDark;

    private LustSky() {
    }

    public static void receive(LustSkyPayload payload) {
        int was = phase;
        phase = payload.phase();
        // The eclipse comes all at once (it's under the black); the moon comes back slowly
        if (phase == 2 && was != 2) eclipse = prevEclipse = 1;
        if (payload.blackout() >= 0) blackout = payload.blackout();
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.world.getRegistryKey().equals(Arenas.key(Sin.LUST));
        if (!inArena) {
            clear();
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        prevEclipse = eclipse;
        eclipse = MathHelper.clamp(eclipse + (phase == 2 ? 0.1f : -1 / 100f), 0f, 1f);
        prevDark = dark;
        if (blackout > 0) blackout--;
        dark = MathHelper.clamp(dark + (blackout > 0 ? 0.2f : -1 / 30f), 0f, 1f);
    }

    public static void clear() {
        inArena = false;
        phase = 1;
        blackout = 0;
        eclipse = prevEclipse = dark = prevDark = 0;
    }

    public static boolean active() {
        return inArena;
    }

    /** 0 under the moon, 1 in the eclipse. */
    public static float eclipse(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevEclipse, eclipse) : 0;
    }

    /** 0 normally, 1 at the height of the Blackout. */
    public static float dark(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevDark, dark) : 0;
    }

    public static Vec3d skyColor(float tickDelta) {
        return NIGHT_SKY.lerp(ECLIPSE_SKY, eclipse(tickDelta)).multiply(1 - dark(tickDelta));
    }

    public static Vec3d fogColor(float tickDelta) {
        return NIGHT_FOG.lerp(ECLIPSE_FOG, eclipse(tickDelta)).multiply(1 - dark(tickDelta));
    }

    public static float light(float tickDelta) {
        return MathHelper.lerp(eclipse(tickDelta), NIGHT_LIGHT, ECLIPSE_LIGHT);
    }

    /** Fog start and end, given what vanilla (or Blindness, water...) set: never thinner than that. */
    public static float fogStart(float vanilla, float tickDelta) {
        float e = eclipse(tickDelta), d = dark(tickDelta);
        float start = Math.min(vanilla, MathHelper.lerp(e, vanilla, ECLIPSE_FOG_START));
        return MathHelper.lerp(d, start, 0f);
    }

    public static float fogEnd(float vanilla, float tickDelta) {
        float e = eclipse(tickDelta), d = dark(tickDelta);
        float end = Math.min(vanilla, MathHelper.lerp(e, vanilla, ECLIPSE_FOG_END));
        return MathHelper.lerp(d * d * (3 - 2 * d), end, MugenTimeline.BLACKOUT_SIGHT);
    }

    // ---- The sky ----

    /** Stars, and the moon or the eclipse, in place of the sky (the fog has already cleared the screen to its colour). */
    public static void render(Matrix4f matrix4f, Matrix4f projection, float tickDelta, VertexBuffer stars, Runnable fogCallback) {
        float d = dark(tickDelta);
        if (d >= 0.99f) return;
        float e = eclipse(tickDelta), seen = 1 - d;
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        if (stars != null) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(150f + ticks * 0.004f));
            float b = MathHelper.lerp(e, 0.85f, 0.25f) * seen;
            BackgroundRenderer.clearFog();
            RenderSystem.setShaderColor(b, b, b, b);
            stars.bind();
            stars.draw(matrices.peek().getPositionMatrix(), projection, GameRenderer.getPositionProgram());
            VertexBuffer.unbind();
            matrices.pop();
        }
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MOON_ANGLE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        if (e < 1) {
            // The full moon, big and low, a violet halo round it
            float moon = (1 - e) * seen;
            RenderSystem.setShaderTexture(0, SUN);
            RenderSystem.setShaderColor(0.62f, 0.38f, 1f, 0.4f * moon);
            quad(m, 64f, 0, 0, 1, 1);
            RenderSystem.setShaderTexture(0, MOON);
            RenderSystem.setShaderColor(0.92f, 0.86f, 1f, moon);
            quad(m, 26f, 0, 0, 0.25f, 0.5f);
        }
        if (e > 0) {
            // The eclipse: the corona glowing, throbbing slowly, and the black moon over it
            float throb = 1f + 0.06f * MathHelper.sin(ticks * 0.08f);
            RenderSystem.setShaderTexture(0, SUN);
            RenderSystem.setShaderColor(1f, 0.12f, 0.08f, 0.35f * e * seen);
            quad(m, 80f * throb, 0, 0, 1, 1);
            RenderSystem.setShaderTexture(0, CORONA);
            RenderSystem.setShaderColor(1f, 1f, 1f, e * seen);
            quad(m, 34f * throb, 0, 0, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, DISC);
            RenderSystem.setShaderColor(1f, 1f, 1f, e * seen);
            quad(m, 34f, 0, 0, 1, 1);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        fogCallback.run();
    }

    private static void quad(Matrix4f m, float k, float u0, float v0, float u1, float v1) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(m, -k, 100.0f, -k).texture(u0, v0);
        buffer.vertex(m, k, 100.0f, -k).texture(u1, v0);
        buffer.vertex(m, k, 100.0f, k).texture(u1, v1);
        buffer.vertex(m, -k, 100.0f, k).texture(u0, v1);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
