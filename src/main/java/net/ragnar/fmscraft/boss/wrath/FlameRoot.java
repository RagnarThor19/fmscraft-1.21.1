package net.ragnar.fmscraft.boss.wrath;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Caught by one of ORNAMIX's fire waves along the floor: stuck where you stand (no walking, no jumping) and burning,
 * for {@link #TICKS}. You can still turn, hit and use things. Kept here rather than on him, so it always wears off on
 * time even if he's gone; like the cutscene freeze it's temporary attribute modifiers, which are never saved.
 */
public final class FlameRoot {
    public static final int TICKS = 100;
    private static final Identifier ID = Identifier.of(FmsCraft.MOD_ID, "flame_root");
    private static final Map<UUID, Integer> ROOTED = new HashMap<>();

    private FlameRoot() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FlameRoot::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ROOTED.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ROOTED.clear());
    }

    public static boolean isRooted(PlayerEntity player) {
        return ROOTED.containsKey(player.getUuid());
    }

    /** Roots {@code player} in flames for {@code ticks} (or keeps them rooted that long, if they already are). */
    public static void root(ServerPlayerEntity player, int ticks) {
        boolean fresh = !ROOTED.containsKey(player.getUuid());
        ROOTED.merge(player.getUuid(), ticks, Math::max);
        addModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        addModifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH));
        player.setSprinting(false);
        player.setVelocity(0, Math.min(0, player.getVelocity().y), 0);
        player.velocityModified = true;
        player.setOnFireFor(ticks / 20f);
        if (fresh) {
            player.sendMessage(Text.translatable("message.fmscraft.ornamix_rooted").formatted(Formatting.GOLD), true);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 0.6f);
            player.playSoundToPlayer(SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
    }

    public static void release(ServerPlayerEntity player) {
        ROOTED.remove(player.getUuid());
        removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH));
    }

    private static void tick(MinecraftServer server) {
        if (ROOTED.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = ROOTED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            int left = entry.getValue() - 1;
            if (player == null || left <= 0 || !player.isAlive()) {
                it.remove();
                if (player != null) {
                    removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                    removeModifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH));
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.6f, 1.2f);
                }
                continue;
            }
            entry.setValue(left);
            // Flames licking round their feet
            if (left % 2 == 0 && player.getWorld() instanceof ServerWorld world) {
                double a = left * 0.5;
                Vec3d feet = player.getPos();
                for (int i = 0; i < 3; i++) {
                    double b = a + i * MathHelper.TAU / 3;
                    world.spawnParticles(ParticleTypes.FLAME, feet.x + Math.cos(b) * 0.45, feet.y + 0.1, feet.z + Math.sin(b) * 0.45, 1, 0.02, 0.05, 0.02, 0.02);
                }
                if (left % 10 == 0) world.spawnParticles(ParticleTypes.LAVA, feet.x, feet.y + 0.2, feet.z, 1, 0.2, 0.05, 0.2, 0);
            }
        }
    }

    private static void addModifier(EntityAttributeInstance attribute) {
        if (attribute != null && !attribute.hasModifier(ID)) {
            attribute.addTemporaryModifier(new EntityAttributeModifier(ID, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifier(EntityAttributeInstance attribute) {
        if (attribute != null) attribute.removeModifier(ID);
    }
}
