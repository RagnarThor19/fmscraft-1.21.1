package net.ragnar.fmscraft.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.reward.WheelTier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds Ragnars Magic Mod's tomes, grouped by tier. The magic mod isn't a compile dependency, so its TomeItem
 * class and getTier() are looked up by name. If the mod is missing or renames them, every pool is just empty.
 */
public final class MagicTomes {
    public static final String MOD_ID = "ragnarsmagicmod";
    private static final String TOME_CLASS = "net.ragnar.ragnarsmagicmod.item.custom.TomeItem";

    private static Map<WheelTier, List<Item>> cache;

    private MagicTomes() {
    }

    public static synchronized Map<WheelTier, List<Item>> byTier() {
        if (cache == null) cache = scan();
        return cache;
    }

    public static List<Item> of(WheelTier tier) {
        return byTier().get(tier);
    }

    public static boolean available() {
        return byTier().values().stream().anyMatch(list -> !list.isEmpty());
    }

    private static Map<WheelTier, List<Item>> scan() {
        Map<WheelTier, List<Item>> tomes = new EnumMap<>(WheelTier.class);
        for (WheelTier tier : WheelTier.values()) tomes.put(tier, new ArrayList<>());
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return freeze(tomes);

        try {
            Class<?> tomeClass = Class.forName(TOME_CLASS, false, MagicTomes.class.getClassLoader());
            Method getTier = tomeClass.getMethod("getTier");
            for (Item item : Registries.ITEM) {
                if (!tomeClass.isInstance(item)) continue;
                String tierName = ((Enum<?>) getTier.invoke(item)).name();
                try {
                    tomes.get(WheelTier.valueOf(tierName)).add(item);
                } catch (IllegalArgumentException unknownTier) {
                    FmsCraft.LOGGER.warn("Unknown tome tier {} on {}", tierName, Registries.ITEM.getId(item));
                }
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            FmsCraft.LOGGER.warn("Couldn't read Ragnars Magic Mod tomes; the tome wheel is disabled", e);
        }
        FmsCraft.LOGGER.info("Tome wheel pools: {} beginner, {} advanced, {} master",
                tomes.get(WheelTier.BEGINNER).size(), tomes.get(WheelTier.ADVANCED).size(), tomes.get(WheelTier.MASTER).size());
        return freeze(tomes);
    }

    private static Map<WheelTier, List<Item>> freeze(Map<WheelTier, List<Item>> tomes) {
        tomes.replaceAll((tier, list) -> Collections.unmodifiableList(list));
        return Collections.unmodifiableMap(tomes);
    }
}
