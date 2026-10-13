package net.ragnar.fmscraft.loot;

import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModLoot {
    public static final LootFunctionType<RandomTomeLootFunction> RANDOM_TOME = Registry.register(Registries.LOOT_FUNCTION_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "random_tome"), new LootFunctionType<>(RandomTomeLootFunction.CODEC));

    public static void initialize() {
    }
}
