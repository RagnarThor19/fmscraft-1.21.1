package net.ragnar.fmscraft.boss;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.EnvyIntro;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
import net.ragnar.fmscraft.boss.envy.GummssIllusionEntity;
import net.ragnar.fmscraft.boss.gluttony.GluttonyIntro;
import net.ragnar.fmscraft.boss.gluttony.LalliEntity;
import net.ragnar.fmscraft.boss.greed.GreedBubbleEntity;
import net.ragnar.fmscraft.boss.greed.GreedIntro;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;
import net.ragnar.fmscraft.boss.greed.RaggimelMirageEntity;
import net.ragnar.fmscraft.boss.lust.LustIntro;
import net.ragnar.fmscraft.boss.lust.MugenCloneEntity;
import net.ragnar.fmscraft.boss.lust.MugenEntity;
import net.ragnar.fmscraft.boss.pride.LeonardEntity;
import net.ragnar.fmscraft.boss.pride.PrideIntro;
import net.ragnar.fmscraft.boss.sloth.RickyEntity;
import net.ragnar.fmscraft.boss.sloth.SlothIntro;
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

    /** The three false RAGGIMELs of his Deluge: just like him, holding up orbs, but only water. */
    public static final EntityType<RaggimelMirageEntity> RAGGIMEL_MIRAGE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "raggimel_mirage"),
            EntityType.Builder.create(RaggimelMirageEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("raggimel_mirage"));

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

    /** Envy. Player-sized, a caster who keeps his distance. */
    public static final EntityType<GummssEntity> GUMMSS = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "gummss"),
            EntityType.Builder.create(GummssEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(14)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("gummss"));

    /** GUMMSS's Mirror Images: just like him, but they break at a touch. */
    public static final EntityType<GummssIllusionEntity> GUMMSS_ILLUSION = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "gummss_illusion"),
            EntityType.Builder.create(GummssIllusionEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .disableSaving()
                    .build("gummss_illusion"));

    /** Gluttony. Three blocks tall (his scale attribute), slow and the toughest of them. */
    public static final EntityType<LalliEntity> LALLI = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "lalli"),
            EntityType.Builder.create(LalliEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("lalli"));

    /** Lust. Player-sized, the fastest of them: a ninja. */
    public static final EntityType<MugenEntity> MUGEN = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "mugen"),
            EntityType.Builder.create(MugenEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(14)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("mugen"));

    /** MUGEN's shadow clones (and the echoes his Taunt leaves): just like him, but they go up in smoke at a touch. */
    public static final EntityType<MugenCloneEntity> MUGEN_CLONE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "mugen_clone"),
            EntityType.Builder.create(MugenCloneEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("mugen_clone"));

    /** Pride. Player-sized and fast; his scale attribute makes CONEMAN five blocks tall. */
    public static final EntityType<LeonardEntity> LEONARD = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "leonard"),
            EntityType.Builder.create(LeonardEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("leonard"));

    /** Sloth. Player-sized; flies once he's a Super Saiyan. */
    public static final EntityType<RickyEntity> RICKY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "ricky"),
            EntityType.Builder.create(RickyEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .trackingTickInterval(1)
                    .disableSaving()
                    .build("ricky"));

    /**
     * Sins whose fights use the stand-in test boss (with no intro, on the plain platform) instead of their real one. For
     * the generic fight tests, which need a boss that dies in one hit; every sin has its real boss now.
     */
    private static final java.util.Set<Sin> STAND_INS = java.util.EnumSet.noneOf(Sin.class);

    /** For tests: {@code sin}'s fights use the stand-in boss (true) or its real one (false). */
    public static void useStandIn(Sin sin, boolean standIn) {
        if (standIn) STAND_INS.add(sin);
        else STAND_INS.remove(sin);
    }

    public static boolean usesStandIn(Sin sin) {
        return STAND_INS.contains(sin);
    }

    /** Which boss each sin's fight spawns. */
    public static EntityType<? extends SinBossEntity> forSin(Sin sin) {
        if (usesStandIn(sin)) return TEST_BOSS;
        return switch (sin) {
            case GREED -> RAGGIMEL;
            case WRATH -> ORNAMIX;
            case ENVY -> GUMMSS;
            case GLUTTONY -> LALLI;
            case LUST -> MUGEN;
            case PRIDE -> LEONARD;
            case SLOTH -> RICKY;
        };
    }

    /** The cutscene that plays while a sin's portal is open, or null to just wait (with a countdown bar). */
    public static FightIntro introFor(Sin sin) {
        if (usesStandIn(sin)) return null;
        return switch (sin) {
            case GREED -> GreedIntro.INSTANCE;
            case WRATH -> WrathIntro.INSTANCE;
            case ENVY -> EnvyIntro.INSTANCE;
            case GLUTTONY -> GluttonyIntro.INSTANCE;
            case LUST -> LustIntro.INSTANCE;
            case PRIDE -> PrideIntro.INSTANCE;
            case SLOTH -> SlothIntro.INSTANCE;
        };
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(TEST_BOSS, TestBossEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RAGGIMEL, RaggimelEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RAGGIMEL_MIRAGE, RaggimelMirageEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ORNAMIX, OrnamixEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GUMMSS, GummssEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GUMMSS_ILLUSION, GummssIllusionEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LALLI, LalliEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MUGEN, MugenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MUGEN_CLONE, MugenCloneEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LEONARD, LeonardEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RICKY, RickyEntity.createAttributes());
    }
}
