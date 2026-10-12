package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
        super(output, registries);
    }

    @Override
    public void generate(RecipeExporter exporter) {
        // 5 fragments in a plus shape -> key
        for (Sin sin : Sin.values()) {
            Item fragment = ModItems.FRAGMENTS.get(sin);
            ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.KEYS.get(sin))
                    .pattern(" F ")
                    .pattern("FFF")
                    .pattern(" F ")
                    .input('F', fragment)
                    .criterion(hasItem(fragment), conditionsFromItem(fragment))
                    .offerTo(exporter);
        }
    }
}
