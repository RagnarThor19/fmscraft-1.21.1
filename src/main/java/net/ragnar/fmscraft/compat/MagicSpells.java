package net.ragnar.fmscraft.compat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Bits of Ragnars Magic Mod that RAGGIMEL uses or has to stand up to, found by id (entities, items) or by name
 * (the sleep code). If the mod is missing, everything here quietly reports "not available".
 * <ul>
 *   <li>His sleep darts are the mod's own Sleep Dart, so being put to sleep looks and works the same as from a tome.</li>
 *   <li>He can't be put to sleep or carried off in a bubble himself: he wakes up / bursts it at once.</li>
 *   <li>He holds the mod's staffs.</li>
 *   <li>LalliLarge's Jump Slam shakes the ground with the Tome of Earthquake's own visuals: the boss sends the two
 *       packets its client half draws from, so the fissures, the heaving blocks and the flung earth are exactly the
 *       tome's.</li>
 * </ul>
 */
public final class MagicSpells {
    public static final Identifier SLEEP_DART = Identifier.of(MagicTomes.MOD_ID, "sleep_dart");
    public static final Identifier SLEEP_POTION = Identifier.of(MagicTomes.MOD_ID, "sleep_potion");
    public static final Identifier BUBBLE = Identifier.of(MagicTomes.MOD_ID, "bubble");
    public static final Identifier DIAMOND_STAFF = Identifier.of(MagicTomes.MOD_ID, "diamond_staff");
    public static final Identifier NETHERITE_STAFF = Identifier.of(MagicTomes.MOD_ID, "netherite_staff");
    public static final Identifier GOLDEN_STAFF = Identifier.of(MagicTomes.MOD_ID, "golden_staff");
    private static final String SLEEP_CLASS = "net.ragnar.ragnarsmagicmod.sleep.Sleep";
    private static final String UPSIDE_DOWN_CLASS = "net.ragnar.ragnarsmagicmod.upsidedown.UpsideDown";

    private static final String QUAKE_CLASS = "net.ragnar.ragnarsmagicmod.earthquake.Earthquake";
    /** How far off people see the quake (the mod's own range). */
    private static final double QUAKE_VIEW = 112;

    private static boolean looked, lookedFlip, lookedQuake;
    private static Constructor<?> quakeCharge, quakeShock;
    private static Method isSleepy, wake, isFlipped, setFlipped;

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

    /** One of the mod's items by its id ({@code "tome_of_the_moon"}), or {@code fallback} without the mod. */
    public static ItemStack itemOr(String id, Item fallback) {
        ItemStack stack = item(Identifier.of(MagicTomes.MOD_ID, id));
        return stack.isEmpty() ? new ItemStack(fallback) : stack;
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

    /**
     * Flips {@code player}'s gravity with the mod's Tome of Upside Down (their view rolls over, everyone sees them
     * hanging upside down), or back. False if the mod (or its upside down) isn't there.
     */
    public static boolean setFlipped(net.minecraft.entity.player.PlayerEntity player, boolean flipped) {
        lookUpFlip();
        if (setFlipped == null) return false;
        try {
            setFlipped.invoke(null, player, flipped);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            setFlipped = null;
            return false;
        }
    }

    /** Whether {@code entity}'s gravity is flipped by the Tome of Upside Down. */
    public static boolean isFlipped(Entity entity) {
        lookUpFlip();
        if (isFlipped == null) return false;
        try {
            return (boolean) isFlipped.invoke(null, entity);
        } catch (ReflectiveOperationException | ClassCastException e) {
            isFlipped = null;
            return false;
        }
    }

    /**
     * Starts the Tome of Earthquake's charge visuals round {@code center} (fissures cracking out over the ground), keyed
     * by {@code casterId}. False (nothing sent) without the mod or its earthquake.
     */
    public static boolean earthquakeCharge(ServerWorld world, int casterId, Vec3d center, long seed) {
        lookUpQuake();
        if (quakeCharge == null) return false;
        try {
            sendNear(world, center, (CustomPayload) quakeCharge.newInstance(casterId, center.toVector3f(), seed));
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            quakeCharge = null;
            return false;
        }
    }

    /** The quake {@code casterId} charged goes off: {@code pulse} 0 is the shock, 1 and 2 the aftershocks, -1 calls it off. */
    public static boolean earthquakeShock(ServerWorld world, int casterId, Vec3d center, int pulse) {
        lookUpQuake();
        if (quakeShock == null) return false;
        try {
            sendNear(world, center, (CustomPayload) quakeShock.newInstance(casterId, pulse));
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            quakeShock = null;
            return false;
        }
    }

    /** Whether the Tome of Earthquake's visuals are there to use. */
    public static boolean hasEarthquake() {
        lookUpQuake();
        return quakeCharge != null && quakeShock != null;
    }

    private static void sendNear(ServerWorld world, Vec3d at, CustomPayload payload) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.getPos().squaredDistanceTo(at) < QUAKE_VIEW * QUAKE_VIEW && ServerPlayNetworking.canSend(p, payload.getId())) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    private static synchronized void lookUpQuake() {
        if (lookedQuake) return;
        lookedQuake = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> charge = Class.forName(QUAKE_CLASS + "$ChargePayload", true, MagicSpells.class.getClassLoader());
            Class<?> shock = Class.forName(QUAKE_CLASS + "$ShockPayload", true, MagicSpells.class.getClassLoader());
            quakeCharge = charge.getConstructor(int.class, org.joml.Vector3f.class, long.class);
            quakeShock = shock.getConstructor(int.class, int.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's Tome of Earthquake ({}); LalliLarge's quake uses particles", e.toString());
        }
    }

    private static synchronized void lookUpFlip() {
        if (lookedFlip) return;
        lookedFlip = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> flip = Class.forName(UPSIDE_DOWN_CLASS, true, MagicSpells.class.getClassLoader());
            isFlipped = flip.getMethod("isFlipped", Entity.class);
            setFlipped = flip.getMethod("setFlipped", net.minecraft.entity.player.PlayerEntity.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's Tome of Upside Down ({}); GUMMSS throws players up himself", e.toString());
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
