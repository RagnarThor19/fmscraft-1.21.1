package net.ragnar.fmscraft.fragment;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Map;

/**
 * Greed: 10% chance per ancient city chest.
 * Envy: a rare find in end cities, bastions and woodland mansions.
 */
public final class LootFragments {
    public static final Map<RegistryKey<LootTable>, Float> GREED_CHANCES = Map.of(
            LootTables.ANCIENT_CITY_CHEST, 0.10f);

    public static final Map<RegistryKey<LootTable>, Float> ENVY_CHANCES = Map.of(
            LootTables.END_CITY_TREASURE_CHEST, 0.05f,
            LootTables.BASTION_TREASURE_CHEST, 0.08f,
            LootTables.BASTION_OTHER_CHEST, 0.03f,
            LootTables.BASTION_BRIDGE_CHEST, 0.03f,
            LootTables.BASTION_HOGLIN_STABLE_CHEST, 0.03f,
            LootTables.WOODLAND_MANSION_CHEST, 0.08f);

    private LootFragments() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!source.isBuiltin()) return;
            addPool(builder, Sin.GREED, GREED_CHANCES.get(key));
            addPool(builder, Sin.ENVY, ENVY_CHANCES.get(key));
        });
    }

    private static void addPool(LootTable.Builder builder, Sin sin, Float chance) {
        if (chance == null) return;
        builder.pool(LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(chance))
                .with(ItemEntry.builder(ModItems.FRAGMENTS.get(sin))));
    }
}
