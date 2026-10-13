package net.ragnar.fmscraft.structure.pride;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.Optional;

/**
 * The Tower of Pride. Placement and rarity live in data/fmscraft/worldgen/structure_set/tower_of_pride.json.
 * It only stands on fairly level, dry land, and is as tall as the build limit allows (up to
 * {@link PrideTowerShape#FULL_HEIGHT} blocks).
 */
public class PrideTowerStructure extends Structure {
    public static final MapCodec<PrideTowerStructure> CODEC = createCodec(PrideTowerStructure::new);
    /** The tallest the ground under the tower may vary. */
    public static final int MAX_UNEVENNESS = 20;
    public static final int MIN_HEIGHT = 150;

    public PrideTowerStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getCenterX(), cz = chunk.getCenterZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, samples = 0;
        for (int ring : new int[]{0, 24, PrideTowerShape.RADIUS - 4}) {
            for (int i = 0; i < (ring == 0 ? 1 : 8); i++) {
                double angle = Math.PI / 4 * i;
                int x = cx + (int) Math.round(Math.cos(angle) * ring), z = cz + (int) Math.round(Math.sin(angle) * ring);
                int surface = context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                int floor = context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
                if (surface != floor) return Optional.empty(); // water
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                sum += surface;
                samples++;
            }
        }
        if (max - min > MAX_UNEVENNESS) return Optional.empty();

        int foot = Math.round((float) sum / samples) + 1;
        int topLimit = context.world().getTopY() - PrideTowerPiece.CROWN - 8;
        int height = Math.min(PrideTowerShape.FULL_HEIGHT, topLimit - foot);
        if (height < MIN_HEIGHT) return Optional.empty();

        BlockPos origin = new BlockPos(cx, foot, cz);
        long seed = context.random().nextLong();
        return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new PrideTowerPiece(seed, origin, height))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.PRIDE_TOWER;
    }
}
