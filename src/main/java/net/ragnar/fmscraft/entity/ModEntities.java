package net.ragnar.fmscraft.entity;

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

    public static void initialize() {
    }
}
