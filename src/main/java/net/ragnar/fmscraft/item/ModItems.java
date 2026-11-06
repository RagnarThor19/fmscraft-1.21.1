package net.ragnar.fmscraft.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.ragnar.fmscraft.block.ModBlocks;
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

    // Creative-only, for building and testing; in survival it only exists in the sin structures
    public static final SinPortalFrameItem SIN_PORTAL_FRAME = (SinPortalFrameItem) register("sin_portal_frame",
            new SinPortalFrameItem(ModBlocks.SIN_PORTAL_FRAME, new Item.Settings().rarity(Rarity.EPIC)));

    // What MUGEN throws, drawn by item displays in his fight. Not obtainable, and in no creative tab
    public static final Item KUNAI = register("kunai", new Item(new Item.Settings()));
    public static final Item SHURIKEN = register("shuriken", new Item(new Item.Settings()));
    public static final Item SHADOW_ORB = register("shadow_orb", new Item(new Item.Settings()));
    public static final Item SHADOW_CHARGE = register("shadow_charge", new Item(new Item.Settings()));

    // LEONARD's lightning bolt: held, thrown and flung back by him, drawn by item displays. Not obtainable
    public static final Item LIGHTNING_BOLT = register("lightning_bolt", new Item(new Item.Settings()));
    /** The traffic cone that makes him CONEMAN. He drops it when he's beaten; it's worn on the head like a carved pumpkin. */
    public static final Item TRAFFIC_CONE = register("traffic_cone", new TrafficConeItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(FmsCraft.MOD_ID, name), item);
    }

    public static void initialize() {
    }
}
