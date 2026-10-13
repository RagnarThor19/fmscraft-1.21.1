package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.BlockStateVariant;
import net.minecraft.data.client.BlockStateVariantMap;
import net.minecraft.data.client.Model;
import net.minecraft.data.client.Models;
import net.minecraft.data.client.TextureKey;
import net.minecraft.data.client.TextureMap;
import net.minecraft.data.client.VariantSettings;
import net.minecraft.data.client.VariantsBlockStateSupplier;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class ModModelProvider extends FabricModelProvider {
    private static final TextureKey KEY = TextureKey.of("key");

    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator generator) {
        // Sin Portal Frame: the hand-made models in resources (block/sin_portal_frame and _keyed); one keyed model per
        // sin showing that sin's key, and every facing, sin and key combination in the blockstate
        Identifier empty = Identifier.of(FmsCraft.MOD_ID, "block/sin_portal_frame");
        Model keyedParent = new Model(Optional.of(Identifier.of(FmsCraft.MOD_ID, "block/sin_portal_frame_keyed")), Optional.empty(), KEY);
        Map<Sin, Identifier> keyed = new EnumMap<>(Sin.class);
        for (Sin sin : Sin.values()) {
            keyed.put(sin, keyedParent.upload(Identifier.of(FmsCraft.MOD_ID, "block/sin_portal_frame_" + sin.id()),
                    new TextureMap().put(KEY, Identifier.of(FmsCraft.MOD_ID, "item/" + sin.id() + "_key")), generator.modelCollector));
        }
        generator.blockStateCollector.accept(VariantsBlockStateSupplier.create(ModBlocks.SIN_PORTAL_FRAME).coordinate(
                BlockStateVariantMap.create(SinPortalFrameBlock.FACING, SinPortalFrameBlock.SIN, SinPortalFrameBlock.KEY)
                        .register((facing, sin, key) -> BlockStateVariant.create()
                                .put(VariantSettings.MODEL, key ? keyed.get(sin) : empty)
                                .put(VariantSettings.Y, switch (facing) {
                                    case EAST -> VariantSettings.Rotation.R90;
                                    case SOUTH -> VariantSettings.Rotation.R180;
                                    case WEST -> VariantSettings.Rotation.R270;
                                    default -> VariantSettings.Rotation.R0;
                                }))));
        generator.registerParentedItemModel(ModBlocks.SIN_PORTAL_FRAME, empty);
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator) {
        // Each item expects assets/fmscraft/textures/item/<item id>.png
        for (Sin sin : Sin.values()) {
            generator.register(ModItems.FRAGMENTS.get(sin), Models.GENERATED);
            generator.register(ModItems.KEYS.get(sin), Models.GENERATED);
            generator.register(ModItems.SOULS.get(sin), Models.GENERATED);
        }
    }
}
