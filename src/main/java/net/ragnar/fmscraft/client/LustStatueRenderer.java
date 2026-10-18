package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.LustStatueEntity;

/**
 * Draws a Statue of Lust so that it is never seen to move. It's drawn where it was last caught standing, and that
 * pose (where it stands, which way it faces) only catches up with the real one while the statue can't be seen: off
 * screen, behind something, or lost in the fog. Look away and back and it's closer. No interpolation, no shadow.
 *
 * Its eyes are the one thing that changes in view: dark slits ({@link #CLOSED}), for a moment mid grey, then pale grey.
 */
public class LustStatueRenderer extends EntityRenderer<LustStatueEntity> {
    private static final Identifier CLOSED = Identifier.of(FmsCraft.MOD_ID, "textures/entity/lust_statue.png");
    private static final Identifier OPENING = Identifier.of(FmsCraft.MOD_ID, "textures/entity/lust_statue_opening.png");
    private static final Identifier OPEN = Identifier.of(FmsCraft.MOD_ID, "textures/entity/lust_statue_open.png");
    /** How long the eyes stay half open, in ticks. */
    private static final int OPENING_TICKS = 3;

    private final LustStatueModel model;

    public LustStatueRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.model = new LustStatueModel(ctx.getPart(LustStatueModel.LAYER));
        this.shadowRadius = 0f;
    }

    /** Called every frame with the view's frustum: the one place to tell whether the drawn statue is in sight. */
    @Override
    public boolean shouldRender(LustStatueEntity statue, Frustum frustum, double x, double y, double z) {
        if (!statue.shownSet || !inSight(statue, frustum)) {
            statue.shownX = statue.getX();
            statue.shownY = statue.getY();
            statue.shownZ = statue.getZ();
            statue.shownYaw = statue.bodyYaw;
            statue.shownSet = true;
        }
        return statue.shouldRender(x, y, z) && frustum.isVisible(shownBox(statue).expand(0.5));
    }

    private static Box shownBox(LustStatueEntity statue) {
        return statue.getDimensions(statue.getPose()).getBoxAt(statue.shownX, statue.shownY, statue.shownZ);
    }

    /** Whether the statue as drawn now is on screen, within the fog and not hidden behind anything. */
    private static boolean inSight(LustStatueEntity statue, Frustum frustum) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Box box = shownBox(statue);
        if (!frustum.isVisible(box.expand(0.5))) return false;
        Vec3d eye = camera.getPos();
        double fog = Math.min(RenderSystem.getShaderFogEnd(), 256);
        Vec3d[] points = {new Vec3d(statue.shownX, box.maxY - 0.2, statue.shownZ), box.getCenter(), new Vec3d(statue.shownX, box.minY + 0.2, statue.shownZ)};
        for (Vec3d point : points) {
            if (point.distanceTo(eye) > fog) continue;
            HitResult hit = statue.getWorld().raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    @Override
    public void render(LustStatueEntity statue, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(statue.shownX - MathHelper.lerp(tickDelta, statue.prevX, statue.getX()),
                statue.shownY - MathHelper.lerp(tickDelta, statue.prevY, statue.getY()),
                statue.shownZ - MathHelper.lerp(tickDelta, statue.prevZ, statue.getZ()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - statue.shownYaw));
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(statue))), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();
        super.render(statue, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(LustStatueEntity statue) {
        if (!statue.eyesOpen()) return CLOSED;
        return statue.age - statue.eyesOpenedAge < OPENING_TICKS ? OPENING : OPEN;
    }
}
