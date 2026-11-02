package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.gluttony.GluttonyArena;
import net.ragnar.fmscraft.boss.gluttony.LalliEntity;
import net.ragnar.fmscraft.boss.gluttony.LalliTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;

/**
 * The skies of LalliLarge's two worlds, on each client. Nothing here needs telling by the server: which world you're in
 * is where you are ({@link GluttonyArena#inSnowfield}), and everything that keeps time does it from the song playing
 * on this client ({@link ClientBossMusic#secondsInto}).
 * <ul>
 *   <li><b>Space</b>, round the Earth: no sky, only black, with the stars (vanilla's, twice over, at full brightness)
 *       and the sun. On every chord strum of Apple Pie they turn {@link LalliTimeline#STRUM_TURN} of their cycle,
 *       swinging round in a moment ({@link LalliTimeline#skyTurn}); when the strums fall out the sky holds still.
 *       While he hangs over the pole to SIRENS it drifts on, slowly, then faster towards the switch-up. No clouds
 *       (they'd cut through the Earth).</li>
 *   <li><b>The snowfield</b>: a grey overcast, grey fog, a dim light, and snow falling (vanilla's own snowfall:
 *       the arena's biome is a frozen one, and here it's always snowing). The seven giant statues round the fight are
 *       drawn here too, players 45 blocks tall in statue.png, nodding their heads to every beat of SIRENS
 *       ({@link LalliTimeline#nod}) and still once the song stops.</li>
 * </ul>
 * Applied by WorldRendererMixin (sky, clouds), ClientWorldMixin (sky colour, light), BackgroundRendererMixin (fog) and
 * WorldMixin (the snow).
 */
public final class GluttonySky {
    public static final Vec3d SNOW_SKY = new Vec3d(0.6, 0.63, 0.68), SNOW_FOG = new Vec3d(0.68, 0.71, 0.75);
    public static final float SNOW_LIGHT = 0.72f, SNOW_FOG_START = 18f, SNOW_FOG_END = 175f;
    private static final Identifier SUN = Identifier.ofVanilla("textures/environment/sun.png");
    private static final Identifier STATUE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/lalli/statue.png");

    private static boolean inArena, inSnow;
    private static double lastTurn = LalliTimeline.SKY_START;
    private static double restingNod;
    private static PlayerEntityModel<LivingEntity> statue;

    private GluttonySky() {
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.player != null && client.world.getRegistryKey().equals(Arenas.key(Sin.GLUTTONY));
        if (!inArena) {
            clear();
            return;
        }
        inSnow = GluttonyArena.inSnowfield(client.player.getPos());
        // Once the song stops, the statue's head comes up and stays
        double seconds = ClientBossMusic.secondsInto(LalliEntity.PHASE_TWO.soundId(), 0);
        restingNod = seconds >= 0 ? LalliTimeline.nod(seconds) : restingNod * 0.75;
    }

    public static void clear() {
        inArena = inSnow = false;
        lastTurn = LalliTimeline.SKY_START;
        restingNod = 0;
    }

    /** Round the Earth, in space. */
    public static boolean space() {
        return inArena && !inSnow;
    }

    /** Out on the snowfield, under the overcast. */
    public static boolean snow() {
        return inArena && inSnow;
    }

    /** How far round the stars and sun have turned, in whole cycles. */
    public static double skyTurn(float tickDelta) {
        double pie = ClientBossMusic.secondsInto(LalliEntity.PHASE_ONE.soundId(), tickDelta);
        if (pie >= 0) {
            lastTurn = LalliTimeline.skyTurn(pie);
            return lastTurn;
        }
        double sirens = ClientBossMusic.secondsInto(LalliEntity.PHASE_TWO.soundId(), tickDelta);
        return sirens >= 0 ? lastTurn + LalliTimeline.drift(sirens) : lastTurn;
    }

    // ---- Space ----

    /** Stars and the sun on black, in place of the sky (the fog has already cleared the screen to black). */
    public static void renderSpace(Matrix4f matrix4f, Matrix4f projection, float tickDelta, VertexBuffer stars, Runnable fogCallback) {
        float turn = (float) (skyTurn(tickDelta) % 1.0) * 360f;
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(turn));
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        if (stars != null) {
            BackgroundRenderer.clearFog();
            // Vanilla's stars, and the same again turned another way, so the dark is full of them
            for (int k = 0; k < 2; k++) {
                matrices.push();
                if (k == 1) {
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(71f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(137f));
                }
                float b = k == 0 ? 1f : 0.75f;
                RenderSystem.setShaderColor(b, b, b, b);
                stars.bind();
                stars.draw(matrices.peek().getPositionMatrix(), projection, GameRenderer.getPositionProgram());
                VertexBuffer.unbind();
                matrices.pop();
            }
        }
        // The sun, and a faint glow round it
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, SUN);
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.25f);
        sun(m, 62f);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        sun(m, 30f);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        fogCallback.run();
    }

    private static void sun(Matrix4f m, float k) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(m, -k, 100.0f, -k).texture(0.0f, 0.0f);
        buffer.vertex(m, k, 100.0f, -k).texture(1.0f, 0.0f);
        buffer.vertex(m, k, 100.0f, k).texture(1.0f, 1.0f);
        buffer.vertex(m, -k, 100.0f, k).texture(0.0f, 1.0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    // ---- The statue ----

    /** How far the statue's head is bowed right now, 0-1. */
    public static double nod(float tickDelta) {
        double seconds = ClientBossMusic.secondsInto(LalliEntity.PHASE_TWO.soundId(), tickDelta);
        return seconds >= 0 ? LalliTimeline.nod(seconds) : restingNod;
    }

    public static void renderStatue(WorldRenderContext context) {
        if (!snow()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (statue == null) statue = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        statue.head.pitch = (float) (nod(tickDelta) * 0.55);
        statue.hat.copyTransform(statue.head);
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        // Lit by the open sky, wherever they stand (their chunks may not even be loaded this far off)
        int light = LightmapTextureManager.pack(0, 15);
        // The model is two blocks from its feet to the top of its head
        float s = GluttonyArena.STATUE_HEIGHT / 2f;
        for (GluttonyArena.Statue each : GluttonyArena.STATUES) {
            Vec3d at = each.pos();
            matrices.push();
            matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - each.yaw()));
            matrices.scale(s, s, s);
            matrices.scale(-1f, -1f, 1f);
            matrices.translate(0f, -1.501f, 0f);
            statue.render(matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(STATUE)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
            matrices.pop();
        }
        buffers.draw();
    }
}
