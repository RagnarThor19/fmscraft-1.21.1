package net.ragnar.fmscraft.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModEntities {
    public static final EntityType<EnvyStatueEntity> ENVY_STATUE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "envy_statue"),
            EntityType.Builder.<EnvyStatueEntity>create(EnvyStatueEntity::new, SpawnGroup.MISC)
                    .dimensions(3.0f, 3.6f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)
                    .build("envy_statue"));

    /** Player-sized, so it can follow you anywhere in the vault you can go. */
    public static final EntityType<GreedStatueEntity> GREED_STATUE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "greed_statue"),
            EntityType.Builder.<GreedStatueEntity>create(GreedStatueEntity::new, SpawnGroup.MISC)
                    .dimensions(0.8f, 1.95f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .build("greed_statue"));

    /** 3 blocks tall but as slender as a player: it can follow you down any path in the garden. */
    public static final EntityType<LustStatueEntity> LUST_STATUE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "lust_statue"),
            EntityType.Builder.<LustStatueEntity>create(LustStatueEntity::new, SpawnGroup.MISC)
                    .dimensions(0.8f, 3.0f)
                    .eyeHeight(2.75f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)
                    .build("lust_statue"));

    /** A warden's size; she's made three times bigger by her scale attribute, which scales her box and model alike. */
    public static final EntityType<MotherWardenEntity> MOTHER_WARDEN = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "mother_warden"),
            EntityType.Builder.<MotherWardenEntity>create(MotherWardenEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 2.9f)
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .build("mother_warden"));

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(GREED_STATUE, GreedStatueEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LUST_STATUE, LustStatueEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MOTHER_WARDEN, MotherWardenEntity.createAttributes());
    }
}
