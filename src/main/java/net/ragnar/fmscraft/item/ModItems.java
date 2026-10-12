package net.ragnar.fmscraft.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {
    // <sin>_fragment: 5 in a plus shape crafts the key
    public static final Map<Sin, Item> FRAGMENTS = new EnumMap<>(Sin.class);
    // <sin>_key: single use, opens that sin's portal structure
    public static final Map<Sin, Item> KEYS = new EnumMap<>(Sin.class);
    // soul_of_<sin>: dropped by the boss, goes into the True Ending structure
    public static final Map<Sin, Item> SOULS = new EnumMap<>(Sin.class);

    static {
        for (Sin sin : Sin.values()) {
            FRAGMENTS.put(sin, register(sin.id() + "_fragment",
                    new Item(new Item.Settings().rarity(Rarity.UNCOMMON))));
            KEYS.put(sin, register(sin.id() + "_key",
                    new SinKeyItem(sin, new Item.Settings().maxCount(1).rarity(Rarity.RARE))));
            SOULS.put(sin, register("soul_of_" + sin.id(),
                    new Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()
                            .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true))));
        }
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(FmsCraft.MOD_ID, name), item);
    }

    public static void initialize() {
    }
}
