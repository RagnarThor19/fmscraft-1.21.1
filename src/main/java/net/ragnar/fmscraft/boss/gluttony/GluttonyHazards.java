package net.ragnar.fmscraft.boss.gluttony;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything LalliLarge's attacks leave in the air or on the ground, ticked by him. The ice is all display entities
 * (blocks of ice, packed ice and blue ice, scaled and turned): it never places or breaks a real block, and it all goes
 * with him. The tomes he casts are built to look like Ragnars Magic Mod's (Glacier, Falling Stalactite, Rising Spikes,
 * Impaling, Freezing, Earthquake), only all of ice.
 */
final class GluttonyHazards {
    static final DustParticleEffect FROST = new DustParticleEffect(new Vector3f(0.62f, 0.86f, 1.0f), 1.2f);
    static final DustParticleEffect DEEP_ICE = new DustParticleEffect(new Vector3f(0.25f, 0.55f, 0.95f), 1.5f);
    static final DustParticleEffect RIME = new DustParticleEffect(new Vector3f(0.94f, 0.98f, 1.0f), 1.0f);
    static final BlockState ICE = Blocks.ICE.getDefaultState(), PACKED = Blocks.PACKED_ICE.getDefaultState(),
            BLUE = Blocks.BLUE_ICE.getDefaultState(), SNOW = Blocks.SNOW_BLOCK.getDefaultState();
    static final BlockStateParticleEffect ICE_CHIPS = new BlockStateParticleEffect(ParticleTypes.BLOCK, ICE);
    static final BlockStateParticleEffect PACKED_CHIPS = new BlockStateParticleEffect(ParticleTypes.BLOCK, PACKED);
    static final BlockStateParticleEffect SNOW_PUFF = new BlockStateParticleEffect(ParticleTypes.BLOCK, SNOW);
    static final Identifier FROZEN_MODIFIER = Identifier.of(FmsCraft.MOD_ID, "lalli_frozen");

    private GluttonyHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it's spent. */
        abstract boolean tick(ServerWorld world, LalliEntity boss);

        /** Taken away early (he died, the fight ended, the fight moved): clean up anything it put in the world. */
        void remove() {
        }
    }

    // ---- Helpers ----

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double spin, double y) {
        for (int i = 0; i < points; i++) {
            double a = i * MathHelper.TAU / points + spin;
            world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + y, center.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    /** A dotted line of {@code particle} from {@code from} to {@code to}, one every {@code step} blocks. */
    static void line(ServerWorld world, ParticleEffect particle, Vec3d from, Vec3d to, double step) {
        double length = from.distanceTo(to);
        for (double s = 0; s < length; s += step) {
            Vec3d p = from.lerp(to, s / length);
            world.spawnParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** A block of {@code state}, {@code sx} by {@code sy} by {@code sz}, standing on {@code at} (centred on it), turned {@code yaw} and leaning {@code tilt}. */
    static DisplayEntity.BlockDisplayEntity block(ServerWorld world, BlockState state, Vec3d at, float sx, float sy, float sz,
                                                  float yaw, float tilt, float tiltDir) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setTeleportDuration(2);
        d.setViewRange(6f);
        d.setBrightness(new Brightness(15, 15));
        d.setTransformation(shape(sx, sy, sz, yaw, tilt, tiltDir, 0));
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        world.spawnEntity(d);
        return d;
    }

    /**
     * A block scaled to {@code sx, sy, sz}, its bottom middle on the display's position (raised {@code lift}), turned
     * {@code yaw} about the vertical and leaning {@code tilt} towards {@code tiltDir}.
     */
    static AffineTransformation shape(float sx, float sy, float sz, float yaw, float tilt, float tiltDir, float lift) {
        Quaternionf rot = new Quaternionf().rotateY(tiltDir).rotateX(tilt).rotateY(yaw - tiltDir);
        Vector3f offset = new Vector3f(-sx / 2, 0, -sz / 2);
        rot.transform(offset);
        offset.add(0, lift, 0);
        return new AffineTransformation(offset, rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    static void move(DisplayEntity d, Vec3d at) {
        if (d != null) d.setPosition(at.x, at.y, at.z);
    }

    static void discard(DisplayEntity d) {
        if (d != null && !d.isRemoved()) d.discard();
    }

    // ---- Frozen solid: a block of ice round a player, holding them where they stand ----

    static final class Frozen extends Hazard {
        final ServerPlayerEntity player;
        int ticks, delay;
        DisplayEntity.BlockDisplayEntity shell;
        private final float damage;

        Frozen(ServerPlayerEntity player, int ticks, int delay, float damage) {
            this.player = player;
            this.ticks = ticks;
            this.delay = delay;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            ServerPlayerEntity p = player;
            if (p.isRemoved() || !p.isAlive() || p.getWorld() != world) return false;
            if (delay > 0) {
                delay--;
                return true;
            }
            age++;
            if (shell == null) {
                float w = p.getWidth() * 1.9f, h = p.getHeight() * 1.15f;
                shell = block(world, ICE, p.getPos(), w, h, w, world.random.nextFloat() * 0.6f - 0.3f, 0.06f, world.random.nextFloat() * MathHelper.TAU);
                hold(p, true);
                Vec3d c = p.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 25, 0.5, 0.8, 0.5, 0.05);
                world.spawnParticles(ICE_CHIPS, c.x, c.y, c.z, 20, 0.4, 0.8, 0.4, 0.1);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 1.2f, 0.7f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.2f, 0.8f);
            }
            // Held exactly where the ice caught them (they can still fall), the frost creeping over their sight
            Vec3d v = p.getVelocity();
            p.setVelocity(0, Math.min(0, v.y), 0);
            p.velocityModified = true;
            p.setSprinting(false);
            p.setFrozenTicks(Math.max(p.getFrozenTicks(), p.getMinFreezeDamageTicks() + 2));
            move(shell, p.getPos());
            if (age % 6 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getBodyY(world.random.nextDouble()), p.getZ(), 1, 0.3, 0.2, 0.3, 0.01);
            if (ticks - age == 14) world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, SoundCategory.HOSTILE, 1.0f, 1.6f);
            if (age < ticks) return true;
            // Time's up: the ice bursts apart and takes a last bite out of them
            Vec3d c = p.getBoundingBox().getCenter();
            world.spawnParticles(ICE_CHIPS, c.x, c.y, c.z, 40, 0.4, 0.8, 0.4, 0.2);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 15, 0.4, 0.6, 0.4, 0.08);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, 1.0f + world.random.nextFloat() * 0.3f);
            if (damage > 0) boss.crush(p, damage);
            return false;
        }

        /** Always run when it ends, however it ends: nobody is ever left held. */
        @Override
        void remove() {
            discard(shell);
            shell = null;
            hold(player, false);
        }

        static void hold(ServerPlayerEntity p, boolean on) {
            for (EntityAttributeInstance a : new EntityAttributeInstance[]{p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH)}) {
                if (a == null) continue;
                if (on && !a.hasModifier(FROZEN_MODIFIER)) {
                    a.addTemporaryModifier(new EntityAttributeModifier(FROZEN_MODIFIER, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else if (!on) {
                    a.removeModifier(FROZEN_MODIFIER);
                }
            }
        }

        static boolean isHeld(ServerPlayerEntity p) {
            EntityAttributeInstance a = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            return a != null && a.hasModifier(FROZEN_MODIFIER);
        }
    }

    // ---- A circle on the ground: where something is about to land ----

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
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            if (age % 2 == 0) ring(world, particle, center, radius, (int) Math.min(80, 12 + radius * 4), age * 0.03, 0.1);
            return age < ticks;
        }
    }

    // ---- A wave of frost rushing out along the ground ----

    /**
     * A ring rushing out from {@code center}. With damage, it only catches whoever is on the ground as it passes (jump
     * it); a {@code wave} (his landing) throws back everyone it reaches, in the air or not, and hurts nobody.
     */
    static final class Shockwave extends Hazard {
        final Vec3d center;
        final double speed, max, push, up;
        final float damage;
        final boolean wave;
        final int slowTicks;
        final Set<UUID> settled = new HashSet<>();
        double radius;

        Shockwave(Vec3d center, double speed, double max, float damage, double push, double up, int slowTicks, boolean wave) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.push = push;
            this.up = up;
            this.slowTicks = slowTicks;
            this.wave = wave;
        }

        static Shockwave visual(Vec3d center, double speed, double max) {
            return new Shockwave(center, speed, max, 0, 0, 0, 0, false);
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            radius += speed;
            if (radius > max) return false;
            int points = (int) Math.min(110, 12 + radius * 5);
            ring(world, ParticleTypes.SNOWFLAKE, center, radius, points, radius * 0.1, 0.15);
            if (age++ % 2 == 0) ring(world, wave ? ParticleTypes.CLOUD : FROST, center, radius - 0.3, points / 2, 0, 0.3);
            if (wave) {
                // A wall of snow thrown up off the ground as it passes
                ring(world, SNOW_PUFF, center, radius, points / 2, age * 0.2, 0.6);
                ring(world, ParticleTypes.ITEM_SNOWBALL, center, radius, points / 3, age * 0.3, 1.4);
            }
            if (damage <= 0 && push <= 0) return true;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (settled.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center);
                if (d > radius || Math.abs(p.getY() - center.y) > (wave ? 12 : 3)) continue;
                settled.add(p.getUuid());
                if (wave) {
                    boss.launch(p, p.getPos().subtract(center), push * Math.max(0.35, 1 - d / max) + 0.4, up);
                    continue;
                }
                if (d < radius - speed - 1.2 || !p.isOnGround()) continue; // jumped it, or was already past
                if (damage > 0) boss.crush(p, damage);
                if (push > 0) boss.launch(p, p.getPos().subtract(center), push, up);
                if (slowTicks > 0) boss.chill(p, slowTicks);
            }
            return true;
        }
    }

    // ---- Ice Shards: little blocks of ice thrown dead straight ----

    static final class Shard extends Hazard {
        static final double SPEED = 1.1, RANGE = 34;
        final DisplayEntity.BlockDisplayEntity display;
        final Vec3d dir;
        final float damage;
        Vec3d pos;

        Shard(ServerWorld world, Vec3d from, Vec3d dir, float damage) {
            this.pos = from;
            this.dir = dir.normalize();
            this.damage = damage;
            this.display = block(world, ICE, from.add(0, -0.2, 0), 0.4f, 0.4f, 0.4f, 0, 0, 0);
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            Vec3d next = pos.add(dir.multiply(SPEED));
            if (age * SPEED > RANGE || boss.blocked(pos, next)) {
                world.spawnParticles(ICE_CHIPS, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.1);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 0.8f, 1.6f);
                discard(display);
                return false;
            }
            pos = next;
            if (display != null) {
                display.setTransformation(shape(0.4f, 0.4f, 0.4f, age * 0.6f, 0.7f, age * 0.3f, 0));
                display.setStartInterpolation(0);
                display.setInterpolationDuration(1);
            }
            move(display, pos.add(0, -0.2, 0));
            world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (!p.getBoundingBox().expand(0.35).contains(pos)) continue;
                boss.strike(p, damage);
                boss.chill(p, 40);
                world.spawnParticles(ICE_CHIPS, pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.1);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.9f, 1.5f);
                discard(display);
                return false;
            }
            return true;
        }

        @Override
        void remove() {
            discard(display);
        }
    }

    // ---- Tome of the Glacier: a tumbling chunk of ice that bursts into a block of ice round whoever it reaches ----

    static final class Glacier extends Hazard {
        static final double SPEED = 1.25, BURST = 22;
        static final float SIZE = 1.5f;
        final DisplayEntity.BlockDisplayEntity display;
        final Vec3d dir;
        final float damage;
        final int hold;
        Vec3d pos;

        Glacier(ServerWorld world, Vec3d from, Vec3d dir, float damage, int hold) {
            this.pos = from;
            this.dir = dir.normalize();
            this.damage = damage;
            this.hold = hold;
            this.display = block(world, PACKED, from, SIZE, SIZE, SIZE, 0, 0, 0);
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            Vec3d next = pos.add(dir.multiply(SPEED));
            boolean hit = age * SPEED >= BURST || boss.blocked(pos, next);
            if (!hit) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (p.getBoundingBox().expand(0.9).contains(next)) hit = true;
                }
            }
            if (hit) {
                boss.addHazard(new Tomb(world, boss.ground(pos), damage, hold));
                discard(display);
                return false;
            }
            pos = next;
            if (display != null) {
                // Tumbling as it flies
                display.setTransformation(shape(SIZE, SIZE, SIZE, age * 0.35f, age * 0.25f, 0.6f, -SIZE / 2));
                display.setStartInterpolation(0);
                display.setInterpolationDuration(1);
            }
            move(display, pos);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0.01);
            if (age % 2 == 0) world.spawnParticles(PACKED_CHIPS, pos.x, pos.y, pos.z, 1, 0.3, 0.3, 0.3, 0);
            if (age % 6 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_POWDER_SNOW_STEP, SoundCategory.HOSTILE, 1.0f, 0.6f);
            return true;
        }

        @Override
        void remove() {
            discard(display);
        }
    }

    /** A 5x5x5 block of ice: everyone inside is entombed, hurt, and held till it shatters. */
    static final class Tomb extends Hazard {
        static final float SIZE = 5f;
        final DisplayEntity.BlockDisplayEntity display;
        final Vec3d center;
        final int hold;
        private final float damage;
        private boolean caught;

        Tomb(ServerWorld world, Vec3d at, float damage, int hold) {
            this.center = at;
            this.hold = hold;
            this.damage = damage;
            this.display = block(world, ICE, at.add(0, -0.5, 0), SIZE, SIZE, SIZE, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 2, at.z, 120, 2.5, 2.5, 2.5, 0.1);
            world.spawnParticles(ICE_CHIPS, at.x, at.y + 2, at.z, 80, 2.0, 2.0, 2.0, 0.2);
            world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.8f, 0.6f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.8f, 0.7f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 1.8f, 0.5f);
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            if (!caught) {
                caught = true;
                Box box = new Box(center.x - SIZE / 2, center.y - 0.5, center.z - SIZE / 2, center.x + SIZE / 2, center.y + SIZE - 0.5, center.z + SIZE / 2);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (!box.intersects(p.getBoundingBox())) continue;
                    boss.crush(p, damage);
                    boss.freeze(p, hold, 0, 0);
                }
            }
            if (age % 5 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 2, center.z, 3, 2.2, 2.2, 2.2, 0.01);
            if (age < hold) return true;
            world.spawnParticles(ICE_CHIPS, center.x, center.y + 2, center.z, 150, 2.0, 2.0, 2.0, 0.3);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.8f, 0.8f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.8f, 1.1f);
            discard(display);
            return false;
        }

        @Override
        void remove() {
            discard(display);
        }
    }

    // ---- Icicle Crash: a great icicle forms over you, trembles, and is driven into the ground ----

    static final class Icicle extends Hazard {
        static final double HOVER = 19, FALL_SPEED = 2.6, DIRECT = 1.4, SPLASH = 3.2;
        static final int TRACK = 18, SHAKE = 16, STUCK = 28;
        static final float[] WIDTHS = {2.0f, 1.6f, 1.2f, 0.8f, 0.45f};
        static final float SEGMENT = 1.15f;
        final UUID target;
        final float direct, splash;
        final int delay;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        Vec3d spot, pos;
        double ground;
        boolean fallen;
        int stuck;

        Icicle(UUID target, Vec3d at, int delay, float direct, float splash) {
            this.target = target;
            this.spot = at;
            this.delay = delay;
            this.direct = direct;
            this.splash = splash;
        }

        private static float height() {
            return WIDTHS.length * SEGMENT;
        }

        private void build(ServerWorld world, Vec3d at) {
            // Widest at the top, narrowing to a point at the bottom
            for (int i = 0; i < WIDTHS.length; i++) {
                float w = WIDTHS[i];
                BlockState state = i % 2 == 0 ? PACKED : i == WIDTHS.length - 1 ? ICE : BLUE;
                DisplayEntity.BlockDisplayEntity d = block(world, state, at, w, SEGMENT, w, i * 0.4f, 0, 0);
                if (d != null) {
                    d.setTransformation(shape(w, SEGMENT, w, i * 0.4f, 0, 0, (WIDTHS.length - 1 - i) * SEGMENT));
                    parts.add(d);
                }
            }
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            if (age <= delay) return true;
            int t = age - delay;
            if (t == 1) {
                ground = boss.groundY(spot);
                pos = new Vec3d(spot.x, ground + HOVER, spot.z);
                build(world, pos);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 2, pos.z, 40, 1, 1.5, 1, 0.05);
            }
            if (!fallen && t <= TRACK) {
                // It drifts after you while it forms, then it's set
                ServerPlayerEntity p = boss.fighter(target);
                if (p != null) {
                    spot = spot.lerp(p.getPos(), 0.25);
                    ground = boss.groundY(spot);
                }
                pos = new Vec3d(spot.x, ground + HOVER, spot.z);
                for (DisplayEntity.BlockDisplayEntity d : parts) move(d, pos);
            }
            Vec3d mark = new Vec3d(spot.x, ground, spot.z);
            if (!fallen && t <= TRACK + SHAKE) {
                // A marker on the ground where it will land, grit of frost trickling off it
                if (t % 2 == 0) ring(world, FROST, mark, SPLASH * (1 - 0.3 * t / (TRACK + SHAKE)), 20, t * 0.2, 0.1);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 1, 0.3, 0.1, 0.3, 0);
                if (t > TRACK) {
                    double jitter = 0.12;
                    Vec3d shake = pos.add((world.random.nextDouble() - 0.5) * jitter, 0, (world.random.nextDouble() - 0.5) * jitter);
                    for (DisplayEntity.BlockDisplayEntity d : parts) move(d, shake);
                    if (t % 4 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 2.0f, 0.5f + (t - TRACK) * 0.04f);
                }
                if (t == TRACK + SHAKE) {
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_FALL, SoundCategory.HOSTILE, 3.0f, 0.7f);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 0.8f, 0.6f);
                }
                return true;
            }
            if (!fallen) {
                pos = pos.add(0, -FALL_SPEED, 0);
                if (pos.y <= ground) {
                    pos = new Vec3d(pos.x, ground - 0.6, pos.z);
                    fallen = true;
                    impact(world, boss);
                }
                for (DisplayEntity.BlockDisplayEntity d : parts) move(d, pos);
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + height(), pos.z, 1, 0.25, 0.1, 0.25, 0);
                return true;
            }
            if (++stuck < STUCK) return true;
            world.spawnParticles(ICE_CHIPS, pos.x, pos.y + 2, pos.z, 50, 0.4, 1.4, 0.4, 0.1);
            world.playSound(null, pos.x, pos.y + 1.5, pos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, 0.8f);
            remove();
            return false;
        }

        private void impact(ServerWorld world, LalliEntity boss) {
            Vec3d c = new Vec3d(spot.x, ground, spot.z);
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = flatDistance(p.getPos(), c);
                if (Math.abs(p.getY() - ground) > 3 || d > SPLASH) continue;
                if (d <= DIRECT) {
                    boss.crush(p, direct);
                    boss.freeze(p, 30, 0, 0);
                } else {
                    boss.strike(p, splash);
                    boss.launch(p, p.getPos().subtract(c), 0.9, 0.35);
                    boss.chill(p, 40);
                }
            }
            world.spawnParticles(ICE_CHIPS, c.x, c.y + 0.5, c.z, 60, 1.0, 0.5, 1.0, 0.25);
            world.spawnParticles(SNOW_PUFF, c.x, c.y + 0.2, c.z, 40, 1.5, 0.2, 1.5, 0.2);
            world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 1.4f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.4f, 0.5f);
            boss.addHazard(Shockwave.visual(c, 0.5, SPLASH + 1));
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : parts) discard(d);
            parts.clear();
        }
    }

    // ---- Rising Ice Spikes: a wedge of ice shards bursting out of the ground in a row from him ----

    static final class SpikeRow extends Hazard {
        static final int LENGTH = 20, STEP = 2, STAND = 14, RISE = 2, SINK = 5;
        static final int[] LANES = {-1, 0, 1};
        static final double HALF_WIDTH = 0.9;
        final Vec3d from, dir, side;
        final float damage;
        final List<Spike> spikes = new ArrayList<>();
        final Set<UUID> hit = new HashSet<>();

        record Spike(DisplayEntity.BlockDisplayEntity display, Vec3d base, float height, int born) {
        }

        SpikeRow(Vec3d from, Vec3d dir, float damage) {
            Vec3d flat = dir.multiply(1, 0, 1);
            this.dir = flat.lengthSquared() > 1e-4 ? flat.normalize() : new Vec3d(0, 0, 1);
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.from = from;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            int step = age / STEP;
            if (age % STEP == 0 && step >= 1 && step <= LENGTH) {
                for (int lane : LANES) {
                    Vec3d at = from.add(dir.multiply(step + 0.5)).add(side.multiply(lane * 1.05));
                    double ground = boss.groundY(at);
                    if (Double.isNaN(ground) || Math.abs(ground - from.y) > 5) continue;
                    Vec3d base = new Vec3d(at.x, ground, at.z);
                    float h = lane == 0 ? 2.6f : 1.9f;
                    float w = lane == 0 ? 0.9f : 0.7f;
                    BlockState state = (step + lane) % 3 == 0 ? BLUE : PACKED;
                    float lean = lane == 0 ? 0.05f : 0.3f;
                    float leanDir = (float) Math.atan2(side.x * lane, side.z * lane);
                    DisplayEntity.BlockDisplayEntity d = block(world, state, base.add(0, -h, 0), w, h, w, 0.785f + step * 0.3f, lean, leanDir);
                    if (d != null) {
                        move(d, base.add(0, -0.15, 0));
                        spikes.add(new Spike(d, base, h, age));
                    }
                    world.spawnParticles(SNOW_PUFF, base.x, base.y + 0.2, base.z, 6, 0.3, 0.1, 0.3, 0.1);
                    world.spawnParticles(ICE_CHIPS, base.x, base.y + 0.6, base.z, 4, 0.2, 0.4, 0.2, 0.1);
                    for (ServerPlayerEntity p : boss.fighters()) {
                        if (hit.contains(p.getUuid()) || Math.abs(p.getY() - ground) > 2.5) continue;
                        if (flatDistance(p.getPos(), base) > HALF_WIDTH + 0.3) continue;
                        hit.add(p.getUuid());
                        boss.crush(p, damage);
                        p.setVelocity(p.getVelocity().x + dir.x * 0.3, 1.0, p.getVelocity().z + dir.z * 0.3);
                        p.velocityModified = true;
                        boss.chill(p, 40);
                    }
                }
                if (step % 2 == 1) {
                    Vec3d at = from.add(dir.multiply(step));
                    world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.0f, 0.7f + world.random.nextFloat() * 0.3f);
                    world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 0.8f, 1.2f);
                }
            }
            for (Spike s : new ArrayList<>(spikes)) {
                int life = age - s.born;
                if (life == STAND) move(s.display, s.base.add(0, -s.height - 0.2, 0));
                if (life >= STAND + SINK) {
                    discard(s.display);
                    spikes.remove(s);
                }
            }
            return step <= LENGTH || !spikes.isEmpty();
        }

        @Override
        void remove() {
            for (Spike s : spikes) discard(s.display);
            spikes.clear();
        }
    }

    // ---- Tome of Impaling, in ice: a spot is marked under you, then a huge spike of ice bursts out of it ----

    static final class Impale extends Hazard {
        static final int TRACK = 10, WARNING = 14, STAND = 50, SINK = 10;
        static final double CORE = 1.6, OUTER = 3.8;
        static final float[] WIDTHS = {2.4f, 2.0f, 1.6f, 1.1f, 0.6f};
        static final float SEGMENT = 1.3f;
        final UUID target;
        final float core, outer;
        final int delay;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        Vec3d spot;
        double ground;
        boolean risen;

        Impale(UUID target, Vec3d at, int delay, float core, float outer) {
            this.target = target;
            this.spot = at;
            this.delay = delay;
            this.core = core;
            this.outer = outer;
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            if (age <= delay) return true;
            int t = age - delay;
            if (t <= TRACK) {
                ServerPlayerEntity p = boss.fighter(target);
                if (p != null) spot = spot.lerp(p.getPos(), 0.35);
                ground = boss.groundY(spot);
            }
            Vec3d base = new Vec3d(spot.x, ground, spot.z);
            if (t <= TRACK + WARNING) {
                // The ground cracks and shakes; a ring tightens on the spot
                double r = OUTER * (1 - (double) t / (TRACK + WARNING)) + CORE * 0.6;
                ring(world, ParticleTypes.CRIT, base, r, 18, t * 0.3, 0.1);
                ring(world, FROST, base, CORE, 10, -t * 0.2, 0.1);
                world.spawnParticles(SNOW_PUFF, base.x, base.y + 0.1, base.z, 3, 0.8, 0.05, 0.8, 0.05);
                if (t % 4 == 0) world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.0f, 0.5f + t * 0.03f);
                return true;
            }
            if (!risen) {
                risen = true;
                build(world, base, world.random.nextFloat() * MathHelper.TAU);
                for (ServerPlayerEntity p : boss.fighters()) {
                    double d = flatDistance(p.getPos(), base);
                    if (Math.abs(p.getY() - ground) > 3 || d > OUTER) continue;
                    if (d <= CORE) {
                        boss.crush(p, core);
                        p.setVelocity(p.getVelocity().x * 0.2, 1.5, p.getVelocity().z * 0.2);
                        p.velocityModified = true;
                    } else {
                        boss.crush(p, outer);
                        boss.launch(p, p.getPos().subtract(base), 1.1, 0.5);
                    }
                    boss.chill(p, 50);
                }
                world.spawnParticles(ICE_CHIPS, base.x, base.y + 2, base.z, 50, 0.8, 2.0, 0.8, 0.2);
                world.spawnParticles(SNOW_PUFF, base.x, base.y + 0.3, base.z, 40, 1.4, 0.3, 1.4, 0.1);
                world.spawnParticles(ParticleTypes.EXPLOSION, base.x, base.y + 0.5, base.z, 2, 0.6, 0.2, 0.6, 0);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.4f);
                return true;
            }
            int s = t - TRACK - WARNING;
            if (s == STAND) {
                for (DisplayEntity.BlockDisplayEntity d : parts) move(d, base.add(0, -WIDTHS.length * SEGMENT - 2, 0));
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.4f, 0.7f);
                world.spawnParticles(ICE_CHIPS, base.x, base.y + 0.5, base.z, 40, 1.0, 0.4, 1.0, 0.1);
            }
            if (s < STAND + SINK) return true;
            remove();
            return false;
        }

        /** The main spike (tapering, twisting) and a crown of smaller ones leaning out round it. */
        private void build(ServerWorld world, Vec3d base, float twist) {
            float y = 0;
            for (int i = 0; i < WIDTHS.length; i++) {
                float w = WIDTHS[i], h = i == WIDTHS.length - 1 ? SEGMENT * 1.8f : SEGMENT;
                DisplayEntity.BlockDisplayEntity d = block(world, i % 2 == 0 ? PACKED : BLUE, base.add(0, -8, 0), w, h, w, twist + i * 0.5f, 0, 0);
                if (d != null) {
                    d.setTransformation(shape(w, h, w, twist + i * 0.5f, 0, 0, y));
                    move(d, base);
                    parts.add(d);
                }
                y += SEGMENT;
            }
            for (int i = 0; i < 6; i++) {
                float a = twist + i * MathHelper.TAU / 6;
                Vec3d at = base.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
                float leanDir = (float) Math.atan2(Math.cos(a), Math.sin(a));
                DisplayEntity.BlockDisplayEntity d = block(world, i % 2 == 0 ? ICE : PACKED, at.add(0, -4, 0), 0.8f, 2.4f, 0.8f, a, 0.4f, leanDir);
                if (d != null) {
                    move(d, at.add(0, -0.3, 0));
                    parts.add(d);
                }
            }
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : parts) discard(d);
            parts.clear();
        }
    }

    // ---- Tome of Freezing: a globe of frost swells round him, then everything in it freezes ----

    static final class Nova extends Hazard {
        static final int GROW = 50, HOLD = 10, STEPS = 10;
        final Vec3d center;
        final double radius;
        final float damage;
        final int freeze;
        final List<DisplayEntity.BlockDisplayEntity> crystals = new ArrayList<>();

        Nova(ServerWorld world, Vec3d center, double radius, float damage, int freeze) {
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.freeze = freeze;
            for (int i = 0; i < 6; i++) {
                DisplayEntity.BlockDisplayEntity d = block(world, i % 2 == 0 ? BLUE : PACKED, center, 0.6f, 0.9f, 0.6f, 0, 0.8f, 0);
                if (d != null) crystals.add(d);
            }
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }

        double size() {
            int step = Math.min(STEPS, age * STEPS / GROW + 1);
            return radius * step / STEPS;
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            // The crystals rise out of the ground and circle him, faster and faster
            float spin = age * (0.08f + age * 0.003f);
            for (int i = 0; i < crystals.size(); i++) {
                double a = spin + i * MathHelper.TAU / crystals.size();
                double lift = Math.min(1, age / 15.0) * 2.2;
                move(crystals.get(i), center.add(Math.cos(a) * 2.6, lift, Math.sin(a) * 2.6));
            }
            if (age <= GROW + HOLD) {
                // The frost sigil under him, and the globe's glass growing in steps you can see
                if (age % 3 == 0) {
                    ring(world, FROST, center, 3.2, 24, age * 0.05, 0.1);
                    for (int k = 0; k < 6; k += 2) {
                        double a1 = age * 0.05 + k * Math.PI / 3, a2 = a1 + 2 * Math.PI / 3;
                        line(world, FROST, center.add(Math.cos(a1) * 3.2, 0.1, Math.sin(a1) * 3.2), center.add(Math.cos(a2) * 3.2, 0.1, Math.sin(a2) * 3.2), 0.5);
                    }
                }
                double r = size();
                if (age % 5 == 0 || age > GROW) {
                    for (double lat = -0.6; lat <= 0.9; lat += 0.3) {
                        double rr = r * Math.cos(lat * Math.PI / 2), y = r * Math.sin(lat * Math.PI / 2);
                        if (y < -0.5) continue;
                        ring(world, ParticleTypes.SNOWFLAKE, center, rr, (int) Math.min(90, 10 + rr * 4), age * 0.02, y);
                    }
                    ring(world, RIME, center, r, (int) Math.min(100, 12 + r * 5), 0, 0.15);
                }
                if (age % 5 == 0 && age <= GROW) {
                    float pitch = 0.6f + age / (float) GROW;
                    world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 2.0f, pitch);
                    world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 1.5f, 0.5f + pitch * 0.4f);
                }
                if (age == GROW) world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
                if (age == GROW + HOLD) detonate(world, boss);
                return true;
            }
            // Snow drifting down over the frozen field
            for (int i = 0; i < 4; i++) {
                double a = world.random.nextDouble() * MathHelper.TAU, rr = Math.sqrt(world.random.nextDouble()) * radius;
                world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x + Math.cos(a) * rr, center.y + 5 + world.random.nextDouble() * 3,
                        center.z + Math.sin(a) * rr, 1, 0, -0.05, 0, 0.01);
            }
            return age < GROW + HOLD + 40;
        }

        private void detonate(ServerWorld world, LalliEntity boss) {
            Vec3d c = center.add(0, 1, 0);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getPos().distanceTo(center) > radius) continue;
                boss.crush(p, damage);
                boss.freeze(p, freeze, 0, 0);
            }
            boss.emit(world, ParticleTypes.FLASH, c, 2, 0, 0, 0, 0);
            for (int i = 0; i < 80; i++) {
                Vec3d dir = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian() * 0.4, world.random.nextGaussian()).normalize();
                world.spawnParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 0, dir.x, dir.y, dir.z, 1.4);
            }
            world.spawnParticles(ICE_CHIPS, c.x, c.y, c.z, 80, 1.5, 1.0, 1.5, 0.4);
            for (DisplayEntity.BlockDisplayEntity d : crystals) {
                Vec3d at = d.getPos();
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, BLUE), at.x, at.y, at.z, 15, 0.2, 0.2, 0.2, 0.2);
                discard(d);
            }
            crystals.clear();
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : crystals) discard(d);
            crystals.clear();
        }
    }

    // ---- The Earthquake from his landing: a quake rolling out through the ground, then two aftershocks ----

    /**
     * The quake from his Jump Slam. With Ragnars Magic Mod it looks exactly like the Tome of Earthquake (the boss
     * started its charge when he leapt; this sends the shock and the aftershocks, and the mod's client draws the
     * fissures, the heaving ground and the flung earth); without it, particles. Either way the damage is done here.
     */

    static final class Quake extends Hazard {
        static final double RADIUS = 25, SPEED = 1.25;
        static final int[] AFTERSHOCKS = {24, 44};
        final Vec3d center;
        final float centerDamage, edgeDamage;
        final Set<UUID> hit = new HashSet<>();
        final BlockState ground;
        final long seed;
        final int casterId;
        final boolean tome;

        Quake(Vec3d center, BlockState ground, float centerDamage, float edgeDamage, long seed, int casterId, boolean tome) {
            this.casterId = casterId;
            this.tome = tome;
            this.center = center;
            this.ground = ground.isAir() ? SNOW : ground;
            this.centerDamage = centerDamage;
            this.edgeDamage = edgeDamage;
            this.seed = seed;
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            double r = age * SPEED;
            BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground);
            if (tome && age == 1) net.ragnar.fmscraft.compat.MagicSpells.earthquakeShock(world, casterId, center, 0);
            if (r <= RADIUS && !tome) {
                // The heave: earth and ice thrown up along the front of the wave, and fissures running out from the middle
                int points = (int) Math.min(120, 16 + r * 4);
                ring(world, debris, center, r, points, r * 0.13, 0.3);
                if (age % 2 == 0) ring(world, ParticleTypes.CLOUD, center, r, points / 3, 0, 0.2);
                for (int k = 0; k < 7; k++) {
                    double a = (seed % 100) * 0.07 + k * MathHelper.TAU / 7 + Math.sin(r * 0.3 + k) * 0.08;
                    Vec3d at = center.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.1, 0.05, 0.1, 0.01);
                    world.spawnParticles(debris, at.x, at.y + 0.3, at.z, 2, 0.15, 0.2, 0.15, 0.15);
                }
            }
            if (r <= RADIUS) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (hit.contains(p.getUuid())) continue;
                    double d = flatDistance(p.getPos(), center);
                    if (d > r || Math.abs(p.getY() - center.y) > 6) continue;
                    hit.add(p.getUuid());
                    if (d < r - SPEED - 1.5 || !p.isOnGround()) continue; // jumped it
                    float damage = (float) MathHelper.lerp(d / RADIUS, centerDamage, edgeDamage);
                    boss.crush(p, damage);
                    boss.launch(p, p.getPos().subtract(center), 0.6 + 0.6 * (1 - d / RADIUS), 0.75);
                }
                if (age % 4 == 0) world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.5f, 0.4f);
            }
            for (int i = 0; i < AFTERSHOCKS.length; i++) {
                if (age != AFTERSHOCKS[i]) continue;
                if (tome) net.ragnar.fmscraft.compat.MagicSpells.earthquakeShock(world, casterId, center, i + 1);
                world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.5f, 0.5f);
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.5f, 0.3f);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (!p.isOnGround() || flatDistance(p.getPos(), center) > RADIUS || Math.abs(p.getY() - center.y) > 6) continue;
                    boss.crush(p, i == 0 ? 4 : 3);
                    p.setVelocity(p.getVelocity().add(0, i == 0 ? 0.45 : 0.32, 0));
                    p.velocityModified = true;
                }
                for (int k = 0; k < (tome ? 0 : 40); k++) {
                    double a = world.random.nextDouble() * MathHelper.TAU, rr = world.random.nextDouble() * RADIUS;
                    world.spawnParticles(debris, center.x + Math.cos(a) * rr, center.y + 0.2, center.z + Math.sin(a) * rr, 2, 0.2, 0.1, 0.2, 0.2);
                }
            }
            return age < AFTERSHOCKS[AFTERSHOCKS.length - 1] + 2;
        }
    }

    // ---- Ice Cannon (the Tome of Stone Cannon, in ice): great icicles spin up over his head, then fire dead straight ----

    static final class IceSpike extends Hazard {
        static final int CHARGE = 30, LOCK = 8, STUCK = 20;
        static final double SPEED = 2.4, RANGE = 60, DIRECT_MARGIN = 0.45, GRAZE = 1.0;
        static final float SIZE = 2.2f, MAX_SPIN = 1.5f;

        /** One piece of the spike: width, length, where it starts along the spike's axis, and its twist. */
        record Part(BlockState state, float width, float length, float start, float twist) {
        }

        static final Part[] PARTS = {
                new Part(PACKED, 0.34f, 0.36f, -0.7f, 0f),
                new Part(BLUE, 0.28f, 0.36f, -0.36f, (float) Math.toRadians(45)),
                new Part(ICE, 0.22f, 0.4f, -0.02f, 0f),
                new Part(ICE, 0.12f, 0.36f, 0.36f, (float) Math.toRadians(45)),
        };

        enum Phase { CHARGING, FLYING, STUCK }

        final DisplayEntity.BlockDisplayEntity[] parts = new DisplayEntity.BlockDisplayEntity[PARTS.length];
        final int slot, fireAt;
        final UUID target;
        final float damage;
        final Set<UUID> hit = new HashSet<>();
        Phase phase = Phase.CHARGING;
        Vec3d pos, dir = new Vec3d(0, 0, 1), aim;
        float spin;
        double travelled;
        int stuck;

        IceSpike(ServerWorld world, LalliEntity boss, int slot, int fireAt, UUID target, float damage) {
            this.slot = slot;
            this.fireAt = fireAt;
            this.target = target;
            this.damage = damage;
            this.pos = holdPoint(boss);
            for (int i = 0; i < PARTS.length; i++) {
                DisplayEntity.BlockDisplayEntity d = block(world, PARTS[i].state(), pos, 1, 1, 1, 0, 0, 0);
                if (d == null) continue;
                d.setTeleportDuration(1);
                d.setTransformation(partTransform(PARTS[i], dir, 0, 0.05f));
                parts[i] = d;
            }
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 1.4f, 0.6f);
        }

        /** Over his head, the three side by side. */
        Vec3d holdPoint(LalliEntity boss) {
            Vec3d forward = Vec3d.fromPolar(0, boss.getBodyYaw());
            Vec3d side = new Vec3d(-forward.z, 0, forward.x);
            return boss.getPos().add(0, boss.getHeight() + 1.3 + (slot == 0 ? 0.7 : 0), 0).add(side.multiply(slot * 2.0)).add(forward.multiply(0.4));
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            switch (phase) {
                case CHARGING -> {
                    float t = Math.min(1f, age / (float) fireAt);
                    // Spins up with an ease-in so the last moment really screams
                    spin += MAX_SPIN * (0.1f + 0.9f * t * t);
                    pos = holdPoint(boss);
                    ServerPlayerEntity p = boss.fighter(target);
                    if (p != null && (aim == null || age < fireAt - LOCK)) aim = p.getBoundingBox().getCenter();
                    if (aim != null) dir = aim.subtract(pos).normalize();
                    pose(SIZE * (0.3f + 0.7f * Math.min(1f, age / (float) CHARGE * 1.4f)));
                    if (age % 2 == 0) {
                        Vec3d off = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian()).normalize().multiply(1.2);
                        Vec3d from = pos.add(off);
                        world.spawnParticles(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, 0, -off.x, -off.y, -off.z, 0.15);
                    }
                    if (t > 0.5f) world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.05);
                    // Set on you a moment before it goes: a dotted line where
                    if (age >= fireAt - LOCK && aim != null && age % 2 == 0) line(world, FROST, pos, pos.add(dir.multiply(30)), 1.0);
                    if (age % 4 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 0.6f + 0.6f * t, 0.6f + 1.2f * t);
                    if (age >= fireAt) fire(world);
                    return true;
                }
                case FLYING -> {
                    return fly(world, boss);
                }
                default -> {
                    spin += MAX_SPIN * 0.3f * (1f - stuck / (float) STUCK);
                    pose(SIZE);
                    if (++stuck < STUCK) return true;
                    world.spawnParticles(ICE_CHIPS, pos.x, pos.y, pos.z, 30, 0.4, 0.4, 0.4, 0.15);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, 1.1f);
                    return false;
                }
            }
        }

        private void fire(ServerWorld world) {
            phase = Phase.FLYING;
            world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.2);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.4f, 0.6f);
        }

        private boolean fly(ServerWorld world, LalliEntity boss) {
            spin += MAX_SPIN;
            Vec3d from = pos;
            Vec3d to = from.add(dir.multiply(Math.min(SPEED, RANGE - travelled)));
            var wall = world.raycast(new net.minecraft.world.RaycastContext(from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
            boolean blocked = wall.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
            Vec3d end = blocked ? wall.getPos() : to;
            // Drilling through whoever is in its way
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hit.contains(p.getUuid())) continue;
                Box box = p.getBoundingBox();
                float dealt;
                if (box.expand(DIRECT_MARGIN).raycast(from, end).isPresent()) dealt = damage;
                else if (box.expand(GRAZE).raycast(from, end).isPresent()) dealt = 4;
                else continue;
                hit.add(p.getUuid());
                boss.strike(p, dealt);
                p.addVelocity(dir.x * (dealt >= damage ? 0.8 : 0.2), 0.15, dir.z * (dealt >= damage ? 0.8 : 0.2));
                p.velocityModified = true;
                boss.chill(p, 40);
                Vec3d c = box.getCenter();
                world.spawnParticles(ICE_CHIPS, c.x, c.y, c.z, dealt >= damage ? 25 : 8, 0.3, 0.4, 0.3, 0.15);
                world.playSound(null, c.x, c.y, c.z, dealt >= damage ? SoundEvents.ITEM_TRIDENT_HIT : SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.3f, 0.8f);
            }
            pos = end;
            travelled += from.distanceTo(end);
            pose(SIZE);
            for (int i = 0; i < 4; i++) {
                Vec3d p = from.lerp(end, i / 4.0);
                world.spawnParticles(i % 2 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            }
            if (blocked) {
                pos = wall.getPos().subtract(dir.multiply(0.6));
                phase = Phase.STUCK;
                pose(SIZE);
                world.spawnParticles(ICE_CHIPS, end.x, end.y, end.z, 30, 0.3, 0.3, 0.3, 0.2);
                world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.5f, 1.2f);
                world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, 0.7f);
                return true;
            }
            return travelled < RANGE - 1.0e-6;
        }

        private void pose(float size) {
            for (int i = 0; i < PARTS.length; i++) {
                DisplayEntity.BlockDisplayEntity d = parts[i];
                if (d == null) continue;
                d.setPosition(pos.x, pos.y, pos.z);
                d.setTransformation(partTransform(PARTS[i], dir, spin, size));
                d.setStartInterpolation(0);
                d.setInterpolationDuration(1);
            }
        }

        /** Lays a part along {@code dir} (the block's up axis becomes the spike's axis) and spins it about that axis. */
        static AffineTransformation partTransform(Part p, Vec3d dir, float spin, float size) {
            Vector3f axis = dir.toVector3f().normalize();
            float angle = (spin + p.twist()) % MathHelper.TAU;
            return new AffineTransformation(new org.joml.Matrix4f()
                    .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis))
                    .scale(size)
                    .rotateY(angle)
                    .translate(0, p.start(), 0)
                    .scale(p.width(), p.length(), p.width())
                    .translate(-0.5f, 0, -0.5f));
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : parts) discard(d);
        }
    }

    // ---- Blade Throw: his ice blade, spinning flat through the air at you ----

    static final class ThrownBlade extends Hazard {
        static final double SPEED = 1.5, RANGE = 46, REACH = 1.5;
        static final float LENGTH = 4.0f;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        final List<float[]> shapes = new ArrayList<>();
        final Vec3d dir;
        final float damage;
        Vec3d pos;
        float spin;

        ThrownBlade(ServerWorld world, Vec3d from, Vec3d dir, float damage) {
            this.pos = from;
            this.dir = dir.normalize();
            this.damage = damage;
            // Length along x, flat: blade, its glassy edge, the guard and the grip
            add(world, PACKED, new float[]{-1.4f, 0.32f, 2.8f, 0.08f, 0f});
            add(world, ICE, new float[]{-1.6f, 0.14f, 3.0f, 0.05f, 0.2f});
            add(world, BLUE, new float[]{1.35f, 0.7f, 0.14f, 0.14f, 0f});
            add(world, BLUE, new float[]{1.45f, 0.14f, 0.6f, 0.12f, 0f});
        }

        /** shape: x start (from the middle), width (z), length (x), thickness (y), z offset. */
        private void add(ServerWorld world, BlockState state, float[] shape) {
            DisplayEntity.BlockDisplayEntity d = block(world, state, pos, 1, 1, 1, 0, 0, 0);
            if (d == null) return;
            d.setTeleportDuration(1);
            parts.add(d);
            shapes.add(shape);
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            spin += 0.9f;
            Vec3d next = pos.add(dir.multiply(SPEED));
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getBoundingBox().expand(REACH, 0.6, REACH).intersects(new Box(pos, next))) {
                    Vec3d c = p.getBoundingBox().getCenter();
                    if (boss.strike(p, damage)) {
                        boss.launch(p, dir, 1.6, 0.5);
                        boss.freeze(p, 40, 6, 0);
                    }
                    shatter(world, c);
                    return false;
                }
            }
            if (age * SPEED > RANGE || boss.blocked(pos, next)) {
                shatter(world, pos);
                return false;
            }
            pos = next;
            for (int i = 0; i < parts.size(); i++) {
                float[] s = shapes.get(i);
                DisplayEntity.BlockDisplayEntity d = parts.get(i);
                d.setPosition(pos.x, pos.y, pos.z);
                d.setTransformation(new AffineTransformation(new org.joml.Matrix4f()
                        .rotateY(spin)
                        .translate(s[0] * LENGTH / 3f, -s[3] / 2, -s[1] / 2 + s[4])
                        .scale(s[2] * LENGTH / 3f, s[3], s[1])));
                d.setStartInterpolation(0);
                d.setInterpolationDuration(1);
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 3, 0.6, 0.1, 0.6, 0.02);
            if (age % 3 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 0.6f);
            return true;
        }

        private void shatter(ServerWorld world, Vec3d at) {
            world.spawnParticles(ICE_CHIPS, at.x, at.y, at.z, 60, 0.6, 0.5, 0.6, 0.25);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, BLUE), at.x, at.y, at.z, 30, 0.5, 0.4, 0.5, 0.2);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.5f, 0.6f);
            remove();
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : parts) discard(d);
            parts.clear();
        }
    }

    // ---- The crystals that circle him while he hangs over the pole ----

    static final class Halo extends Hazard {
        final List<DisplayEntity.BlockDisplayEntity> crystals = new ArrayList<>();
        final int count;
        double radius = 2.5;

        Halo(ServerWorld world, Vec3d at, int count) {
            this.count = count;
            for (int i = 0; i < count; i++) {
                DisplayEntity.BlockDisplayEntity d = block(world, i % 3 == 0 ? BLUE : i % 3 == 1 ? PACKED : ICE, at, 0.7f, 1.1f, 0.7f, i, 0.6f, i * 1.3f);
                if (d != null) crystals.add(d);
            }
        }

        @Override
        boolean tick(ServerWorld world, LalliEntity boss) {
            age++;
            Vec3d c = boss.getPos().add(0, boss.getHeight() * 0.55, 0);
            radius = Math.min(9, 2.5 + age * 0.015);
            double spin = age * 0.03;
            for (int i = 0; i < crystals.size(); i++) {
                double a = spin + i * MathHelper.TAU / count;
                double tilt = Math.sin(a * 2 + age * 0.02) * 1.2;
                move(crystals.get(i), c.add(Math.cos(a) * radius, tilt, Math.sin(a) * radius));
                if ((age + i) % 10 == 0) {
                    Vec3d at = crystals.get(i).getPos();
                    world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
            return true;
        }

        /** Thrown out from him in every direction, shattering. */
        void burst(ServerWorld world) {
            for (DisplayEntity.BlockDisplayEntity d : crystals) {
                Vec3d at = d.getPos();
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, BLUE), at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.3);
                world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.2);
            }
            remove();
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : crystals) discard(d);
            crystals.clear();
        }
    }

    /** The middle block under {@code at}, for particles. */
    static BlockPos below(Vec3d at) {
        return BlockPos.ofFloored(at.x, at.y - 0.5, at.z);
    }
}
