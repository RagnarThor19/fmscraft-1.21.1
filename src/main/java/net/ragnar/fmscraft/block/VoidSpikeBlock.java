package net.ragnar.fmscraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Thickness;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;

/**
 * MUGEN's void spikes: pointed dripstone's shape in the black of the void (tools/textures/mugen_textures.py). Never
 * placed in the world, and no item: his Void Spikes show it in block displays, one per section, base to tip.
 */
public class VoidSpikeBlock extends Block {
    public static final EnumProperty<Thickness> THICKNESS = Properties.THICKNESS;

    public VoidSpikeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(THICKNESS, Thickness.TIP));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(THICKNESS);
    }
}
