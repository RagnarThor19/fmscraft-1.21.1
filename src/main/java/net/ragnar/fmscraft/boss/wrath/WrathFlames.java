package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * ORNAMIX's fire made solid: block displays of real fire (vanilla's own animated flames), magma and the sand he tears up,
 * scaled, turned and moved through their transformation. Each one is tagged {@link #TAG}; the arena reset wipes any a
 * stopped server left behind. Also the pieces of his two cutscenes: the cracks, the pillar and the shard storm, and the
 * Inferno's firestorm.
 */
final class WrathFlames {
    /** On every display of his fire, so tests (and anything else) can tell it from the meteors' rock. */
    static final String TAG = "fmscraft.wrath_flame";
    static final BlockState FIRE = Blocks.FIRE.getDefaultState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.getDefaultState();

    private WrathFlames() {
    }

    static DisplayEntity.BlockDisplayEntity spawn(ServerWorld world, Vec3d at, BlockState state, AffineTransformation shape) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setBrightness(Brightness.FULL);
        d.setTeleportDuration(1);
        d.setViewRange(4f);
        d.addCommandTag(TAG);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        d.setTransformation(shape);
        world.spawnEntity(d);
        return d;
    }

    /** Eases {@code d} into {@code shape} over {@code ticks}, starting now. */
    static void animate(DisplayEntity.BlockDisplayEntity d, AffineTransformation shape, int ticks) {
        if (d == null) return;
        d.setTransformation(shape);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(ticks);
    }

    /**
     * A block standing on its entity's spot: turned {@code yaw} radians about Y, pushed {@code out} along its own x,
     * lifted {@code y}, and {@code sx} x {@code sy} x {@code sz} big (x along {@code out}), centred on x and z. Every
     * number goes into it linearly, so easing between two of these with the same yaw grows or slides it smoothly.
     */
    static AffineTransformation shape(float yaw, float out, float y, float sx, float sy, float sz) {
        Quaternionf turn = new Quaternionf().rotateY(yaw);
        Vector3f move = turn.transform(new Vector3f(out - 0.5f * sx, y, -0.5f * sz));
        return new AffineTransformation(move, turn, new Vector3f(sx, Math.max(0.001f, sy), sz), null);
    }

    /** The yaw {@link #shape} needs for its x to point along {@code dir}. */
    static float yawAlong(Vec3d dir) {
        return (float) Math.atan2(-dir.z, dir.x);
    }

    static void discard(List<DisplayEntity.BlockDisplayEntity> displays) {
        for (DisplayEntity.BlockDisplayEntity d : displays) if (d != null) d.discard();
        displays.clear();
    }

    // ---- Cracks: glowing seams of magma spreading out over the sand from him, that burst into walls of flame ----

    static final class Cracks extends WrathHazards.Hazard {
        static final float WIDTH = 0.32f, FLAME_GAP = 1.4f;
        static final int ERUPT_RISE = 3, BURN = 26, SINK = 8;
        final Vec3d center;
        final float[] angles, branchAngles, branchStart;
        final List<DisplayEntity.BlockDisplayEntity> seams = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> flames = new ArrayList<>();
        final List<Float> flameYaw = new ArrayList<>();
        float length;
        int erupted = -1, pendingTicks = -1;

        Cracks(Vec3d center, int count, Random random) {
            this.center = center;
            angles = new float[count];
            branchAngles = new float[count];
            branchStart = new float[count];
            float offset = random.nextFloat() * MathHelper.TAU;
            for (int i = 0; i < count; i++) {
                angles[i] = offset + i * MathHelper.TAU / count + (random.nextFloat() - 0.5f) * 0.35f;
                branchAngles[i] = angles[i] + (random.nextBoolean() ? 1 : -1) * (0.35f + random.nextFloat() * 0.3f);
                branchStart[i] = 0.35f + random.nextFloat() * 0.2f;
            }
        }

        private Vec3d along(float yaw, double d) {
            return center.add(Math.cos(yaw) * d, 0, -Math.sin(yaw) * d);
        }

        /** Spreads them out to {@code to} blocks long over {@code ticks}, the ground grinding. */
        void grow(ServerWorld world, float to, int ticks) {
            if (seams.isEmpty()) {
                for (int i = 0; i < angles.length; i++) {
                    seams.add(spawn(world, center.add(0, 0.012, 0), MAGMA, shape(angles[i], 0, 0, 0.01f, 0.05f, WIDTH)));
                    seams.add(spawn(world, along(angles[i], length * branchStart[i]).add(0, 0.014, 0), MAGMA,
                            shape(branchAngles[i], 0, 0, 0.01f, 0.04f, WIDTH * 0.7f)));
                }
                // Spread out on the next tick, once the clients have them
                length = to;
                pendingTicks = ticks;
                return;
            }
            length = to;
            spread(world, ticks);
        }

        private void spread(ServerWorld world, int ticks) {
            float to = length;
            for (int i = 0; i < angles.length; i++) {
                animate(seams.get(2 * i), shape(angles[i], to / 2, 0, to, 0.05f, WIDTH), ticks);
                float branch = to * 0.45f;
                DisplayEntity.BlockDisplayEntity b = seams.get(2 * i + 1);
                Vec3d start = along(angles[i], to * branchStart[i]);
                if (b != null) b.setPosition(start.x, start.y + 0.014, start.z);
                animate(b, shape(branchAngles[i], branch / 2, 0, branch, 0.04f, WIDTH * 0.7f), ticks);
                Vec3d end = along(angles[i], to);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()), end.x, end.y + 0.1, end.z, 8, 0.3, 0.05, 0.3, 0.1);
                world.spawnParticles(ParticleTypes.LAVA, end.x, end.y + 0.1, end.z, 1, 0.2, 0, 0.2, 0);
            }
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }

        /** Every seam bursts into a wall of flame, which burns a moment and sinks back into the sand. */
        void erupt(ServerWorld world) {
            if (erupted >= 0) return;
            erupted = 0;
            Random r = world.random;
            for (int i = 0; i < angles.length; i++) {
                for (float d = 1.2f; d <= length; d += FLAME_GAP) {
                    Vec3d at = along(angles[i], d);
                    flames.add(spawn(world, at, FIRE, shape(angles[i], 0, 0, 1.3f, 0.1f, 1.3f)));
                    flameYaw.add(angles[i] + (r.nextFloat() - 0.5f) * 0.6f);
                }
            }
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (pendingTicks >= 0) {
                spread(world, pendingTicks);
                pendingTicks = -1;
            }
            if (erupted < 0) return true;
            int e = ++erupted;
            Random r = world.random;
            // (e == 1 is the tick they were made: they shoot up on the next)
            if (e == 2 || e % 4 == 0 && e > 2 && e < ERUPT_RISE + BURN) {
                // Up out of the seams, highest nearest him, then licking up and down
                for (int i = 0; i < flames.size(); i++) {
                    float tall = (e == 2 ? 4.2f : 3.0f + r.nextFloat() * 1.4f) * (1f - 0.35f * (i % 8) / 8f);
                    animate(flames.get(i), shape(flameYaw.get(i), 0, 0, 1.3f, tall, 1.3f), e == 2 ? ERUPT_RISE : 4);
                }
            }
            if (e == ERUPT_RISE + BURN) {
                for (int i = 0; i < flames.size(); i++) animate(flames.get(i), shape(flameYaw.get(i), 0, 0, 1.1f, 0.01f, 1.1f), SINK);
                for (int i = 0; i < seams.size(); i++) {
                    float yaw = i % 2 == 0 ? angles[i / 2] : branchAngles[i / 2];
                    float len = i % 2 == 0 ? length : length * 0.45f;
                    animate(seams.get(i), shape(yaw, len / 2, -0.06f, len, 0.01f, 0.01f), SINK);
                }
            }
            if (e < ERUPT_RISE + BURN && e % 3 == 0 && !flames.isEmpty()) {
                DisplayEntity.BlockDisplayEntity f = flames.get(r.nextInt(flames.size()));
                if (f != null) world.spawnParticles(ParticleTypes.LAVA, f.getX(), f.getY() + 0.5, f.getZ(), 1, 0.3, 0.3, 0.3, 0);
            }
            if (e % 6 == 1 && e < ERUPT_RISE + BURN) world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            return e < ERUPT_RISE + BURN + SINK;
        }

        @Override
        void remove() {
            WrathFlames.discard(seams);
            WrathFlames.discard(flames);
        }
    }

    // ---- A pillar of fire: a column of flames crossed every way, rising, roaring, sinking ----

    static final class Pillar extends WrathHazards.Hazard {
        static final int LAYERS = 4;
        final float width;
        final List<DisplayEntity.BlockDisplayEntity> flames = new ArrayList<>();
        Vec3d base;
        float height = 0.1f;
        int sinking = -1, sinkTicks, pendingTicks = -1, growUntil;

        Pillar(ServerWorld world, Vec3d base, float width) {
            this.base = base;
            this.width = width;
            for (int i = 0; i < LAYERS; i++) flames.add(spawn(world, base, FIRE, shape(i * MathHelper.PI / LAYERS / 2, 0, 0, width, height, width)));
        }

        void to(float height, int ticks) {
            this.height = height;
            if (age < 1) {
                // Just made: grow on the next tick, once the clients have it
                pendingTicks = ticks;
                return;
            }
            growUntil = age + ticks;
            for (int i = 0; i < flames.size(); i++) {
                float w = width * (i % 2 == 0 ? 1f : 0.75f);
                animate(flames.get(i), shape(i * MathHelper.PI / LAYERS / 2, 0, 0, w, height * (i % 2 == 0 ? 1f : 1.15f), w), ticks);
            }
        }

        void moveTo(Vec3d base) {
            this.base = base;
            for (DisplayEntity.BlockDisplayEntity d : flames) if (d != null) d.setPosition(base.x, base.y, base.z);
        }

        void end(int ticks) {
            if (sinking >= 0) return;
            sinking = 0;
            sinkTicks = ticks;
            for (int i = 0; i < flames.size(); i++) animate(flames.get(i), shape(i * MathHelper.PI / LAYERS / 2, 0, 0, width * 0.4f, 0.01f, width * 0.4f), ticks);
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (sinking >= 0) return ++sinking < sinkTicks;
            if (pendingTicks >= 0 && age >= 2) {
                int ticks = pendingTicks;
                pendingTicks = -1;
                to(height, ticks);
                return true;
            }
            if (age % 5 == 0 && pendingTicks < 0 && age >= growUntil) {
                // Licking: each layer a little taller or shorter
                for (int i = 0; i < flames.size(); i++) {
                    float w = width * (i % 2 == 0 ? 1f : 0.75f);
                    float h = height * (0.9f + world.random.nextFloat() * 0.25f);
                    animate(flames.get(i), shape(i * MathHelper.PI / LAYERS / 2, 0, 0, w, h, w), 5);
                }
            }
            if (age % 3 == 0) boss.emit(world, ParticleTypes.LARGE_SMOKE, base.add(0, height + 0.5, 0), 2, 0.4, 0.3, 0.4, 0.03);
            return true;
        }

        @Override
        void remove() {
            WrathFlames.discard(flames);
        }
    }

    // ---- The shard storm: chunks of the pit torn up and whirled round him, closing in, then flung away ----

    static final class Shards extends WrathHazards.Hazard {
        static final int FLY = 26;
        static final BlockState[] ROCK = {Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(),
                Blocks.NETHERRACK.getDefaultState(), Blocks.SMOOTH_SANDSTONE.getDefaultState(), Blocks.GILDED_BLACKSTONE.getDefaultState(),
                Blocks.SHROOMLIGHT.getDefaultState()};
        final Vec3d center;
        final int gather;
        final List<Shard> shards = new ArrayList<>();
        int flung = -1;

        static final class Shard {
            final DisplayEntity.BlockDisplayEntity display;
            final double radius, lift, speed;
            final float size;
            double angle;
            Vec3d pos, vel = Vec3d.ZERO;

            Shard(DisplayEntity.BlockDisplayEntity display, double angle, double radius, double lift, double speed, float size, Vec3d pos) {
                this.display = display;
                this.angle = angle;
                this.radius = radius;
                this.lift = lift;
                this.speed = speed;
                this.size = size;
                this.pos = pos;
            }
        }

        /** Torn up round {@code center} at once; they close in on him over {@code gather} ticks. */
        Shards(ServerWorld world, Vec3d center, int count, int gather) {
            this.center = center;
            this.gather = gather;
            Random r = world.random;
            for (int i = 0; i < count; i++) {
                double a = i * MathHelper.TAU / count + r.nextDouble() * 0.4, rad = 5 + r.nextDouble() * 4;
                Vec3d at = center.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                float size = 0.45f + r.nextFloat() * 0.5f;
                DisplayEntity.BlockDisplayEntity d = spawn(world, at, ROCK[i % ROCK.length], spin(0, size));
                shards.add(new Shard(d, a, rad, 1.2 + r.nextDouble() * 3.5, 0.03 + r.nextDouble() * 0.03, size, at));
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()), at.x, at.y + 0.2, at.z, 10, 0.3, 0.1, 0.3, 0.1);
            }
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }

        private static AffineTransformation spin(float t, float size) {
            Quaternionf q = new Quaternionf().rotateXYZ(t * 0.21f, t * 0.17f, t * 0.11f);
            Vector3f half = q.transform(new Vector3f(-0.5f * size, -0.5f * size, -0.5f * size));
            return new AffineTransformation(half, q, new Vector3f(size), null);
        }

        /** They fly out every way, burning. */
        void fling(ServerWorld world) {
            if (flung >= 0) return;
            flung = 0;
            Random r = world.random;
            for (Shard s : shards) {
                Vec3d out = s.pos.subtract(center).multiply(1, 0, 1);
                out = out.lengthSquared() > 1e-4 ? out.normalize() : new Vec3d(1, 0, 0);
                s.vel = out.multiply(1.1 + r.nextDouble() * 0.6).add(0, 0.5 + r.nextDouble() * 0.5, 0);
            }
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (flung < 0) {
                double u = Math.min(1, age / (double) gather);
                for (Shard s : shards) {
                    s.angle += s.speed * (1 + 5 * u * u);
                    double rad = s.radius * (1 - 0.72 * u * u);
                    double h = s.lift * Math.min(1, age / 12.0) + u * 1.5 + Math.sin(age * 0.2 + s.angle) * 0.25;
                    s.pos = center.add(Math.cos(s.angle) * rad, h, Math.sin(s.angle) * rad);
                    move(s, age);
                    if (age % 3 == 0) world.spawnParticles(ParticleTypes.FLAME, s.pos.x, s.pos.y, s.pos.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
                return true;
            }
            int f = ++flung;
            for (Shard s : shards) {
                s.pos = s.pos.add(s.vel);
                s.vel = s.vel.multiply(0.96).add(0, -0.07, 0);
                move(s, age);
                if (f % 2 == 0) world.spawnParticles(ParticleTypes.FLAME, s.pos.x, s.pos.y, s.pos.z, 1, 0.05, 0.05, 0.05, 0);
            }
            return f < FLY;
        }

        private void move(Shard s, int t) {
            if (s.display == null) return;
            s.display.setPosition(s.pos.x, s.pos.y, s.pos.z);
            animate(s.display, spin(t, s.size), 1);
        }

        @Override
        void remove() {
            for (Shard s : shards) if (s.display != null) s.display.discard();
            shards.clear();
        }
    }

    // ---- The Inferno: a wall of fire marking the circle, then a whirling firestorm filling it ----

    static final class Firestorm extends WrathHazards.Hazard {
        static final int WALL_SEGMENTS = 36, ARMS = 5, PER_ARM = 6, RAYS = 12, SINK = 8;
        final Vec3d center;
        final double radius;
        final List<DisplayEntity.BlockDisplayEntity> wall = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> arms = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> floor = new ArrayList<>();
        int erupted = -1, dying = -1;

        /** The wall rises round the circle over {@code markTicks}, while he hangs over it. */
        Firestorm(ServerWorld world, Vec3d center, double radius, int markTicks) {
            this.center = center;
            this.radius = radius;
            for (int i = 0; i < WALL_SEGMENTS; i++) {
                float a = i * MathHelper.TAU / WALL_SEGMENTS;
                wall.add(spawn(world, center, FIRE, wallShape(a, 0.05f)));
            }
            // (The rise is set on the next tick, once the clients have them)
            this.markTicks = markTicks;
        }

        private final int markTicks;

        private AffineTransformation wallShape(float a, float height) {
            float w = (float) (MathHelper.TAU * radius / WALL_SEGMENTS * 1.2);
            return shape(a, (float) radius, 0, 0.9f, height, w);
        }

        /** He's come down: the wall roars up, the ground inside glows and the storm whirls. */
        void erupt(ServerWorld world) {
            if (erupted >= 0) return;
            erupted = 0;
            for (int i = 0; i < wall.size(); i++) animate(wall.get(i), wallShape(i * MathHelper.TAU / WALL_SEGMENTS, 3.6f), 3);
            for (int i = 0; i < RAYS; i++) {
                float a = i * MathHelper.TAU / RAYS;
                floor.add(spawn(world, center.add(0, 0.015, 0), MAGMA, shape(a, (float) radius / 2, 0, (float) radius, 0.04f, 0.9f)));
            }
            for (int i = 0; i < ARMS * PER_ARM; i++) arms.add(spawn(world, center, FIRE, armShape(i, 0)));
        }

        private AffineTransformation armShape(int i, int t) {
            int arm = i / PER_ARM, k = i % PER_ARM;
            double r = 1.2 + k * (radius - 1.6) / (PER_ARM - 1);
            float a = (float) (t * 0.42 + arm * MathHelper.TAU / ARMS + r * 0.3);
            float size = 1.2f + k * 0.12f;
            return shape(a, (float) r, 0, size, 2.0f + k * 0.45f, size);
        }

        void die() {
            if (dying >= 0) return;
            dying = 0;
            for (int i = 0; i < wall.size(); i++) animate(wall.get(i), wallShape(i * MathHelper.TAU / WALL_SEGMENTS, 0.01f), SINK);
            for (int i = 0; i < arms.size(); i++) {
                AffineTransformation s = armShape(i, erupted);
                animate(arms.get(i), new AffineTransformation(s.getTranslation(), s.getLeftRotation(), new Vector3f(0.4f, 0.01f, 0.4f), null), SINK);
            }
            for (int i = 0; i < floor.size(); i++) animate(floor.get(i), shape(i * MathHelper.TAU / RAYS, (float) radius / 2, -0.05f, (float) radius, 0.01f, 0.01f), SINK);
        }

        @Override
        boolean tick(ServerWorld world, OrnamixEntity boss) {
            age++;
            if (dying >= 0) return ++dying < SINK;
            if (age == 2) {
                for (int i = 0; i < wall.size(); i++) animate(wall.get(i), wallShape(i * MathHelper.TAU / WALL_SEGMENTS, 1.6f), Math.max(1, markTicks - 2));
            }
            if (erupted >= 0) {
                int t = ++erupted;
                for (int i = 0; i < arms.size(); i++) animate(arms.get(i), armShape(i, t), 1);
                if (t % 4 == 0) {
                    for (int i = 0; i < wall.size(); i++) {
                        animate(wall.get(i), wallShape(i * MathHelper.TAU / WALL_SEGMENTS, 3.0f + world.random.nextFloat() * 1.2f), 4);
                    }
                }
            }
            return true;
        }

        @Override
        void remove() {
            WrathFlames.discard(wall);
            WrathFlames.discard(arms);
            WrathFlames.discard(floor);
        }
    }
}
