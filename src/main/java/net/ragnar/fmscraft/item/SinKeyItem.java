package net.ragnar.fmscraft.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.ragnar.fmscraft.sin.Sin;

import java.util.List;

/** A key forged from five fragments. Opens its sin's portal structure once. */
public class SinKeyItem extends Item {
    private final Sin sin;

    public SinKeyItem(Sin sin, Settings settings) {
        super(settings);
        this.sin = sin;
    }

    public Sin getSin() {
        return sin;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fmscraft.sin_key.tooltip", sin.displayName()).formatted(Formatting.GRAY));
    }
}
