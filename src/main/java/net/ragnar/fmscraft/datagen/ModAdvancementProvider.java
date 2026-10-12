package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.advancement.criterion.ImpossibleCriterion;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ModAdvancementProvider extends FabricAdvancementProvider {
    public ModAdvancementProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
        super(output, registries);
    }

    @Override
    public void generateAdvancement(RegistryWrapper.WrapperLookup lookup, Consumer<AdvancementEntry> consumer) {
        // Root tab: unlocked by picking up any fragment
        Advancement.Builder rootBuilder = Advancement.Builder.create()
                .display(ModItems.KEYS.get(Sin.WRATH),
                        Text.translatable("advancements.fmscraft.root.title"),
                        Text.translatable("advancements.fmscraft.root.description"),
                        Identifier.ofVanilla("textures/gui/advancements/backgrounds/nether.png"),
                        AdvancementFrame.TASK, false, false, false)
                .criteriaMerger(AdvancementRequirements.CriterionMerger.OR);
        for (Sin sin : Sin.values()) {
            rootBuilder.criterion(sin.id(), InventoryChangedCriterion.Conditions.items(ModItems.FRAGMENTS.get(sin)));
        }
        AdvancementEntry root = rootBuilder.build(consumer, id("root"));

        // One per sin, titled just the sin, for your first fragment of it
        for (Sin sin : Sin.values()) {
            Advancement.Builder.create()
                    .parent(root)
                    .display(ModItems.FRAGMENTS.get(sin),
                            Text.translatable("advancements.fmscraft." + sin.id() + ".title"),
                            Text.translatable("advancements.fmscraft." + sin.id() + ".description"),
                            null, AdvancementFrame.TASK, true, true, false)
                    .criterion("has_fragment", InventoryChangedCriterion.Conditions.items(ModItems.FRAGMENTS.get(sin)))
                    .build(consumer, id(sin.id()));
        }

        // Granted from code to everyone online when all 7 souls are placed
        Advancement.Builder.create()
                .parent(root)
                .display(ModItems.SOULS.get(Sin.PRIDE),
                        Text.translatable("advancements.fmscraft.true_ending.title"),
                        Text.translatable("advancements.fmscraft.true_ending.description"),
                        null, AdvancementFrame.CHALLENGE, true, true, true)
                .criterion("placed_all_souls", Criteria.IMPOSSIBLE.create(new ImpossibleCriterion.Conditions()))
                .build(consumer, id("true_ending"));
    }

    private static String id(String path) {
        return FmsCraft.MOD_ID + ":" + path;
    }
}
