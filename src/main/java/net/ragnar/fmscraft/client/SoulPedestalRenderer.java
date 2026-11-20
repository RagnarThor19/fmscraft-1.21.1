package net.ragnar.fmscraft.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.block.SoulPedestalBlock;
import net.ragnar.fmscraft.block.SoulPedestalBlockEntity;

/**
 * A filled Soul Pedestal sends up a beacon beam in its sin's color, drawn exactly like the vanilla beacon's (same
 * texture, spin and glow) and seen from as far away.
 */
public class SoulPedestalRenderer implements BlockEntityRenderer<SoulPedestalBlockEntity> {
    public SoulPedestalRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(SoulPedestalBlockEntity pedestal, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        if (pedestal.getWorld() == null || !pedestal.getCachedState().get(SoulPedestalBlock.SOUL)) return;
        int color = beamColor(pedestal.getCachedState().get(SoulPedestalBlock.SIN).color());
        BeaconBlockEntityRenderer.renderBeam(matrices, vertexConsumers, BeaconBlockEntityRenderer.BEAM_TEXTURE, tickDelta, 1.0f,
                pedestal.getWorld().getTime(), 0, BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT, color, 0.2f, 0.25f);
    }

    /** The sin colors are muted for blocks and particles; the beam lifts them a little so it glows like stained glass does. */
    static int beamColor(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (255 - r) / 4;
        g += (255 - g) / 4;
        b += (255 - b) / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public boolean rendersOutsideBoundingBox(SoulPedestalBlockEntity pedestal) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 256;
    }

    @Override
    public boolean isInRenderDistance(SoulPedestalBlockEntity pedestal, Vec3d cameraPos) {
        return Vec3d.ofCenter(pedestal.getPos()).multiply(1.0, 0.0, 1.0).isInRange(cameraPos.multiply(1.0, 0.0, 1.0), getRenderDistance());
    }
}
