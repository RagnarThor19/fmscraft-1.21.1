package net.ragnar.fmscraft.world;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.network.DeathMarkPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Die in the Overworld, the Nether, the End or the Twilight and lose your things, and a mark at the top of your screen
 * (DeathMarkHud) shows which way they are until you get back to them. In the Twilight they aren't dropped but buried
 * in a grave where you fell (the twilight mod's PlayerGrave), so what counts is that you had things and lost them,
 * not that any were dropped. Nothing for boss fights: our arenas and the twilight's Blank are dimensions of their own,
 * and a death there puts your things somewhere else anyway. Nothing either when you lost nothing (keepInventory, an
 * empty inventory) or fell into the void.
 */
public final class DeathMarks {
    /** Where a mark is shown. */
    public static final Set<RegistryKey<World>> WORLDS = Set.of(World.OVERWORLD, World.NETHER, World.END, Sin.SinWorlds.TWILIGHT);
    /** Getting this close (in blocks) to the spot counts as being back. */
    public static final double REACHED = 4;

    /** Who had something on them as they went down (checked just before the death, cleared just after). */
    private static final Set<UUID> CARRYING = new HashSet<>();

    private DeathMarks() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (player.getInventory().isEmpty()) CARRYING.remove(player.getUuid());
                else CARRYING.add(player.getUuid());
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) onDeath(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(DeathMarks::tick);
    }

    private static void onDeath(ServerPlayerEntity player) {
        boolean carrying = CARRYING.remove(player.getUuid());
        ServerWorld world = player.getServerWorld();
        if (!WORLDS.contains(world.getRegistryKey()) || player.getY() < world.getBottomY()) return;
        // Dropped on the floor or buried in a grave, they're gone from their inventory (keepInventory leaves them there)
        if (!carrying || !player.getInventory().isEmpty()) return;
        FmsWorldState.get(player.getServer()).setDeathMark(player.getUuid(), GlobalPos.create(world.getRegistryKey(), player.getBlockPos()));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 0) return;
        FmsWorldState state = FmsWorldState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive()) continue;
            Optional<GlobalPos> mark = state.getDeathMark(player.getUuid());
            if (mark.isEmpty() || mark.get().dimension() != player.getWorld().getRegistryKey()) continue;
            if (player.getPos().isInRange(Vec3d.ofBottomCenter(mark.get().pos()), REACHED)) {
                state.clearDeathMark(player.getUuid());
                send(player);
            }
        }
    }

    /** Tells the player's client where their mark is now (or that there isn't one). */
    public static void send(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, DeathMarkPayload.ID)) return;
        ServerPlayNetworking.send(player, new DeathMarkPayload(FmsWorldState.get(player.getServer()).getDeathMark(player.getUuid())));
    }

    /**
     * How far (in degrees, -180 to 180) someone looking along {@code yaw} has to turn to face a spot {@code dx}, {@code dz}
     * away: positive is to their right.
     */
    public static float turnTo(double dx, double dz, float yaw) {
        float bearing = (float) (MathHelper.atan2(-dx, dz) * MathHelper.DEGREES_PER_RADIAN);
        return MathHelper.wrapDegrees(bearing - yaw);
    }
}
