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
        builder.add("bossbar.fmscraft.wrath", "WRATH  %s/%s  %s");
        builder.add("message.fmscraft.wrath_failed", "The Wrath fades...");
        builder.add("message.fmscraft.lust_progress", "Lust: bred %s (%s/%s)");

        builder.add(ModBlocks.SIN_PORTAL, "Sin Portal");
        builder.add(ModBossEntities.TEST_BOSS, "Test Boss");
        builder.add(ModBossEntities.RAGGIMEL, "RAGGIMEL");
        builder.add(ModBossEntities.GREED_BUBBLE, "Bubble");
        builder.add("subtitles.fmscraft.entity.raggimel.voiceline", "RAGGIMEL speaks");
        builder.add(ModBossEntities.ORNAMIX, "ORNAMIX");
        builder.add(ModBossEntities.ORNAMIX_FIREBALL, "Fireball");
        builder.add("subtitles.fmscraft.entity.ornamix.ragemode", "ORNAMIX rages");
        builder.add("message.fmscraft.ornamix_rooted", "You're caught in the flames!");
        builder.add("message.fmscraft.ornamix_pit", "The flames drive you back into the pit!");
        builder.add(ModBossEntities.GUMMSS, "GUMMSS");
        builder.add(ModBossEntities.GUMMSS_ILLUSION, "GUMMSS");
        builder.add("message.fmscraft.gummss_stare", "Don't look at him...");
        builder.add("message.fmscraft.gummss_caught", "GUMMSS drags you back. You leave when he says so.");
        builder.add("message.fmscraft.gummss_disguise", "GUMMSS is wearing your face. And you're wearing his.");
        builder.add(ModBossEntities.LALLI, "LALLILARGE");
        builder.add("message.fmscraft.lalli_caught", "The cold wind carries you back to LALLILARGE.");
        builder.add(ModBossEntities.MUGEN, "MUGEN");
        builder.add(ModBossEntities.MUGEN_CLONE, "MUGEN");
        builder.add(ModBossEntities.RAGGIMEL_MIRAGE, "RAGGIMEL");
        builder.add(ModBossEntities.LEONARD, "LEONARD");
        builder.add(ModItems.LIGHTNING_BOLT, "Lightning Bolt");
        builder.add(ModItems.TRAFFIC_CONE, "Traffic Cone");
        builder.add("message.fmscraft.leonard_paralysed", "You're paralysed!");
        builder.add("message.fmscraft.leonard_caught", "The lightning carries you back to the plaza.");
        builder.add("message.fmscraft.leonard_grid", "Stand on the squares that aren't lit!");
        builder.add("message.fmscraft.munch_beam", "Munch is looking at you...");
        builder.add(ModBossEntities.RICKY, "RICKY");
        builder.add("subtitles.fmscraft.entity.ricky.powerup", "RICKY powers up");
        builder.add("subtitles.fmscraft.entity.ricky.teleport", "RICKY teleports");
        builder.add("message.fmscraft.ricky_caught", "The city won't let you go. RICKY drags you back.");
        builder.add("message.fmscraft.ricky_spirit_bomb", "The light is gathering... RUN!");
        builder.add("message.fmscraft.ricky_final_beam", "Get behind something!");
        builder.add("message.fmscraft.ricky_hand", "Something stirs under your feet...");
        builder.add(ModItems.KUNAI, "Kunai");
        builder.add(ModBlocks.VOID_SPIKE, "Void Spike");
        builder.add("message.fmscraft.mugen_bound", "The shadow holds you fast!");
        builder.add("message.fmscraft.mugen_strings", "MUGEN has your strings...");
        builder.add("message.fmscraft.mugen_black_hole", "The dark is gathering... RUN!");
        builder.add(ModItems.SHURIKEN, "Shuriken");
        builder.add(ModItems.SHADOW_ORB, "Black Orb");
        builder.add(ModItems.SHADOW_CHARGE, "Black Blaze");
        builder.add("message.fmscraft.mugen_caught", "The shadows drag you back to MUGEN.");
        builder.add("message.fmscraft.mugen_get_over_here", "GET OVER HERE!");
        builder.add("message.fmscraft.mugen_grappled", "MUGEN has you on his chain!");
        builder.add("bossbar.fmscraft.fight_starts", "THE FIGHT BEGINS IN %s");
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

        builder.add("bossbar.fmscraft.drowsiness", "DROWSINESS");
        // Boss bars are always in capitals
        builder.add("bossbar.fmscraft.gorged_brute", "GORGED BRUTE");
        builder.add("bossbar.fmscraft.mother_warden", "MOTHER WARDEN");
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
