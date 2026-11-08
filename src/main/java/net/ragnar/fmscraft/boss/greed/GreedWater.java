package net.ragnar.fmscraft.boss.greed;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AnvilBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * RAGGIMEL's water made solid: block displays of ice, stained glass and snow (vanilla's own blocks; a fluid can't be
 * shown in a display), scaled, turned and moved through their transformation. Each display is tagged {@link #TAG}; the
 * arena reset wipes any a stopped server left behind. Each piece ticks on its own until it's done
 * ({@link Piece#tick}), and cleans up after itself ({@link Piece#remove}).
 */
final class GreedWater {
    static final String TAG = "fmscraft.greed_water";
    static final BlockState ICE = Blocks.ICE.getDefaultState();
    static final BlockState BLUE_ICE = Blocks.BLUE_ICE.getDefaultState();
    static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
    static final BlockState DEEP = Blocks.BLUE_STAINED_GLASS.getDefaultState();
    static final BlockState FOAM = Blocks.SNOW_BLOCK.getDefaultState();
    static final BlockState MIST = Blocks.WHITE_STAINED_GLASS.getDefaultState();

    private GreedWater() {
    }

    abstract static class Piece {
        int age;

        /** One tick; false once it's done. */
        abstract boolean tick(ServerWorld world, RaggimelEntity boss);

        /** Taken away (done, or early: the fight ended): clean up anything it put in the world. */
        abstract void remove();
    }

    static DisplayEntity.BlockDisplayEntity spawn(ServerWorld world, Vec3d at, BlockState state, AffineTransformation shape) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setBrightness(Brightness.FULL);
        d.setTeleportDuration(2);
        d.setViewRange(4f);
        d.addCommandTag(TAG);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        d.setTransformation(shape);
        world.spawnEntity(d);
        return d;
    }

    static void animate(DisplayEntity.BlockDisplayEntity d, AffineTransformation shape, int ticks) {
        if (d == null) return;
        d.setTransformation(shape);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(ticks);
    }

    /**
     * A block on its entity's spot: turned {@code yaw} radians about Y, pushed {@code out} along its own x, lifted
     * {@code y}, {@code sx} x {@code sy} x {@code sz} big, centred on x and z. Linear in every number, so two of these with
     * the same yaw ease into each other smoothly.
     */
    static AffineTransformation shape(float yaw, float out, float y, float sx, float sy, float sz) {
        Quaternionf turn = new Quaternionf().rotateY(yaw);
        Vector3f move = turn.transform(new Vector3f(out - 0.5f * sx, y, -0.5f * sz));
        return new AffineTransformation(move, turn, new Vector3f(sx, Math.max(0.001f, sy), sz), null);
    }

    /** A cube {@code size} across, centred on its spot, tumbled by {@code turn}. */
    static AffineTransformation cube(Quaternionf turn, float size) {
        Vector3f half = turn.transform(new Vector3f(-0.5f * size, -0.5f * size, -0.5f * size));
        return new AffineTransformation(half, turn, new Vector3f(size), null);
    }

    static float yawAlong(Vec3d dir) {
        return (float) Math.atan2(-dir.z, dir.x);
    }

    static void discard(List<DisplayEntity.BlockDisplayEntity> displays) {
        for (DisplayEntity.BlockDisplayEntity d : displays) if (d != null) d.discard();
        displays.clear();
    }

    // ---- A column of water: a ripple on the ground, then up it shoots, foam boiling on top, and falls back ----

    static final class Column extends Piece {
        static final int SINK = 6;
        final Vec3d base;
        final float width;
        final List<DisplayEntity.BlockDisplayEntity> core = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> foam = new ArrayList<>();
        DisplayEntity.BlockDisplayEntity ripple;
        float height;
        int charge, hold, risen = -1, falling = -1, rise;

        /**
         * A ripple on the ground for {@code charge} ticks (0 for none), then it shoots up {@code height} blocks over
         * {@code rise} ticks, stands {@code hold} ticks (forever if negative, until {@link #fall}), and falls back.
         */
        Column(ServerWorld world, Vec3d base, float width, float height, int charge, int rise, int hold) {
            this.base = base;
            this.width = width;
            this.height = height;
            this.charge = charge;
            this.rise = rise;
            this.hold = hold;
            if (charge > 0) ripple = spawn(world, base.add(0, 0.02, 0), GLASS, shape(MathHelper.PI / 4, 0, 0, width * 0.6f, 0.06f, width * 0.6f));
        }

        private AffineTransformation layer(int i, float h) {
            // Four layers: a narrow core of ice, a wider glassy shell, both turned so the column looks round
            float w = width * (i == 0 ? 0.62f : i == 1 ? 0.62f : 1f);
            float yaw = i % 2 == 0 ? 0 : MathHelper.PI / 4;
            float tall = h * (i < 2 ? 1f : 0.86f);
            return shape(yaw + (i >= 2 ? MathHelper.PI / 8 : 0), 0, 0, w, tall, w);
        }

        private AffineTransformation foamShape(int i, float h, int t) {
            float a = i * MathHelper.TAU / 3 + t * 0.35f;
            Quaternionf turn = new Quaternionf().rotateXYZ(t * 0.21f + i, t * 0.3f, t * 0.17f + i * 2);
            float size = width * (0.42f + 0.08f * MathHelper.sin(t * 0.6f + i));
            Vector3f c = new Vector3f(MathHelper.cos(a) * width * 0.25f, h + 0.1f + 0.25f * MathHelper.sin(t * 0.5f + i), MathHelper.sin(a) * width * 0.25f);
            AffineTransformation s = cube(turn, size);
            return new AffineTransformation(s.getTranslation().add(c), s.getLeftRotation(), s.getScale(), null);
        }

        void fall() {
            if (falling >= 0 || risen < 0) return;
            falling = 0;
            for (int i = 0; i < core.size(); i++) animate(core.get(i), layer(i, 0.01f), SINK);
            for (int i = 0; i < foam.size(); i++) animate(foam.get(i), foamShape(i, 0.01f, risen), SINK);
        }

        boolean up() {
            return risen >= 0 && falling < 0;
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            if (age <= charge) {
                // The ground bubbling and swelling where it'll burst
                float pulse = 0.6f + 0.25f * MathHelper.sin(age * 1.2f) + 0.35f * age / charge;
                animate(ripple, shape(MathHelper.PI / 4 + age * 0.15f, 0, 0, width * pulse, 0.06f, width * pulse), 1);
                world.spawnParticles(ParticleTypes.BUBBLE_POP, base.x, base.y + 0.2, base.z, 3, width * 0.3, 0.05, width * 0.3, 0.02);
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.SPLASH, base.x, base.y + 0.1, base.z, 6, width * 0.35, 0.05, width * 0.35, 0);
                return true;
            }
            if (risen < 0) {
                risen = 0;
                if (ripple != null) ripple.discard();
                ripple = null;
                for (int i = 0; i < 4; i++) core.add(spawn(world, base, i % 2 == 0 ? (i == 0 ? ICE : GLASS) : (i == 1 ? ICE : GLASS), layer(i, 0.2f)));
                for (int i = 0; i < 3; i++) foam.add(spawn(world, base, i == 0 ? FOAM : MIST, foamShape(i, 0.2f, 0)));
                world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 2.0f, 0.8f);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.HOSTILE, 2.0f, 0.7f);
                world.spawnParticles(ParticleTypes.SPLASH, base.x, base.y + 0.5, base.z, 60, width * 0.4, 0.4, width * 0.4, 0.5);
                return true;
            }
            int t = ++risen;
            if (falling >= 0) {
                if (falling % 2 == 0) world.spawnParticles(ParticleTypes.FALLING_WATER, base.x, base.y + height * 0.5, base.z, 12, width * 0.5, height * 0.3, width * 0.5, 0);
                return ++falling < SINK;
            }
            if (t == 1) {
                for (int i = 0; i < core.size(); i++) animate(core.get(i), layer(i, height), rise);
            }
            float h = height * Math.min(1f, t / (float) Math.max(1, rise));
            for (int i = 0; i < foam.size(); i++) animate(foam.get(i), foamShape(i, h, t), 1);
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.SPLASH, base.x, base.y + h, base.z, 6, width * 0.4, 0.2, width * 0.4, 0.2);
            if (t % 3 == 0) world.spawnParticles(ParticleTypes.FALLING_WATER, base.x, base.y + h, base.z, 6, width * 0.6, 0.2, width * 0.6, 0);
            if (t % 8 == 0) world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.8f);
            if (hold >= 0 && t >= rise + hold) fall();
            return true;
        }

        @Override
        void remove() {
            if (ripple != null) ripple.discard();
            ripple = null;
            discard(core);
            discard(foam);
        }
    }

    // ---- A waterspout: a whirling column round him, rings of water climbing it, growing up into the sky ----

    static final class Spout extends Piece {
        static final int RINGS = 6, PER_RING = 10, SINK = 8;
        final Vec3d base;
        final float radius;
        final List<DisplayEntity.BlockDisplayEntity> walls = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> rings = new ArrayList<>();
        float height, target;
        int growTicks, dying = -1, pending = -1;

        Spout(ServerWorld world, Vec3d base, float radius) {
            this.base = base;
            this.radius = radius;
            // The wall of the spout: tall glassy panels round a circle, a little apart, turning
            for (int i = 0; i < 12; i++) walls.add(spawn(world, base, i % 3 == 0 ? ICE : GLASS, wall(i, 0, 0.05f)));
            for (int r = 0; r < RINGS; r++) {
                for (int k = 0; k < PER_RING; k++) rings.add(spawn(world, base, k % 2 == 0 ? MIST : GLASS, ringPiece(r, k, 0, 0.05f)));
            }
        }

        private AffineTransformation wall(int i, int t, float h) {
            float a = i * MathHelper.TAU / 12 + t * 0.12f;
            float w = (float) (MathHelper.TAU * radius / 12 * 1.15);
            return shape(a, radius, 0, 0.35f, h, w);
        }

        private AffineTransformation ringPiece(int r, int k, int t, float h) {
            // Each ring climbs the spout and widens a little, round and round
            float climb = ((t * 0.18f + r * (1f / RINGS) * Math.max(1f, h)) % Math.max(1f, h));
            float rr = radius * (1.15f + 0.25f * climb / Math.max(1f, h));
            float a = k * MathHelper.TAU / PER_RING - t * 0.22f - r;
            float w = (float) (MathHelper.TAU * rr / PER_RING * 0.8);
            return shape(a, rr, climb, 0.25f, 0.35f, w);
        }

        /** Grows (or shrinks) to {@code to} blocks over {@code ticks}. */
        void grow(float to, int ticks) {
            target = to;
            growTicks = Math.max(1, ticks);
            if (age < 1) pending = 1;
        }

        void end() {
            if (dying >= 0) return;
            dying = 0;
            for (int i = 0; i < walls.size(); i++) animate(walls.get(i), wall(i, age, 0.01f), SINK);
            for (int i = 0; i < rings.size(); i++) animate(rings.get(i), ringPiece(i / PER_RING, i % PER_RING, age, 0.01f), SINK);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            if (dying >= 0) return ++dying < SINK;
            if (growTicks > 0) {
                height += (target - height) / growTicks;
                growTicks--;
            }
            for (int i = 0; i < walls.size(); i++) animate(walls.get(i), wall(i, age, height), 1);
            for (int i = 0; i < rings.size(); i++) animate(rings.get(i), ringPiece(i / PER_RING, i % PER_RING, age, height), 1);
            if (age % 2 == 0) {
                double a = world.random.nextDouble() * MathHelper.TAU;
                world.spawnParticles(ParticleTypes.SPLASH, base.x + Math.cos(a) * radius, base.y + world.random.nextDouble() * height, base.z + Math.sin(a) * radius, 4, 0.2, 0.3, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.CLOUD, base.x, base.y + height, base.z, 2, radius * 0.5, 0.3, radius * 0.5, 0.02);
            }
            if (age % 10 == 0) world.playSound(null, base.x, base.y + 2, base.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.HOSTILE, 3.0f, 0.6f);
            return true;
        }

        @Override
        void remove() {
            discard(walls);
            discard(rings);
        }
    }

    // ---- The wave: a wall of water, foam on its crest, rising as it rolls in ----

    static final class Wave extends Piece {
        static final int SEGMENTS = 18;
        final Vec3d dir, side;
        final double width, speed;
        final float yaw;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        Vec3d pos;
        int life;

        /** From {@code from}, rolling along {@code dir} at {@code speed} for {@code life} ticks, {@code width} wide. */
        Wave(ServerWorld world, Vec3d from, Vec3d dir, double width, double speed, int life) {
            this.pos = from;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.width = width;
            this.speed = speed;
            this.life = life;
            this.yaw = yawAlong(side);
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3d at = segment(i);
                parts.add(spawn(world, at, DEEP, body(0.5f)));
                parts.add(spawn(world, at, ICE, face(0.5f)));
                parts.add(spawn(world, at, FOAM, crest(0.5f, 0)));
            }
        }

        private Vec3d segment(int i) {
            double s = -width / 2 + (i + 0.5) * width / SEGMENTS;
            // The ends lag a little behind the middle
            return pos.add(side.multiply(s)).subtract(dir.multiply(s * s / (width * width) * 6));
        }

        private float segWidth() {
            return (float) (width / SEGMENTS * 1.1);
        }

        private AffineTransformation body(float h) {
            return new AffineTransformation(new Quaternionf().rotateY(yaw).transform(new Vector3f(-0.5f * segWidth(), 0, -1.6f)),
                    new Quaternionf().rotateY(yaw), new Vector3f(segWidth(), h, 3.2f), null);
        }

        private AffineTransformation face(float h) {
            // The front of the wave, leaning forward over its foot
            Quaternionf turn = new Quaternionf().rotateY(yaw).rotateX(-0.35f);
            return new AffineTransformation(turn.transform(new Vector3f(-0.5f * segWidth(), 0, 1.0f)), turn, new Vector3f(segWidth(), h * 0.92f, 1.2f), null);
        }

        private AffineTransformation crest(float h, int t) {
            Quaternionf turn = new Quaternionf().rotateY(yaw).rotateX(-0.6f + 0.1f * MathHelper.sin(t * 0.4f));
            return new AffineTransformation(new Quaternionf().rotateY(yaw).transform(new Vector3f(-0.5f * segWidth(), h - 0.2f, 0.8f)),
                    turn, new Vector3f(segWidth(), 0.7f, 1.6f), null);
        }

        float height() {
            return (float) Math.min(13, 1 + age * 0.45);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            pos = pos.add(dir.multiply(speed));
            float h = height();
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3d at = segment(i);
                for (int k = 0; k < 3; k++) {
                    DisplayEntity.BlockDisplayEntity d = parts.get(i * 3 + k);
                    if (d == null) continue;
                    d.setPosition(at.x, at.y, at.z);
                    animate(d, k == 0 ? body(h) : k == 1 ? face(h) : crest(h, age + i), 2);
                }
            }
            for (int i = 0; i < 6; i++) {
                Vec3d at = segment(world.random.nextInt(SEGMENTS)).add(dir.multiply(1.5));
                world.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + h, at.z, 6, 0.6, 0.2, 0.6, 0.3);
                world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 1, 0.5, 0.1, 0.5, 0.05);
            }
            if (age % 6 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            if (age % 9 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 3.0f, 0.5f);
            return age < life;
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- A ring of water rolling out over the ground (a shockwave you can see; it hurts nobody by itself) ----

    static final class Ring extends Piece {
        static final int SEGMENTS = 48, SINK = 4;
        final Vec3d center;
        final double speed, max;
        final float height;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        double radius;
        int dying = -1;

        Ring(Vec3d center, double speed, double max, float height) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.height = height;
        }

        private AffineTransformation segment(int i, double r, float h) {
            float w = (float) Math.max(0.3, MathHelper.TAU * r / SEGMENTS * 1.15);
            return shape(i * MathHelper.TAU / SEGMENTS, (float) r, 0, 0.8f, h, w);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            if (dying >= 0) return ++dying < SINK;
            radius += speed;
            if (age == 1) {
                for (int i = 0; i < SEGMENTS; i++) parts.add(spawn(world, center, i % 4 == 0 ? FOAM : i % 2 == 0 ? ICE : GLASS, segment(i, radius, height)));
            } else if (age == 2) {
                int ticks = Math.max(1, (int) Math.ceil((max - radius) / speed));
                for (int i = 0; i < parts.size(); i++) animate(parts.get(i), segment(i, radius + ticks * speed, height * 0.5f), ticks);
            }
            if (radius > max) {
                for (int i = 0; i < parts.size(); i++) animate(parts.get(i), segment(i, max, 0.01f), SINK);
                dying = 0;
                return true;
            }
            if (age % 2 == 0) {
                double a = world.random.nextDouble() * MathHelper.TAU;
                world.spawnParticles(ParticleTypes.SPLASH, center.x + Math.cos(a) * radius, center.y + 0.4, center.z + Math.sin(a) * radius, 8, 0.4, 0.1, 0.4, 0.2);
            }
            return true;
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- A water bolt from the cloud: a glassy ball with a heart of blue ice, splashing where it lands ----

    static final class Bolt extends Piece {
        static final int LIFE = 30;
        static final double HIT = 0.9;
        final float damage;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        Vec3d pos, vel;

        Bolt(ServerWorld world, Vec3d from, Vec3d vel, float damage) {
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            parts.add(spawn(world, from, GLASS, cube(new Quaternionf(), 0.55f)));
            parts.add(spawn(world, from, BLUE_ICE, cube(new Quaternionf(), 0.28f)));
            for (DisplayEntity.BlockDisplayEntity d : parts) if (d != null) d.setTeleportDuration(1);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            Vec3d next = pos.add(vel);
            // Anyone in its way
            for (ServerPlayerEntity p : boss.fighterList()) {
                if (p.getBoundingBox().expand(HIT * 0.5).raycast(pos, next).isPresent() || p.getBoundingBox().getCenter().distanceTo(next) < HIT) {
                    boss.boltHit(p, this);
                    splash(world, p.getBoundingBox().getCenter());
                    return false;
                }
            }
            BlockPos at = BlockPos.ofFloored(next);
            BlockState there = world.getBlockState(at);
            // (Barriers don't stop water: there are none in his arenas, and an invisible wall shouldn't eat it)
            if (!there.isOf(Blocks.BARRIER) && !there.getCollisionShape(world, at).isEmpty() || age > LIFE) {
                splash(world, pos);
                return false;
            }
            pos = next;
            vel = vel.add(0, -0.03, 0);
            Quaternionf turn = new Quaternionf().rotateXYZ(age * 0.5f, age * 0.3f, 0);
            for (int i = 0; i < parts.size(); i++) {
                DisplayEntity.BlockDisplayEntity d = parts.get(i);
                if (d == null) continue;
                d.setPosition(pos.x, pos.y, pos.z);
                animate(d, cube(turn, i == 0 ? 0.55f : 0.28f), 1);
            }
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0);
            return true;
        }

        private void splash(ServerWorld world, Vec3d at) {
            world.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 25, 0.4, 0.2, 0.4, 0.3);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.2, at.z, 6, 0.3, 0.2, 0.3, 0.05);
            if (world.random.nextInt(3) == 0) world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 1.0f, 1.2f + world.random.nextFloat() * 0.4f);
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- The anvil: shaking over your head, its shadow under you, then down ----

    static final class Anvil extends Piece {
        static final int SHAKE = 28, LOCK = 20, FALL = 3, LIE = 12, SINK = 8;
        static final float SIZE = 2.2f, HIGH = 9f;
        static final double RADIUS = 1.9;
        final java.util.UUID target;
        final float damage;
        final DisplayEntity.BlockDisplayEntity anvil, shadow;
        Vec3d spot;
        double ground;
        int landed = -1;

        Anvil(ServerWorld world, ServerPlayerEntity target, float damage) {
            this.target = target.getUuid();
            this.damage = damage;
            this.spot = target.getPos();
            this.ground = target.getY();
            BlockState state = Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.Type.HORIZONTAL.random(world.random));
            anvil = spawn(world, spot.add(0, HIGH, 0), state, held(0, 0));
            shadow = spawn(world, spot.add(0, 0.03, 0), Blocks.BLACK_STAINED_GLASS.getDefaultState(), shadowShape(0.4f));
            world.playSound(null, spot.x, spot.y + HIGH, spot.z, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.spawnParticles(ParticleTypes.CLOUD, spot.x, spot.y + HIGH, spot.z, 12, 0.8, 0.4, 0.8, 0.02);
        }

        private static AffineTransformation held(int t, float shake) {
            // Shaking harder and harder as it gets ready to drop
            Quaternionf turn = new Quaternionf().rotateXYZ(shake * MathHelper.sin(t * 2.3f), 0, shake * MathHelper.cos(t * 1.9f));
            Vector3f half = turn.transform(new Vector3f(-0.5f * SIZE, 0, -0.5f * SIZE));
            return new AffineTransformation(half, turn, new Vector3f(SIZE), null);
        }

        private static AffineTransformation shadowShape(float grow) {
            return shape(0, 0, 0, SIZE * grow * 1.4f, 0.04f, SIZE * grow * 1.4f);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            if (landed >= 0) {
                int l = ++landed;
                if (l == LIE) {
                    // Into the ground it goes
                    animate(anvil, new AffineTransformation(new Vector3f(-0.5f * SIZE, -SIZE, -0.5f * SIZE), null, new Vector3f(SIZE), null), SINK);
                    animate(shadow, shadowShape(0.01f), SINK);
                }
                return l < LIE + SINK;
            }
            if (age <= SHAKE) {
                // Following you (a little behind), then locked
                ServerPlayerEntity p = boss.fighterById(target);
                if (p != null && age <= LOCK) {
                    spot = spot.lerp(p.getPos(), 0.35);
                    ground = boss.groundUnder(spot, p.getY());
                }
                float shake = 0.04f + 0.18f * age / SHAKE;
                Vec3d jitter = new Vec3d((world.random.nextDouble() - 0.5) * shake, 0, (world.random.nextDouble() - 0.5) * shake);
                if (anvil != null) anvil.setPosition(spot.x + jitter.x, ground + HIGH, spot.z + jitter.z);
                animate(anvil, held(age, shake), 1);
                if (shadow != null) shadow.setPosition(spot.x, ground + 0.03, spot.z);
                animate(shadow, shadowShape(0.4f + 0.6f * age / SHAKE), 1);
                if (age % 5 == 0) world.playSound(null, spot.x, ground + HIGH, spot.z, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 1.2f, 0.6f + age / 40f);
                if (age == LOCK) world.playSound(null, spot.x, ground + HIGH, spot.z, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.HOSTILE, 1.5f, 0.5f);
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.CRIT, spot.x, ground + HIGH - 0.2, spot.z, 3, 0.6, 0.1, 0.6, 0.05);
                if (age == SHAKE) {
                    // Down
                    if (anvil != null) anvil.setPosition(spot.x, ground, spot.z);
                    if (anvil != null) anvil.setTeleportDuration(FALL);
                    animate(anvil, held(age, 0), FALL);
                }
                return true;
            }
            if (age == SHAKE + FALL) {
                landed = 0;
                boss.anvilLands(world, this);
                world.playSound(null, spot.x, ground, spot.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.5f, 0.5f);
                world.playSound(null, spot.x, ground, spot.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.0f, 0.7f);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ANVIL.getDefaultState()), spot.x, ground + 0.4, spot.z, 40, 1.0, 0.2, 1.0, 0.2);
                world.spawnParticles(ParticleTypes.CLOUD, spot.x, ground + 0.2, spot.z, 20, 1.2, 0.1, 1.2, 0.08);
            }
            return true;
        }

        Vec3d landing() {
            return new Vec3d(spot.x, ground, spot.z);
        }

        @Override
        void remove() {
            if (anvil != null) anvil.discard();
            if (shadow != null) shadow.discard();
        }
    }

    // ---- The cloud he rides: Tome of Clouds' cloud, big enough for him, riding along under his feet ----

    static final class Cloud extends Piece {
        /** The Tome of Clouds' puffs {minX, minY, minZ, sizeX, sizeY, sizeZ} (feet at 0), scaled up for him. */
        static final float[][] PUFFS = {
                {-1.10f, -0.45f, -1.10f, 2.20f, 0.42f, 2.20f}, {-0.80f, -0.68f, -0.80f, 1.60f, 0.30f, 1.60f},
                {-1.50f, -0.40f, -0.60f, 0.55f, 0.30f, 1.20f}, {0.95f, -0.38f, -0.45f, 0.55f, 0.28f, 1.00f},
                {-0.60f, -0.39f, -1.50f, 1.10f, 0.29f, 0.55f}, {-0.35f, -0.41f, 0.95f, 1.00f, 0.31f, 0.55f},
                {-1.05f, -0.10f, -0.95f, 0.70f, 0.22f, 0.70f}, {0.40f, -0.10f, 0.30f, 0.60f, 0.17f, 0.70f},
                {0.45f, -0.10f, -0.90f, 0.50f, 0.14f, 0.45f}};
        static final float SCALE = 1.8f;
        final List<DisplayEntity.BlockDisplayEntity> puffs = new ArrayList<>();
        final Entity rider;
        final float drop;
        int gone = -1;

        /** Riding {@code rider}: each puff sits on him as a passenger, moved down to his feet. */
        Cloud(ServerWorld world, Entity rider) {
            this.rider = rider;
            this.drop = rider.getHeight();
            for (int i = 0; i < PUFFS.length; i++) {
                DisplayEntity.BlockDisplayEntity d = spawn(world, rider.getPos(), i < 2 ? FOAM : i % 2 == 0 ? FOAM : MIST, puff(i, 0.05f));
                if (d == null) continue;
                d.startRiding(rider, true);
                puffs.add(d);
            }
            world.playSound(null, rider.getX(), rider.getY(), rider.getZ(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.spawnParticles(ParticleTypes.CLOUD, rider.getX(), rider.getY(), rider.getZ(), 40, 1.6, 0.3, 1.6, 0.05);
        }

        private AffineTransformation puff(int i, float grow) {
            float[] p = PUFFS[i];
            float s = SCALE * grow;
            // Passengers sit on top of him: down by his height to his feet
            return new AffineTransformation(new Vector3f(p[0] * s, -drop + p[1] * s - 0.05f, p[2] * s), null,
                    new Vector3f(Math.max(0.01f, p[3] * s), Math.max(0.01f, p[4] * s), Math.max(0.01f, p[5] * s)), null);
        }

        void burst(ServerWorld world) {
            if (gone >= 0) return;
            gone = 0;
            for (int i = 0; i < puffs.size(); i++) animate(puffs.get(i), puff(i, 0.05f), 4);
            world.spawnParticles(ParticleTypes.CLOUD, rider.getX(), rider.getY() - 0.3, rider.getZ(), 60, 2.0, 0.4, 2.0, 0.1);
            world.playSound(null, rider.getX(), rider.getY(), rider.getZ(), SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            if (gone >= 0) return ++gone < 5;
            if (age == 2) for (int i = 0; i < puffs.size(); i++) animate(puffs.get(i), puff(i, 1f), 5);
            if (age > 6 && age % 4 == 0) {
                // Breathing, and shedding wisps
                for (int i = 0; i < puffs.size(); i++) animate(puffs.get(i), puff(i, 1f + 0.06f * MathHelper.sin(age * 0.2f + i)), 4);
                world.spawnParticles(ParticleTypes.CLOUD, rider.getX(), rider.getY() - 0.5, rider.getZ(), 2, 1.6, 0.1, 1.6, 0.01);
            }
            return true;
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : puffs) {
                d.stopRiding();
                d.discard();
            }
            puffs.clear();
        }
    }

    static Vec3d randomUnit(Random r) {
        double z = r.nextDouble() * 2.0 - 1.0, theta = r.nextDouble() * MathHelper.TAU, s = Math.sqrt(1.0 - z * z);
        return new Vec3d(s * Math.cos(theta), z, s * Math.sin(theta));
    }
}
