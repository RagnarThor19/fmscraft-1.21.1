package net.ragnar.fmscraft.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
import net.ragnar.fmscraft.item.SinPortalFrameItem;
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
        // Sin Portal Frame: the vanilla end portal frame with each sin's recolored textures
        // (tools/textures/frame_textures.py). The keyed model (hand-made in resources) adds that sin's key standing
        // in the socket. Every facing, sin and key combination goes in the blockstate.
        Model frameParent = new Model(Optional.of(Identifier.ofVanilla("block/end_portal_frame")), Optional.empty(),
                TextureKey.PARTICLE, TextureKey.TOP, TextureKey.SIDE, TextureKey.BOTTOM);
        Model keyedParent = new Model(Optional.of(Identifier.of(FmsCraft.MOD_ID, "block/sin_portal_frame_keyed")), Optional.empty(),
                TextureKey.TOP, TextureKey.SIDE, TextureKey.BOTTOM, KEY);
        Map<Sin, Identifier> empty = new EnumMap<>(Sin.class);
        Map<Sin, Identifier> keyed = new EnumMap<>(Sin.class);
        for (Sin sin : Sin.values()) {
            String name = "block/sin_portal_frame_" + sin.id();
            TextureMap textures = new TextureMap()
                    .put(TextureKey.PARTICLE, Identifier.of(FmsCraft.MOD_ID, name + "_side"))
                    .put(TextureKey.TOP, Identifier.of(FmsCraft.MOD_ID, name + "_top"))
                    .put(TextureKey.SIDE, Identifier.of(FmsCraft.MOD_ID, name + "_side"))
                    .put(TextureKey.BOTTOM, Identifier.of(FmsCraft.MOD_ID, name + "_bottom"));
            empty.put(sin, frameParent.upload(Identifier.of(FmsCraft.MOD_ID, name), textures, generator.modelCollector));
            keyed.put(sin, keyedParent.upload(Identifier.of(FmsCraft.MOD_ID, name + "_keyed"),
                    textures.copyAndAdd(KEY, Identifier.of(FmsCraft.MOD_ID, "item/" + sin.id() + "_key")), generator.modelCollector));
        }
        generator.blockStateCollector.accept(VariantsBlockStateSupplier.create(ModBlocks.SIN_PORTAL_FRAME).coordinate(
                BlockStateVariantMap.create(SinPortalFrameBlock.FACING, SinPortalFrameBlock.SIN, SinPortalFrameBlock.KEY)
                        .register((facing, sin, key) -> BlockStateVariant.create()
                                .put(VariantSettings.MODEL, key ? keyed.get(sin) : empty.get(sin))
                                .put(VariantSettings.Y, switch (facing) {
                                    case EAST -> VariantSettings.Rotation.R90;
                                    case SOUTH -> VariantSettings.Rotation.R180;
                                    case WEST -> VariantSettings.Rotation.R270;
                                    default -> VariantSettings.Rotation.R0;
                                }))));

        // The frame item: the plain frame looks like the default (Greed) block, and an override per sin picks that
        // sin's frame, by the predicate registered in FmsCraftClient
        generator.modelCollector.accept(Identifier.of(FmsCraft.MOD_ID, "item/sin_portal_frame"), () -> {
            JsonObject model = new JsonObject();
            model.addProperty("parent", empty.get(Sin.GREED).toString());
            JsonArray overrides = new JsonArray();
            for (Sin sin : Sin.values()) {
                JsonObject predicate = new JsonObject();
                predicate.addProperty(SinPortalFrameItem.SIN_PREDICATE.toString(), (sin.ordinal() + 1) / 10f);
                JsonObject override = new JsonObject();
                override.add("predicate", predicate);
                override.addProperty("model", empty.get(sin).toString());
                overrides.add(override);
            }
            model.add("overrides", overrides);
            return model;
        });
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
