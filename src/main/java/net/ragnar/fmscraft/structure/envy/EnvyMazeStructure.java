package net.ragnar.fmscraft.structure.envy;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.Optional;

/** The buried Envy maze. Placement and rarity live in data/fmscraft/worldgen/structure_set/envy_maze.json. */
public class EnvyMazeStructure extends Structure {
    public static final MapCodec<EnvyMazeStructure> CODEC = createCodec(EnvyMazeStructure::new);

    public EnvyMazeStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        BlockPos origin = origin(context.chunkPos());
        long seed = context.random().nextLong();
        return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new EnvyMazePiece(seed, origin))));
    }

    /**
     * The north-west corner of a maze started in {@code chunk}. The maze is centered on that chunk: the game only
     * builds a structure in chunks up to 8 away from the one it starts in, and the maze is 10 chunks across.
     */
    public static BlockPos origin(ChunkPos chunk) {
        return new BlockPos(chunk.getCenterX() - EnvyMazePiece.SIZE / 2, EnvyMazePiece.FLOOR_Y, chunk.getCenterZ() - EnvyMazePiece.SIZE / 2);
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.ENVY_MAZE;
    }
}
