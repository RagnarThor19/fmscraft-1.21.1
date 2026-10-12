package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

import java.util.concurrent.CompletableFuture;

public class ModLanguageProvider extends FabricLanguageProvider {
    public ModLanguageProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
        super(output, registries);
    }

    @Override
    public void generateTranslations(RegistryWrapper.WrapperLookup lookup, TranslationBuilder builder) {
        builder.add("itemGroup.fmscraft.fmscraft", "FMSCraft");

        for (Sin sin : Sin.values()) {
            String name = sin.displayName();
            builder.add(ModItems.FRAGMENTS.get(sin), name + " Fragment");
            builder.add(ModItems.KEYS.get(sin), name + " Key");
            builder.add(ModItems.SOULS.get(sin), "Soul of " + name);
            builder.add("advancements.fmscraft." + sin.id() + ".title", name);
            builder.add("advancements.fmscraft." + sin.id() + ".description", "Obtain a fragment of " + name);
        }

        builder.add("item.fmscraft.sin_key.tooltip", "Opens a Structure of %s");
        builder.add("bossbar.fmscraft.wrath", "Wrath  %s/%s  %s");
        builder.add("message.fmscraft.wrath_failed", "The Wrath fades...");
        builder.add("message.fmscraft.lust_progress", "Lust: bred %s (%s/%s)");

        builder.add("advancements.fmscraft.root.title", "The Seven Sins");
        builder.add("advancements.fmscraft.root.description", "Obtain your first sin fragment");
        builder.add("advancements.fmscraft.true_ending.title", "True Ending");
        builder.add("advancements.fmscraft.true_ending.description", "Place all seven souls");
    }
}
