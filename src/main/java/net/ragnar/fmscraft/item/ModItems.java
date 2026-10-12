package net.ragnar.fmscraft.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModItems {
    // Add items here, e.g.:
    // public static final Item EXAMPLE = register("example", new Item(new Item.Settings()));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(FmsCraft.MOD_ID, name), item);
    }

    public static void initialize() {
    }
}
