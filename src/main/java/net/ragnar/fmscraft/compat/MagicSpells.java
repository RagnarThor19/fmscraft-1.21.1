package net.ragnar.fmscraft.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Bits of Ragnars Magic Mod that RAGGIMEL uses or has to stand up to, found by id (entities, items) or by name
 * (the sleep code). If the mod is missing, everything here quietly reports "not available".
 * <ul>
 *   <li>His sleep darts are the mod's own Sleep Dart, so being put to sleep looks and works the same as from a tome.</li>
 *   <li>He can't be put to sleep or carried off in a bubble himself: he wakes up / bursts it at once.</li>
 *   <li>He holds the mod's staffs.</li>
 * </ul>
 */
public final class MagicSpells {
    public static final Identifier SLEEP_DART = Identifier.of(MagicTomes.MOD_ID, "sleep_dart");
    public static final Identifier SLEEP_POTION = Identifier.of(MagicTomes.MOD_ID, "sleep_potion");
    public static final Identifier BUBBLE = Identifier.of(MagicTomes.MOD_ID, "bubble");
    public static final Identifier DIAMOND_STAFF = Identifier.of(MagicTomes.MOD_ID, "diamond_staff");
    public static final Identifier NETHERITE_STAFF = Identifier.of(MagicTomes.MOD_ID, "netherite_staff");
    private static final String SLEEP_CLASS = "net.ragnar.ragnarsmagicmod.sleep.Sleep";

    private static boolean looked;
    private static Method isSleepy, wake;

    private MagicSpells() {
    }

    public static Optional<EntityType<?>> entity(Identifier id) {
        return Registries.ENTITY_TYPE.getOrEmpty(id);
    }

    public static boolean is(Entity entity, Identifier type) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).equals(type);
    }

    public static ItemStack item(Identifier id) {
        return Registries.ITEM.getOrEmpty(id).map(Item::getDefaultStack).orElse(ItemStack.EMPTY);
    }

    /** Whether {@code entity} is drowsy or asleep from a sleep dart or potion. */
    public static boolean isSleepy(Entity entity) {
        lookUp();
        if (isSleepy == null) return false;
        try {
            return (boolean) isSleepy.invoke(null, entity);
        } catch (ReflectiveOperationException | ClassCastException e) {
            isSleepy = null;
            return false;
        }
    }

    /** Wakes {@code entity} up with the mod's little pop. */
    public static void wake(LivingEntity entity) {
        lookUp();
        if (wake == null) return;
        try {
            wake.invoke(null, entity, true);
        } catch (ReflectiveOperationException e) {
            wake = null;
        }
    }

    private static synchronized void lookUp() {
        if (looked) return;
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> sleep = Class.forName(SLEEP_CLASS, true, MagicSpells.class.getClassLoader());
            isSleepy = sleep.getMethod("isSleepy", Entity.class);
            wake = sleep.getMethod("wake", LivingEntity.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's sleep code ({}); RAGGIMEL can be put to sleep", e.toString());
        }
    }
}
