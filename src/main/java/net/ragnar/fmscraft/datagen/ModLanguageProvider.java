package net.ragnar.fmscraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.ModEntities;
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

        builder.add(ModEntities.ENVY_STATUE, "Statue of Envy");
        builder.add("death.attack.fmscraft.envy_statue", "%1$s was crushed by the Statue of Envy");
        builder.add("death.attack.fmscraft.envy_statue.player", "%1$s was crushed by the Statue of Envy while fleeing %2$s");
        builder.add(ModBlocks.SIN_PORTAL_FRAME, "Sin Portal Frame");
        builder.add(ModItems.SIN_PORTAL_FRAME.getTranslationKey() + ".sin", "%s Portal Frame");
        builder.add("message.fmscraft.wrong_key", "This key does not fit.");
        builder.add("message.fmscraft.frame_open", "The portal is already open.");
        builder.add("message.fmscraft.frame_awaits", "Only the %s Key fits this lock...");

        builder.add("bossbar.fmscraft.drowsiness", "Drowsiness");
        builder.add("message.fmscraft.sloth_asleep", "You nodded off...");
        builder.add("message.fmscraft.sloth_bells", "Bells ringing: %s/%s");
        builder.add("message.fmscraft.sloth_seal_open", "The Sphere stirs. Its heart lies open.");

        builder.add(ModEntities.GREED_STATUE, "Statue of Greed");
        builder.add("death.attack.fmscraft.greed_statue", "%1$s was claimed by the Statue of Greed");
        builder.add("death.attack.fmscraft.greed_statue.player", "%1$s was claimed by the Statue of Greed while fleeing %2$s");
        builder.add(ModEffects.BLOCK_BARRIER.value(), "Block Barrier");
        builder.add("message.fmscraft.greed_door_open", "The vault door sinks into the floor...");

        builder.add(ModEntities.LUST_STATUE, "Statue of Lust");
        builder.add("death.attack.fmscraft.lust_statue", "%1$s was embraced by the Statue of Lust");
        builder.add("death.attack.fmscraft.lust_statue.player", "%1$s was embraced by the Statue of Lust while fleeing %2$s");
        builder.add(ModEffects.ITEM_BARRIER.value(), "Item Barrier");
        builder.add("message.fmscraft.lust_statue_home", "A statue has found its place... (%s/%s)");
        builder.add("message.fmscraft.lust_fountain_dry", "The fountain falls silent. Something lies open beneath it.");

        builder.add(ModEntities.MOTHER_WARDEN, "Mother Warden");
        builder.add("subtitles.fmscraft.entity.mother_warden.snore", "Mother Warden snores");
        builder.add("message.fmscraft.mother_warden_stirs", "The Mother Warden stirs... (%s/%s)");
        builder.add("message.fmscraft.mother_warden_wakes", "The Mother Warden awakens!");
        builder.add("message.fmscraft.mother_warden_sleeps", "The Mother Warden settles back into her nest...");

        builder.add(ModEntities.GORGED_BRUTE, "Gorged Brute");
        builder.add("message.fmscraft.gorged_brute_eats", "The Gorged Brute gulps it down... (%s/%s)");
        builder.add("message.fmscraft.gorged_brute_bursts", "The Gorged Brute bursts! The way to the portal lies open.");

        builder.add("advancements.fmscraft.root.title", "The Seven Sins");
        builder.add("advancements.fmscraft.root.description", "Obtain your first sin fragment");
        builder.add("advancements.fmscraft.true_ending.title", "True Ending");
        builder.add("advancements.fmscraft.true_ending.description", "Place all seven souls");
    }
}
