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
        ChunkPos chunk = context.chunkPos();
        BlockPos origin = new BlockPos(chunk.getStartX(), EnvyMazePiece.FLOOR_Y, chunk.getStartZ());
        long seed = context.random().nextLong();
        return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new EnvyMazePiece(seed, origin))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.ENVY_MAZE;
    }
}
