package net.ragnar.fmscraft.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.reward.WheelTier;

import java.util.List;

/**
 * {@code "function": "fmscraft:random_tome", "tier": "advanced"}: replaces the item with a random Ragnars Magic Mod
 * tome of that tier. Gives nothing if the magic mod isn't installed.
 */
public class RandomTomeLootFunction extends ConditionalLootFunction {
    public static final MapCodec<RandomTomeLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> addConditionsField(instance)
            .and(WheelTier.CODEC.fieldOf("tier").forGetter(function -> function.tier))
            .apply(instance, RandomTomeLootFunction::new));

    private final WheelTier tier;

    public RandomTomeLootFunction(List<LootCondition> conditions, WheelTier tier) {
        super(conditions);
        this.tier = tier;
    }

    @Override
    protected ItemStack process(ItemStack stack, LootContext context) {
        List<Item> tomes = MagicTomes.of(tier);
        if (tomes.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(tomes.get(context.getRandom().nextInt(tomes.size())));
    }

    @Override
    public LootFunctionType<RandomTomeLootFunction> getType() {
        return ModLoot.RANDOM_TOME;
    }
}
