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
 *   <li>MUGEN's shadow (his entrance, and his Tome of Unseen Hands) is the Tome of Unseen Hands' own: the boss sends
 *       the two packets its client half draws the pool and the hands from.</li>
 *   <li>LEONARD's zaps (and his chain lightning) are the Tome of Zap's own bolts: the boss sends the packet its client
 *       half draws them from, with himself as the caster, and plays its cast sound.</li>
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

    private static final String HANDS_CLASS = "net.ragnar.ragnarsmagicmod.shadowhands.ShadowHands";
    /** How far off people see the shadow (the mod's own range). */
    private static final double HANDS_VIEW = 128;

    private static final String ZAP_CLASS = "net.ragnar.ragnarsmagicmod.network.ZapPayload";
    /** How far off people see a zap (the mod's own range). */
    private static final double ZAP_VIEW = 96;
    public static final int ZAP_MISS = 0, ZAP_BLOCK = 1, ZAP_ENTITY = 2;

    public static final Identifier ROCK = Identifier.of(MagicTomes.MOD_ID, "rock");
    public static final Identifier BOULDER = Identifier.of(MagicTomes.MOD_ID, "boulder");
    private static final String BOULDER_CLASS = "net.ragnar.ragnarsmagicmod.boulders.BoulderEntity";
    private static final String SHAKE_CLASS = "net.ragnar.ragnarsmagicmod.network.ShakePayload";
    /** The Tome of Boulders' stages: held overhead by us (a stage the boulder itself does nothing in), then flying. */
    private static final int BOULDER_HELD = 3, BOULDER_FLYING = 1;

    private static boolean looked, lookedFlip, lookedQuake, lookedHands, lookedZap, lookedBoulder, lookedShake;
    private static Method boulderStage, shakeAround;
    private static java.lang.reflect.Field boulderOwner;
    private static Constructor<?> zapPayload;
    private static Constructor<?> quakeCharge, quakeShock, handsStart, handsGrab;
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

    /**
     * Starts the Tome of Unseen Hands' pool creeping out from {@code center} to {@code radius}, keyed by {@code id}
     * (keep ours clear of the mod's own small numbers). False (nothing sent) without the mod or its hands.
     */
    public static boolean shadowPool(ServerWorld world, int id, Vec3d center, float radius) {
        lookUpHands();
        if (handsStart == null) return false;
        try {
            sendNear(world, center, (CustomPayload) handsStart.newInstance(id, center.toVector3f(), radius), HANDS_VIEW);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            handsStart = null;
            return false;
        }
    }

    /** The hands of pool {@code id} burst up and close on these entities (by network id). False without the mod. */
    public static boolean shadowGrab(ServerWorld world, int id, Vec3d center, java.util.List<Integer> victims) {
        lookUpHands();
        if (handsGrab == null) return false;
        try {
            sendNear(world, center, (CustomPayload) handsGrab.newInstance(id, victims), HANDS_VIEW);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            handsGrab = null;
            return false;
        }
    }

    /** Whether the Tome of Unseen Hands' visuals are there to use. */
    public static boolean hasShadowHands() {
        lookUpHands();
        return handsStart != null && handsGrab != null;
    }

    /**
     * A Tome of Zap bolt from {@code from} to {@code to}, drawn by the mod: {@code casterId} is the entity it comes out of
     * (its hand, if the client has it; -1 to start at {@code from}), {@code hit} one of ZAP_MISS/BLOCK/ENTITY and
     * {@code normal} the face it struck. False (nothing sent) without the mod.
     */
    public static boolean zap(ServerWorld world, int casterId, Vec3d from, Vec3d to, int hit, Vec3d normal) {
        lookUpZap();
        if (zapPayload == null) return false;
        try {
            CustomPayload payload = (CustomPayload) zapPayload.newInstance(casterId, from.toVector3f(), to.toVector3f(), hit, normal.toVector3f());
            for (ServerPlayerEntity p : world.getPlayers()) {
                if ((p.getPos().squaredDistanceTo(from) < ZAP_VIEW * ZAP_VIEW || p.getPos().squaredDistanceTo(to) < ZAP_VIEW * ZAP_VIEW)
                        && ServerPlayNetworking.canSend(p, payload.getId())) {
                    ServerPlayNetworking.send(p, payload);
                }
            }
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            zapPayload = null;
            return false;
        }
    }

    /** The Tome of Zap's cast sound, or null without the mod. */
    public static net.minecraft.sound.SoundEvent zapSound() {
        return Registries.SOUND_EVENT.getOrEmpty(Identifier.of(MagicTomes.MOD_ID, "zap_cast")).orElse(null);
    }

    // ---- RICKY's earth tomes ----

    /**
     * A Tome of Rocks rock (the mod's own: its tumbling lump of stone, its hit and its spatter), thrown by {@code owner}
     * from {@code from} at {@code velocity}. Null (nothing thrown) without the mod.
     */
    public static Entity rock(ServerWorld world, LivingEntity owner, Vec3d from, Vec3d velocity) {
        Entity rock = entity(ROCK).map(type -> type.create(world)).orElse(null);
        if (!(rock instanceof net.minecraft.entity.projectile.ProjectileEntity projectile)) return null;
        projectile.setOwner(owner);
        projectile.refreshPositionAndAngles(from.x, from.y, from.z, owner.getYaw(), owner.getPitch());
        projectile.setVelocity(velocity);
        world.spawnEntity(projectile);
        return projectile;
    }

    /**
     * A Tome of Boulders boulder (the mod's own: its rock model, its flight, its slam, its roll), hanging at {@code at}
     * until {@link #throwBoulder}. It never hurts {@code ownerId}. Null without the mod (or its boulders).
     */
    public static Entity boulder(ServerWorld world, java.util.UUID ownerId, Vec3d at) {
        lookUpBoulder();
        if (boulderStage == null || boulderOwner == null) return null;
        Entity boulder = entity(BOULDER).map(type -> type.create(world)).orElse(null);
        if (boulder == null) return null;
        try {
            boulderOwner.set(boulder, ownerId);
            boulderStage.invoke(boulder, BOULDER_HELD);
        } catch (ReflectiveOperationException | ClassCastException e) {
            boulderStage = null;
            return null;
        }
        boulder.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        boulder.setNoGravity(true);
        world.spawnEntity(boulder);
        return boulder;
    }

    /** The boulder from {@link #boulder} is hurled at {@code velocity}; from here it's the tome's own. */
    public static boolean throwBoulder(Entity boulder, Vec3d velocity) {
        lookUpBoulder();
        if (boulderStage == null) return false;
        try {
            boulderStage.invoke(boulder, BOULDER_FLYING);
        } catch (ReflectiveOperationException | ClassCastException e) {
            boulderStage = null;
            return false;
        }
        boulder.setNoGravity(false);
        boulder.setVelocity(velocity);
        boulder.velocityDirty = true;
        return true;
    }

    /** The mod's camera shake for everyone near {@code at}: full within {@code radius}, fading out to twice that. */
    public static boolean shake(ServerWorld world, Vec3d at, double radius, float strength, int ticks) {
        lookUpShake();
        if (shakeAround == null) return false;
        try {
            shakeAround.invoke(null, world, at, radius, strength, ticks);
            return true;
        } catch (ReflectiveOperationException e) {
            shakeAround = null;
            return false;
        }
    }

    private static synchronized void lookUpBoulder() {
        if (lookedBoulder) return;
        lookedBoulder = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> boulder = Class.forName(BOULDER_CLASS, true, MagicSpells.class.getClassLoader());
            boulderStage = boulder.getDeclaredMethod("setStage", int.class);
            boulderStage.setAccessible(true);
            boulderOwner = boulder.getDeclaredField("ownerId");
            boulderOwner.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            boulderStage = null;
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's Tome of Boulders ({}); RICKY's boulders are his own", e.toString());
        }
    }

    private static synchronized void lookUpShake() {
        if (lookedShake) return;
        lookedShake = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> shake = Class.forName(SHAKE_CLASS, true, MagicSpells.class.getClassLoader());
            shakeAround = shake.getMethod("around", ServerWorld.class, Vec3d.class, double.class, float.class, int.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's camera shake ({})", e.toString());
        }
    }

    private static synchronized void lookUpZap() {
        if (lookedZap) return;
        lookedZap = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> zap = Class.forName(ZAP_CLASS, true, MagicSpells.class.getClassLoader());
            zapPayload = zap.getConstructor(int.class, org.joml.Vector3f.class, org.joml.Vector3f.class, int.class, org.joml.Vector3f.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's Tome of Zap ({}); LEONARD's zaps use particles", e.toString());
        }
    }

    private static void sendNear(ServerWorld world, Vec3d at, CustomPayload payload) {
        sendNear(world, at, payload, QUAKE_VIEW);
    }

    private static void sendNear(ServerWorld world, Vec3d at, CustomPayload payload, double range) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.getPos().squaredDistanceTo(at) < range * range && ServerPlayNetworking.canSend(p, payload.getId())) {
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

    private static synchronized void lookUpHands() {
        if (lookedHands) return;
        lookedHands = true;
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) return;
        try {
            Class<?> start = Class.forName(HANDS_CLASS + "$StartPayload", true, MagicSpells.class.getClassLoader());
            Class<?> grab = Class.forName(HANDS_CLASS + "$GrabPayload", true, MagicSpells.class.getClassLoader());
            handsStart = start.getConstructor(int.class, org.joml.Vector3f.class, float.class);
            handsGrab = grab.getConstructor(int.class, java.util.List.class);
        } catch (ReflectiveOperationException e) {
            FmsCraft.LOGGER.warn("Couldn't find Ragnars Magic Mod's Tome of Unseen Hands ({}); MUGEN's shadow uses particles", e.toString());
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
