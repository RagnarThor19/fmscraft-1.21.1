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

    // ---- The Twister: a giant tornado of water, a funnel of spinning glass and ice that wanders where it likes ----

    /** A box {@code sx} x {@code sy} x {@code sz} centred on {@code center} (from its entity's spot), turned {@code rot}. */
    static AffineTransformation box(Vector3f center, Quaternionf rot, float sx, float sy, float sz) {
        Vector3f half = rot.transform(new Vector3f(-0.5f * sx, -0.5f * Math.max(0.001f, sy), -0.5f * sz));
        return new AffineTransformation(half.add(center), rot, new Vector3f(sx, Math.max(0.001f, sy), sz), null);
    }

    /**
     * The tornado RAGGIMEL raises in the Twister. While he casts, a whirlpool opens on the ground, a thin rope of water
     * climbs out of it and widens into a funnel 24 blocks high (stacked rings of glassy panels, each ring spinning, the
     * whole thing bending and swaying, the top lagging behind the base), chunks of the ground and ice whirling up it,
     * a cloud cap on top, spray round its foot. Once it's half up it drags everyone within 18 blocks towards it, harder
     * the closer they are (sprint away and you'll get clear). Once he lets it go it wanders: it picks a new place to go every second
     * or two (often one of you, often not), weaves as it goes, and now and then lurches off sideways. Get too close and
     * it takes you: up and round the funnel, and out, thrown a long way (see {@link RaggimelEntity#twisterFling}).
     */
    static final class Twister extends Piece {
        static final int LAYERS = 10, PANELS = 7, LAG = 2, SKIRT = 10, DEBRIS = 6, CAP = 5, FADE = 20;
        static final float HEIGHT = 24f, R0 = 1.1f, RTOP = 6.5f;
        /** Ticks a caught player is carried round before being thrown; ticks until the same one can be caught again. */
        static final int CARRY = 16, RECATCH = 30;
        /** How far out it drags you in, and how hard at its foot (added to your own speed every tick). */
        static final double PULL_RANGE = 18, PULL = 0.05;
        final List<DisplayEntity.BlockDisplayEntity> panels = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> skirt = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> debris = new ArrayList<>();
        final List<DisplayEntity.BlockDisplayEntity> cap = new ArrayList<>();
        /** Where its foot was, newest first: the funnel above follows it a little behind. */
        final java.util.ArrayDeque<Vec3d> trail = new java.util.ArrayDeque<>();
        final java.util.Map<java.util.UUID, Carry> carried = new java.util.HashMap<>();
        final java.util.Map<java.util.UUID, Integer> recatch = new java.util.HashMap<>();
        final Vec3d leashCenter;
        final double leash;
        final int form;
        final float seed;
        Vec3d pos, vel = Vec3d.ZERO, goal;
        float spin, spinSpeed = 0.04f, grow;
        int roam = -1, roamTicks, retarget, fading = -1;
        double maxSpeed;

        static final class Carry {
            float angle;
            double rise;
            int t;
        }

        /**
         * Forming at {@code spot} over {@code form} ticks (it stays put until {@link #release}); once loose it never
         * strays further than {@code leash} from {@code leashCenter}.
         */
        Twister(ServerWorld world, Vec3d spot, int form, Vec3d leashCenter, double leash) {
            this.pos = spot;
            this.form = form;
            this.leashCenter = leashCenter;
            this.leash = leash;
            this.goal = spot;
            this.seed = world.random.nextFloat() * 100f;
            BlockState ground = world.getBlockState(BlockPos.ofFloored(spot).down());
            if (ground.isAir() || ground.isOf(Blocks.BARRIER)) ground = Blocks.DIRT.getDefaultState();
            for (int i = 0; i < LAYERS; i++) {
                for (int k = 0; k < PANELS; k++) {
                    BlockState state = i >= LAYERS - 2 ? (k % 2 == 0 ? MIST : GLASS)
                            : (i + k) % 4 == 0 ? ICE : k % 3 == 0 ? DEEP : GLASS;
                    panels.add(spawn(world, spot, state, box(new Vector3f(), new Quaternionf(), 0.01f, 0.01f, 0.01f)));
                }
            }
            for (int k = 0; k < SKIRT; k++) skirt.add(spawn(world, spot, k % 3 == 0 ? FOAM : k % 3 == 1 ? DEEP : GLASS, box(new Vector3f(), new Quaternionf(), 0.01f, 0.01f, 0.01f)));
            for (int d = 0; d < DEBRIS; d++) debris.add(spawn(world, spot, d % 2 == 0 ? ground : d == 1 ? BLUE_ICE : ICE, box(new Vector3f(), new Quaternionf(), 0.01f, 0.01f, 0.01f)));
            for (int c = 0; c < CAP; c++) cap.add(spawn(world, spot, c % 2 == 0 ? MIST : FOAM, box(new Vector3f(), new Quaternionf(), 0.01f, 0.01f, 0.01f)));
            trail.addFirst(spot);
        }

        /** Let loose: it wanders for {@code ticks}, at up to {@code speed} blocks a tick, then falls apart. */
        void release(int ticks, double speed) {
            if (roam >= 0) return;
            roam = 0;
            roamTicks = ticks;
            maxSpeed = speed;
            retarget = 0;
        }

        /** How far it's formed, 0 to 1: once it's half up it can take you. */
        float formed() {
            return grow;
        }

        boolean loose() {
            return roam >= 0 && fading < 0;
        }

        float height() {
            return HEIGHT * grow;
        }

        /** The funnel's radius at {@code f} of the way up (0 the foot, 1 the top). */
        float radiusAt(float f) {
            f = MathHelper.clamp(f, 0, 1);
            return (R0 + (RTOP - R0) * (float) Math.pow(f, 1.7)) * (0.35f + 0.65f * grow);
        }

        /** The middle of the funnel {@code f} of the way up: behind the foot, the higher the further, and swaying. */
        Vec3d centerAt(float f) {
            f = MathHelper.clamp(f, 0, 1);
            int back = Math.round(f * (LAYERS - 1) * LAG);
            Vec3d foot = pos;
            int i = 0;
            for (Vec3d past : trail) {
                foot = past;
                if (i++ >= back) break;
            }
            float sway = 1.5f * (float) Math.pow(f, 1.3) * grow;
            return new Vec3d(foot.x + sway * MathHelper.sin(age * 0.09f + f * 5.5f + seed),
                    pos.y + f * height(), foot.z + sway * MathHelper.cos(age * 0.07f + f * 4.5f + seed));
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            Random r = world.random;
            if (fading >= 0) return fade(world);
            if (roam < 0) {
                // Forming: the whirlpool first, then the funnel rising out of it, spinning faster and faster
                float u = MathHelper.clamp((age - 18) / (float) Math.max(1, form - 22), 0, 1);
                grow = u * u * (3 - 2 * u);
                spinSpeed = 0.04f + 0.26f * Math.min(1f, age / (float) form);
                if (age % 2 == 0) {
                    // Spray sucked in towards the eye of the whirlpool
                    for (int i = 0; i < 4; i++) {
                        double a = r.nextDouble() * MathHelper.TAU, d = 3 + r.nextDouble() * 2.5;
                        double x = pos.x + Math.cos(a) * d, z = pos.z + Math.sin(a) * d;
                        world.spawnParticles(ParticleTypes.SPLASH, x, pos.y + 0.15, z, 0, -Math.cos(a) - Math.sin(a), 0.4, -Math.sin(a) + Math.cos(a), 0.25);
                        world.spawnParticles(ParticleTypes.CLOUD, x, pos.y + 0.2, z, 0, -Math.cos(a) * 0.6 - Math.sin(a), 0.1, -Math.sin(a) * 0.6 + Math.cos(a), 0.12);
                    }
                }
                if (age % 15 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.HOSTILE, 3.0f, 0.4f + age / (float) form * 0.4f);
                if (age == 20) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            } else {
                roam++;
                grow = 1;
                spinSpeed = 0.3f;
                wander(boss, r);
                if (roam >= roamTicks) {
                    fading = 0;
                    world.playSound(null, pos.x, pos.y + 4, pos.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 4.0f, 0.4f);
                    world.playSound(null, pos.x, pos.y + 4, pos.z, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.HOSTILE, 4.0f, 0.6f);
                    for (DisplayEntity.BlockDisplayEntity d : all()) animate(d, collapsed(d), FADE);
                    return true;
                }
            }
            spin += spinSpeed;
            trail.addFirst(pos);
            while (trail.size() > LAYERS * LAG + 1) trail.removeLast();
            if (age % 2 == 0) draw();
            catchPlayers(world, boss);
            effects(world, r);
            return true;
        }

        /** Somewhere new every second or two (one of you, or anywhere), weaving on the way, now and then a lurch. */
        private void wander(RaggimelEntity boss, Random r) {
            if (--retarget <= 0) {
                retarget = 15 + r.nextInt(40);
                List<ServerPlayerEntity> fighters = boss.fighterList();
                if (!fighters.isEmpty() && r.nextFloat() < 0.55f) {
                    ServerPlayerEntity p = fighters.get(r.nextInt(fighters.size()));
                    goal = p.getPos().add((r.nextDouble() - 0.5) * 8, 0, (r.nextDouble() - 0.5) * 8);
                } else {
                    double a = r.nextDouble() * MathHelper.TAU, d = Math.sqrt(r.nextDouble()) * leash * 0.85;
                    goal = leashCenter.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                }
                if (r.nextFloat() < 0.3f) {
                    // A lurch, off to one side
                    double a = r.nextDouble() * MathHelper.TAU, kick = 0.22 + r.nextDouble() * 0.2;
                    vel = vel.add(Math.cos(a) * kick, 0, Math.sin(a) * kick);
                }
            }
            Vec3d to = new Vec3d(goal.x - pos.x, 0, goal.z - pos.z);
            Vec3d push = to.lengthSquared() > 1 ? to.normalize().multiply(0.02) : Vec3d.ZERO;
            Vec3d side = to.lengthSquared() > 1e-4 ? new Vec3d(-to.z, 0, to.x).normalize().multiply(0.018 * MathHelper.sin(age * 0.13f + seed)) : Vec3d.ZERO;
            vel = vel.multiply(0.94).add(push).add(side);
            double speed = vel.horizontalLength(), cap = Math.max(maxSpeed, speed * 0.97);
            if (speed > cap) vel = vel.multiply(cap / speed);
            Vec3d next = pos.add(vel);
            Vec3d out = new Vec3d(next.x - leashCenter.x, 0, next.z - leashCenter.z);
            if (out.length() > leash) {
                // Turned back at the edge of the fight
                Vec3d n = out.normalize();
                next = new Vec3d(leashCenter.x + n.x * leash, next.y, leashCenter.z + n.z * leash);
                vel = vel.subtract(n.multiply(vel.dotProduct(n) * 1.6));
            }
            double ground = boss.groundUnder(next, pos.y);
            pos = new Vec3d(next.x, MathHelper.lerp(0.2, pos.y, ground), next.z);
        }

        private List<DisplayEntity.BlockDisplayEntity> all() {
            List<DisplayEntity.BlockDisplayEntity> all = new ArrayList<>(panels);
            all.addAll(skirt);
            all.addAll(debris);
            all.addAll(cap);
            return all;
        }

        /** Every piece where it should be: the funnel's rings, the whirlpool at its foot, the debris, the cloud on top. */
        private void draw() {
            float h = height(), layerH = Math.max(0.05f, h / LAYERS);
            for (int i = 0; i < LAYERS; i++) {
                float f0 = i / (float) LAYERS, f1 = (i + 1) / (float) LAYERS, fm = (i + 0.5f) / LAYERS;
                // Layers above how far it's risen stay flat on top of it
                float shown = MathHelper.clamp(grow * LAYERS - i, 0, 1);
                Vec3d c = centerAt(fm);
                float rad = radiusAt(fm), lean = (float) Math.atan2(radiusAt(f1) - radiusAt(f0), layerH);
                float w = MathHelper.TAU * rad / PANELS * 0.95f;
                for (int k = 0; k < PANELS; k++) {
                    DisplayEntity.BlockDisplayEntity d = panels.get(i * PANELS + k);
                    if (d == null) continue;
                    d.setPosition(c.x, pos.y + f0 * h, c.z);
                    float a = k * MathHelper.TAU / PANELS + spin * (1 - 0.35f * fm) + i * 0.6f;
                    Quaternionf rot = new Quaternionf().rotateY(a).rotateZ(-lean).rotateX(0.35f);
                    Vector3f center = new Vector3f(MathHelper.cos(a) * rad, layerH * 0.5f, -MathHelper.sin(a) * rad);
                    animate(d, box(center, rot, 0.3f, layerH * 1.35f * shown, w * (0.4f + 0.6f * shown)), 2);
                }
            }
            // The whirlpool and the spray round its foot
            for (int k = 0; k < SKIRT; k++) {
                DisplayEntity.BlockDisplayEntity d = skirt.get(k);
                if (d == null) continue;
                d.setPosition(pos.x, pos.y, pos.z);
                float open = Math.min(1f, age / 25f);
                float rad = (0.6f + 2.6f * open) * (0.85f + 0.15f * MathHelper.sin(age * 0.3f + k));
                float a = k * MathHelper.TAU / SKIRT + spin * 1.4f;
                Quaternionf rot = new Quaternionf().rotateY(a).rotateZ(0.5f);
                float tall = (k % 3 == 0 ? 1.1f : 0.5f) * open * (0.7f + 0.3f * MathHelper.sin(age * 0.5f + k * 2));
                animate(d, box(new Vector3f(MathHelper.cos(a) * rad, tall * 0.5f, -MathHelper.sin(a) * rad), rot, 0.5f, tall, MathHelper.TAU * rad / SKIRT * 1.05f), 2);
            }
            // The ground and ice it's torn up, whirling up the funnel
            for (int k = 0; k < DEBRIS; k++) {
                DisplayEntity.BlockDisplayEntity d = debris.get(k);
                if (d == null) continue;
                float f = ((age * 0.008f + k / (float) DEBRIS) % 1f) * grow;
                Vec3d c = centerAt(f);
                float a = spin * 1.25f + k * MathHelper.TAU / DEBRIS, rad = radiusAt(f) + 0.7f;
                d.setPosition(c.x + MathHelper.cos(a) * rad, c.y, c.z - MathHelper.sin(a) * rad);
                Quaternionf tumble = new Quaternionf().rotateXYZ(age * 0.31f + k, age * 0.23f, age * 0.17f + k * 2);
                animate(d, cube(tumble, grow * (k % 2 == 0 ? 0.8f : 0.55f)), 2);
            }
            // The cloud on top
            Vec3d top = centerAt(1);
            for (int k = 0; k < CAP; k++) {
                DisplayEntity.BlockDisplayEntity d = cap.get(k);
                if (d == null) continue;
                d.setPosition(top.x, top.y, top.z);
                float a = k * MathHelper.TAU / CAP + spin * 0.5f, rad = RTOP * 0.75f * grow;
                Quaternionf rot = new Quaternionf().rotateY(a);
                float size = (3.2f + 0.4f * MathHelper.sin(age * 0.1f + k)) * grow;
                animate(d, box(new Vector3f(MathHelper.cos(a) * rad, 0.2f + (k % 2) * 0.5f, -MathHelper.sin(a) * rad), rot, size, 1.0f * grow, size * 0.8f), 2);
            }
        }

        /** Where a piece ends up as it falls apart: flat on the ground under where it is. */
        private AffineTransformation collapsed(DisplayEntity.BlockDisplayEntity d) {
            float drop = (float) (pos.y - d.getY());
            return box(new Vector3f(0, drop, 0), new Quaternionf().rotateY(spin), 0.4f, 0.001f, 0.4f);
        }

        /** Anyone near enough is taken up and round, then thrown out of it. */
        private void catchPlayers(ServerWorld world, RaggimelEntity boss) {
            recatch.replaceAll((id, t) -> t - 1);
            recatch.values().removeIf(t -> t <= 0);
            float h = height();
            if (grow >= 0.5f) pull(boss, h);
            if (grow >= 0.5f) {
                for (ServerPlayerEntity p : boss.fighterList()) {
                    java.util.UUID id = p.getUuid();
                    if (carried.containsKey(id) || recatch.containsKey(id)) continue;
                    double dy = p.getY() - pos.y;
                    if (dy < -1.5 || dy > h) continue;
                    float f = (float) Math.max(0, dy) / h;
                    Vec3d c = centerAt(f);
                    double dx = p.getX() - c.x, dz = p.getZ() - c.z, reach = radiusAt(f) * 0.85 + 0.5;
                    if (dx * dx + dz * dz > reach * reach) continue;
                    Carry carry = new Carry();
                    carry.angle = (float) Math.atan2(-dz, dx);
                    carry.rise = Math.max(0, dy);
                    carried.put(id, carry);
                    p.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_WHIRL, SoundCategory.HOSTILE, 1.5f, 0.6f);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 1.0f, 0.8f);
                }
            }
            var it = carried.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                ServerPlayerEntity p = boss.fighterById(e.getKey());
                if (p == null) {
                    it.remove();
                    continue;
                }
                Carry carry = e.getValue();
                carry.t++;
                carry.angle += 0.45f;
                carry.rise = Math.min(h * 0.6, carry.rise + 0.45);
                float f = (float) (carry.rise / Math.max(1, h));
                Vec3d c = centerAt(f);
                double rad = radiusAt(f) * 0.7 + 0.6;
                Vec3d want = new Vec3d(c.x + Math.cos(carry.angle) * rad, c.y, c.z - Math.sin(carry.angle) * rad);
                Vec3d pull = want.subtract(p.getPos()).multiply(0.45);
                if (pull.length() > 1.2) pull = pull.normalize().multiply(1.2);
                p.setVelocity(pull);
                p.velocityModified = true;
                p.fallDistance = 0;
                world.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.5, 0.4, 0.2);
                if (carry.t >= CARRY) {
                    // Out of the spin: outwards and on round the way it was going
                    Vec3d radial = new Vec3d(Math.cos(carry.angle), 0, -Math.sin(carry.angle));
                    Vec3d tangent = new Vec3d(-Math.sin(carry.angle), 0, -Math.cos(carry.angle));
                    boss.twisterFling(p, radial.multiply(0.6).add(tangent.multiply(0.8)).normalize());
                    world.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.5, 0.5, 0.5);
                    world.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                    it.remove();
                    recatch.put(e.getKey(), RECATCH);
                }
            }
        }

        /** Everyone near is dragged in, and a little round the way it spins. */
        private void pull(RaggimelEntity boss, float h) {
            for (ServerPlayerEntity p : boss.fighterList()) {
                if (carried.containsKey(p.getUuid())) continue;
                double dy = p.getY() - pos.y;
                if (dy < -3 || dy > h + 4) continue;
                Vec3d c = centerAt((float) MathHelper.clamp(dy / h, 0, 1));
                Vec3d in = new Vec3d(c.x - p.getX(), 0, c.z - p.getZ());
                double d = in.length();
                if (d > PULL_RANGE || d < 0.3) continue;
                in = in.multiply(1 / d);
                Vec3d round = new Vec3d(in.z, 0, -in.x);
                double strength = PULL * (1 - d / PULL_RANGE) + 0.008;
                boss.pushPlayer(p, in.add(round.multiply(0.35)).multiply(strength));
            }
        }

        /** The particles that go with it: spray streaking round the funnel, rain off the top, the churn at its foot. */
        private void effects(ServerWorld world, Random r) {
            float h = height();
            if (h > 1) {
                for (int i = 0; i < 5; i++) {
                    float f = r.nextFloat();
                    Vec3d c = centerAt(f);
                    double a = r.nextDouble() * MathHelper.TAU, rad = radiusAt(f) + 0.3;
                    double x = c.x + Math.cos(a) * rad, z = c.z - Math.sin(a) * rad;
                    // Flying round it the way it spins
                    double tx = -Math.sin(a), tz = -Math.cos(a);
                    world.spawnParticles(i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SPLASH, x, c.y, z, 0, tx, 0.15, tz, i % 2 == 0 ? 0.35 : 0.6);
                }
                Vec3d top = centerAt(1);
                world.spawnParticles(ParticleTypes.FALLING_WATER, top.x, top.y - 1, top.z, 4, RTOP * 0.6 * grow, 0.5, RTOP * 0.6 * grow, 0);
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.CLOUD, top.x, top.y + 0.5, top.z, 3, RTOP * 0.6, 0.4, RTOP * 0.6, 0.02);
            }
            world.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.2, pos.z, 6, 2.2, 0.1, 2.2, 0.3);
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y + 0.2, pos.z, 3, 1.5, 0.1, 1.5, 0.05);
            if (age % 4 == 0 && grow > 0.3f) {
                BlockState ground = world.getBlockState(BlockPos.ofFloored(pos).down());
                if (!ground.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), pos.x, pos.y + 0.3, pos.z, 6, 1.5, 0.2, 1.5, 0.2);
            }
            if (loose()) {
                if (age % 20 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.HOSTILE, 4.0f, 0.5f);
                if (age % 26 == 0) world.playSound(null, pos.x, pos.y + 6, pos.z, SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 4.0f, 0.4f);
                if (age % 34 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.HOSTILE, 3.0f, 0.6f);
            }
        }

        /** Falling apart: all of it comes down at once, a deluge where it stood. */
        private boolean fade(ServerWorld world) {
            fading++;
            float h = HEIGHT * (1 - fading / (float) FADE);
            world.spawnParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y + h * 0.5, pos.z, 30, RTOP * 0.5, h * 0.4 + 0.5, RTOP * 0.5, 0);
            world.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.2, pos.z, 25, 3.5, 0.1, 3.5, 0.4);
            if (fading == FADE - 4) {
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 60, 3, 0.3, 3, 0.15);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 4.0f, 0.5f);
            }
            return fading < FADE;
        }

        @Override
        void remove() {
            discard(panels);
            discard(skirt);
            discard(debris);
            discard(cap);
            carried.clear();
        }
    }

    // ---- The Deluge's orb: a great ball of the sea held up over his head, then thrown up and brought down on you ----

    static final net.minecraft.particle.DustParticleEffect ORB_BLUE = new net.minecraft.particle.DustParticleEffect(new Vector3f(0.2f, 0.5f, 1.0f), 2.5f);
    static final net.minecraft.particle.DustParticleEffect ORB_PALE = new net.minecraft.particle.DustParticleEffect(new Vector3f(0.65f, 0.92f, 1.0f), 1.6f);

    /**
     * A sphere as blocky as everything else: a heart of blue ice, three shells of blue and light blue glass turning
     * against each other (overlapping cubes make a faceted ball), a tilted ring of ice shards whirling round it, all of
     * it swelling and throbbing. Water is drawn up into it out of the ground while it's held; sparks crackle over it.
     * Thrown, it climbs high into the sky on an arc and comes down where it was aimed (nothing on the ground shows where:
     * look up), and {@link RaggimelEntity#orbLands} does the rest.
     */
    static final class Orb extends Piece {
        static final float SIZE = 3f;
        static final int SHELLS = 3, SHARDS = 10;
        /** Ticks it's in the air, and how high its arc climbs over the higher of its two ends. */
        static final int FLIGHT = 60;
        static final double APEX = 26;
        final net.minecraft.entity.Entity holder;
        final int growTicks;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        final Quaternionf ringTilt;
        Vec3d pos, from, to;
        float grow;
        int flying = -1;

        /** Over {@code holder}'s head, swelling to full size over {@code growTicks}. */
        Orb(ServerWorld world, net.minecraft.entity.Entity holder, int growTicks) {
            this.holder = holder;
            this.growTicks = Math.max(1, growTicks);
            this.ringTilt = new Quaternionf().rotateX(0.45f + world.random.nextFloat() * 0.3f).rotateZ((world.random.nextFloat() - 0.5f) * 0.8f);
            pos = heldAt();
            parts.add(spawn(world, pos, BLUE_ICE, cube(new Quaternionf(), 0.01f)));
            for (int i = 0; i < SHELLS; i++) parts.add(spawn(world, pos, i == 1 ? DEEP : GLASS, cube(new Quaternionf(), 0.01f)));
            for (int k = 0; k < SHARDS; k++) parts.add(spawn(world, pos, k % 3 == 0 ? MIST : k % 3 == 1 ? ICE : BLUE_ICE, cube(new Quaternionf(), 0.01f)));
            for (DisplayEntity.BlockDisplayEntity d : parts) if (d != null) d.setTeleportDuration(1);
        }

        private Vec3d heldAt() {
            return holder.getPos().add(0, holder.getHeight() + 1.2 + SIZE * Math.max(0.3f, grow) * 1.15, 0);
        }

        boolean thrown() {
            return flying >= 0;
        }

        Vec3d landing() {
            return to;
        }

        /** Up into the sky and down on {@code target}. */
        void throwAt(Vec3d target) {
            if (flying >= 0) return;
            from = pos;
            to = target;
            flying = 0;
        }

        @Override
        boolean tick(ServerWorld world, RaggimelEntity boss) {
            age++;
            Random r = world.random;
            if (flying < 0) {
                float u = Math.min(1f, age / (float) growTicks);
                grow = 1 - (1 - u) * (1 - u);
                if (!holder.isRemoved()) pos = heldAt();
                // Water drawn up out of the ground into it, in streams
                Vec3d foot = holder.getPos();
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * MathHelper.TAU, d = 2.5 + r.nextDouble() * 3;
                    Vec3d ground = foot.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                    Vec3d along = ground.lerp(pos, r.nextDouble());
                    world.spawnParticles(i == 0 ? ORB_PALE : ParticleTypes.SPLASH, along.x, along.y, along.z, 2, 0.1, 0.1, 0.1, 0.02);
                }
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y - SIZE * grow, pos.z, 3, SIZE * grow * 0.5, 0.1, SIZE * grow * 0.5, 0);
                if (age % 20 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.HOSTILE, 3.0f, 0.5f + grow * 0.3f);
                if (age % 25 == 12) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            } else {
                flying++;
                double u = Math.min(1, flying / (double) FLIGHT);
                Vec3d line = from.lerp(to, u);
                pos = new Vec3d(line.x, line.y + APEX * 4 * u * (1 - u), line.z);
                // A trail of spray and mist behind it
                world.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z, 8, SIZE * 0.5, SIZE * 0.5, SIZE * 0.5, 0.2);
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 2, SIZE * 0.4, SIZE * 0.4, SIZE * 0.4, 0.02);
                world.spawnParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y, pos.z, 4, SIZE * 0.6, SIZE * 0.6, SIZE * 0.6, 0);
                world.spawnParticles(ORB_PALE, pos.x, pos.y, pos.z, 3, SIZE * 0.6, SIZE * 0.6, SIZE * 0.6, 0);
                if (flying % 10 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 3.0f, 0.5f);
                if (flying >= FLIGHT) {
                    boss.orbLands(world, this);
                    return false;
                }
            }
            // Crackling with power
            float g = grow, pulse = 1 + 0.06f * MathHelper.sin(age * 0.5f);
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 3, SIZE * g * 0.7, SIZE * g * 0.7, SIZE * g * 0.7, 0.15);
            world.spawnParticles(ORB_BLUE, pos.x, pos.y, pos.z, 2, SIZE * g * 0.8, SIZE * g * 0.8, SIZE * g * 0.8, 0);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y, pos.z, 3, SIZE * g * 0.6, SIZE * g * 0.6, SIZE * g * 0.6, 0.02);
            int i = 0;
            for (DisplayEntity.BlockDisplayEntity d : parts) {
                int k = i++;
                if (d == null) continue;
                d.setPosition(pos.x, pos.y, pos.z);
                AffineTransformation shape;
                if (k == 0) {
                    shape = cube(new Quaternionf().rotateXYZ(age * 0.07f, age * 0.11f, age * 0.05f), SIZE * 0.95f * g * pulse);
                } else if (k <= SHELLS) {
                    float s = SIZE * (1.5f + 0.17f * k) * g * (1 + 0.04f * MathHelper.sin(age * 0.4f + k * 2));
                    float sp = (k % 2 == 0 ? 1 : -1) * (0.05f + 0.03f * k);
                    shape = cube(new Quaternionf().rotateXYZ(age * sp + k, age * sp * 1.3f + k * 0.7f, age * sp * 0.7f), s);
                } else {
                    // The ring of shards, whirling round it
                    int n = k - SHELLS - 1;
                    float a = age * 0.22f + n * MathHelper.TAU / SHARDS;
                    Vector3f at = ringTilt.transform(new Vector3f(MathHelper.cos(a), 0, MathHelper.sin(a)).mul(SIZE * 1.55f * g));
                    AffineTransformation c = cube(new Quaternionf().rotateXYZ(age * 0.3f + n, age * 0.2f, n), (n % 2 == 0 ? 0.55f : 0.4f) * g);
                    shape = new AffineTransformation(c.getTranslation().add(at), c.getLeftRotation(), c.getScale(), null);
                }
                animate(d, shape, 1);
            }
            return true;
        }

        @Override
        void remove() {
            discard(parts);
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
