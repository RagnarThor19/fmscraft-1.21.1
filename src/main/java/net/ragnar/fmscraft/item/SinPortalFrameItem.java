package net.ragnar.fmscraft.item;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import org.jetbrains.annotations.Nullable;

/**
 * One item for every sin's frame: the sin rides along in the stack's block state component, so the placed block
 * gets it, the name reads "Envy Portal Frame" and the item model shows that sin's colors.
 */
public class SinPortalFrameItem extends BlockItem {
    /** Item model predicate: 0 for no sin, (ordinal + 1) / 10 for a sin. */
    public static final Identifier SIN_PREDICATE = Identifier.of(FmsCraft.MOD_ID, "sin");

    public SinPortalFrameItem(Block block, Settings settings) {
        super(block, settings);
    }

    public ItemStack stack(Sin sin) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(SinPortalFrameBlock.SIN, sin));
        return stack;
    }

    @Nullable
    public static Sin sinOf(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT).getValue(SinPortalFrameBlock.SIN);
    }

    @Override
    public Text getName(ItemStack stack) {
        Sin sin = sinOf(stack);
        return sin == null ? super.getName(stack) : Text.translatable(getTranslationKey() + ".sin", sin.displayName());
    }
}
