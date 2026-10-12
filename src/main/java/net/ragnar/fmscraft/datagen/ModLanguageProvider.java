package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
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

        builder.add(ModBlocks.SIN_PORTAL, "Sin Portal");
        builder.add(ModBossEntities.TEST_BOSS, "Test Boss");
        builder.add("bossbar.fmscraft.fight_starts", "The fight begins in %s");
        builder.add("message.fmscraft.boss_defeated", "%s, the Sin of %s, has been defeated!");
        builder.add("message.fmscraft.fight_failed", "%s has fallen. The Sin of %s endures.");
        builder.add("message.fmscraft.fight_ended", "The fight against the Sin of %s has ended.");

        builder.add("message.fmscraft.portal_closing", "The portal is closing!");
        builder.add("wheel.fmscraft.title", "Spoils of %s");
        builder.add("wheel.fmscraft.tier.beginner", "Beginner Tome");
        builder.add("wheel.fmscraft.tier.advanced", "Advanced Tome");
        builder.add("wheel.fmscraft.tier.master", "Master Tome");

        builder.add("advancements.fmscraft.root.title", "The Seven Sins");
        builder.add("advancements.fmscraft.root.description", "Obtain your first sin fragment");
        builder.add("advancements.fmscraft.true_ending.title", "True Ending");
        builder.add("advancements.fmscraft.true_ending.description", "Place all seven souls");
    }
}
