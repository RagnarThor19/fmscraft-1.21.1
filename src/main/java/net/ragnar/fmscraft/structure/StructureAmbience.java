package net.ragnar.fmscraft.structure;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.envy.EnvyMazePiece;
import net.ragnar.fmscraft.structure.pride.PrideTowerPiece;
import org.joml.Vector3f;

/**
 * A little atmosphere inside the sin structures, sent only to the players who are in them, and kept sparse:
 * <ul>
 *   <li>Envy maze: moss dust sifting down from the ceiling, and now and then a faint green mote hanging in the air.</li>
 *   <li>Tower of Pride: gold glints in the air all the way up, and wind-blown ash once you're high on the tower.</li>
 * </ul>
 */
public final class StructureAmbience {
    private static final int INTERVAL = 4;
    private static final int REACH = 8;
    private static final RegistryKey<Structure> ENVY_MAZE = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "envy_maze"));
    private static final RegistryKey<Structure> PRIDE_TOWER = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "tower_of_pride"));
    private static final ParticleEffect MOSS_DUST = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
    private static final ParticleEffect ENVY_MOTE = new DustParticleEffect(color(Sin.ENVY.color()).lerp(new Vector3f(1, 1, 1), 0.3f), 0.5f);

    private StructureAmbience() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StructureAmbience::tick);
    }

    private static Vector3f color(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % INTERVAL != 0) return;
        ServerWorld world = server.getOverworld();
        var structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Structure maze = structures.get(ENVY_MAZE), tower = structures.get(PRIDE_TOWER);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (maze != null && piece(world.getStructureAccessor().getStructureContaining(at, maze)) instanceof EnvyMazePiece) {
                envy(world, player);
            } else if (tower != null && piece(world.getStructureAccessor().getStructureContaining(at, tower)) instanceof PrideTowerPiece piece) {
                pride(world, player, piece);
            }
        }
    }

    private static Object piece(StructureStart start) {
        return start.hasChildren() ? start.getChildren().get(0) : null;
    }

    private static void envy(ServerWorld world, ServerPlayerEntity player) {
        Random random = world.getRandom();
        BlockPos at = player.getBlockPos();
        int x = at.getX() + random.nextInt(REACH * 2 + 1) - REACH, z = at.getZ() + random.nextInt(REACH * 2 + 1) - REACH;
        if (random.nextFloat() < 0.6f) {
            // From just under the ceiling
            BlockPos pos = new BlockPos(x, EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT - 2, z);
            if (world.getBlockState(pos).isAir() && !world.getBlockState(pos.up()).isAir()) {
                world.spawnParticles(player, MOSS_DUST, false, x + random.nextDouble(), pos.getY() + 0.9, z + random.nextDouble(), 1, 0, 0, 0, 0);
            }
        } else if (random.nextFloat() < 0.35f) {
            BlockPos pos = new BlockPos(x, EnvyMazePiece.FLOOR_Y + 1 + random.nextInt(4), z);
            if (world.getBlockState(pos).isAir()) {
                world.spawnParticles(player, ENVY_MOTE, false, x + random.nextDouble(), pos.getY() + random.nextDouble(), z + random.nextDouble(), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void pride(ServerWorld world, ServerPlayerEntity player, PrideTowerPiece piece) {
        Random random = world.getRandom();
        double height = (player.getY() - piece.foot().getY()) / piece.height();
        BlockPos pos = player.getBlockPos().add(random.nextInt(REACH * 2 + 1) - REACH, random.nextInt(6) - 1, random.nextInt(REACH * 2 + 1) - REACH);
        if (!world.getBlockState(pos).isAir()) return;
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (random.nextFloat() < 0.5f) {
            world.spawnParticles(player, ParticleTypes.WAX_ON, false, x, y, z, 1, 0, 0, 0, 0);
        } else if (height > 0.5 && random.nextFloat() < 0.6f) {
            world.spawnParticles(player, ParticleTypes.WHITE_ASH, false, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
