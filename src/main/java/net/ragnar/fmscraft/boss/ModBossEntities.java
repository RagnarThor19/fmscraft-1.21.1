package net.ragnar.fmscraft.boss;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.greed.GreedBubbleEntity;
import net.ragnar.fmscraft.boss.greed.GreedIntro;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;
import net.ragnar.fmscraft.boss.wrath.OrnamixFireballEntity;
import net.ragnar.fmscraft.boss.wrath.WrathIntro;
import net.ragnar.fmscraft.fight.FightIntro;
import net.ragnar.fmscraft.sin.Sin;

public class ModBossEntities {
    public static final EntityType<TestBossEntity> TEST_BOSS = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "test_boss"),
            EntityType.Builder.create(TestBossEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(10)
                    .disableSaving()
                    .build("test_boss"));

    /** Greed. Player-sized in phase 1; his scale attribute makes him 3 blocks tall in phase 2. */
    public static final EntityType<RaggimelEntity> RAGGIMEL = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "raggimel"),
            EntityType.Builder.create(RaggimelEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .trackingTickInterval(1)
                    // Fights don't survive a restart, so neither do bosses: one saved mid-fight would be a second boss later
                    .disableSaving()
                    .build("raggimel"));

    public static final EntityType<GreedBubbleEntity> GREED_BUBBLE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "greed_bubble"),
            EntityType.Builder.<GreedBubbleEntity>create(GreedBubbleEntity::new, SpawnGroup.MISC)
                    .dimensions(GreedBubbleEntity.SIZE, GreedBubbleEntity.SIZE)
                    .makeFireImmune()
                    .disableSaving()
                    .maxTrackingRange(8)
                    .trackingTickInterval(1)
                    .build("greed_bubble"));

    /** Wrath. Player-sized: fast and hard to pin down. */
    public static final EntityType<OrnamixEntity> ORNAMIX = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "ornamix"),
            EntityType.Builder.create(OrnamixEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("ornamix"));

    /** ORNAMIX's ghast fireballs (they break nothing). */
    public static final EntityType<OrnamixFireballEntity> ORNAMIX_FIREBALL = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "ornamix_fireball"),
            EntityType.Builder.<OrnamixFireballEntity>create(OrnamixFireballEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0f, 1.0f)
                    .makeFireImmune()
                    .disableSaving()
                    .maxTrackingRange(8)
                    .trackingTickInterval(1)
                    .build("ornamix_fireball"));

    /** Which boss each sin's fight spawns. Swap in the real boss here once it's built. */
    public static EntityType<? extends SinBossEntity> forSin(Sin sin) {
        return switch (sin) {
            case GREED -> RAGGIMEL;
            case WRATH -> ORNAMIX;
            case PRIDE, GLUTTONY, ENVY, LUST, SLOTH -> TEST_BOSS;
        };
    }

    /** The cutscene that plays while a sin's portal is open, or null to just wait (with a countdown bar). */
    public static FightIntro introFor(Sin sin) {
        return switch (sin) {
            case GREED -> GreedIntro.INSTANCE;
            case WRATH -> WrathIntro.INSTANCE;
            default -> null;
        };
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(TEST_BOSS, TestBossEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RAGGIMEL, RaggimelEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ORNAMIX, OrnamixEntity.createAttributes());
    }
}
