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

/**
 * Sloth: let your pets do the work. A kill by a tamed wolf, a tome summon (the {@link #SUMMONS} tag) or one of the Tome
 * of the Dead's undead has an 8% chance to drop a fragment.
 */
public final class SlothTrigger {
    public static final float CHANCE = 0.08f;
    /** Summons from other mods that count as pets. Edit data/fmscraft/tags/entity_type/sloth_summons.json to add more. */
    public static final TagKey<EntityType<?>> SUMMONS =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(FmsCraft.MOD_ID, "sloth_summons"));

    /**
     * The command tag Ragnars Magic Mod's Tome of the Dead puts on the zombies and skeletons it raises (DeadSpell.TAG).
     * They're vanilla mobs, so this tag is the only way to tell them from wild ones.
     */
    public static final String RAISED_UNDEAD_TAG = "ragnarsmagicmod_undead";

    private SlothTrigger() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Entity killer = source.getAttacker();
            if (killer == null || !(entity.getWorld() instanceof ServerWorld world)) return;

            boolean isPet = (killer instanceof WolfEntity wolf && wolf.isTamed()) || killer.getType().isIn(SUMMONS)
                    || killer.getCommandTags().contains(RAISED_UNDEAD_TAG);
            if (isPet && entity.getRandom().nextFloat() < CHANCE) {
                FragmentEffects.drop(world, entity.getPos().add(0, 0.5, 0), Sin.SLOTH, 1);
            }
        });
    }
}
