package net.ragnar.fmscraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.command.FmsCommands;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.loot.ModLoot;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.StructureAmbience;
import net.ragnar.fmscraft.structure.envy.EnvyMazes;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fragment.FragmentTriggers;
import net.ragnar.fmscraft.item.ModItemGroups;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.network.BossMusicPayload;
import net.ragnar.fmscraft.network.TomeWheelPayload;
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
		ModBlocks.initialize();
		ModItemGroups.initialize();
		ModBossEntities.initialize();
		ModEntities.initialize();
		ModStructures.initialize();
		ModLoot.initialize();
		PayloadTypeRegistry.playS2C().register(BossMusicPayload.ID, BossMusicPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TomeWheelPayload.ID, TomeWheelPayload.CODEC);

		FragmentTriggers.register();
		BossFights.register();
		TomeWheel.register();
		EnvyMazes.register();
		StructureAmbience.register();
		FmsCommands.register();

		LOGGER.info("FMSCraft loaded");
	}
}
