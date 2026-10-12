package net.ragnar.fmscraft.boss;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

public class ModBossEntities {
    public static final EntityType<TestBossEntity> TEST_BOSS = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "test_boss"),
            EntityType.Builder.create(TestBossEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(10)
                    .build("test_boss"));

    /** Which boss each sin's fight spawns. Swap in the real boss here once it's built. */
    public static EntityType<? extends SinBossEntity> forSin(Sin sin) {
        return switch (sin) {
            case GREED, WRATH, PRIDE, GLUTTONY, ENVY, LUST, SLOTH -> TEST_BOSS;
        };
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(TEST_BOSS, TestBossEntity.createAttributes());
    }
}
