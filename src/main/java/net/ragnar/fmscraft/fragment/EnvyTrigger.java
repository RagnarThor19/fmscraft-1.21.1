package net.ragnar.fmscraft.fragment;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Envy: a rare mossy skeleton (a Bogged) wearing a carved pumpkin and holding an iron sword. It spawns anywhere in the
 * Overworld a skeleton can: {@link #CHANCE} of natural skeleton spawns come out as one instead, a little more common
 * than a spider jockey (1 in 100 spiders; skeletons and spiders spawn about as often). It shivers whenever it isn't
 * after a player. Killing one drops the pumpkin, and a fragment if a player killed it. Spawners and spawn eggs never
 * make one.
 */
public final class EnvyTrigger {
    public static final float CHANCE = 0.015f;

    private EnvyTrigger() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof BoggedEntity bogged) || !isShivering(bogged)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            if (world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) bogged.dropStack(new ItemStack(Blocks.CARVED_PUMPKIN));
            if (source.getAttacker() instanceof PlayerEntity) {
                FragmentEffects.drop(world, entity.getPos().add(0, 0.5, 0), Sin.ENVY, 1);
            }
        });
    }

    /**
     * Natural spawning just made {@code mob}: if it's a skeleton in the Overworld, it may be swapped for the shivering
     * Bogged. Returns the replacement, or null to keep {@code mob}. (Called before it's placed or initialized.)
     */
    @Nullable
    public static MobEntity rollSpawn(ServerWorld world, @Nullable MobEntity mob, Random random) {
        if (mob == null || mob.getType() != EntityType.SKELETON || world.getRegistryKey() != World.OVERWORLD) return null;
        if (random.nextFloat() >= CHANCE) return null;
        BoggedEntity bogged = EntityType.BOGGED.create(world);
        if (bogged == null) return null;
        ((ShiveringBogged) bogged).fmscraft$setShivering(true);
        return bogged;
    }

    /** After a skeleton's spawn equipment is chosen: the shivering Bogged swaps its bow for the sword and pumpkin. */
    public static void onInitialize(Entity entity) {
        if (entity instanceof BoggedEntity bogged && isShivering(bogged)) makeShivering(bogged);
    }

    /** Spawns one at {@code pos}, armed as a natural one would be (for /fmscraft envyskeleton). */
    @Nullable
    public static BoggedEntity spawn(ServerWorld world, Vec3d pos, float yaw) {
        BoggedEntity bogged = EntityType.BOGGED.create(world);
        if (bogged == null) return null;
        ((ShiveringBogged) bogged).fmscraft$setShivering(true);
        bogged.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
        bogged.initialize(world, world.getLocalDifficulty(bogged.getBlockPos()), SpawnReason.COMMAND, null);
        world.spawnEntityAndPassengers(bogged);
        return bogged;
    }

    public static void makeShivering(BoggedEntity bogged) {
        ((ShiveringBogged) bogged).fmscraft$setShivering(true);
        bogged.equipStack(EquipmentSlot.HEAD, new ItemStack(Blocks.CARVED_PUMPKIN));
        bogged.setEquipmentDropChance(EquipmentSlot.HEAD, 0); // dropped on death instead, always
        bogged.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); // switches it to melee
    }

    public static boolean isShivering(BoggedEntity bogged) {
        return ((ShiveringBogged) bogged).fmscraft$isShivering();
    }

    /** Whether it should be shivering right now (server side; clients read the synced flag). */
    public static boolean calm(BoggedEntity bogged) {
        return isShivering(bogged) && !(bogged.getTarget() instanceof PlayerEntity);
    }
}
