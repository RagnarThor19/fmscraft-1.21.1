package net.ragnar.fmscraft;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.ragnar.fmscraft.datagen.ModAdvancementProvider;
import net.ragnar.fmscraft.datagen.ModLanguageProvider;
import net.ragnar.fmscraft.datagen.ModModelProvider;
import net.ragnar.fmscraft.datagen.ModRecipeProvider;

public class FmsCraftDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
		pack.addProvider(ModModelProvider::new);
		pack.addProvider(ModRecipeProvider::new);
		pack.addProvider(ModAdvancementProvider::new);
		pack.addProvider(ModLanguageProvider::new);
	}
}
