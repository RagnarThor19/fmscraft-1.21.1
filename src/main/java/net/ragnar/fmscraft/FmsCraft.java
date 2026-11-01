package net.ragnar.fmscraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.command.FmsCommands;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.loot.ModLoot;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.StructureAmbience;
import net.ragnar.fmscraft.structure.envy.EnvyMazes;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHalls;
import net.ragnar.fmscraft.structure.greed.GreedVaults;
import net.ragnar.fmscraft.structure.lust.LustGardens;
import net.ragnar.fmscraft.structure.sloth.SlothSpheres;
import net.ragnar.fmscraft.structure.wrath.WrathDens;
import net.ragnar.fmscraft.sound.ModSounds;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fragment.FragmentTriggers;
import net.ragnar.fmscraft.item.ModItemGroups;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.network.BossMusicPayload;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.network.GreedSkyPayload;
import net.ragnar.fmscraft.network.WrathSkyPayload;
import net.ragnar.fmscraft.boss.wrath.FlameRoot;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
import net.ragnar.fmscraft.network.GardenFogPayload;
import net.ragnar.fmscraft.network.TomeWheelPayload;
import net.ragnar.fmscraft.network.VaultFogPayload;
import net.ragnar.fmscraft.reward.TomeWheel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FmsCraft implements ModInitializer {
	public static final String MOD_ID = "fmscraft";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModSounds.initialize();
		ModBlocks.initialize();
		ModItemGroups.initialize();
		ModBossEntities.initialize();
		ModEffects.initialize();
		ModEntities.initialize();
		ModStructures.initialize();
		ModLoot.initialize();
		PayloadTypeRegistry.playS2C().register(BossMusicPayload.ID, BossMusicPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TomeWheelPayload.ID, TomeWheelPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VaultFogPayload.ID, VaultFogPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GardenFogPayload.ID, GardenFogPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BossScreenPayload.ID, BossScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GreedSkyPayload.ID, GreedSkyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WrathSkyPayload.ID, WrathSkyPayload.CODEC);

		FragmentTriggers.register();
		BossFights.register();
		Cinematics.register();
		TomeWheel.register();
		FlameRoot.register();
		OrnamixEntity.registerEvents();
		GummssEntity.registerEvents();
		EnvyMazes.register();
		SlothSpheres.register();
		GreedVaults.register();
		LustGardens.register();
		WrathDens.register();
		GluttonyHalls.register();
		StructureAmbience.register();
		FmsCommands.register();

		LOGGER.info("FMSCraft loaded");
	}
}
