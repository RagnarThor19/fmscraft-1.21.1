package net.ragnar.fmscraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalBlock;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.item.SinPortalFrameItem;
import net.ragnar.fmscraft.sin.Sin;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.TestBossEntity;
import net.ragnar.fmscraft.client.ClientBossMusic;
import net.ragnar.fmscraft.client.EnvyStatueModel;
import net.ragnar.fmscraft.client.EnvyStatueRenderer;
import net.ragnar.fmscraft.client.GreedStatueModel;
import net.ragnar.fmscraft.client.GreedStatueRenderer;
import net.ragnar.fmscraft.client.GardenFog;
import net.ragnar.fmscraft.client.LustStatueModel;
import net.ragnar.fmscraft.client.LustStatueRenderer;
import net.ragnar.fmscraft.client.MotherWardenRenderer;
import net.ragnar.fmscraft.client.SleepZs;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ragnar.fmscraft.client.VaultFog;
import net.ragnar.fmscraft.client.StatueGrindSound;
import net.ragnar.fmscraft.entity.EnvyStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.client.TomeWheelHud;
import net.ragnar.fmscraft.network.BossMusicPayload;
import net.ragnar.fmscraft.network.GardenFogPayload;
import net.ragnar.fmscraft.network.TomeWheelPayload;
import net.ragnar.fmscraft.network.VaultFogPayload;

public class FmsCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SIN_PORTAL, RenderLayer.getTranslucent());
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> 0xFF000000 | state.get(SinPortalBlock.SIN).color(),
                ModBlocks.SIN_PORTAL);
        // The key standing in the frame is cut out of its item texture
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SIN_PORTAL_FRAME, RenderLayer.getCutout());
        // Picks the frame item's model override for its sin: 0 without one, (ordinal + 1) / 10 with
        ModelPredicateProviderRegistry.register(ModItems.SIN_PORTAL_FRAME, SinPortalFrameItem.SIN_PREDICATE, (stack, world, entity, seed) -> {
            Sin sin = SinPortalFrameItem.sinOf(stack);
            return sin == null ? 0 : (sin.ordinal() + 1) / 10f;
        });

        // Placeholder look for the test boss: a Steve-skinned zombie body
        Identifier steve = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
        EntityRendererRegistry.register(ModBossEntities.TEST_BOSS, ctx ->
                new BipedEntityRenderer<TestBossEntity, BipedEntityModel<TestBossEntity>>(
                        ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f) {
                    @Override
                    public Identifier getTexture(TestBossEntity entity) {
                        return steve;
                    }
                });

        EntityModelLayerRegistry.registerModelLayer(EnvyStatueModel.LAYER, EnvyStatueModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.ENVY_STATUE, EnvyStatueRenderer::new);
        EnvyStatueEntity.clientTick = FmsCraftClient::statueEffects;
        EntityModelLayerRegistry.registerModelLayer(GreedStatueModel.LAYER, GreedStatueModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.GREED_STATUE, GreedStatueRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(LustStatueModel.LAYER, LustStatueModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.LUST_STATUE, LustStatueRenderer::new);
        EntityRendererRegistry.register(ModEntities.MOTHER_WARDEN, MotherWardenRenderer::new);
        WorldRenderEvents.AFTER_ENTITIES.register(SleepZs::render);

        ClientPlayNetworking.registerGlobalReceiver(BossMusicPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientBossMusic.play(payload.sound())));
        ClientPlayNetworking.registerGlobalReceiver(TomeWheelPayload.ID,
                (payload, context) -> context.client().execute(() -> TomeWheelHud.start(payload)));
        ClientPlayNetworking.registerGlobalReceiver(VaultFogPayload.ID,
                (payload, context) -> context.client().execute(() -> VaultFog.set(payload.inside())));
        ClientPlayNetworking.registerGlobalReceiver(GardenFogPayload.ID,
                (payload, context) -> context.client().execute(() -> GardenFog.set(payload.inside())));
        HudRenderCallback.EVENT.register(TomeWheelHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TomeWheelHud.tick();
            VaultFog.tick();
            GardenFog.tick();
            SleepZs.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ClientBossMusic.stop();
            TomeWheelHud.clear();
            VaultFog.clear();
            GardenFog.clear();
            SleepZs.clear();
        }));
    }

    /** Grinding rumble that follows each statue, and stone dust kicked up from the floor while it slides. */
    private static void statueEffects(EnvyStatueEntity statue) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!statue.clientSoundStarted) {
            statue.clientSoundStarted = true;
            client.getSoundManager().play(new StatueGrindSound(statue));
        }
        if (!statue.isAwake() || statue.getState() != EnvyStatueEntity.STATE_SLIDING || statue.age % 2 != 0) return;
        BlockState floor = statue.getWorld().getBlockState(statue.getBlockPos().down());
        if (floor.isAir()) return;
        Direction back = Direction.fromRotation(statue.getYaw()).getOpposite();
        Direction side = back.rotateYClockwise();
        for (int s = -1; s <= 1; s += 2) {
            double x = statue.getX() + back.getOffsetX() * 1.4 + side.getOffsetX() * 1.3 * s;
            double z = statue.getZ() + back.getOffsetZ() * 1.4 + side.getOffsetZ() * 1.3 * s;
            statue.getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), x, statue.getY() + 0.1, z, 0, 0.05, 0);
        }
    }
}
