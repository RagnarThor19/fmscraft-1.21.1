package net.ragnar.fmscraft.fragment;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

/** Sloth: let your pets do the work. A tamed wolf or tome summon kill has a 5% chance to drop a fragment. */
public final class SlothTrigger {
    public static final float CHANCE = 0.05f;
    /** Summons from other mods that count as pets. Edit data/fmscraft/tags/entity_type/sloth_summons.json to add more. */
    public static final TagKey<EntityType<?>> SUMMONS =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(FmsCraft.MOD_ID, "sloth_summons"));

    private SlothTrigger() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Entity killer = source.getAttacker();
            if (killer == null || !(entity.getWorld() instanceof ServerWorld world)) return;

            boolean isPet = (killer instanceof WolfEntity wolf && wolf.isTamed()) || killer.getType().isIn(SUMMONS);
            if (isPet && entity.getRandom().nextFloat() < CHANCE) {
                FragmentEffects.drop(world, entity.getPos().add(0, 0.5, 0), Sin.SLOTH, 1);
            }
        });
    }
}
