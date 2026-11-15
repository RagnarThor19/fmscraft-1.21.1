package net.ragnar.fmscraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * CONEMAN's traffic cone, set down on the ground. Its outline is the cone's own cubes (models/block/traffic_cone.json),
 * tip and all, a little over a block tall.
 */
public class TrafficConeBlock extends Block {
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1, 0, 1, 15, 1.5, 15),
            Block.createCuboidShape(2.5, 1.5, 2.5, 13.5, 5, 13.5),
            Block.createCuboidShape(3, 5, 3, 13, 8.5, 13),
            Block.createCuboidShape(3.5, 8.5, 3.5, 12.5, 12, 12.5),
            Block.createCuboidShape(4.5, 12, 4.5, 11.5, 14.5, 11.5),
            Block.createCuboidShape(5.5, 14.5, 5.5, 10.5, 17.5, 10.5),
            Block.createCuboidShape(6.5, 17.5, 6.5, 9.5, 19.5, 9.5));

    public TrafficConeBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
