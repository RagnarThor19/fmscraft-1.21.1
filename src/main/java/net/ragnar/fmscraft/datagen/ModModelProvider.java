package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.Models;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator generator) {
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
