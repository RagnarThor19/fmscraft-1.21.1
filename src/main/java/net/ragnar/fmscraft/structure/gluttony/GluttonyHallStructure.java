package net.ragnar.fmscraft.structure.gluttony;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * The Hall of Gluttony. Placement and rarity live in data/fmscraft/worldgen/structure_set/hall_of_gluttony.json; it's
 * the Nether only (data/fmscraft/tags/worldgen/biome/has_structure/hall_of_gluttony.json).
 *
 * It only stands in a big lava lake: its floor is at {@link #FLOOR_Y}, a few blocks over the Nether's lava sea, and
 * nearly everywhere under its terrace has to be lava with open air over it (a cavern big enough for the cathedral), or
 * the attempt is dropped. Then its bridge has to reach the shore: for each of the four ways the hall can face, it looks
 * out from the front of the terrace for the nearest land, and the hall turns to whichever is closest. With no shore
 * within {@link GluttonyHallShape#MAX_BRIDGE} blocks either way, there's no hall.
 */
public class GluttonyHallStructure extends Structure {
    public static final MapCodec<GluttonyHallStructure> CODEC = createCodec(GluttonyHallStructure::new);
    /** World height of the hall's floor block: three over the lava sea's surface (y = 31). */
    public static final int FLOOR_Y = 34;
    /**
     * How much of the terrace has to be over open lava, how much of the hall has to stand in open air, and how much of
     * the roof. The spires may well reach up into the cavern's roof, as a fortress runs into the rock.
     */
    public static final double MIN_LAVA = 0.75, MIN_OPEN = 0.85, MIN_ROOF = 0.5;
    private static final BlockRotation[] ROTATIONS = BlockRotation.values();

    public GluttonyHallStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos center = new BlockPos(chunk.getCenterX(), FLOOR_Y, chunk.getCenterZ());
        if (context.world().getBottomY() > FLOOR_Y + GluttonyHallShape.BOTTOM
                || context.world().getTopY() <= FLOOR_Y + GluttonyHallShape.TOP) return Optional.empty();
        Map<Long, VerticalBlockSample> columns = new HashMap<>();
        // Quickly first, whichever way it faces: open lava all round the middle
        if (!lake(context, center, columns)) return Optional.empty();

        BlockRotation best = null;
        int bestBridge = Integer.MAX_VALUE;
        // Start from a random side, so ties don't always face the same way
        int first = context.random().nextInt(4);
        for (int i = 0; i < 4; i++) {
            BlockRotation rotation = ROTATIONS[(first + i) % 4];
            if (!fits(context, center, rotation, columns)) continue;
            int bridge = bridgeTo(context, center, rotation, columns, Math.min(bestBridge, GluttonyHallShape.MAX_BRIDGE + 1));
            if (bridge < bestBridge) {
                bestBridge = bridge;
                best = rotation;
            }
        }
        if (best == null || bestBridge > GluttonyHallShape.MAX_BRIDGE) return Optional.empty();
        long seed = context.random().nextLong();
        BlockRotation rotation = best;
        int bridge = bestBridge;
        return Optional.of(new StructurePosition(center, collector -> collector.addPiece(new GluttonyHallPiece(seed, center, rotation, bridge))));
    }

    private static VerticalBlockSample column(Context context, int x, int z, Map<Long, VerticalBlockSample> columns) {
        return columns.computeIfAbsent(ChunkPos.toLong(x, z),
                k -> context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig()));
    }

    private static boolean lava(BlockState state) {
        return state.isOf(Blocks.LAVA);
    }

    /** Open lava (lava with air over it) at the middle, and nearly all the way round two rings round it. */
    private static boolean lake(Context context, BlockPos center, Map<Long, VerticalBlockSample> columns) {
        int[] misses = new int[3];
        for (int ring = 0; ring <= 2; ring++) {
            int count = ring == 0 ? 1 : 8;
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                int x = center.getX() + (int) Math.round(Math.cos(a) * ring * 20), z = center.getZ() + (int) Math.round(Math.sin(a) * ring * 20);
                // The rings may catch the shore here and there, the outer one more
                if (!openLava(column(context, x, z, columns)) && ++misses[ring] > ring * ring) return false;
            }
        }
        return true;
    }

    private static boolean openLava(VerticalBlockSample sample) {
        return lava(sample.getState(FLOOR_Y + GluttonyHallShape.LAVA)) && sample.getState(FLOOR_Y + 4).isAir();
    }

    /**
     * Whether the hall fits facing {@code rotation}: lava under nearly all of its terrace, open air through nearly all
     * of the cathedral up to the top of its walls, and over at least half of it up to its ridge.
     */
    private static boolean fits(Context context, BlockPos center, BlockRotation rotation, Map<Long, VerticalBlockSample> columns) {
        int lava = 0, total = 0, open = 0, roof = 0;
        for (int x = GluttonyHallShape.TERRACE_WEST; x <= GluttonyHallShape.TERRACE_EAST; x += 8) {
            for (int z = -GluttonyHallShape.TERRACE_HALF; z <= GluttonyHallShape.TERRACE_HALF; z += 13) {
                VerticalBlockSample sample = sample(context, center, rotation, x, z, columns);
                total++;
                if (openLava(sample)) lava++;
                boolean clear = true;
                for (int y : new int[]{4, 12, 22}) clear &= sample.getState(FLOOR_Y + y).isAir();
                if (clear) open++;
                if (sample.getState(FLOOR_Y + GluttonyHallShape.RIDGE).isAir()) roof++;
            }
        }
        return lava >= total * MIN_LAVA && open >= total * MIN_OPEN && roof >= total * MIN_ROOF;
    }

    private static VerticalBlockSample sample(Context context, BlockPos center, BlockRotation rotation, int lx, int lz, Map<Long, VerticalBlockSample> columns) {
        int x = center.getX() + GluttonyHallPiece.rotateX(lx, lz, rotation), z = center.getZ() + GluttonyHallPiece.rotateZ(lx, lz, rotation);
        return column(context, x, z, columns);
    }

    /**
     * How long the bridge would have to be, facing {@code rotation}, to reach the shore: the first ground at about the
     * lava's height with room to walk off the end onto it. More than {@code limit} if it wouldn't within it.
     */
    private static int bridgeTo(Context context, BlockPos center, BlockRotation rotation, Map<Long, VerticalBlockSample> columns, int limit) {
        // Every four blocks first, then back to the exact block
        int found = -1;
        for (int bridge = GluttonyHallShape.MIN_BRIDGE; bridge < limit; bridge += 4) {
            if (shore(sample(context, center, rotation, GluttonyHallShape.TERRACE_EAST + bridge, 0, columns))) {
                found = bridge;
                break;
            }
        }
        if (found < 0) return limit;
        for (int bridge = Math.max(GluttonyHallShape.MIN_BRIDGE, found - 3); bridge < found; bridge++) {
            if (shore(sample(context, center, rotation, GluttonyHallShape.TERRACE_EAST + bridge, 0, columns))) return bridge;
        }
        return found;
    }

    /** Solid ground somewhere from the lava's surface up to the bridge's deck, and open air over the deck. */
    private static boolean shore(VerticalBlockSample sample) {
        boolean ground = false;
        for (int y = GluttonyHallShape.LAVA; y <= 0; y++) {
            BlockState state = sample.getState(FLOOR_Y + y);
            if (!state.isAir() && !lava(state)) ground = true;
        }
        return ground && sample.getState(FLOOR_Y + 4).isAir();
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.GLUTTONY_HALL;
    }
}
