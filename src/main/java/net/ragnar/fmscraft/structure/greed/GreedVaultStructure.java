package net.ragnar.fmscraft.structure.greed;

import com.mojang.serialization.MapCodec;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.Optional;

/**
 * The Greed Vault, buried just under the twilight surface with only its pipe sticking up out of the ground.
 * Placement and rarity live in data/fmscraft/worldgen/structure_set/greed_vault.json. It's centered on the chunk it
 * starts in, turned to a random side, and sunk so the top of the pipe stands 4 blocks above the ground there while
 * the rooms stay at least a block under the ground everywhere above them.
 */
public class GreedVaultStructure extends Structure {
    public static final MapCodec<GreedVaultStructure> CODEC = createCodec(GreedVaultStructure::new);

    public GreedVaultStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        BlockRotation rotation = BlockRotation.random(context.random());
        ChunkPos chunk = context.chunkPos();
        BlockPos center = new BlockPos(chunk.getCenterX(), 0, chunk.getCenterZ());
        BlockPos corner = center.subtract(GreedVaultLayout.PIVOT);
        StructurePlacementData placement = GreedVaultPiece.placement(rotation);

        BlockPos pipe = StructureTemplate.transform(placement, GreedVaultLayout.PIPE_CENTER).add(corner);
        int pipeGround = height(context, pipe.getX(), pipe.getZ());
        int lowest = pipeGround;
        for (int x = 0; x < GreedVaultLayout.SIZE_X; x += 12) {
            for (int z = 0; z < GreedVaultLayout.SIZE_Z; z += 11) {
                BlockPos at = StructureTemplate.transform(placement, new BlockPos(x, 0, z)).add(corner);
                lowest = Math.min(lowest, height(context, at.getX(), at.getZ()));
            }
        }
        int y = Math.min(pipeGround - GreedVaultLayout.SURFACE, lowest - GreedVaultLayout.BODY_TOP - 2);
        // Twilight has nothing but air below y = 0: never hang the vault out into that
        if (y < 1 || y + GreedVaultLayout.SIZE_Y >= context.world().getTopY()) return Optional.empty();

        BlockPos origin = corner.withY(y);
        return Optional.of(new StructurePosition(center.withY(y), collector ->
                collector.addPiece(new GreedVaultPiece(context.structureTemplateManager(), origin, rotation))));
    }

    /** The first free block above the ground. */
    private static int height(Context context, int x, int z) {
        return context.chunkGenerator().getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.GREED_VAULT;
    }
}
