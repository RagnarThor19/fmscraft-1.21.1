package net.ragnar.fmscraft.structure.sloth;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * The Sphere of Sloth. Placement and rarity live in data/fmscraft/worldgen/structure_set/sphere_of_sloth.json.
 * It only hangs out among the outer End islands, at least {@link #MIN_DISTANCE} blocks from the dragon's island, and
 * only in open void: no land within {@link #CLEARANCE} blocks of its center. If the chunk it would start in is too
 * close to land, it looks a couple of chunks around for a clear spot before giving up. Its mouth faces the main island.
 */
public class SlothSphereStructure extends Structure {
    public static final MapCodec<SlothSphereStructure> CODEC = createCodec(SlothSphereStructure::new);
    public static final int CENTER_Y = 100;
    public static final int MIN_DISTANCE = 1500;
    public static final int CLEARANCE = SlothSphereShape.RADIUS + 32;
    /** Centers to try, nearest the start chunk first. Kept within 2 chunks so the sphere stays in range of it. */
    private static final int[][] OFFSETS;

    static {
        OFFSETS = new int[25][];
        int i = 0;
        for (int x = -32; x <= 32; x += 16) {
            for (int z = -32; z <= 32; z += 16) OFFSETS[i++] = new int[]{x, z};
        }
        Arrays.sort(OFFSETS, Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
    }

    public SlothSphereStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getCenterX(), cz = chunk.getCenterZ();
        if ((long) cx * cx + (long) cz * cz < (long) MIN_DISTANCE * MIN_DISTANCE) return Optional.empty();
        if (context.world().getBottomY() > CENTER_Y - SlothSphereShape.RADIUS
                || context.world().getTopY() <= CENTER_Y + SlothSphereShape.RADIUS) return Optional.empty();
        for (int[] offset : OFFSETS) {
            int x = cx + offset[0], z = cz + offset[1];
            if (!clearOfLand(context, x, z)) continue;
            BlockPos center = new BlockPos(x, CENTER_Y, z);
            long seed = context.random().nextLong();
            BlockRotation rotation = facingOrigin(x, z);
            return Optional.of(new StructurePosition(center, collector -> collector.addPiece(new SlothSpherePiece(seed, center, rotation))));
        }
        return Optional.empty();
    }

    /** Whether there's nothing but void in rings out to {@link #CLEARANCE} blocks around (x, z). */
    private static boolean clearOfLand(Context context, int x, int z) {
        int bottom = context.world().getBottomY();
        for (int ring = 0; ring <= CLEARANCE; ring += 16) {
            int samples = ring == 0 ? 1 : Math.max(8, ring / 4);
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2 * i / samples;
                int sx = x + (int) Math.round(Math.cos(angle) * ring), sz = z + (int) Math.round(Math.sin(angle) * ring);
                int height = context.chunkGenerator().getHeight(sx, sz, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                if (height > bottom) return false;
            }
        }
        return true;
    }

    /** The rotation that turns the mouth (local +x) toward the main island. */
    static BlockRotation facingOrigin(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return x > 0 ? BlockRotation.CLOCKWISE_180 : BlockRotation.NONE;
        return z > 0 ? BlockRotation.COUNTERCLOCKWISE_90 : BlockRotation.CLOCKWISE_90;
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.SLOTH_SPHERE;
    }
}
