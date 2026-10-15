package net.ragnar.fmscraft.structure.envy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.EnvyStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps exactly {@link #STATUES} statues in every Envy maze. Each maze's statues are remembered in
 * {@link FmsWorldState}. Whenever a player comes within {@link #WAKE_DISTANCE} blocks of a maze and it's short (first
 * visit, or one was destroyed), new statues appear in its corners, out of sight; extras (from older saves) crumble
 * away when they load. Statues stand still until a player is that close, then start roaming.
 */
public final class EnvyMazes {
    public static final int STATUES = 4;
    /** New statues never appear closer than this to a player. */
    public static final double SPAWN_DISTANCE = 24;
    /** Statues only move while a player is this close to their maze (measured flat, so from the surface too). */
    public static final double WAKE_DISTANCE = 50;
    /** A corner with a statue this close to it is taken. */
    private static final double CORNER_TAKEN = 2 * EnvyMazePiece.UNIT;
    private static final int CHECK_INTERVAL = 40;
    private static final RegistryKey<Structure> MAZE = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "envy_maze"));
    private static final Map<Long, EnvyMazeLayout> LAYOUTS = new ConcurrentHashMap<>();

    private EnvyMazes() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EnvyMazes::tick);
    }

    public static EnvyMazeLayout layout(long seed) {
        return LAYOUTS.computeIfAbsent(seed, EnvyMazeLayout::generate);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % CHECK_INTERVAL != 0) return;
        ServerWorld world = server.getOverworld();
        Structure maze = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(MAZE);
        if (maze == null) return;
        // Look through the loaded chunks around each player for mazes they're near
        int radius = MathHelper.ceil(WAKE_DISTANCE / 16) + 1;
        Map<Long, EnvyMazePiece> near = new HashMap<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            ChunkPos at = player.getChunkPos();
            for (int cx = at.x - radius; cx <= at.x + radius; cx++) {
                for (int cz = at.z - radius; cz <= at.z + radius; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    for (StructureStart start : world.getStructureAccessor().getStructureStarts(ChunkSectionPos.from(new ChunkPos(cx, cz), 0), maze)) {
                        if (start.hasChildren() && start.getChildren().get(0) instanceof EnvyMazePiece piece
                                && flatDistance(player, piece.origin()) <= WAKE_DISTANCE) {
                            near.put(piece.origin().asLong(), piece);
                        }
                    }
                }
            }
        }
        for (EnvyMazePiece piece : near.values()) ensureStatues(world, piece.origin(), piece.seed());
    }

    /** Whether a player (not a spectator) is within {@link #WAKE_DISTANCE} blocks of the maze at this origin. */
    public static boolean playerNear(ServerWorld world, BlockPos origin) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && flatDistance(player, origin) <= WAKE_DISTANCE) return true;
        }
        return false;
    }

    /** Horizontal distance from a player to the edge of the maze at this origin (0 inside or above it). */
    private static double flatDistance(Entity player, BlockPos origin) {
        double dx = Math.max(0, Math.max(origin.getX() - player.getX(), player.getX() - (origin.getX() + EnvyMazePiece.SIZE)));
        double dz = Math.max(0, Math.max(origin.getZ() - player.getZ(), player.getZ() - (origin.getZ() + EnvyMazePiece.SIZE)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Tops the maze up to {@link #STATUES} statues. New ones only ever appear in a free corner (the one farthest from
     * the statues already there first), on a built cell in a loaded chunk and away from every player; if none fits,
     * it waits.
     */
    public static void ensureStatues(ServerWorld world, BlockPos origin, long seed) {
        FmsWorldState state = FmsWorldState.get(world.getServer());
        Set<UUID> existing = state.envyStatues(origin.asLong());
        int missing = STATUES - existing.size();
        if (missing <= 0) return;

        List<Vec3d> statues = new ArrayList<>();
        for (UUID id : existing) {
            Entity statue = world.getEntity(id);
            if (statue != null) statues.add(statue.getPos());
        }

        while (missing > 0) {
            BlockPos best = null;
            int bestDir = 0;
            double bestDistance = -1;
            for (EnvyMazeLayout.Spot corner : layout(seed).statues) {
                EnvyMazeLayout.Spot spot = builtCellNear(world, origin, layout(seed), corner);
                if (spot == null) continue;
                BlockPos center = unitCenter(origin, spot.x(), spot.z());
                Vec3d pos = center.toCenterPos();
                double nearest = statues.stream().mapToDouble(p -> p.squaredDistanceTo(pos)).min().orElse(Double.MAX_VALUE);
                if (nearest < CORNER_TAKEN * CORNER_TAKEN || nearest <= bestDistance) continue;
                if (world.getPlayers().stream().anyMatch(p -> p.squaredDistanceTo(pos) < SPAWN_DISTANCE * SPAWN_DISTANCE)) continue;
                best = center;
                bestDir = spot.dir();
                bestDistance = nearest;
            }
            if (best == null) return;

            EnvyStatueEntity statue = ModEntities.ENVY_STATUE.create(world);
            if (statue == null) return;
            statue.place(best, EnvyMazePiece.direction(bestDir), origin, seed);
            world.spawnEntity(statue);
            state.addEnvyStatue(origin.asLong(), statue.getUuid());
            statues.add(statue.getPos());
            missing--;
        }
    }

    /**
     * The maze cell nearest {@code corner} that has really been built: loaded, open, with the chiseled stone that
     * marks the middle of every cell under it. Mazes generated before they were centered on their start chunk are
     * missing their east and south edges (the game never built that far), so their corners may be plain stone.
     */
    private static EnvyMazeLayout.Spot builtCellNear(ServerWorld world, BlockPos origin, EnvyMazeLayout layout, EnvyMazeLayout.Spot corner) {
        EnvyMazeLayout.Spot best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 1; x < EnvyMazeLayout.N; x += 2) {
            for (int z = 1; z < EnvyMazeLayout.N; z += 2) {
                int distance = Math.abs(x - corner.x()) + Math.abs(z - corner.z());
                if (distance >= bestDistance || !layout.isPath(x, z)) continue;
                BlockPos center = unitCenter(origin, x, z);
                if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(center.getX()), ChunkSectionPos.getSectionCoord(center.getZ()))) continue;
                if (!world.getBlockState(center).isAir() || !world.getBlockState(center.down()).isOf(Blocks.CHISELED_STONE_BRICKS)) continue;
                for (int d = 0; d < 4; d++) {
                    if (layout.isPath(x + EnvyMazeLayout.DIRS[d][0], z + EnvyMazeLayout.DIRS[d][1])) {
                        best = new EnvyMazeLayout.Spot(x, z, d);
                        bestDistance = distance;
                        break;
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos unitCenter(BlockPos origin, int ux, int uz) {
        return new BlockPos(origin.getX() + ux * EnvyMazePiece.UNIT + 2, EnvyMazePiece.FLOOR_Y + 1, origin.getZ() + uz * EnvyMazePiece.UNIT + 2);
    }

    /** A statue checking in when it loads. Returns false if its maze already has its full set (it should go). */
    public static boolean claimSlot(ServerWorld world, BlockPos origin, UUID statue) {
        FmsWorldState state = FmsWorldState.get(world.getServer());
        Set<UUID> statues = state.envyStatues(origin.asLong());
        if (statues.contains(statue)) return true;
        if (statues.size() >= STATUES) return false;
        state.addEnvyStatue(origin.asLong(), statue);
        return true;
    }

    /** A statue was destroyed (not just unloaded): free its slot so a new one can take its place. */
    public static void release(ServerWorld world, BlockPos origin, UUID statue) {
        FmsWorldState.get(world.getServer()).removeEnvyStatue(origin.asLong(), statue);
    }
}
