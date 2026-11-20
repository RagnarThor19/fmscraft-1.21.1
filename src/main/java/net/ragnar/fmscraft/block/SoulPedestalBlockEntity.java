package net.ragnar.fmscraft.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Holds nothing: it's only there so the client can draw the pedestal's beam (SoulPedestalRenderer). */
public class SoulPedestalBlockEntity extends BlockEntity {
    public SoulPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SOUL_PEDESTAL_ENTITY, pos, state);
    }
}
