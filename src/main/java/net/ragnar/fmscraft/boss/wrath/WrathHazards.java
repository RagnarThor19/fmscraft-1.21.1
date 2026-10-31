package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fire ORNAMIX leaves in the arena: things that outlive the attack that made them and tick on their own until
 * they burn out. Each one hurts only the players fighting him (never him, his skeletons or a block). The spells are
 * Ragnars Magic Mod's own, cast by him: they look and sound the same, but he doesn't break or burn the arena.
 */
final class WrathHazards {
    private static final DustParticleEffect SUN_BODY = new DustParticleEffect(new Vector3f(1.0f, 0.62f, 0.12f), 1.6f);
    private static final DustParticleEffect SUN_HOT = new DustParticleEffect(new Vector3f(1.0f, 0.93f, 0.55f), 2.0f);

    private WrathHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it has burnt out. */
        abstract boolean tick(ServerWorld world, OrnamixEntity boss);

        /** Taken away early (he died, the fight ended): clean up anything it put in the world. */
        void remove() {
        }
    }

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    // ---- Fire Wave: a wall of flame sweeping across the pit. Too tall to jump: step out of its way ----

    static final class FireWave extends Hazard {
        static final double HEIGHT = 2.6, BOW = 0.07;
        final Vec3d dir, side;
        final double speed, range, halfWidth;
        final float damage;
        final Set<UUID> hit = new HashSet<>();
        Vec3d pos;
        double travelled;

        FireWave(Vec3d from, Vec3d dir, double speed, double range, double halfWidth, float damage) {
            this.pos = from;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.speed = speed;
            this.range = range;
            this.halfWidth = halfWidth;
            this.damage = damage;
        }

        /** How far back the wave's edge trails its middle, {@code across} blocks out: a crescent. */
        double bow(double across) {
            return across * across * BOW;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            pos = pos.add(dir.multiply(speed));
            travelled += speed;
            BlockPos ahead = BlockPos.ofFloored(pos.add(dir.multiply(0.6)).add(0, 0.6, 0));
            if (travelled > range || !world.getBlockState(ahead).getCollisionShape(world, ahead).isEmpty()) {
                world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.2, pos.z, 40, halfWidth * 0.4, 0.8, halfWidth * 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.2, pos.z, 10, halfWidth * 0.3, 0.6, halfWidth * 0.3, 0.02);
                return false;
            }
            for (double s = -halfWidth; s <= halfWidth + 1e-6; s += 0.6) {
                Vec3d p = pos.add(side.multiply(s)).subtract(dir.multiply(bow(s)));
                world.spawnParticles(ParticleTypes.FLAME, p.x, p.y + HEIGHT * 0.45, p.z, 3, 0.08, HEIGHT * 0.3, 0.08, 0.01);
                if (world.random.nextInt(4) == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, p.x, p.y + HEIGHT, p.z, 1, 0.1, 0.1, 0.1, 0.02);
                if (world.random.nextInt(14) == 0) world.spawnParticles(ParticleTypes.LAVA, p.x, p.y + 0.4, p.z, 1, 0, 0, 0, 0);
            }
            if (age % 4 == 0) world.playSound(null, pos.x, pos.y + 1, pos.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hit.contains(p.getUuid())) continue;
                Vec3d rel = p.getPos().subtract(pos);
                double across = rel.dotProduct(side), along = rel.dotProduct(dir) + bow(across), dy = rel.y;
                if (Math.abs(across) > halfWidth + 0.4 || Math.abs(along) > 0.9 || dy < -1 || dy > HEIGHT) continue;
                hit.add(p.getUuid());
                boss.hurt(p, damage, 5);
                boss.launch(p, dir, 0.9, 0.35);
            }
            return true;
        }
    }

    // ---- Floor Wave: a low ring of fire rolling out along the floor. Jump it, or be rooted in flames ----

    static final class FloorRing extends Hazard {
        final Vec3d center;
        final double speed, max;
        final float damage;
        final Set<UUID> hit = new HashSet<>();
        double radius;
        int delay;

        FloorRing(Vec3d center, double speed, double max, float damage, int delay) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.delay = delay;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            if (delay > 0) {
                if (--delay == 0) world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.5f);
                return true;
            }
            radius += speed;
            if (radius > max) return false;
            int points = (int) Math.min(72, 10 + radius * 3);
            double offset = world.random.nextDouble() * MathHelper.TAU;
            for (int i = 0; i < points; i++) {
                double a = offset + i * MathHelper.TAU / points;
                double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
                world.spawnParticles(ParticleTypes.FLAME, x, center.y + 0.1, z, 1, 0.05, 0.06, 0.05, 0.015);
                if (i % 4 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, x, center.y + 0.35, z, 1, 0.05, 0.05, 0.05, 0.01);
                if (i % 11 == 0) world.spawnParticles(ParticleTypes.LAVA, x, center.y + 0.2, z, 1, 0, 0, 0, 0);
            }
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hit.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center), dy = p.getY() - center.y;
                if (Math.abs(d - radius) > 0.8 || dy < -1 || dy > 1.2 || !p.isOnGround()) continue;
                hit.add(p.getUuid());
                boss.hurt(p, damage, 0);
                FlameRoot.root(p, FlameRoot.TICKS);
            }
            return true;
        }
    }

    // ---- Ignite (Ragnars Magic Mod's Ignition): a ring of fire bursting out from him, setting everyone it reaches alight ----

    static final class Nova extends Hazard {
        final Vec3d center;
        final double max;
        final int expand;
        final float damage;
        final Set<UUID> ignited = new HashSet<>();

        Nova(Vec3d center, double max, int expand, float damage) {
            this.center = center;
            this.max = max;
            this.expand = expand;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            double t = age / (double) expand;
            double r = max * (1.0 - (1.0 - t) * (1.0 - t));
            int points = (int) Math.max(16, Math.min(90, r * MathHelper.TAU * 1.4));
            for (int i = 0; i < points; i++) {
                double a = i * MathHelper.TAU / points + world.random.nextDouble() * 0.05;
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                world.spawnParticles(ParticleTypes.FLAME, x, center.y + 0.1, z, 0, Math.cos(a) * 0.05, 0.12 + world.random.nextDouble() * 0.12, Math.sin(a) * 0.05, 1.0);
                if (i % 4 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, x, center.y + 0.6, z, 1, 0.1, 0.2, 0.1, 0.01);
                if (i % 6 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, center.y + 0.3, z, 0, 0, 0.05, 0, 1.0);
            }
            if (age % 2 == 0) {
                world.playSound(null, center.x + (world.random.nextDouble() - 0.5) * r, center.y, center.z + (world.random.nextDouble() - 0.5) * r,
                        SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.6f + world.random.nextFloat() * 0.4f);
            }
            if (damage > 0) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (flatDistance(p.getPos(), center) > r || Math.abs(p.getY() - center.y) > 4 || !ignited.add(p.getUuid())) continue;
                    boss.hurt(p, damage, 8);
                    Vec3d body = p.getBoundingBox().getCenter();
                    world.spawnParticles(ParticleTypes.FLAME, body.x, body.y, body.z, 15, 0.25, 0.5, 0.25, 0.05);
                }
            }
            return age < expand;
        }
    }

    // ---- Tome of the Sun (Ragnars Magic Mod): a small sun swells before him, then drifts across the pit, growing ----

    static final class Sun extends Hazard {
        static final double START = 0.2, IGNITED = 1.6, END = 5.0;
        final UUID target;
        final int ignite;
        final double speed, growth;
        final float burn, collapse;
        final Map<UUID, Integer> lastBurn = new HashMap<>();
        Vec3d pos, forward;
        double radius = START;

        Sun(Vec3d pos, Vec3d forward, UUID target, int ignite, double speed, double growth, float burn, float collapse) {
            this.pos = pos;
            this.forward = forward;
            this.target = target;
            this.ignite = ignite;
            this.speed = speed;
            this.growth = growth;
            this.burn = burn;
            this.collapse = collapse;
        }

        boolean igniting() {
            return age <= ignite;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (radius >= END) {
                collapse(world, boss);
                return false;
            }
            Random rand = world.getRandom();
            if (igniting()) {
                // It swells in front of him and follows his aim until he lets it go
                float t = age / (float) ignite;
                radius = MathHelper.lerp(1.0 - Math.pow(1.0 - t, 3), START, IGNITED);
                ServerPlayerEntity aim = boss.fighter(target);
                if (aim != null) {
                    Vec3d to = aim.getPos().subtract(boss.getPos()).multiply(1, 0, 1);
                    if (to.lengthSquared() > 1e-4) forward = to.normalize();
                }
                pos = boss.getPos().add(0, 1.5, 0).add(forward.multiply(2.0 + radius));
                for (int i = 0; i < 12; i++) {
                    Vec3d dir = randomUnit(rand);
                    double dist = radius + 2.5 + rand.nextDouble() * 1.5;
                    Vec3d p = pos.add(dir.multiply(dist)), v = dir.multiply(-(dist - radius) / 10.0);
                    boss.emit(world, i % 4 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, p, 0, v.x, v.y, v.z, 1.0);
                }
                if (age == ignite) {
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.5f);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0f, 0.6f);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 0.5f);
                }
            } else {
                pos = pos.add(forward.multiply(speed));
                radius += growth;
                // Into the pit wall: it bursts there
                BlockPos at = BlockPos.ofFloored(pos.add(forward.multiply(radius * 0.5)));
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty() && radius > IGNITED + 0.3) {
                    collapse(world, boss);
                    return false;
                }
            }

            // Anything inside burns
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                if (c.distanceTo(pos) > radius + 0.5) continue;
                p.setOnFireFor(6);
                Integer last = lastBurn.get(p.getUuid());
                if (last != null && age - last < 10) continue;
                lastBurn.put(p.getUuid(), age);
                p.timeUntilRegen = 0;
                boss.hurt(p, burn, 0);
            }

            // A boiling ball of flame with a white-hot heart, flares licking off it
            double r = radius;
            if (age % 3 == 0) boss.emit(world, ParticleTypes.FLASH, pos, 1, 0, 0, 0, 0);
            boss.emit(world, ParticleTypes.FLAME, pos, (int) (16 + 9 * r), r * 0.42, r * 0.42, r * 0.42, 0.01);
            boss.emit(world, SUN_BODY, pos, (int) (6 + 3 * r), r * 0.45, r * 0.45, r * 0.45, 0);
            boss.emit(world, SUN_HOT, pos, (int) (3 + 2 * r), r * 0.22, r * 0.22, r * 0.22, 0);
            if (age % 3 == 0) boss.emit(world, ParticleTypes.LAVA, pos, 1, r * 0.3, r * 0.3, r * 0.3, 0);
            for (int f = 0; f < (r > 1 ? 2 : 1); f++) {
                Vec3d dir = randomUnit(rand), base = pos.add(dir.multiply(r));
                double s = 0.08 + rand.nextDouble() * 0.12 * (0.5 + r / END);
                for (int i = 0; i < 3; i++) {
                    Vec3d v = dir.multiply(s * (1.0 + i * 0.35));
                    boss.emit(world, ParticleTypes.FLAME, base, 0, v.x, v.y, v.z, 1.0);
                }
            }
            float volume = (float) (1.0 + r * 0.4);
            if (age % 10 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, volume, 0.5f);
            if (age % 20 == 5) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, volume, 0.4f);
            if (rand.nextInt(10) == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_LAVA_POP, SoundCategory.HOSTILE, 0.8f, 0.6f + rand.nextFloat() * 0.4f);
            return true;
        }

        /** It goes out in a burst of light and fire that throws back anyone close. */
        void collapse(ServerWorld world, OrnamixEntity boss) {
            Vec3d c = pos;
            for (int i = 0; i < 3; i++) boss.emit(world, ParticleTypes.FLASH, c, 1, 0.5, 0.5, 0.5, 0);
            boss.emit(world, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
            boss.emit(world, ParticleTypes.FLAME, c, 140, radius * 0.4, radius * 0.4, radius * 0.4, 0.35);
            boss.emit(world, ParticleTypes.LAVA, c, 30, radius * 0.5, radius * 0.5, radius * 0.5, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.5f, 0.5f);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d body = p.getBoundingBox().getCenter();
                if (body.distanceTo(c) > radius + 2) continue;
                p.timeUntilRegen = 0;
                boss.hurt(p, collapse, 6);
                boss.launch(p, body.subtract(c), 1.0, 0.5);
            }
        }
    }

    // ---- Tome of Meteor (Ragnars Magic Mod): marked on the ground, then down out of the sky. Breaks nothing ----

    static final class Meteor extends Hazard {
        static final int RING_TICKS = 14, DEBRIS_LIFE = 40;
        final Vec3d target, start;
        final int delay, fall;
        final double blast;
        final float damage;
        final List<Chunk> chunks = new ArrayList<>();
        final List<Debris> debris = new ArrayList<>();
        Vec3d pos;
        int impactAge = -1;

        record Chunk(DisplayEntity.BlockDisplayEntity display, Vector3f offset, float size, Vector3f spin) {
        }

        static final class Debris {
            final DisplayEntity.BlockDisplayEntity display;
            Vec3d pos, vel;
            int age;

            Debris(DisplayEntity.BlockDisplayEntity display, Vec3d pos, Vec3d vel) {
                this.display = display;
                this.pos = pos;
                this.vel = vel;
            }
        }

        /** Marks {@code target} at once; the rock appears and falls after {@code delay}, landing {@code fall} ticks later. */
        Meteor(Vec3d target, Vec3d from, int delay, int fall, double blast, float damage) {
            this.target = target;
            Vec3d back = target.subtract(from).multiply(1, 0, 1);
            back = back.lengthSquared() > 1e-4 ? back.normalize() : new Vec3d(1, 0, 0);
            this.start = target.add(back.multiply(-28)).add(0, 55, 0);
            this.pos = start;
            this.delay = delay;
            this.fall = fall;
            this.blast = blast;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (age <= delay) {
                marks(world, 0);
                return true;
            }
            int t = age - delay;
            if (impactAge < 0) {
                if (t == 1) build(world);
                fall(world, boss, t);
                return true;
            }
            int since = age - impactAge;
            if (since <= RING_TICKS) shockRing(world, since);
            tickDebris(world);
            return since < DEBRIS_LIFE || !debris.isEmpty();
        }

        /** Where it'll hit: a ring of fire round the blast, and a second one closing in as it gets near. */
        private void marks(ServerWorld world, double progress) {
            if (age % 2 != 0) return;
            int points = 30;
            for (int i = 0; i < points; i++) {
                double a = i * MathHelper.TAU / points + age * 0.02;
                world.spawnParticles(ParticleTypes.FLAME, target.x + Math.cos(a) * blast, target.y + 0.1, target.z + Math.sin(a) * blast, 1, 0, 0.02, 0, 0);
            }
            double inner = blast * (1.0 - progress);
            for (int i = 0; i < 14; i++) {
                double a = i * MathHelper.TAU / 14 - age * 0.05;
                world.spawnParticles(ParticleTypes.SMALL_FLAME, target.x + Math.cos(a) * inner, target.y + 0.1, target.z + Math.sin(a) * inner, 1, 0, 0, 0, 0);
            }
            if (age % 6 == 0) world.spawnParticles(ParticleTypes.LAVA, target.x, target.y + 0.2, target.z, 1, blast * 0.3, 0, blast * 0.3, 0);
        }

        private void build(ServerWorld world) {
            Random rnd = world.random;
            addChunk(world, Blocks.MAGMA_BLOCK.getDefaultState(), new Vector3f(), 3.0f);
            BlockState[] rocks = {Blocks.BLACKSTONE.getDefaultState(), Blocks.NETHERRACK.getDefaultState(),
                    Blocks.BASALT.getDefaultState(), Blocks.MAGMA_BLOCK.getDefaultState()};
            for (int i = 0; i < 6; i++) {
                Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
                addChunk(world, rocks[i % rocks.length], dir.mul(1.2f + rnd.nextFloat() * 0.4f), 1.3f + rnd.nextFloat() * 0.7f);
            }
            world.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_GOAT_SCREAMING_AMBIENT, SoundCategory.HOSTILE, 2.2f, 0.9f);
        }

        private void addChunk(ServerWorld world, BlockState state, Vector3f offset, float size) {
            DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
            if (d == null) return;
            Random rnd = world.random;
            Chunk c = new Chunk(d, offset, size, new Vector3f(rnd.nextFloat() * 0.3f - 0.15f, rnd.nextFloat() * 0.3f - 0.15f, rnd.nextFloat() * 0.3f - 0.15f));
            d.setBlockState(state);
            d.setBrightness(Brightness.FULL);
            d.setTeleportDuration(1);
            d.setViewRange(8.0f);
            d.refreshPositionAndAngles(start.x, start.y, start.z, 0f, 0f);
            d.setTransformation(transform(c, 0));
            world.spawnEntity(d);
            chunks.add(c);
        }

        private static AffineTransformation transform(Chunk c, int age) {
            return new AffineTransformation(new Matrix4f()
                    .rotateXYZ(age * 0.12f, age * 0.07f, age * 0.05f)
                    .translate(c.offset)
                    .rotateXYZ(age * c.spin.x, age * c.spin.y, age * c.spin.z)
                    .scale(c.size)
                    .translate(-0.5f, -0.5f, -0.5f));
        }

        private void fall(ServerWorld world, OrnamixEntity boss, int t) {
            double u = Math.min(1.0, t / (double) fall);
            Vec3d prev = pos;
            pos = start.lerp(target, u * u);
            for (Chunk c : chunks) {
                c.display.setPosition(pos.x, pos.y, pos.z);
                c.display.setTransformation(transform(c, t));
                c.display.setStartInterpolation(0);
                c.display.setInterpolationDuration(1);
            }
            Vec3d back = prev.subtract(pos);
            boss.emit(world, ParticleTypes.FLAME, pos, 20, 1.3, 1.3, 1.3, 0.05);
            boss.emit(world, ParticleTypes.LAVA, pos, 2, 1.0, 1.0, 1.0, 0);
            for (int i = 0; i < 3; i++) boss.emit(world, ParticleTypes.LARGE_SMOKE, pos.add(back.multiply(i / 3.0)), 3, 0.8, 0.8, 0.8, 0.02);
            marks(world, u);
            if (t % 6 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.4f);
            if (t == fall - 20) world.playSound(null, target.x, target.y, target.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            if (t >= fall) impact(world, boss);
        }

        private void impact(ServerWorld world, OrnamixEntity boss) {
            impactAge = age;
            for (Chunk c : chunks) c.display.discard();
            chunks.clear();
            Vec3d g = target;
            // The blast, without the explosion: nothing in the arena breaks
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d to = p.getPos().subtract(g);
                double d = flatDistance(p.getPos(), g);
                double shock = blast * 1.8;
                if (d > shock || to.y > 6 || to.y < -3) continue;
                double falloff = 1.0 - d / shock;
                boss.launch(p, to, 1.6 * falloff, 0.4 + 0.6 * falloff);
                if (d <= blast) {
                    p.timeUntilRegen = 0;
                    boss.hurt(p, (float) (damage * (0.6 + 0.4 * (1 - d / blast))), 6);
                }
            }
            boss.emit(world, ParticleTypes.EXPLOSION_EMITTER, g.add(0, 1, 0), 3, 2.0, 0.8, 2.0, 0);
            boss.emit(world, ParticleTypes.FLAME, g.add(0, 1, 0), 160, 1.5, 1.0, 1.5, 0.5);
            boss.emit(world, ParticleTypes.LAVA, g.add(0, 1, 0), 40, 2.5, 1.0, 2.5, 0);
            boss.emit(world, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, g.add(0, 1, 0), 16, 2.0, 1.0, 2.0, 0.03);
            boss.emit(world, ParticleTypes.LARGE_SMOKE, g.add(0, 1, 0), 60, 3.0, 1.5, 3.0, 0.1);
            world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 6.0f, 0.5f);
            world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 4.0f, 0.6f);
            for (ServerPlayerEntity p : boss.audience(world)) {
                double d = p.getPos().distanceTo(g);
                if (d < 20) boss.flash(p, (float) (0.35 * (1 - d / 20)), 10);
            }
            spawnDebris(world);
        }

        private void shockRing(ServerWorld world, int since) {
            double r = blast * 1.8 * since / (double) RING_TICKS;
            int points = (int) (r * 4) + 12;
            for (int i = 0; i < points; i++) {
                double a = i * MathHelper.TAU / points;
                world.spawnParticles(since % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.CLOUD,
                        target.x + Math.cos(a) * r, target.y + 0.3, target.z + Math.sin(a) * r, 1, 0.1, 0.05, 0.1, 0.01);
            }
        }

        private void spawnDebris(ServerWorld world) {
            Random rnd = world.random;
            BlockState[] rocks = {Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(), Blocks.NETHERRACK.getDefaultState()};
            for (int i = 0; i < 10; i++) {
                DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
                if (d == null) continue;
                double a = rnd.nextDouble() * MathHelper.TAU, speed = 0.4 + rnd.nextDouble() * 0.5;
                Vec3d vel = new Vec3d(Math.cos(a) * speed, 0.6 + rnd.nextDouble() * 0.6, Math.sin(a) * speed);
                Vec3d at = target.add(0, 1.0, 0);
                d.setBlockState(rocks[i % rocks.length]);
                d.setBrightness(Brightness.FULL);
                d.setTeleportDuration(1);
                d.setViewRange(4.0f);
                d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
                d.setTransformation(new AffineTransformation(new Matrix4f().scale(0.55f).translate(-0.5f, -0.5f, -0.5f)));
                world.spawnEntity(d);
                debris.add(new Debris(d, at, vel));
            }
        }

        private void tickDebris(ServerWorld world) {
            Iterator<Debris> it = debris.iterator();
            while (it.hasNext()) {
                Debris d = it.next();
                d.age++;
                Vec3d next = d.pos.add(d.vel);
                BlockPos at = BlockPos.ofFloored(next);
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty() || d.age > DEBRIS_LIFE) {
                    world.spawnParticles(ParticleTypes.LAVA, d.pos.x, d.pos.y, d.pos.z, 2, 0.2, 0.1, 0.2, 0);
                    world.spawnParticles(ParticleTypes.SMOKE, d.pos.x, d.pos.y, d.pos.z, 5, 0.2, 0.1, 0.2, 0.02);
                    d.display.discard();
                    it.remove();
                    continue;
                }
                d.pos = next;
                d.vel = d.vel.multiply(0.98).add(0, -0.08, 0);
                d.display.setPosition(next.x, next.y, next.z);
                float spin = d.age * 0.4f;
                d.display.setTransformation(new AffineTransformation(new Matrix4f().rotateXYZ(spin, spin * 0.7f, 0).scale(0.55f).translate(-0.5f, -0.5f, -0.5f)));
                d.display.setStartInterpolation(0);
                d.display.setInterpolationDuration(1);
                world.spawnParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }

        @Override
        void remove() {
            for (Chunk c : chunks) c.display.discard();
            for (Debris d : debris) d.display.discard();
            chunks.clear();
            debris.clear();
        }
    }

    // ---- A circle of flame on the ground: where something is about to hit ----

    static final class Circle extends Hazard {
        final Vec3d center;
        final double radius;
        final int ticks;
        final ParticleEffect particle;

        Circle(Vec3d center, double radius, int ticks, ParticleEffect particle) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
            this.particle = particle;
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (age % 2 == 0) {
                int points = (int) Math.min(64, 12 + radius * 4);
                for (int i = 0; i < points; i++) {
                    double a = i * MathHelper.TAU / points + age * 0.03;
                    world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0.03, 0, 0.005);
                }
            }
            return age < ticks;
        }
    }

    static Vec3d randomUnit(Random r) {
        double z = r.nextDouble() * 2.0 - 1.0, theta = r.nextDouble() * MathHelper.TAU, s = Math.sqrt(1.0 - z * z);
        return new Vec3d(s * Math.cos(theta), z, s * Math.sin(theta));
    }
}
