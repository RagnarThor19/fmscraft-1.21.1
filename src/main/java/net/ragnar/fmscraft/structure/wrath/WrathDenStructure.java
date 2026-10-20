package net.ragnar.fmscraft.structure.wrath;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Den of Wrath. Placement and rarity live in data/fmscraft/worldgen/structure_set/den_of_wrath.json; it's the
 * Nether only (data/fmscraft/tags/worldgen/biome/has_structure/den_of_wrath.json).
 *
 * The hall is buried with its floor at {@link #FLOOR_Y}, under the lava sea, and needs to sit mostly in solid
 * ground: at least {@link #MIN_SOLID} of the points sampled round its shell must be rock (or lava), or the attempt is
 * dropped. Then the entrance tunnel has to find the Nether: for each of the four ways the den can face, it looks out
 * from the gate for the nearest open cave at the tunnel's height, and the den turns to whichever is closest. The tunnel
 * runs exactly that far, so it always opens out of a cave wall. With no cave within {@link WrathDenShape#MAX_TUNNEL}
 * blocks either way, there's no den.
 */
public class WrathDenStructure extends Structure {
    public static final MapCodec<WrathDenStructure> CODEC = createCodec(WrathDenStructure::new);
    /** World height of the hall's floor block. */
    public static final int FLOOR_Y = 22;
    public static final double MIN_SOLID = 0.6;
    private static final BlockRotation[] ROTATIONS = BlockRotation.values();

    public WrathDenStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos center = new BlockPos(chunk.getCenterX(), FLOOR_Y, chunk.getCenterZ());
        if (context.world().getBottomY() > FLOOR_Y + WrathDenShape.BOTTOM
                || context.world().getTopY() <= FLOOR_Y + WrathDenShape.TOP) return Optional.empty();
        Map<Long, VerticalBlockSample> columns = new HashMap<>();
        if (solidity(context, center, columns) < MIN_SOLID) return Optional.empty();

        BlockRotation best = null;
        int bestTunnel = Integer.MAX_VALUE;
        // Start from a random side, so ties don't always face the same way
        int first = context.random().nextInt(4);
        for (int i = 0; i < 4; i++) {
            BlockRotation rotation = ROTATIONS[(first + i) % 4];
            int tunnel = tunnelTo(context, center, rotation, columns, Math.min(bestTunnel, WrathDenShape.MAX_TUNNEL + 1));
            if (tunnel < bestTunnel) {
                bestTunnel = tunnel;
                best = rotation;
            }
        }
        if (best == null || bestTunnel > WrathDenShape.MAX_TUNNEL) return Optional.empty();
        long seed = context.random().nextLong();
        BlockRotation rotation = best;
        int tunnel = bestTunnel;
        return Optional.of(new StructurePosition(center, collector -> collector.addPiece(new WrathDenPiece(seed, center, rotation, tunnel))));
    }

    private static VerticalBlockSample column(Context context, int x, int z, Map<Long, VerticalBlockSample> columns) {
        return columns.computeIfAbsent(ChunkPos.toLong(x, z),
                k -> context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig()));
    }

    /** The share of points round the hall's shell (its walls at two heights, and the dome) that aren't open air. */
    private static double solidity(Context context, BlockPos center, Map<Long, VerticalBlockSample> columns) {
        int solid = 0, total = 0;
        double r = WrathDenShape.OUTER - 2;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int x = center.getX() + (int) Math.round(Math.cos(a) * r), z = center.getZ() + (int) Math.round(Math.sin(a) * r);
            VerticalBlockSample sample = column(context, x, z, columns);
            for (int y : new int[]{10, 30}) {
                total++;
                if (!sample.getState(center.getY() + y).isAir()) solid++;
            }
        }
        VerticalBlockSample top = column(context, center.getX(), center.getZ(), columns);
        for (int y : new int[]{WrathDenShape.WALL_TOP + WrathDenShape.DOME + 2, WrathDenShape.WALL_TOP + 10}) {
            total++;
            if (!top.getState(center.getY() + y).isAir()) solid++;
        }
        return solid / (double) total;
    }

    /**
     * How far the entrance tunnel would have to run past the outside of the wall, facing {@code rotation}, to come out
     * in an open cave (three blocks of air at walking height). More than {@code limit} if it wouldn't within it.
     */
    private static int tunnelTo(Context context, BlockPos center, BlockRotation rotation, Map<Long, VerticalBlockSample> columns, int limit) {
        int walk = center.getY() + WrathDenShape.BALCONY + 1;
        for (int tunnel = WrathDenShape.MIN_TUNNEL; tunnel < limit; tunnel++) {
            int lx = WrathDenShape.OUTER + tunnel;
            int x = center.getX() + WrathDenPiece.rotateX(lx, 0, rotation), z = center.getZ() + WrathDenPiece.rotateZ(lx, 0, rotation);
            VerticalBlockSample sample = column(context, x, z, columns);
            if (sample.getState(walk).isAir() && sample.getState(walk + 1).isAir() && sample.getState(walk + 2).isAir()) return tunnel;
        }
        return limit;
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.WRATH_DEN;
    }
}
