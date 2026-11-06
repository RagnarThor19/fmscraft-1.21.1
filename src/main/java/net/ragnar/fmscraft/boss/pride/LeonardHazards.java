package net.ragnar.fmscraft.boss.pride;

import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.item.ModItems;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What LEONARD (and CONEMAN) leaves in the world: things that outlive the move that made them and tick on their own
 * until they're spent. They only ever hurt the players fighting him, and never break, place or burn a block: every bolt
 * of lightning is the game's own, but cosmetic (no fire), and the damage is ours.
 */
final class LeonardHazards {
    static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1f, 0.86f, 0.25f), 1.3f);
    static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1f, 1f, 0.92f), 1.6f);
    static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.55f, 0.8f, 1f), 1.2f);
    static final DustParticleEffect STORM = new DustParticleEffect(new Vector3f(0.16f, 0.17f, 0.24f), 2.2f);
    static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, 0.25f, 0.15f), 1.5f);
    private static final Identifier PARALYSIS = Identifier.of(FmsCraft.MOD_ID, "paralysis");
    /** Item sprites in ModelTransformationMode.NONE point their tip this way (as MUGEN's kunai). */
    private static final Vector3f TIP_LOCAL = new Vector3f(-1, 1, 0).normalize();

    private LeonardHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it's spent. */
        abstract boolean tick(ServerWorld world, LeonardEntity boss);

        /** Taken away early (he died, the fight ended): clean up anything it put in the world. */
        void remove() {
        }
    }

    // ---- Helpers ----

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    /** A bolt of the game's own lightning, cosmetic: the flash, the thunder, no fire. */
    static void lightning(ServerWorld world, Vec3d at) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(at);
        bolt.setCosmetic(true);
        world.spawnEntity(bolt);
    }

    static DisplayEntity.ItemDisplayEntity itemDisplay(ServerWorld world, ItemStack stack, Vec3d at) {
        DisplayEntity.ItemDisplayEntity d = EntityType.ITEM_DISPLAY.create(world);
        if (d == null) return null;
        d.setItemStack(stack);
        d.setTransformationMode(ModelTransformationMode.NONE);
        d.setTeleportDuration(1);
        d.setViewRange(6f);
        d.setBrightness(new Brightness(15, 15));
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        world.spawnEntity(d);
        return d;
    }

    static DisplayEntity.BlockDisplayEntity blockDisplay(ServerWorld world, net.minecraft.block.BlockState state, Vec3d at) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setTeleportDuration(1);
        d.setViewRange(6f);
        d.setBrightness(new Brightness(15, 15));
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        world.spawnEntity(d);
        return d;
    }

    static void place(DisplayEntity d, Vec3d at, AffineTransformation transformation) {
        d.setPosition(at.x, at.y, at.z);
        if (transformation != null) d.setTransformation(transformation);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(1);
    }

    /** The bolt sprite pointing along {@code dir}, spun {@code spin} radians about that line, {@code scale} times its size. */
    static AffineTransformation pointing(Vec3d dir, float spin, float scale) {
        Vector3f d = dir.lengthSquared() > 1e-6 ? dir.normalize().toVector3f() : new Vector3f(0, 0, 1);
        Quaternionf q = new Quaternionf().rotationAxis(spin, d).mul(new Quaternionf().rotationTo(TIP_LOCAL, d));
        return new AffineTransformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** The bolt sprite lying flat, whirling: {@code spin} radians round, {@code scale} times its size. */
    static AffineTransformation whirling(float spin, float scale) {
        Quaternionf q = new Quaternionf().rotationY(spin).rotateX(MathHelper.HALF_PI);
        return new AffineTransformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double spin) {
        for (int i = 0; i < points; i++) {
            double a = i * MathHelper.TAU / points + spin;
            world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.15, center.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    /** A crackling line of sparks from {@code a} to {@code b}, jittered like an arc. */
    static void arc(ServerWorld world, Vec3d a, Vec3d b, ParticleEffect particle, double step, double jitter) {
        double len = a.distanceTo(b);
        int n = Math.max(2, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.lerp(b, i / (double) n);
            double j = (i == 0 || i == n) ? 0 : jitter;
            world.spawnParticles(particle, p.x + (world.random.nextDouble() - 0.5) * j, p.y + (world.random.nextDouble() - 0.5) * j,
                    p.z + (world.random.nextDouble() - 0.5) * j, 1, 0, 0, 0, 0);
        }
    }

    /** Distance from {@code p} to the segment {@code a}-{@code b}, flat (ignoring height). */
    static double flatToSegment(Vec3d p, Vec3d a, Vec3d b) {
        double ax = b.x - a.x, az = b.z - a.z, len2 = ax * ax + az * az;
        double t = len2 < 1e-9 ? 0 : MathHelper.clamp(((p.x - a.x) * ax + (p.z - a.z) * az) / len2, 0, 1);
        return Math.sqrt(MathHelper.square(p.x - (a.x + ax * t)) + MathHelper.square(p.z - (a.z + az * t)));
    }

    /** The first fighter whose box (grown by {@code grow}) the segment {@code from}-{@code to} passes through, or null. */
    static ServerPlayerEntity firstHit(LeonardEntity boss, Vec3d from, Vec3d to, double grow, Set<UUID> skip) {
        ServerPlayerEntity hit = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : boss.fighters()) {
            if (skip != null && skip.contains(p.getUuid())) continue;
            var clip = p.getBoundingBox().expand(grow).raycast(from, to);
            if (clip.isEmpty() && !p.getBoundingBox().expand(grow).contains(from)) continue;
            double d = clip.map(from::squaredDistanceTo).orElse(0.0);
            if (d < best) {
                best = d;
                hit = p;
            }
        }
        return hit;
    }

    static BlockHitResult wall(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
    }

    /** A burst of lightning where something lands: the bolt, a flash of sparks, and {@code damage} to whoever's within {@code radius}. */
    static void burst(ServerWorld world, LeonardEntity boss, Vec3d at, double radius, float damage, boolean bolt) {
        if (bolt) lightning(world, at);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 40, radius * 0.4, 0.5, radius * 0.4, 0.4);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        world.spawnParticles(WHITE, at.x, at.y + 0.3, at.z, 20, radius * 0.4, 0.2, radius * 0.4, 0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.6f, 1.2f);
        if (damage <= 0) return;
        for (ServerPlayerEntity p : boss.fighters()) {
            if (flatDistance(p.getPos(), at) <= radius && Math.abs(p.getY() - at.y) < 4) {
                if (boss.shock(p, damage)) boss.launch(p, p.getPos().subtract(at), 0.6, 0.45);
            }
        }
    }

    // ---- Paralysed: held where you stand, sparks crawling over you ----

    static final class Paralysis extends Hazard {
        final ServerPlayerEntity player;
        int ticks;

        Paralysis(ServerPlayerEntity player, int ticks) {
            this.player = player;
            this.ticks = ticks;
            hold(player, true);
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (!player.isAlive() || player.isRemoved() || age >= ticks) {
                remove();
                return false;
            }
            player.setVelocity(0, Math.min(0, player.getVelocity().y), 0);
            player.velocityModified = true;
            if (age % 2 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 0.3, 0.6, 0.3, 0.05);
            }
            if (age % 8 == 0) world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, SoundCategory.HOSTILE, 0.6f, 1.8f);
            return true;
        }

        @Override
        void remove() {
            hold(player, false);
        }

        static void hold(ServerPlayerEntity player, boolean on) {
            for (EntityAttributeInstance a : new EntityAttributeInstance[]{player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH)}) {
                if (a == null) continue;
                if (on && !a.hasModifier(PARALYSIS)) a.addTemporaryModifier(new EntityAttributeModifier(PARALYSIS, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                else if (!on) a.removeModifier(PARALYSIS);
            }
        }
    }

    // ---- Discharge (and the Cone Slam): a ring of lightning rolling out along the floor. Jump it ----

    /**
     * A ring rushing out along the floor. One that hurts is a solid band, half a block high, of glowing yellow glass
     * round an ochre froglight core: {@link #SEGMENTS} block displays, each set once to where it starts and once to
     * where it ends, the client sliding it out smoothly between (each segment's offset from the middle is in its
     * transformation, so one interpolation carries it). No particles: you see it coming from across the plaza. One that
     * only throws people back (his landing, the crowning, the Pull's blast) is drawn in sparks instead, so a solid band
     * always means "jump this".
     */
    static final class Ring extends Hazard {
        static final int SEGMENTS = 40;
        /** The glass band, and the bright core inside it. */
        static final float SHELL_HEIGHT = 0.5f, SHELL_WIDTH = 0.42f, CORE_HEIGHT = 0.3f, CORE_WIDTH = 0.16f;
        final Vec3d center;
        final double speed, max;
        final float damage;
        final int paralyze;
        final Set<UUID> settled = new HashSet<>();
        final List<DisplayEntity.BlockDisplayEntity> shell = new ArrayList<>(), core = new ArrayList<>();
        double radius;

        Ring(Vec3d center, double speed, double max, float damage, int paralyze) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.paralyze = paralyze;
        }

        boolean solid() {
            return damage > 0;
        }

        /** One segment of the band at {@code radius}: centred on the circle, lying along it. */
        static AffineTransformation segment(int i, double radius, float height, float width) {
            double a = i * MathHelper.TAU / SEGMENTS;
            float length = (float) Math.max(0.05, MathHelper.TAU * radius / SEGMENTS * 1.08);
            Quaternionf turn = new Quaternionf().rotationY((float) -(a + MathHelper.HALF_PI));
            Vector3f half = new Vector3f(length / 2, 0, width / 2).rotate(turn);
            Vector3f at = new Vector3f((float) (Math.cos(a) * radius), 0.01f, (float) (Math.sin(a) * radius)).sub(half);
            return new AffineTransformation(at, turn, new Vector3f(length, height, width), new Quaternionf());
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            radius += speed;
            if (radius > max) {
                remove();
                return false;
            }
            if (solid()) {
                if (age == 0) {
                    for (int i = 0; i < SEGMENTS; i++) {
                        DisplayEntity.BlockDisplayEntity glass = blockDisplay(world, Blocks.YELLOW_STAINED_GLASS.getDefaultState(), center);
                        DisplayEntity.BlockDisplayEntity light = blockDisplay(world, Blocks.OCHRE_FROGLIGHT.getDefaultState(), center);
                        if (glass != null) {
                            glass.setViewRange(8f);
                            glass.setTransformation(segment(i, radius, SHELL_HEIGHT, SHELL_WIDTH));
                            shell.add(glass);
                        }
                        if (light != null) {
                            light.setViewRange(8f);
                            light.setTransformation(segment(i, radius, CORE_HEIGHT, CORE_WIDTH));
                            core.add(light);
                        }
                    }
                } else if (age == 1) {
                    // Out to its full size, smoothly, at the speed it really travels
                    int left = (int) Math.ceil((max - radius) / speed);
                    for (int i = 0; i < shell.size(); i++) slide(shell.get(i), segment(i, max, SHELL_HEIGHT, SHELL_WIDTH), left);
                    for (int i = 0; i < core.size(); i++) slide(core.get(i), segment(i, max, CORE_HEIGHT, CORE_WIDTH), left);
                }
            } else {
                int points = (int) Math.min(140, 12 + radius * 4.5);
                ring(world, ParticleTypes.ELECTRIC_SPARK, center, radius, points, age * 0.3);
                ring(world, YELLOW, center, radius, points / 2, age * 0.17);
            }
            if (age++ % 6 == 0) world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.HOSTILE, 1.4f, 0.6f + (float) (radius / max));
            if (!solid()) return true;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (settled.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center);
                if (d > radius || Math.abs(p.getY() - center.y) > 3) continue;
                settled.add(p.getUuid());
                // Jumped it, or was inside it when it started
                if (d < radius - speed - 1.0 || !p.isOnGround()) continue;
                if (boss.shock(p, damage)) {
                    boss.paralyze(p, paralyze);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getBodyY(0.5), p.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
                }
            }
            return true;
        }

        private static void slide(DisplayEntity.BlockDisplayEntity d, AffineTransformation to, int ticks) {
            d.setTransformation(to);
            d.setStartInterpolation(0);
            d.setInterpolationDuration(ticks);
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : shell) d.discard();
            for (DisplayEntity.BlockDisplayEntity d : core) d.discard();
            shell.clear();
            core.clear();
        }
    }

    // ---- A strike from the sky: a ring marks the spot, then the bolt falls ----

    static final class Strike extends Hazard {
        final Vec3d at;
        final int delay;
        final double radius;
        final float damage;
        final boolean bolt;

        Strike(Vec3d at, int delay, double radius, float damage, boolean bolt) {
            this.at = at;
            this.delay = delay;
            this.radius = radius;
            this.damage = damage;
            this.bolt = bolt;
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (age < delay) {
                if (age % 2 == 0) ring(world, YELLOW, at, radius, (int) (8 + radius * 6), age * 0.2);
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 2, radius * 0.4, 0.05, radius * 0.4, 0.02);
                if (age == 1) world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 0.5f, 1.9f);
                return true;
            }
            burst(world, boss, at, radius, damage, bolt);
            return false;
        }
    }

    // ---- His bolt, thrown: straight and fast. A strike where it lands, then it's back in his hand ----

    static final class Bolt extends Hazard {
        final DisplayEntity.ItemDisplayEntity display;
        final float damage, scale;
        final double range;
        final boolean returns;
        Vec3d pos, vel;
        double travelled;

        Bolt(ServerWorld world, Vec3d from, Vec3d vel, float damage, double range, float scale, boolean returns) {
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            this.range = range;
            this.scale = scale;
            this.returns = returns;
            this.display = itemDisplay(world, new ItemStack(ModItems.LIGHTNING_BOLT), from);
            if (display != null) display.setTransformation(pointing(vel, 0, scale));
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (display == null) return false;
            Vec3d from = pos, to = pos.add(vel);
            ServerPlayerEntity hitPlayer = firstHit(boss, from, to, 0.5 * scale, null);
            BlockHitResult block = wall(world, from, to);
            boolean wallFirst = block.getType() == HitResult.Type.BLOCK
                    && (hitPlayer == null || from.squaredDistanceTo(block.getPos()) < from.squaredDistanceTo(hitPlayer.getPos()));
            if (wallFirst || travelled > range) {
                return land(world, boss, wallFirst ? block.getPos() : pos, null);
            }
            if (hitPlayer != null) return land(world, boss, hitPlayer.getPos(), hitPlayer);
            pos = to;
            travelled += vel.length();
            place(display, pos, pointing(vel, age * 0.5f, scale));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 3, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(YELLOW, from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0);
            if (age % 4 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.7f, 1.6f);
            return true;
        }

        private boolean land(ServerWorld world, LeonardEntity boss, Vec3d at, ServerPlayerEntity hit) {
            Vec3d ground = boss.ground(at);
            if (hit != null && boss.strike(hit, damage)) boss.launch(hit, vel, 1.1, 0.5);
            // Where it lands, the sky answers
            burst(world, boss, ground, 3.2, damage * 0.45f, true);
            display.discard();
            if (returns) boss.boltReturns();
            return false;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- Boomerang: his bolt whirls out in a loop and comes back to his hand ----

    static final class Boomerang extends Hazard {
        static final int TICKS = 46;
        final DisplayEntity.ItemDisplayEntity display;
        final Vec3d start, dir, side;
        final double reach, swing;
        final float damage;
        final Set<UUID> hitOut = new HashSet<>(), hitBack = new HashSet<>();
        Vec3d pos;

        Boomerang(ServerWorld world, Vec3d start, Vec3d dir, int sideSign, double reach, float damage) {
            this.start = start;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x).multiply(sideSign);
            this.reach = reach;
            this.swing = reach * 0.4;
            this.damage = damage;
            this.pos = start;
            this.display = itemDisplay(world, new ItemStack(ModItems.LIGHTNING_BOLT), start);
            if (display != null) display.setTransformation(whirling(0, 2.2f));
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (display == null) return false;
            double u = Math.min(1, age / (double) TICKS);
            Vec3d hand = boss.handPos(true);
            // An oval loop: out along the throw swinging to one side, back along the other, home to wherever he is now
            Vec3d base = start.lerp(hand, u);
            Vec3d next = base.add(dir.multiply(reach * Math.sin(Math.PI * u))).add(side.multiply(swing * Math.sin(2 * Math.PI * u)));
            if (u >= 1) next = hand;
            Set<UUID> hits = u < 0.5 ? hitOut : hitBack;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hits.contains(p.getUuid())) continue;
                if (p.getBoundingBox().expand(0.9).raycast(pos, next).isPresent() || p.getBoundingBox().expand(0.9).contains(next)) {
                    hits.add(p.getUuid());
                    if (boss.strike(p, damage)) boss.launch(p, next.subtract(pos), 0.9, 0.4);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getBodyY(0.5), p.getZ(), 16, 0.3, 0.5, 0.3, 0.3);
                }
            }
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 3, 0.25, 0.1, 0.25, 0.05);
            world.spawnParticles(YELLOW, pos.x, pos.y, pos.z, 2, 0.3, 0.05, 0.3, 0);
            if (age % 3 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.9f, 1.4f);
            pos = next;
            place(display, pos, whirling(age * 0.9f, 2.2f));
            if (u >= 1) {
                display.discard();
                boss.boltReturns();
                return false;
            }
            return true;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- What a Zoom leaves behind: a line of crackling light along the floor, fading ----

    static final class Trail extends Hazard {
        final Vec3d from, to;
        final int ticks;

        Trail(Vec3d from, Vec3d to, int ticks) {
            this.from = from;
            this.to = to;
            this.ticks = ticks;
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (age % 3 == 0) {
                double step = 0.7 + age * 0.06;
                arc(world, from.add(0, 0.3, 0), to.add(0, 0.3, 0), age < ticks / 2 ? ParticleTypes.ELECTRIC_SPARK : YELLOW, step, 0.4);
            }
            return age < ticks;
        }
    }

    // ---- Tesla Rods (CONEMAN): three great lightning rods driven into the plaza, lightning arcing between them ----

    static final class Rods extends Hazard {
        static final int SETUP = 24, LIVE = 150;
        final List<Vec3d> spots;
        final List<DisplayEntity.BlockDisplayEntity> rods = new ArrayList<>();
        final float damage;
        final java.util.Map<UUID, Integer> lastHit = new java.util.HashMap<>();

        Rods(ServerWorld world, List<Vec3d> spots, float damage) {
            this.spots = spots;
            this.damage = damage;
            for (Vec3d s : spots) {
                DisplayEntity.BlockDisplayEntity d = blockDisplay(world, Blocks.LIGHTNING_ROD.getDefaultState(), s.add(0, 12, 0));
                if (d != null) {
                    d.setTransformation(new AffineTransformation(new Vector3f(-1f, 0, -1f), new Quaternionf(), new Vector3f(2f, 3.2f, 2f), new Quaternionf()));
                    rods.add(d);
                }
            }
        }

        Vec3d top(int i) {
            return spots.get(i).add(0, 1.3, 0);
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (age <= 6) {
                // Driven down out of the sky
                for (int i = 0; i < rods.size(); i++) place(rods.get(i), spots.get(i).add(0, 12 * (1 - age / 6.0), 0), null);
                if (age == 6) {
                    for (Vec3d s : spots) {
                        lightning(world, s);
                        world.spawnParticles(ParticleTypes.EXPLOSION, s.x, s.y + 0.5, s.z, 2, 0.3, 0.2, 0.3, 0);
                        world.playSound(null, s.x, s.y, s.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.0f, 0.7f);
                    }
                }
                return true;
            }
            int n = spots.size();
            boolean live = age > SETUP;
            for (int i = 0; i < n; i++) {
                Vec3d a = top(i), b = top((i + 1) % n);
                if (age % 2 == 0) {
                    if (live) {
                        arc(world, a, b, ParticleTypes.ELECTRIC_SPARK, 0.45, 0.35);
                        arc(world, a, b, YELLOW, 0.9, 0.2);
                    } else {
                        arc(world, a, b, BLUE, 1.6, 0.05);
                    }
                }
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, a.x, a.y + 1.5, a.z, 1, 0.2, 0.4, 0.2, 0.05);
            }
            if (age == SETUP) for (Vec3d s : spots) world.playSound(null, s.x, s.y, s.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.6f, 0.6f);
            if (live && age % 10 == 0) world.playSound(null, spots.get(0).x, spots.get(0).y, spots.get(0).z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 1.8f);
            if (live) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (age - lastHit.getOrDefault(p.getUuid(), -100) < 10) continue;
                    for (int i = 0; i < n; i++) {
                        Vec3d a = top(i), b = top((i + 1) % n);
                        // The arcs hang chest high: no jumping them
                        if (flatToSegment(p.getPos(), a, b) < 0.9 && p.getY() < a.y + 1.2 && p.getY() > a.y - 3) {
                            lastHit.put(p.getUuid(), age);
                            if (boss.shock(p, damage)) {
                                Vec3d mid = a.lerp(b, 0.5);
                                Vec3d away = p.getPos().subtract(mid);
                                boss.launch(p, away, 0.9, 0.35);
                            }
                            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.2f, 1.6f);
                            break;
                        }
                    }
                }
            }
            if (age >= SETUP + LIVE) {
                for (Vec3d s : spots) burst(world, boss, s, 3, damage * 0.75f, true);
                remove();
                return false;
            }
            return true;
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : rods) d.discard();
            rods.clear();
        }
    }

    // ---- Ball Lightning (CONEMAN): slow balls of crackling light that drift after you. A sprint gets you away ----

    static final class Ball extends Hazard {
        static final int LIFE = 170;
        final DisplayEntity.BlockDisplayEntity core;
        final UUID target;
        final float damage;
        final double speed;
        Vec3d pos, vel = Vec3d.ZERO;

        Ball(ServerWorld world, Vec3d from, UUID target, float damage, double speed) {
            this.pos = from;
            this.target = target;
            this.damage = damage;
            this.speed = speed;
            this.core = blockDisplay(world, Blocks.OCHRE_FROGLIGHT.getDefaultState(), from);
            if (core != null) core.setTransformation(spin(0));
        }

        static AffineTransformation spin(float a) {
            Quaternionf q = new Quaternionf().rotationXYZ(a, a * 1.3f, a * 0.7f);
            Vector3f half = new Vector3f(-0.35f, -0.35f, -0.35f).rotate(q);
            return new AffineTransformation(half, q, new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf());
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            if (core == null) return false;
            ServerPlayerEntity p = boss.fighter(target);
            if (p != null && age > 16) {
                Vec3d want = p.getPos().add(0, 1.0, 0).subtract(pos);
                want = want.lengthSquared() > 1e-4 ? want.normalize().multiply(speed) : Vec3d.ZERO;
                // Turns slowly: sidestep it
                vel = vel.multiply(0.86).add(want.multiply(0.14));
            } else if (age <= 16) {
                vel = new Vec3d(0, 0.06, 0);
            }
            pos = pos.add(vel);
            place(core, pos, spin(age * 0.35f));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 4, 0.35, 0.35, 0.35, 0.08);
            world.spawnParticles(YELLOW, pos.x, pos.y, pos.z, 2, 0.4, 0.4, 0.4, 0);
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.2, 0.2, 0.2, 0.01);
            if (age % 12 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.2f, 2.0f);
            for (ServerPlayerEntity q : boss.fighters()) {
                if (q.getBoundingBox().expand(0.6).contains(pos)) {
                    if (boss.shock(q, damage)) boss.paralyze(q, 20);
                    return pop(world);
                }
            }
            if (age >= LIFE) return pop(world);
            return true;
        }

        private boolean pop(ServerWorld world) {
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 30, 0.4, 0.4, 0.4, 0.5);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.5f, 1.5f);
            remove();
            return false;
        }

        @Override
        void remove() {
            if (core != null) core.discard();
        }
    }

    // ---- Munch's beam: a great ring burns on the floor while he gathers it, then the beam. Devastating, slow ----

    static final class MunchBeam extends Hazard {
        final Vec3d target;
        final int charge, fire;
        final double radius;
        final float damage;
        final Set<UUID> struck = new HashSet<>();

        MunchBeam(Vec3d target, int charge, int fire, double radius, float damage) {
            this.target = target;
            this.charge = charge;
            this.fire = fire;
            this.radius = radius;
            this.damage = damage;
        }

        boolean firing() {
            return age >= charge && age < charge + fire;
        }

        @Override
        boolean tick(ServerWorld world, LeonardEntity boss) {
            age++;
            Vec3d face = PrideArena.munchFace(target);
            if (age < charge) {
                double k = age / (double) charge;
                // The ring burns brighter and spins faster as he gathers it; light drawn in to his eyes
                ring(world, k > 0.7 ? WHITE : YELLOW, target, radius, 60, age * (0.05 + k * 0.2));
                if (age % 2 == 0) ring(world, ParticleTypes.ELECTRIC_SPARK, target, radius * (1 - k * 0.8), 30, -age * 0.1);
                if (age % 4 == 0) {
                    for (int i = 0; i < 6; i++) {
                        Vec3d p = face.add((world.random.nextDouble() - 0.5) * 22, (world.random.nextDouble() - 0.5) * 22, (world.random.nextDouble() - 0.5) * 22);
                        Vec3d in = face.subtract(p);
                        world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, in.x, in.y, in.z, 0.08);
                    }
                }
                if (age == 1) boss.audienceSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
                if (age == charge - 30) boss.audienceSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 0.5f);
                if (age % 20 == 10) boss.audienceSound(SoundEvents.BLOCK_BEACON_AMBIENT, 2.0f, 0.5f + (float) k);
                return true;
            }
            if (age == charge) {
                boss.audienceSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.5f);
                boss.audienceSound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2.0f, 0.5f);
                boss.audienceSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.6f);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.5, target.z, 2, radius * 0.4, 0.2, radius * 0.4, 0);
                world.spawnParticles(ParticleTypes.FLASH, target.x, target.y + 1, target.z, 3, radius * 0.3, 0.3, radius * 0.3, 0);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (flatDistance(p.getPos(), target) < 18) net.ragnar.fmscraft.fight.Cinematics.flash(p, 0.5f, 14);
                }
            }
            if (firing()) {
                world.spawnParticles(ParticleTypes.END_ROD, target.x, target.y + 0.3, target.z, 12, radius * 0.45, 0.2, radius * 0.45, 0.3);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 0.3, target.z, 6, radius * 0.4, 0.2, radius * 0.4, 0.05);
                world.spawnParticles(WHITE, target.x, target.y + 0.3, target.z, 10, radius * 0.4, 0.4, radius * 0.4, 0);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (struck.contains(p.getUuid())) continue;
                    if (flatDistance(p.getPos(), target) <= radius && Math.abs(p.getY() - target.y) < 7) {
                        struck.add(p.getUuid());
                        if (boss.shock(p, damage)) boss.launch(p, p.getPos().subtract(target), 1.4, 0.8);
                    }
                }
                return true;
            }
            return false;
        }
    }
}
