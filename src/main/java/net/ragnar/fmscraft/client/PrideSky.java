package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.pride.LeonardEntity;
import net.ragnar.fmscraft.boss.pride.LeonardTimeline;
import net.ragnar.fmscraft.boss.pride.PrideArena;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.network.PrideSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;

/**
 * The sky over LEONARD's plaza, and Munch, on each client ({@link PrideSkyPayload}).
 * <ul>
 *   <li><b>The day</b>: a light blue sky and the noon sun, the game's own clouds drawn as a sea far under the plaza
 *       ({@link PrideArena#CLOUD_SEA}), and banks of blocky cloud all round the horizon (drawn here, so they're there
 *       even with clouds turned off).</li>
 *   <li><b>The storm</b> (CONEMAN): the sky goes a dark slate, the sun is gone, the clouds turn grey, the light dims.
 *       It comes on over three seconds and lifts when he's beaten.</li>
 *   <li><b>Munch</b>: a white giant 40 blocks tall (a grown player model, not a child's) (munch.png on a player model) rising out of the clouds past the
 *       east rim with Grove St. Party's first five seconds ({@link LeonardTimeline#munchRise}), then nodding hard on
 *       every beat ({@link LeonardTimeline#nod}) and turning his head to watch you. Gathering his beam his head stops on
 *       its target and his eyes burn brighter and brighter; then the beam, five blocks thick. When LEONARD becomes
 *       CONEMAN he topples back into the clouds.</li>
 * </ul>
 * Applied by WorldRendererMixin (the sky, the cloud sea), ClientWorldMixin (sky and cloud colour, light) and
 * BackgroundRendererMixin (fog); Munch and his beam are drawn after the entities (FmsCraftClient).
 */
public final class PrideSky {
    public static final Vec3d DAY_SKY = new Vec3d(0.47, 0.71, 1.0), DAY_FOG = new Vec3d(0.76, 0.87, 1.0);
    public static final Vec3d STORM_SKY = new Vec3d(0.2, 0.23, 0.3), STORM_FOG = new Vec3d(0.27, 0.29, 0.35);
    public static final Vec3d STORM_CLOUDS = new Vec3d(0.42, 0.44, 0.5);
    public static final float STORM_LIGHT = 0.55f;
    private static final Identifier MUNCH = Identifier.of(FmsCraft.MOD_ID, "textures/entity/leonard/munch.png");
    private static final RenderLayer MUNCH_EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/leonard/munch_eyes.png"));
    private static final RenderLayer BEAM = RenderLayer.of("fmscraft_munch_beam",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.COLOR_PROGRAM)
                    .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .build(false));
    /** Cloud banks round the horizon: how many, how far out (in the sky's own units). */
    private static final int BANKS = 64;
    private static final float BANK_DISTANCE = 110f;

    private static boolean inArena;
    private static int phase = 1, munch = PrideSkyPayload.MUNCH_HIDDEN, munchTicks, munchFor;
    private static float storm, prevStorm;
    private static Vec3d target = Vec3d.ZERO;
    private static double headYaw, headPitch, prevHeadYaw, prevHeadPitch, lastNod;
    private static PlayerEntityModel<LivingEntity> giant;

    private PrideSky() {
    }

    public static void receive(PrideSkyPayload payload) {
        phase = payload.phase();
        if (payload.munch() != munch) munchTicks = 0;
        munch = payload.munch();
        munchFor = payload.ticks();
        if (munch == PrideSkyPayload.MUNCH_CHARGING || munch == PrideSkyPayload.MUNCH_FIRING) target = new Vec3d(payload.x(), payload.y(), payload.z());
        // Into the storm at once if it's already raging when you arrive
        if (phase == 2 && storm == 0 && munch == PrideSkyPayload.MUNCH_HIDDEN) storm = prevStorm = 0.01f;
    }

    public static void tick(MinecraftClient client) {
        inArena = client.world != null && client.player != null && client.world.getRegistryKey().equals(Arenas.key(Sin.PRIDE));
        if (!inArena) {
            clear();
            return;
        }
        if (client.isPaused()) return;
        munchTicks++;
        prevStorm = storm;
        storm = MathHelper.clamp(storm + (phase == 2 ? 1 / 60f : -1 / 80f), 0f, 1f);
        if (munch == PrideSkyPayload.MUNCH_FALLING && munchTicks > LeonardTimeline.MUNCH_FALL + 5) munch = PrideSkyPayload.MUNCH_HIDDEN;
        // Where his head turns: you, or the spot he's burning
        prevHeadYaw = headYaw;
        prevHeadPitch = headPitch;
        Vec3d eye = PrideArena.munchHead();
        Vec3d look = munch == PrideSkyPayload.MUNCH_CHARGING || munch == PrideSkyPayload.MUNCH_FIRING ? target : client.player.getEyePos();
        double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
        double yaw = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90 - PrideArena.MUNCH_YAW);
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        yaw = MathHelper.clamp(yaw, -65, 65);
        pitch = MathHelper.clamp(pitch, -20, 50);
        double rate = munch == PrideSkyPayload.MUNCH_CHARGING ? 0.25 : 0.12;
        headYaw += (yaw - headYaw) * rate;
        headPitch += (pitch - headPitch) * rate;
    }

    public static void clear() {
        inArena = false;
        phase = 1;
        munch = PrideSkyPayload.MUNCH_HIDDEN;
        munchTicks = 0;
        storm = prevStorm = 0;
        lastNod = 0;
    }

    public static boolean active() {
        return inArena;
    }

    /** How far into the storm, 0-1. */
    public static float storm(float tickDelta) {
        return inArena ? MathHelper.lerp(tickDelta, prevStorm, storm) : 0f;
    }

    public static Vec3d mix(Vec3d day, Vec3d stormy, float tickDelta) {
        return day.lerp(stormy, storm(tickDelta));
    }

    public static float light(float vanilla, float tickDelta) {
        return MathHelper.lerp(storm(tickDelta), vanilla, STORM_LIGHT);
    }

    /** Whether the vanilla sky (its blue dome, the sun) is drawn under the banks: not in the storm. */
    public static boolean vanillaSky(float tickDelta) {
        return storm(tickDelta) < 0.5f;
    }

    // ---- The cloud banks round the horizon ----

    /** Blocky banks of cloud all round the horizon, a little above it and a long way below (you're over the clouds). */
    public static void renderBanks(Matrix4f matrix4f, float tickDelta) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        float time = (MinecraftClient.getInstance().world == null ? 0 : MinecraftClient.getInstance().world.getTime()) + tickDelta;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.004f));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float s = storm(tickDelta);
        Vec3d fog = mix(DAY_FOG, STORM_FOG, tickDelta);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Random r = Random.create(7781L);
        for (int i = 0; i < BANKS; i++) {
            double a = i * MathHelper.TAU / BANKS + r.nextDouble() * 0.12;
            double elevation = Math.toRadians(-14 + r.nextDouble() * 18);
            float w = 8 + r.nextFloat() * 16, h = 2.5f + r.nextFloat() * 4, d = 6 + r.nextFloat() * 8;
            // Lower banks fade into the haze
            float haze = (float) MathHelper.clamp(0.25 - elevation * 1.2, 0.15, 0.6);
            float base = MathHelper.lerp(s, 1f, 0.5f);
            float cr = MathHelper.lerp(haze, base, (float) fog.x), cg = MathHelper.lerp(haze, base, (float) fog.y),
                    cb = MathHelper.lerp(haze, base * (1 + 0.04f * (1 - s)), (float) fog.z);
            Vec3d out = new Vec3d(Math.cos(a) * Math.cos(elevation), Math.sin(elevation), Math.sin(a) * Math.cos(elevation));
            Vec3d c = out.multiply(BANK_DISTANCE);
            Vec3d right = new Vec3d(-Math.sin(a), 0, Math.cos(a));
            Vec3d fwd = new Vec3d(Math.cos(a), 0, Math.sin(a));
            int parts = 2 + r.nextInt(3);
            for (int k = 0; k < parts; k++) {
                float pw = w * (0.45f + r.nextFloat() * 0.5f), ph = h * (0.5f + r.nextFloat() * 0.6f), pd = d * (0.6f + r.nextFloat() * 0.4f);
                Vec3d pc = c.add(right.multiply((r.nextFloat() - 0.5f) * w)).add(0, (r.nextFloat() - 0.3f) * h * 0.6, 0)
                        .add(fwd.multiply((r.nextFloat() - 0.5f) * d * 0.5));
                box(buffer, m, pc, right, fwd, pw, ph, pd, cr, cg, cb, 0.92f);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    /** A box of cloud, lit from above like the game's own: the top brightest, the sides darker, the bottom darkest. */
    private static void box(BufferBuilder b, Matrix4f m, Vec3d c, Vec3d right, Vec3d fwd, float w, float h, float d,
                            float r, float g, float bl, float a) {
        Vec3d x = right.multiply(w / 2), y = new Vec3d(0, h / 2, 0), z = fwd.multiply(d / 2);
        Vec3d[] p = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            p[i] = c.add(x.multiply((i & 1) == 0 ? -1 : 1)).add(y.multiply((i & 2) == 0 ? -1 : 1)).add(z.multiply((i & 4) == 0 ? -1 : 1));
        }
        quad(b, m, p[2], p[3], p[7], p[6], r, g, bl, a, 1f);      // top
        quad(b, m, p[0], p[4], p[5], p[1], r, g, bl, a, 0.7f);    // bottom
        quad(b, m, p[0], p[1], p[3], p[2], r, g, bl, a, 0.9f);    // near
        quad(b, m, p[4], p[6], p[7], p[5], r, g, bl, a, 0.9f);    // far
        quad(b, m, p[0], p[2], p[6], p[4], r, g, bl, a, 0.8f);    // side
        quad(b, m, p[1], p[5], p[7], p[3], r, g, bl, a, 0.8f);    // side
    }

    private static void quad(BufferBuilder b, Matrix4f m, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float r, float g, float bl, float a, float shade) {
        for (Vec3d p : new Vec3d[]{p0, p1, p2, p3}) b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r * shade, g * shade, bl * shade, a);
    }

    // ---- Munch ----

    /** How far Munch has risen (0-1) right now. */
    private static double rise(float tickDelta) {
        if (munch == PrideSkyPayload.MUNCH_HIDDEN) return 0;
        double seconds = ClientBossMusic.secondsInto(LeonardEntity.PHASE_ONE.soundId(), tickDelta);
        if (munch == PrideSkyPayload.MUNCH_WATCHING && seconds >= 0 && seconds < LeonardTimeline.RISEN_SECONDS) return LeonardTimeline.munchRise(seconds);
        return 1;
    }

    /** How far his head is bowed, 0-1: on the beat while he watches, still while he gathers his beam. */
    private static double nod(float tickDelta) {
        if (munch != PrideSkyPayload.MUNCH_WATCHING) {
            lastNod *= 0.8;
            return lastNod;
        }
        double seconds = ClientBossMusic.secondsInto(LeonardEntity.PHASE_ONE.soundId(), tickDelta);
        lastNod = seconds >= 0 ? LeonardTimeline.nod(seconds) : lastNod * 0.8;
        return lastNod;
    }

    public static void render(WorldRenderContext context) {
        if (!inArena || munch == PrideSkyPayload.MUNCH_HIDDEN) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (giant == null) giant = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
        // A model starts out as a child's (big head, half the height) until a renderer says otherwise: he's grown
        giant.child = false;
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        float s = PrideArena.MUNCH_SCALE;
        double fall = munch == PrideSkyPayload.MUNCH_FALLING ? LeonardTimeline.munchFall(munchTicks + tickDelta) : 0;
        Vec3d feet = PrideArena.MUNCH.add(0, -(1 - rise(tickDelta)) * PrideArena.MUNCH_DEPTH - fall * 120, 0);

        giant.setVisible(true);
        giant.head.resetTransform();
        giant.head.yaw = (float) Math.toRadians(MathHelper.lerp(tickDelta, prevHeadYaw, headYaw));
        giant.head.pitch = (float) Math.toRadians(MathHelper.lerp(tickDelta, prevHeadPitch, headPitch)) + (float) (nod(tickDelta) * 0.5);
        giant.hat.copyTransform(giant.head);
        // Arms reaching forward over the rim, as if leaning in to look
        boolean beam = munch == PrideSkyPayload.MUNCH_CHARGING || munch == PrideSkyPayload.MUNCH_FIRING;
        giant.rightArm.resetTransform();
        giant.leftArm.resetTransform();
        giant.rightArm.pitch = beam ? -1.6f : -1.05f;
        giant.rightArm.yaw = -0.15f;
        giant.leftArm.pitch = beam ? -1.6f : -1.05f;
        giant.leftArm.yaw = 0.15f;
        if (fall > 0) {
            giant.rightArm.pitch = -2.6f;
            giant.leftArm.pitch = -2.6f;
            giant.rightArm.roll = 0.5f;
            giant.leftArm.roll = -0.5f;
        }
        giant.rightSleeve.copyTransform(giant.rightArm);
        giant.leftSleeve.copyTransform(giant.leftArm);

        matrices.push();
        matrices.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - PrideArena.MUNCH_YAW));
        if (fall > 0) {
            // Toppling back away from the plaza
            matrices.translate(0, 1.2 * s, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (-fall * 70)));
            matrices.translate(0, -1.2 * s, 0);
        }
        matrices.scale(s, s, s);
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        int light = LightmapTextureManager.pack(15, 15);
        giant.render(matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(MUNCH)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        if (beam) {
            float glow = munch == PrideSkyPayload.MUNCH_FIRING ? 1f : MathHelper.clamp((munchTicks + tickDelta) / Math.max(1, munchFor), 0.15f, 1f);
            if (munch == PrideSkyPayload.MUNCH_CHARGING && munchTicks % 4 < 2) glow *= 0.8f;
            int c = (int) (glow * 255);
            giant.render(matrices, buffers.getBuffer(MUNCH_EYES), light, OverlayTexture.DEFAULT_UV, 0xFF000000 | c << 16 | c << 8 | c);
        }
        matrices.pop();
        buffers.draw();

        if (beam) renderBeam(matrices, buffers, cam, tickDelta);
    }

    /** The aim while he gathers (a thin flickering line), then the beam itself, five blocks thick, throbbing. */
    private static void renderBeam(MatrixStack matrices, VertexConsumerProvider.Immediate buffers, Vec3d cam, float tickDelta) {
        Vec3d face = PrideArena.munchFace(target);
        Vec3d from = face.subtract(cam), to = target.subtract(cam);
        Matrix4f pose = matrices.peek().getPositionMatrix();
        VertexConsumer vc = buffers.getBuffer(BEAM);
        float t = munchTicks + tickDelta;
        if (munch == PrideSkyPayload.MUNCH_CHARGING) {
            float k = MathHelper.clamp(t / Math.max(1, munchFor), 0f, 1f);
            float flicker = (munchTicks % 3 == 0) ? 0.5f : 1f;
            ribbon(vc, pose, from, to, 0.15f + k * 0.5f, 1f, 0.9f, 0.5f, (0.15f + k * 0.45f) * flicker);
        } else {
            float k = MathHelper.clamp(1.4f - t / Math.max(1, munchFor), 0f, 1f);
            float throb = 1f + 0.12f * MathHelper.sin(t * 1.7f);
            ribbon(vc, pose, from, to, 7.5f * throb * k, 1f, 0.78f, 0.25f, 0.25f * k);
            ribbon(vc, pose, from, to, 4.5f * throb * k, 1f, 0.92f, 0.5f, 0.55f * k);
            ribbon(vc, pose, from, to, 2.2f * throb * k, 1f, 1f, 0.95f, 0.95f * k);
            // Turned a quarter, so it's thick from every side
            ribbon2(vc, pose, from, to, 4.5f * throb * k, 1f, 0.92f, 0.5f, 0.45f * k);
        }
        buffers.draw(BEAM);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f pose, Vec3d a, Vec3d b, float width, float r, float g, float bl, float alpha) {
        Vec3d d = b.subtract(a);
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d side = d.crossProduct(mid);
        if (side.lengthSquared() < 1e-8) side = d.crossProduct(new Vec3d(0, 1, 0));
        side = side.normalize().multiply(width * 0.5);
        quadColor(vc, pose, a.subtract(side), b.subtract(side), b.add(side), a.add(side), r, g, bl, alpha);
    }

    private static void ribbon2(VertexConsumer vc, Matrix4f pose, Vec3d a, Vec3d b, float width, float r, float g, float bl, float alpha) {
        Vec3d d = b.subtract(a);
        Vec3d side = d.crossProduct(a.add(b).multiply(0.5));
        if (side.lengthSquared() < 1e-8) side = new Vec3d(1, 0, 0);
        Vec3d up = d.crossProduct(side).normalize().multiply(width * 0.5);
        quadColor(vc, pose, a.subtract(up), b.subtract(up), b.add(up), a.add(up), r, g, bl, alpha);
    }

    private static void quadColor(VertexConsumer vc, Matrix4f pose, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float r, float g, float b, float a) {
        for (Vec3d p : new Vec3d[]{p0, p1, p2, p3}) vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, MathHelper.clamp(a, 0f, 1f));
    }
}
