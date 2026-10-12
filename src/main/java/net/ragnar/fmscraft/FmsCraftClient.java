package net.ragnar.fmscraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalBlock;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.TestBossEntity;
import net.ragnar.fmscraft.client.ClientBossMusic;
import net.ragnar.fmscraft.client.TomeWheelHud;
import net.ragnar.fmscraft.network.BossMusicPayload;
import net.ragnar.fmscraft.network.TomeWheelPayload;

public class FmsCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SIN_PORTAL, RenderLayer.getTranslucent());
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> 0xFF000000 | state.get(SinPortalBlock.SIN).color(),
                ModBlocks.SIN_PORTAL);

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

        ClientPlayNetworking.registerGlobalReceiver(BossMusicPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientBossMusic.play(payload.sound())));
        ClientPlayNetworking.registerGlobalReceiver(TomeWheelPayload.ID,
                (payload, context) -> context.client().execute(() -> TomeWheelHud.start(payload)));
        HudRenderCallback.EVENT.register(TomeWheelHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> TomeWheelHud.tick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ClientBossMusic.stop();
            TomeWheelHud.clear();
        }));
    }
}
