package net.ragnar.fmscraft.structure.envy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.EnvyStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps exactly {@link #STATUES} statues roaming every Envy maze. Each maze's statues are remembered in
 * {@link FmsWorldState}. Whenever a player is in or above a maze and it's short (first visit, or one was removed),
 * new statues appear on corridors out of sight; extras (from older saves) crumble away when they load.
 */
public final class EnvyMazes {
    public static final int STATUES = 4;
    /** New statues never appear closer than this to a player. */
    public static final double SPAWN_DISTANCE = 24;
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
        for (ServerPlayerEntity player : world.getPlayers()) {
            for (StructureStart start : world.getStructureAccessor().getStructureStarts(ChunkSectionPos.from(player.getBlockPos()), maze)) {
                if (start.hasChildren() && start.getChildren().get(0) instanceof EnvyMazePiece piece) {
                    ensureStatues(world, piece.origin(), piece.seed());
                }
            }
        }
    }

    /** Tops the maze up to {@link #STATUES} statues, placing new ones on loaded corridors away from every player. */
    public static void ensureStatues(ServerWorld world, BlockPos origin, long seed) {
        FmsWorldState state = FmsWorldState.get(world.getServer());
        int missing = STATUES - state.envyStatues(origin.asLong()).size();
        if (missing <= 0) return;

        EnvyMazeLayout layout = layout(seed);
        List<int[]> cells = new ArrayList<>();
        for (int x = 1; x < EnvyMazeLayout.N; x += 2) {
            for (int z = 1; z < EnvyMazeLayout.N; z += 2) {
                if (layout.isPath(x, z)) cells.add(new int[]{x, z});
            }
        }
        Collections.shuffle(cells, new java.util.Random(world.getRandom().nextLong()));
        for (int[] cell : cells) {
            if (missing <= 0) break;
            BlockPos center = new BlockPos(origin.getX() + cell[0] * EnvyMazePiece.UNIT + 2, EnvyMazePiece.FLOOR_Y + 1,
                    origin.getZ() + cell[1] * EnvyMazePiece.UNIT + 2);
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(center.getX()), ChunkSectionPos.getSectionCoord(center.getZ()))) continue;
            if (world.getPlayers().stream().anyMatch(p -> p.squaredDistanceTo(center.toCenterPos()) < SPAWN_DISTANCE * SPAWN_DISTANCE)) continue;

            int dir = -1;
            for (int d = 0; d < 4 && dir < 0; d++) {
                if (layout.isPath(cell[0] + EnvyMazeLayout.DIRS[d][0], cell[1] + EnvyMazeLayout.DIRS[d][1])) dir = d;
            }
            if (dir < 0) continue;

            EnvyStatueEntity statue = ModEntities.ENVY_STATUE.create(world);
            if (statue == null) return;
            statue.place(center, EnvyMazePiece.direction(dir), origin, seed);
            world.spawnEntity(statue);
            state.addEnvyStatue(origin.asLong(), statue.getUuid());
            missing--;
        }
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
