package net.ragnar.fmscraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.entity.MotherWardenEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Z's drifting up off a sleeping Mother Warden: the magic mod's sleep dart Z's (SleepClient there), the same soft
 * lavender, wobble and fade, only sized for her.
 */
public final class SleepZs {
    /** How long one Z takes to drift up and fade. */
    private static final int LIFE = 46;
    private static final int COLOR = 0xE2D8FF;

    private record Floater(double x, double y, double z, float size, float wobble, int[] age) {
    }

    private static final List<Floater> FLOATERS = new ArrayList<>();

    private SleepZs() {
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.isPaused()) return;
        Iterator<Floater> it = FLOATERS.iterator();
        while (it.hasNext()) {
            if (++it.next().age[0] >= LIFE) it.remove();
        }
        for (Entity e : world.getEntities()) {
            if (!(e instanceof MotherWardenEntity mother) || !mother.isDozing()) continue;
            int age = mother.phaseAge();
            boolean asleep = mother.sleepPhase() == MotherWardenEntity.ASLEEP;
            if (asleep ? age % 15 != 1 : age != MotherWardenEntity.DROWSY_TICKS / 2) continue;
            float big = MathHelper.clamp(e.getHeight() / 1.8f, 0.6f, 7f);
            float size = (asleep ? 0.8f + world.random.nextFloat() * 0.5f : 0.55f) * big;
            float side = (e.getId() & 1) == 0 ? 1f : -1f;
            Vec3d look = Vec3d.fromPolar(0, e.getHeadYaw());
            double x = e.getX() + look.x * e.getWidth() * 0.3 + side * 0.15 * big;
            double z = e.getZ() + look.z * e.getWidth() * 0.3;
            FLOATERS.add(new Floater(x, e.getY() + e.getHeight() + 0.15, z, size, world.random.nextFloat() * MathHelper.TAU, new int[1]));
        }
    }

    public static void render(WorldRenderContext context) {
        if (FLOATERS.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        TextRenderer text = client.textRenderer;
        float half = text.getWidth("Z") / 2f;
        for (Floater f : FLOATERS) {
            float t = f.age[0] + tickDelta;
            float alpha = Math.min(1f, t / 5f) * MathHelper.clamp((LIFE - t) / 16f, 0f, 1f);
            int a = (int) (alpha * 255);
            if (a < 10) continue; // the font draws anything fainter than this fully opaque
            double x = f.x + MathHelper.sin(t * 0.13f + f.wobble) * 0.18 * f.size;
            double y = f.y + t * 0.024 * f.size;
            double z = f.z + MathHelper.cos(t * 0.11f + f.wobble) * 0.1 * f.size;
            float s = 0.028f * f.size * (0.6f + 0.7f * t / LIFE);
            matrices.push();
            matrices.translate(x - cam.x, y - cam.y, z - cam.z);
            matrices.multiply(camera.getRotation());
            matrices.scale(s, -s, s);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 0.09f + f.wobble) * 14f));
            text.draw("Z", -half, -4f, (a << 24) | COLOR, true, matrices.peek().getPositionMatrix(), buffers,
                    TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
        buffers.draw();
    }

    public static void clear() {
        FLOATERS.clear();
    }
}
