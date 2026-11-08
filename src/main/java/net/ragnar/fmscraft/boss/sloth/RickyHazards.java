package net.ragnar.fmscraft.boss.sloth;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.EntityType;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything RICKY leaves in the world: his earth tomes (the magic mod's Rising Spikes, Impaling and Stone Cannon, built
 * the same way out of the same blocks, with the same sounds; his Rocks and Boulders are the mod's own entities when it's
 * there, see {@link net.ragnar.fmscraft.compat.MagicSpells}), the snaking spikes, the shadow hands, the shockwaves you
 * jump, and as a Super Saiyan his ki blasts, the black beam, the spirit bomb and the rubble he hurls. All of it is
 * display entities and particles: nothing here places or breaks a block.
 */
final class RickyHazards {
    static final DustParticleEffect DARK_RED = new DustParticleEffect(new Vector3f(0.42f, 0.02f, 0.04f), 1.3f);
    static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.06f), 1.0f);
    static final DustParticleEffect SHADOW = new DustParticleEffect(new Vector3f(0.04f, 0.02f, 0.03f), 2.0f);
    static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.82f, 0.18f), 1.2f);
    static final DustParticleEffect PALE_GOLD = new DustParticleEffect(new Vector3f(1f, 0.95f, 0.6f), 0.9f);
    static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1f, 1f, 1f), 2.2f);
    static final DustParticleEffect SKY = new DustParticleEffect(new Vector3f(0.7f, 0.9f, 1f), 1.6f);

    static final BlockState DRIPSTONE = Blocks.DRIPSTONE_BLOCK.getDefaultState();
    static final BlockState TIP = Blocks.POINTED_DRIPSTONE.getDefaultState()
            .with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP).with(PointedDripstoneBlock.THICKNESS, Thickness.TIP);

    private RickyHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it's spent. */
        abstract boolean tick(ServerWorld world, RickyEntity boss);

        /** Taken away early (he died, the fight ended): clean up anything it put in the world. */
        void remove() {
        }
    }

    // ---- Helpers ----

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    static DisplayEntity.BlockDisplayEntity blockDisplay(ServerWorld world, BlockState state, Vec3d at) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setTeleportDuration(1);
        d.setViewRange(4f);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        world.spawnEntity(d);
        return d;
    }

    static void place(DisplayEntity d, Vec3d at, AffineTransformation transformation, int interpolation) {
        if (d == null) return;
        d.setPosition(at.x, at.y, at.z);
        if (transformation != null) d.setTransformation(transformation);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(interpolation);
    }

    static void animate(DisplayEntity d, AffineTransformation to, int ticks) {
        if (d == null) return;
        d.setTransformation(to);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(ticks);
    }

    static void discard(List<? extends DisplayEntity> displays) {
        for (DisplayEntity d : displays) if (d != null) d.discard();
        displays.clear();
    }

    /** A cube {@code size} across, centred, turned {@code a} round three axes at once: tumbling. */
    static AffineTransformation spinning(float a, float size) {
        return new AffineTransformation(new Matrix4f().rotateXYZ(a * 0.9f, a * 1.3f, a * 0.6f).scale(size).translate(-0.5f, -0.5f, -0.5f));
    }

    /** A block stretched along {@code dir} from the display's position: {@code width} square, {@code length} long. */
    static AffineTransformation along(Vec3d dir, float width, float length, float spin) {
        Vector3f d = dir.lengthSquared() > 1e-8 ? dir.normalize().toVector3f() : new Vector3f(0, 0, 1);
        return new AffineTransformation(new Matrix4f().rotate(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), d))
                .rotateZ(spin).translate(-width / 2, -width / 2, 0).scale(width, width, length));
    }

    static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double spin) {
        for (int i = 0; i < points; i++) {
            double a = i * MathHelper.TAU / points + spin;
            world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    static BlockHitResult wall(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
    }

    static boolean blocked(ServerWorld world, Vec3d from, Vec3d to) {
        return wall(world, from, to).getType() != HitResult.Type.MISS;
    }

    /** Distance from {@code p} to the segment {@code a}-{@code b}. */
    static double toSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len = ab.lengthSquared();
        double t = len < 1e-9 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / len, 0, 1);
        return p.distanceTo(a.add(ab.multiply(t)));
    }

    /** The top of the first block (not a fluid) at or below {@code startY}, looking at most {@code depth} down; null if none. */
    static BlockPos groundAt(ServerWorld world, double x, double z, int startY, int depth) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(x), startY, MathHelper.floor(z));
        for (int i = 0; i <= depth; i++) {
            BlockState state = world.getBlockState(pos);
            if (state.getFluidState().isEmpty() && !state.getCollisionShape(world, pos).isEmpty()) return pos.toImmutable();
            pos.move(Direction.DOWN);
        }
        return null;
    }

    /** What the ground at {@code pos} is made of, for dust (stone if there's nothing). */
    static BlockState groundState(ServerWorld world, BlockPos pos) {
        BlockState s = pos == null ? Blocks.STONE.getDefaultState() : world.getBlockState(pos);
        return s.isAir() ? Blocks.STONE.getDefaultState() : s;
    }

    // ---- One dripstone spike: a rock base and a pointed tip, shooting up out of the ground and sinking again ----

    /** A spike of the magic mod's look: dripstone base, pointed dripstone tip, twisted and leaning as it was asked. */
    static final class Spike {
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        final List<AffineTransformation> raised = new ArrayList<>(), buried = new ArrayList<>();

        Spike(ServerWorld world, Vec3d at, float height, float width, float twist, float lean, Vec3d leanAxis) {
            float baseH = height * 0.45f;
            add(world, at, DRIPSTONE, 0, 0.7f * width, baseH, twist, lean, leanAxis);
            add(world, at, TIP, baseH, 0.9f * width, height - baseH, twist, lean, leanAxis);
        }

        private void add(ServerWorld world, Vec3d at, BlockState state, float y, float width, float height, float twist, float lean, Vec3d axis) {
            DisplayEntity.BlockDisplayEntity d = blockDisplay(world, state, at);
            if (d == null) return;
            d.setViewRange(2.5f);
            AffineTransformation up = shape(y, width, height, twist, lean, axis, 0);
            AffineTransformation down = shape(y, width, height, twist, lean, axis, -(y + height + 0.3f));
            d.setTransformation(down);
            parts.add(d);
            raised.add(up);
            buried.add(down);
        }

        static AffineTransformation shape(float y, float width, float height, float twist, float lean, Vec3d axis, float sink) {
            Matrix4f m = new Matrix4f().translate(0, sink, 0);
            if (lean != 0) m.rotate(lean, (float) axis.x, 0, (float) axis.z);
            m.translate(0, y, 0).rotateY(twist).scale(width, height, width).translate(-0.5f, 0, -0.5f);
            return new AffineTransformation(m);
        }

        void rise(int ticks) {
            for (int i = 0; i < parts.size(); i++) animate(parts.get(i), raised.get(i), ticks);
        }

        void sink(int ticks) {
            for (int i = 0; i < parts.size(); i++) animate(parts.get(i), buried.get(i), ticks);
        }

        void remove() {
            discard(parts);
        }
    }

    // ---- Tome of Rising Spikes: a wave of spikes bursting out of the ground, three lanes wide ----

    static final class SpikeWave extends Hazard {
        static final int LENGTH = 18, STEP = 2, LIFETIME = 15, RISE = 2, SINK = 5;
        static final float CENTER_HEIGHT = 2.4f, SIDE_HEIGHT = 1.8f, SIDE_LEAN = (float) Math.toRadians(16);
        static final double LIFT = 1.3, PUSH = 0.35, HALF_WIDTH = 0.85;
        final Vec3d origin, dir, left;
        final float damage;
        final double startY;
        final Set<UUID> hit = new HashSet<>();
        final List<Object[]> live = new ArrayList<>();

        SpikeWave(Vec3d origin, Vec3d dir, float damage) {
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.left = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.origin = origin.add(this.dir.multiply(1.5));
            this.damage = damage;
            this.startY = origin.y + 2.5;
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            int step = age / STEP;
            if (age % STEP == 0 && step >= 1 && step <= LENGTH) {
                Vec3d base = origin.add(dir.multiply(step));
                for (int lane = -1; lane <= 1; lane++) erupt(world, boss, base.add(left.multiply(lane)), lane);
                // The ground ahead cracks a moment before
                Vec3d ahead = origin.add(dir.multiply(step + 1));
                BlockPos g = groundAt(world, ahead.x, ahead.z, MathHelper.floor(startY), 12);
                if (g != null) {
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState(world, g)), ahead.x, g.getY() + 1.05, ahead.z, 6, 0.6, 0.02, 0.6, 0.05);
                }
            }
            for (int i = live.size() - 1; i >= 0; i--) {
                Object[] s = live.get(i);
                Spike spike = (Spike) s[0];
                int a = ++((int[]) s[1])[0];
                if (a == 1) spike.rise(RISE);
                else if (a == 1 + RISE + LIFETIME) spike.sink(SINK);
                else if (a > 1 + RISE + LIFETIME + SINK) {
                    spike.remove();
                    live.remove(i);
                }
            }
            return step <= LENGTH || !live.isEmpty();
        }

        private void erupt(ServerWorld world, RickyEntity boss, Vec3d p, int lane) {
            BlockPos ground = groundAt(world, p.x, p.z, MathHelper.floor(startY), 12);
            if (ground == null || !world.getBlockState(ground.up()).getCollisionShape(world, ground.up()).isEmpty()) return;
            float height = (lane == 0 ? CENTER_HEIGHT : SIDE_HEIGHT) * (0.9f + world.random.nextFloat() * 0.2f);
            double baseY = ground.getY() + 1.0;
            Vec3d at = new Vec3d(p.x, baseY, p.z);
            for (ServerPlayerEntity pl : boss.fighters()) {
                if (Math.abs(pl.getX() - p.x) > HALF_WIDTH + 0.3 || Math.abs(pl.getZ() - p.z) > HALF_WIDTH + 0.3) continue;
                if (pl.getY() < baseY - 0.5 || pl.getY() > baseY + height || !hit.add(pl.getUuid())) continue;
                pl.setPosition(pl.getX(), baseY + height, pl.getZ());
                if (boss.crush(pl, damage)) boss.launch(pl, dir, PUSH + 0.3, LIFT);
                world.spawnParticles(ParticleTypes.CRIT, pl.getX(), pl.getBodyY(0.5), pl.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            }
            Spike spike = new Spike(world, at, height, 1f, world.random.nextFloat() * MathHelper.TAU, lane * SIDE_LEAN, dir);
            live.add(new Object[]{spike, new int[]{0}});
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState(world, ground)), p.x, baseY + 0.1, p.z, 14, 0.4, 0.1, 0.4, 0.15);
            world.spawnParticles(ParticleTypes.POOF, p.x, baseY + 0.2, p.z, 3, 0.2, 0.1, 0.2, 0.02);
            if (lane == 0) {
                world.playSound(null, p.x, baseY, p.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 0.9f, 0.7f + world.random.nextFloat() * 0.2f);
                world.playSound(null, p.x, baseY, p.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 0.8f, 0.8f);
            }
        }

        @Override
        void remove() {
            for (Object[] s : live) ((Spike) s[0]).remove();
            live.clear();
        }
    }

    // ---- Tome of Impaling: a spot marked on the ground, a rumble, then a huge spike ringed by smaller ones ----

    static final class Impale extends Hazard {
        static final int WARNING = 18, STAND = 50, SINK = 10;
        static final double CORE = 1.5, OUTER = 3.8;
        static final int SEGMENTS = 5;
        static final float MAIN_WIDTH = 2.4f, SEGMENT = 1.2f, TIP_WIDTH = 1.8f, TIP_HEIGHT = 2.4f, HEIGHT = SEGMENTS * SEGMENT + TIP_HEIGHT;
        final Vec3d base;
        final BlockState ground;
        final float core, outer;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        final List<AffineTransformation> raised = new ArrayList<>(), buried = new ArrayList<>();

        Impale(ServerWorld world, Vec3d base, float core, float outer) {
            this.base = base;
            this.core = core;
            this.outer = outer;
            this.ground = groundState(world, BlockPos.ofFloored(base).down());
            world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            Vec3d b = base;
            if (age < WARNING) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), b.x, b.y + 0.05, b.z, 6, 0.8, 0.02, 0.8, 0.1);
                double r = 3.2 * (1.0 - age / (double) WARNING) + 0.4;
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI * 2 / 12 + age * 0.3;
                    world.spawnParticles(ParticleTypes.CRIT, b.x + Math.cos(a) * r, b.y + 0.1, b.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                ring(world, DARK_RED, b, OUTER, 20, age * 0.1);
                if (age % 4 == 0) world.playSound(null, b.x, b.y, b.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_BREAK, SoundCategory.HOSTILE, 1.0f, 0.5f + age * 0.03f);
                return true;
            }
            if (age == WARNING) {
                build(world);
                strike(world, boss);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), b.x, b.y + 0.3, b.z, 80, 1.5, 0.4, 1.5, 0.3);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, DRIPSTONE), b.x, b.y + 2, b.z, 40, 0.8, 2.0, 0.8, 0.2);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, b.x, b.y + 0.3, b.z, 12, 1.2, 0.2, 1.2, 0.03);
                world.spawnParticles(ParticleTypes.EXPLOSION, b.x, b.y + 0.5, b.z, 2, 0.6, 0.2, 0.6, 0);
                world.playSound(null, b.x, b.y, b.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, b.x, b.y, b.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
                world.playSound(null, b.x, b.y, b.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.4f);
                boss.shake(b, 6, 0.4f, 8);
                return true;
            }
            if (age == WARNING + 1) {
                for (int i = 0; i < parts.size(); i++) animate(parts.get(i), raised.get(i), 3);
                return true;
            }
            int sink = WARNING + 4 + STAND;
            if (age == sink) {
                for (int i = 0; i < parts.size(); i++) animate(parts.get(i), buried.get(i), SINK);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, DRIPSTONE), b.x, b.y + 0.3, b.z, 40, 1.2, 0.3, 1.2, 0.1);
                world.playSound(null, b.x, b.y, b.z, SoundEvents.BLOCK_DRIPSTONE_BLOCK_BREAK, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            if (age >= sink + SINK) {
                remove();
                return false;
            }
            return true;
        }

        private void build(ServerWorld world) {
            float y = 0;
            for (int i = 0; i < SEGMENTS; i++) {
                float w = MAIN_WIDTH * (1f - i / (float) (SEGMENTS + 1));
                add(world, base, DRIPSTONE, y, w, SEGMENT, i * 0.3f, 0, 0, 0, HEIGHT);
                y += SEGMENT;
            }
            add(world, base, TIP, y, TIP_WIDTH, TIP_HEIGHT, 0.4f, 0, 0, 0, HEIGHT);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI * 2 / 6 + world.random.nextDouble() * 0.4;
                Vec3d spot = base.add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
                BlockPos g = groundAt(world, spot.x, spot.z, MathHelper.floor(base.y + 1.5), 4);
                if (g == null) continue;
                spot = new Vec3d(spot.x, g.getY() + 1, spot.z);
                float s = 0.8f + world.random.nextFloat() * 0.4f;
                float ax = (float) Math.sin(a), az = (float) -Math.cos(a);
                add(world, spot, DRIPSTONE, 0, s, 1.1f * s, (float) a, (float) Math.toRadians(22), ax, az, 3.5f);
                add(world, spot, TIP, 1.1f * s, 1.2f * s, 1.6f * s, (float) a, (float) Math.toRadians(22), ax, az, 3.5f);
            }
        }

        private void add(ServerWorld world, Vec3d at, BlockState state, float y, float width, float height, float twist, float tilt, float ax, float az, float sink) {
            DisplayEntity.BlockDisplayEntity d = blockDisplay(world, state, at);
            if (d == null) return;
            d.setViewRange(3f);
            AffineTransformation up = shape(y, width, height, twist, tilt, ax, az, 0);
            AffineTransformation down = shape(y, width, height, twist, tilt, ax, az, -(sink + 0.5f));
            d.setTransformation(down);
            parts.add(d);
            raised.add(up);
            buried.add(down);
        }

        private static AffineTransformation shape(float y, float width, float height, float twist, float tilt, float ax, float az, float offset) {
            Matrix4f m = new Matrix4f().translate(0, offset, 0);
            if (tilt != 0) m.rotate(tilt, ax, 0, az);
            m.translate(0, y, 0).rotateY(twist).scale(width, height, width).translate(-0.5f, 0, -0.5f);
            return new AffineTransformation(m);
        }

        private void strike(ServerWorld world, RickyEntity boss) {
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = flatDistance(p.getPos(), base);
                if (p.getY() < base.y - 1 || p.getY() > base.y + HEIGHT) continue;
                Vec3d out = p.getPos().subtract(base);
                if (d <= CORE) {
                    if (boss.crush(p, core)) boss.launch(p, out, 0.2, 1.5);
                } else if (d <= OUTER && p.getY() < base.y + 3.5) {
                    if (boss.crush(p, outer)) boss.launch(p, out, MathHelper.lerp((d - CORE) / (OUTER - CORE), 1.1, 0.6), 0.6);
                } else {
                    continue;
                }
                Vec3d c = p.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.1);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.2f, 0.6f);
            }
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- Tome of the Stone Cannon: a spinning spike, fired dead straight; where it lands it beeps three times and blows ----

    static final class Cannon extends Hazard {
        enum Stage { CHARGING, FLYING, STUCK }

        static final double SPEED = 2.6, RANGE = 70;
        static final int BEEPS = 3, BEEP_GAP = 7, FUSE = BEEPS * BEEP_GAP + 2;
        static final double BLAST = 3.4;
        /** The spike's parts: width, length, where each starts along it, its twist (the mod's own). */
        static final float[][] PARTS = {{0.30f, 0.34f, -0.65f, 0f}, {0.24f, 0.34f, -0.33f, 0.785f}, {0.55f, 0.75f, -0.02f, 0f}};
        final DisplayEntity.BlockDisplayEntity[] parts = new DisplayEntity.BlockDisplayEntity[PARTS.length];
        final float direct, blast;
        final int charge;
        Stage stage = Stage.CHARGING;
        Vec3d pos, dir;
        float spin, size = 0.35f;
        double travelled;
        int stuck;

        Cannon(ServerWorld world, Vec3d pos, Vec3d dir, int charge, float direct, float blast) {
            this.pos = pos;
            this.dir = dir.normalize();
            this.charge = charge;
            this.direct = direct;
            this.blast = blast;
            for (int i = 0; i < PARTS.length; i++) {
                parts[i] = blockDisplay(world, i == 2 ? TIP : DRIPSTONE, pos);
                if (parts[i] != null) {
                    parts[i].setViewRange(4f);
                    parts[i].setTransformation(shape(i, this.dir, 0, 0.05f));
                }
            }
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_PLACE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }

        static AffineTransformation shape(int i, Vec3d dir, float spin, float size) {
            float[] p = PARTS[i];
            Vector3f axis = dir.toVector3f().normalize();
            return new AffineTransformation(new Matrix4f().rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis))
                    .scale(size).rotateY((spin + p[3]) % MathHelper.TAU).translate(0, p[2], 0).scale(p[0], p[1], p[0]).translate(-0.5f, 0, -0.5f));
        }

        void pose() {
            for (int i = 0; i < parts.length; i++) place(parts[i], pos, shape(i, dir, spin, size), 1);
        }

        /** While charging, the boss holds it here, pointing this way. */
        void hold(Vec3d at, Vec3d aim) {
            pos = at;
            dir = aim.normalize();
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            switch (stage) {
                case CHARGING -> {
                    float t = Math.min(1f, age / (float) charge);
                    spin += 1.5f * (0.1f + 0.9f * t * t);
                    size = 0.35f + 0.65f * Math.min(1f, t * 1.4f);
                    pose();
                    if (age % 2 == 0) {
                        Vec3d off = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian()).normalize().multiply(0.7);
                        Vec3d from = pos.add(off);
                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, DRIPSTONE), from.x, from.y, from.z, 0, -off.x, -off.y, -off.z, 0.15);
                    }
                    if (age % 4 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 0.35f + 0.4f * t, 0.6f + 1.2f * t);
                    if (age >= charge) fire(world);
                    return true;
                }
                case FLYING -> {
                    return fly(world, boss);
                }
                default -> {
                    return fuse(world, boss);
                }
            }
        }

        private void fire(ServerWorld world) {
            stage = Stage.FLYING;
            size = 1f;
            world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.15);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.2f, 1.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.2f, 0.6f);
        }

        private boolean fly(ServerWorld world, RickyEntity boss) {
            spin += 1.5f;
            Vec3d from = pos;
            Vec3d to = from.add(dir.multiply(Math.min(SPEED, RANGE - travelled)));
            BlockHitResult hitWall = wall(world, from, to);
            boolean blocked = hitWall.getType() == HitResult.Type.BLOCK;
            Vec3d end = blocked ? hitWall.getPos() : to;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getBoundingBox().expand(0.2).raycast(from, end).isEmpty()) continue;
                // A clean hit: it shatters on them (and there's nothing left to go off)
                Vec3d c = p.getBoundingBox().getCenter();
                if (boss.strike(p, direct)) boss.launch(p, dir, 0.6, 0.15);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, DRIPSTONE), c.x, c.y, c.z, 20, 0.2, 0.3, 0.2, 0.15);
                world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 15, 0.2, 0.3, 0.2, 0.4);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.3f, 0.7f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.2f, 0.8f);
                remove();
                return false;
            }
            pos = end;
            travelled += from.distanceTo(end);
            pose();
            for (int i = 0; i < 4; i++) {
                Vec3d p = from.lerp(end, i / 4.0);
                world.spawnParticles(i % 2 == 0 ? ParticleTypes.CRIT : ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (blocked) {
                pos = hitWall.getPos().subtract(dir.multiply(0.3));
                stage = Stage.STUCK;
                pose();
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(hitWall.getBlockPos())), end.x, end.y, end.z, 25, 0.2, 0.2, 0.2, 0.15);
                world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.5f, 1.2f);
                world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_STONE_HIT, SoundCategory.HOSTILE, 1.5f, 0.6f);
                return true;
            }
            if (travelled >= RANGE - 1e-6) {
                remove();
                return false;
            }
            return true;
        }

        /** Stuck where it landed: three beeps, each brighter, then it blows. */
        private boolean fuse(ServerWorld world, RickyEntity boss) {
            stuck++;
            spin += 0.25f;
            pose();
            if (stuck % BEEP_GAP == 1 && stuck < BEEPS * BEEP_GAP) {
                int beep = stuck / BEEP_GAP;
                float pitch = 1.4f + beep * 0.3f;
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.HOSTILE, 2.0f, pitch);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.HOSTILE, 1.0f, pitch);
                world.spawnParticles(BLOOD, pos.x, pos.y, pos.z, 8 + beep * 6, 0.15, 0.15, 0.15, 0);
                world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                ring(world, BLOOD, pos.add(0, -0.3, 0), BLAST, 24, beep);
            }
            if (stuck < FUSE) return true;
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, DRIPSTONE), pos.x, pos.y, pos.z, 60, 0.6, 0.6, 0.6, 0.35);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 20, 0.6, 0.4, 0.6, 0.05);
            world.spawnParticles(DARK_RED, pos.x, pos.y, pos.z, 30, 1.2, 0.8, 1.2, 0);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.9f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_DRIPSTONE_BLOCK_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            boss.shake(pos, 5, 0.3f, 6);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                double d = c.distanceTo(pos);
                if (d > BLAST) continue;
                float k = (float) (1 - d / BLAST);
                if (boss.blast(p, MathHelper.lerp(k, blast * 0.4f, blast))) boss.launch(p, c.subtract(pos), 0.5 + 0.8 * k, 0.45);
            }
            remove();
            return false;
        }

        @Override
        void remove() {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) parts[i].discard();
                parts[i] = null;
            }
        }
    }

    // ---- Snake Spikes: rows of spikes snaking out over the ground, turning this way and that ----

    static final class Snake extends Hazard {
        static final int STEP = 2, STAND = 12, RISE = 2, SINK = 5;
        final Vec3d center;
        final double reach;
        final int life;
        final float damage;
        final Map<UUID, Integer> lastHit = new HashMap<>();
        final List<Object[]> live = new ArrayList<>();
        Vec3d head, dir;
        int sinceTurn, turnAfter;

        Snake(Vec3d from, Vec3d dir, Vec3d center, double reach, int life, float damage) {
            this.head = from;
            this.dir = cardinal(dir);
            this.center = center;
            this.reach = reach;
            this.life = life;
            this.damage = damage;
        }

        /** Snakes go forward, left or right: they move along the grid. */
        static Vec3d cardinal(Vec3d d) {
            if (Math.abs(d.x) > Math.abs(d.z)) return new Vec3d(Math.signum(d.x), 0, 0);
            return new Vec3d(0, 0, d.z == 0 ? 1 : Math.signum(d.z));
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            if (age <= life && age % STEP == 0) step(world, boss);
            for (int i = live.size() - 1; i >= 0; i--) {
                Object[] s = live.get(i);
                Spike spike = (Spike) s[0];
                int a = ++((int[]) s[1])[0];
                if (a == 1) spike.rise(RISE);
                else if (a == 1 + RISE + STAND) spike.sink(SINK);
                else if (a > 1 + RISE + STAND + SINK) {
                    spike.remove();
                    live.remove(i);
                }
            }
            return age <= life || !live.isEmpty();
        }

        private void step(ServerWorld world, RickyEntity boss) {
            if (turnAfter == 0) turnAfter = 2 + world.random.nextInt(4);
            // Turn now and then, and always before it would leave the fight or run into a wall
            boolean turn = ++sinceTurn >= turnAfter;
            Vec3d next = head.add(dir);
            if (flatDistance(next, center) > reach || blockedAhead(world, next)) turn = true;
            if (turn) {
                Vec3d left = new Vec3d(dir.z, 0, -dir.x), right = left.multiply(-1);
                Vec3d first = world.random.nextBoolean() ? left : right, second = first.multiply(-1);
                Vec3d pick = null;
                for (Vec3d d : new Vec3d[]{first, second, dir}) {
                    Vec3d n = head.add(d);
                    if (flatDistance(n, center) <= reach && !blockedAhead(world, n)) {
                        pick = d;
                        break;
                    }
                }
                // Hemmed in: it heads back in towards the middle
                dir = pick != null ? pick : cardinal(center.subtract(head));
                sinceTurn = 0;
                turnAfter = 2 + world.random.nextInt(4);
            }
            head = head.add(dir);
            BlockPos ground = groundAt(world, head.x, head.z, MathHelper.floor(head.y + 3), 8);
            if (ground == null) return;
            head = new Vec3d(head.x, ground.getY() + 1, head.z);
            float height = 2.6f + world.random.nextFloat() * 0.6f;
            Spike spike = new Spike(world, head, height, 1.15f, world.random.nextFloat() * MathHelper.TAU, (float) Math.toRadians(8), new Vec3d(-dir.z, 0, dir.x));
            live.add(new Object[]{spike, new int[]{0}});
            BlockState under = groundState(world, ground);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), head.x, head.y + 0.1, head.z, 12, 0.4, 0.1, 0.4, 0.15);
            world.spawnParticles(DARK_RED, head.x, head.y + 0.6, head.z, 4, 0.3, 0.4, 0.3, 0);
            Vec3d ahead = head.add(dir);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), ahead.x, head.y + 0.05, ahead.z, 5, 0.4, 0.02, 0.4, 0.05);
            if (age % 4 == 0) world.playSound(null, head.x, head.y, head.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.0f, 0.6f + world.random.nextFloat() * 0.3f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (Math.abs(p.getX() - head.x) > 1.1 || Math.abs(p.getZ() - head.z) > 1.1) continue;
                if (p.getY() < head.y - 0.5 || p.getY() > head.y + height) continue;
                if (boss.age - lastHit.getOrDefault(p.getUuid(), -100) < 20) continue;
                lastHit.put(p.getUuid(), boss.age);
                p.setPosition(p.getX(), head.y + height * 0.8, p.getZ());
                if (boss.crush(p, damage)) boss.launch(p, dir, 0.6, 1.25);
                world.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getBodyY(0.5), p.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.4f, 0.5f);
            }
        }

        private static boolean blockedAhead(ServerWorld world, Vec3d at) {
            BlockPos above = BlockPos.ofFloored(at.x, at.y + 0.5, at.z);
            return !world.getBlockState(above).getCollisionShape(world, above).isEmpty()
                    && !world.getBlockState(above.up()).getCollisionShape(world, above.up()).isEmpty();
        }

        @Override
        void remove() {
            for (Object[] s : live) ((Spike) s[0]).remove();
            live.clear();
        }
    }

    // ---- The Hands: a huge hand of shadow rises out of the ground, looms, and slams down flat ----

    static final class Hand extends Hazard {
        /** Warning (the shadow of where it'll fall), rising out of the ground, looming, slamming down, lying, sinking. */
        static final int WARN = 26, RISE = 10, LOOM = 8, SLAM = 5, LIE = 14, SINK = 14;
        static final float LENGTH = 11.2f;
        static final double HALF_WIDTH = 2.1;
        /** The parts, upright, palm towards +z: block, then min corner and size in blocks. */
        static final float[][] PARTS = {
                // forearm and wrist
                {0, -1.1f, 0f, -0.9f, 2.2f, 5.2f, 1.8f},
                {0, -1.25f, 4.6f, -1.0f, 2.5f, 0.8f, 2.0f},
                // palm
                {0, -1.6f, 5.2f, -0.7f, 3.2f, 3.0f, 1.4f},
                // fingers: lower then upper joints (the upper ones curled in a little, towards the palm side)
                {0, -1.55f, 8.2f, -0.4f, 0.7f, 1.6f, 0.75f}, {0, -1.6f, 9.75f, -0.15f, 0.65f, 1.2f, 0.7f},
                {0, -0.75f, 8.2f, -0.4f, 0.7f, 1.9f, 0.75f}, {0, -0.8f, 10.05f, -0.15f, 0.65f, 1.4f, 0.7f},
                {0, 0.05f, 8.2f, -0.4f, 0.7f, 1.8f, 0.75f}, {0, 0f, 9.95f, -0.15f, 0.65f, 1.3f, 0.7f},
                {0, 0.85f, 8.2f, -0.4f, 0.7f, 1.4f, 0.75f}, {0, 0.8f, 9.55f, -0.15f, 0.65f, 1.0f, 0.7f},
                // thumb, out to the side
                {0, 1.6f, 5.6f, -0.2f, 0.8f, 1.6f, 0.75f}, {0, 2.1f, 7.1f, 0.0f, 0.65f, 1.3f, 0.7f},
                // claws: dark red at the fingertips
                {1, -1.55f, 10.9f, 0.05f, 0.6f, 0.4f, 0.55f}, {1, -0.75f, 11.4f, 0.05f, 0.6f, 0.4f, 0.55f},
                {1, 0.05f, 11.2f, 0.05f, 0.6f, 0.4f, 0.55f}, {1, 0.85f, 10.5f, 0.05f, 0.6f, 0.4f, 0.55f},
        };
        static final BlockState[] BLOCKS = {Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState()};
        final Vec3d base, dir;
        final float yaw, damage;
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        boolean landed;

        Hand(Vec3d base, Vec3d dir, float damage) {
            this.base = base;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.yaw = (float) Math.atan2(this.dir.x, this.dir.z);
            this.damage = damage;
        }

        /** Where its palm comes down, flat on the ground: the strip from its base out along {@link #dir}. */
        boolean inFootprint(Vec3d p) {
            Vec3d rel = p.subtract(base);
            double along = rel.x * dir.x + rel.z * dir.z, side = Math.abs(rel.x * dir.z - rel.z * dir.x);
            return along > 0.5 && along < LENGTH + 0.6 && side < HALF_WIDTH && rel.y > -1.5 && rel.y < 2.8;
        }

        AffineTransformation shape(float[] p, float rise, float tilt) {
            return new AffineTransformation(new Matrix4f().translate(0, rise, 0).rotateY(yaw).rotateX(tilt)
                    .translate(p[1], p[2], p[3]).scale(p[4], p[5], p[6]));
        }

        void pose(float rise, float tilt, int interpolation) {
            for (int i = 0; i < parts.size(); i++) animate(parts.get(i), shape(PARTS[i], rise, tilt), interpolation);
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            int up = WARN, loom = up + RISE, slam = loom + LOOM, lie = slam + SLAM, sink = lie + LIE, end = sink + SINK;
            if (age <= WARN) {
                // A pool of shadow where it'll come up, and the dark shape of where it'll fall
                if (age % 2 == 0) {
                    for (int i = 0; i < 14; i++) {
                        double along = 0.5 + world.random.nextDouble() * LENGTH, side = (world.random.nextDouble() * 2 - 1) * HALF_WIDTH;
                        Vec3d p = base.add(dir.multiply(along)).add(dir.z * side, 0.05, -dir.x * side);
                        world.spawnParticles(SHADOW, p.x, p.y, p.z, 1, 0.05, 0, 0.05, 0);
                    }
                    world.spawnParticles(ParticleTypes.SQUID_INK, base.x, base.y + 0.1, base.z, 4, 0.8, 0.05, 0.8, 0.02);
                    outline(world);
                }
                if (age % 6 == 0) world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 2.0f, 0.5f);
                if (age == 1) world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.4f, 1.2f);
                return true;
            }
            if (age == WARN + 1) {
                for (float[] p : PARTS) {
                    DisplayEntity.BlockDisplayEntity d = blockDisplay(world, BLOCKS[(int) p[0]], base);
                    if (d == null) continue;
                    d.setViewRange(6f);
                    d.setBrightness(new Brightness(p[0] == 0 ? 0 : 6, 0));
                    d.setTransformation(shape(p, -LENGTH - 1, 0));
                    parts.add(d);
                }
            }
            if (age == WARN + 2) {
                pose(0, 0, RISE);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState(world, BlockPos.ofFloored(base).down())), base.x, base.y + 0.3, base.z, 60, 1.2, 0.3, 1.2, 0.3);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + 0.5, base.z, 20, 1.0, 0.3, 1.0, 0.05);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 2.0f, 0.6f);
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                boss.shake(base, 8, 0.3f, 10);
            }
            if (age > up && age <= slam) {
                // Smoke and ink coming off it as it looms over you
                Vec3d top = base.add(0, 7 + world.random.nextDouble() * 3, 0);
                world.spawnParticles(ParticleTypes.SQUID_INK, top.x, top.y, top.z, 2, 0.8, 0.8, 0.8, 0.01);
                world.spawnParticles(DARK_RED, top.x, top.y, top.z, 2, 1.0, 1.5, 1.0, 0);
                if (age % 3 == 0) outline(world);
                if (age == slam - 2) world.playSound(null, base.x, base.y + 6, base.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 1.6f, 0.4f);
            }
            if (age > slam && age <= lie) {
                float u = (age - slam) / (float) SLAM;
                pose(0, MathHelper.HALF_PI * u * u, 1);
                if (age == slam + 1) world.playSound(null, base.x, base.y + 4, base.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.4f);
            }
            if (age == lie) impact(world, boss);
            if (age == sink) pose(-3.2f, MathHelper.HALF_PI, SINK);
            if (age >= end) {
                remove();
                return false;
            }
            return true;
        }

        private void outline(ServerWorld world) {
            Vec3d side = new Vec3d(dir.z, 0, -dir.x);
            for (double a = 0.5; a <= LENGTH + 0.5; a += 0.6) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3d p = base.add(dir.multiply(a)).add(side.multiply(HALF_WIDTH * s));
                    world.spawnParticles(DARK_RED, p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
                }
            }
            for (double s = -HALF_WIDTH; s <= HALF_WIDTH; s += 0.6) {
                Vec3d p = base.add(dir.multiply(LENGTH + 0.5)).add(side.multiply(s));
                world.spawnParticles(DARK_RED, p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
            }
        }

        private void impact(ServerWorld world, RickyEntity boss) {
            landed = true;
            Vec3d mid = base.add(dir.multiply(LENGTH * 0.6));
            BlockState under = groundState(world, BlockPos.ofFloored(mid).down());
            for (double a = 1; a <= LENGTH; a += 1.5) {
                Vec3d p = base.add(dir.multiply(a));
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), p.x, p.y + 0.2, p.z, 14, 1.4, 0.2, 1.4, 0.3);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.4, p.z, 3, 1.2, 0.2, 1.2, 0.04);
            }
            world.spawnParticles(ParticleTypes.EXPLOSION, mid.x, mid.y + 0.5, mid.z, 4, 2.5, 0.3, 2.5, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, mid.x, mid.y + 0.5, mid.z, 40, 3, 0.4, 3, 0.1);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.6f, 0.5f);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2f, 0.4f);
            boss.shake(mid, 10, 0.8f, 14);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (!inFootprint(p.getPos())) continue;
                if (boss.crush(p, damage)) {
                    Vec3d side = new Vec3d(dir.z, 0, -dir.x);
                    double s = p.getPos().subtract(base).dotProduct(side);
                    boss.launch(p, side.multiply(s >= 0 ? 1 : -1).add(dir.multiply(0.4)), 1.2, 0.5);
                }
            }
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- A shockwave rolling out along the ground: a band of upturned earth (or golden ki). Jump it ----

    static final class Quake extends Hazard {
        static final int SEGMENTS = 44;
        final Vec3d center;
        final double speed, max;
        final float damage;
        final BlockState shellBlock, coreBlock;
        final Set<UUID> settled = new HashSet<>();
        final List<DisplayEntity.BlockDisplayEntity> shell = new ArrayList<>(), core = new ArrayList<>();
        double radius;

        Quake(Vec3d center, double start, double speed, double max, float damage, boolean ki) {
            this.center = center;
            this.radius = start;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.shellBlock = ki ? Blocks.YELLOW_STAINED_GLASS.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState();
            this.coreBlock = ki ? Blocks.SHROOMLIGHT.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState();
        }

        static AffineTransformation segment(int i, double radius, float height, float width) {
            double a = i * MathHelper.TAU / SEGMENTS;
            float length = (float) Math.max(0.05, MathHelper.TAU * radius / SEGMENTS * 1.08);
            Quaternionf turn = new Quaternionf().rotationY((float) -(a + MathHelper.HALF_PI));
            Vector3f half = new Vector3f(length / 2, 0, width / 2).rotate(turn);
            Vector3f at = new Vector3f((float) (Math.cos(a) * radius), 0.01f, (float) (Math.sin(a) * radius)).sub(half);
            return new AffineTransformation(at, turn, new Vector3f(length, height, width), new Quaternionf());
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            if (age == 0) {
                for (int i = 0; i < SEGMENTS; i++) {
                    DisplayEntity.BlockDisplayEntity s = blockDisplay(world, shellBlock, center);
                    DisplayEntity.BlockDisplayEntity c = blockDisplay(world, coreBlock, center);
                    if (s != null) {
                        s.setViewRange(8f);
                        s.setTransformation(segment(i, radius, 0.55f, 0.5f));
                        shell.add(s);
                    }
                    if (c != null) {
                        c.setViewRange(8f);
                        c.setBrightness(new Brightness(15, 15));
                        c.setTransformation(segment(i, radius, 0.3f, 0.2f));
                        core.add(c);
                    }
                }
            } else if (age == 1) {
                int left = (int) Math.ceil((max - radius) / speed);
                for (int i = 0; i < shell.size(); i++) animate(shell.get(i), segment(i, max, 0.55f, 0.5f), left);
                for (int i = 0; i < core.size(); i++) animate(core.get(i), segment(i, max, 0.3f, 0.2f), left);
            }
            radius += speed;
            if (radius > max) {
                remove();
                return false;
            }
            if (age++ % 5 == 0) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 1.6f, 0.5f + (float) (radius / max) * 0.5f);
            }
            if (age % 2 == 0) ring(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, shellBlock), center, radius, (int) Math.min(80, 8 + radius * 2.5), 0);
            // A wave that only shows (his stamp, his burst) hurts and throws nobody
            if (damage <= 0) return true;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (settled.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center);
                if (d > radius || Math.abs(p.getY() - center.y) > 3) continue;
                settled.add(p.getUuid());
                // Jumped it, or was inside it when it started
                if (d < radius - speed - 1.0 || !p.isOnGround()) continue;
                if (boss.crush(p, damage)) boss.launch(p, p.getPos().subtract(center), 0.8, 0.55);
            }
            return true;
        }

        @Override
        void remove() {
            discard(shell);
            discard(core);
        }
    }

    // ---- When the Tome of Boulders isn't there: his own boulder, a craggy lump of stone that slams where it lands ----

    static final class Boulder extends Hazard {
        static final BlockState[] LUMP = {Blocks.COBBLESTONE.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.ANDESITE.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.TUFF.getDefaultState()};
        static final float[][] SHAPE = {{0f, 0f, 0f, 1.3f}, {0.05f, 0.05f, 0f, 1.2f}, {-0.1f, -0.05f, 0.1f, 1.1f}, {0.35f, 0.3f, -0.2f, 0.8f}, {-0.3f, -0.3f, 0.25f, 0.75f}};
        final List<DisplayEntity.BlockDisplayEntity> parts = new ArrayList<>();
        final float size, hitDamage, slamDamage;
        final Set<UUID> hit = new HashSet<>();
        Vec3d pos, vel;
        boolean flying;
        float spin;

        Boulder(ServerWorld world, Vec3d pos, float size, float hitDamage, float slamDamage) {
            this.pos = pos;
            this.size = size;
            this.hitDamage = hitDamage;
            this.slamDamage = slamDamage;
            for (int i = 0; i < SHAPE.length; i++) {
                DisplayEntity.BlockDisplayEntity d = blockDisplay(world, LUMP[i], pos);
                if (d == null) continue;
                d.setViewRange(6f);
                parts.add(d);
            }
            pose(0.1f);
        }

        void pose(float grow) {
            for (int i = 0; i < parts.size(); i++) {
                float[] s = SHAPE[i];
                float k = size * grow;
                place(parts.get(i), pos, new AffineTransformation(new Matrix4f().rotateXYZ(spin * 0.7f + i, spin + i * 0.6f, spin * 0.4f)
                        .translate(s[0] * k, s[1] * k, s[2] * k).scale(s[3] * k).translate(-0.5f, -0.5f, -0.5f)), 1);
            }
        }

        void hold(Vec3d at, float grow) {
            pos = at;
            spin += 0.1f;
            pose(grow);
        }

        void launch(Vec3d velocity) {
            vel = velocity;
            flying = true;
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            if (!flying) return age < 200;
            spin += 0.35f;
            Vec3d from = pos;
            vel = vel.add(0, -0.05, 0).multiply(0.99);
            Vec3d to = from.add(vel);
            BlockHitResult w = wall(world, from, to);
            pos = w.getType() == HitResult.Type.BLOCK ? w.getPos() : to;
            pose(1f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hit.contains(p.getUuid()) || p.getBoundingBox().expand(size * 0.6).raycast(from, pos).isEmpty()) continue;
                hit.add(p.getUuid());
                if (boss.strike(p, hitDamage)) boss.launch(p, vel, 2.0, 0.65);
            }
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.STONE.getDefaultState()), pos.x, pos.y, pos.z, 2, 0.4, 0.4, 0.4, 0);
            if (w.getType() == HitResult.Type.BLOCK || age > 120) {
                slam(world, boss);
                return false;
            }
            return true;
        }

        private void slam(ServerWorld world, RickyEntity boss) {
            Vec3d c = pos;
            double radius = 2.5 + size * 2;
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = flatDistance(p.getPos(), c) + Math.abs(p.getY() - c.y) * 0.5;
                if (d > radius) continue;
                float k = (float) (1 - d / radius);
                if (boss.blast(p, MathHelper.lerp(k, slamDamage * 0.35f, slamDamage))) boss.launch(p, p.getPos().subtract(c), 0.8 + 1.4 * k, 0.45 + 0.5 * k);
            }
            for (BlockState s : LUMP) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), c.x, c.y + 0.5, c.z, 30, 0.7, 0.6, 0.7, 0.25);
            world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 1.2, 0.3, 1.2, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.2f, 0.7f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            boss.shake(c, 8, 0.5f, 10);
            remove();
        }

        @Override
        void remove() {
            discard(parts);
        }
    }

    // ---- Super Saiyan: a ki blast, a small hot ball of light that bursts where it hits ----

    static final class KiBlast extends Hazard {
        final DisplayEntity.BlockDisplayEntity core;
        final float damage;
        final boolean dark;
        Vec3d pos, vel;

        KiBlast(ServerWorld world, Vec3d pos, Vec3d vel, float damage, boolean dark) {
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
            this.dark = dark;
            this.core = blockDisplay(world, (dark ? Blocks.REDSTONE_BLOCK : Blocks.OCHRE_FROGLIGHT).getDefaultState(), pos);
            if (core != null) {
                core.setBrightness(new Brightness(15, 15));
                core.setViewRange(6f);
                core.setTransformation(spinning(0, 0.45f));
            }
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            Vec3d from = pos, to = pos.add(vel);
            BlockHitResult w = wall(world, from, to);
            Vec3d end = w.getType() == HitResult.Type.BLOCK ? w.getPos() : to;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getBoundingBox().expand(0.3).raycast(from, end).isPresent()) {
                    pos = p.getBoundingBox().getCenter();
                    if (boss.strike(p, damage)) boss.launch(p, vel, 0.6, 0.3);
                    burst(world, boss, p);
                    return false;
                }
            }
            pos = end;
            place(core, pos, spinning(age * 0.8f, 0.45f + 0.05f * MathHelper.sin(age)), 1);
            world.spawnParticles(dark ? DARK_RED : GOLD, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            if (w.getType() == HitResult.Type.BLOCK || age > 50) {
                burst(world, boss, null);
                return false;
            }
            return true;
        }

        private void burst(ServerWorld world, RickyEntity boss, ServerPlayerEntity direct) {
            world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(dark ? DARK_RED : GOLD, pos.x, pos.y, pos.z, 16, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.9f, 1.4f + world.random.nextFloat() * 0.3f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p == direct) continue;
                double d = p.getBoundingBox().getCenter().distanceTo(pos);
                if (d < 2.2 && boss.blast(p, damage * 0.5f)) boss.launch(p, p.getPos().subtract(pos), 0.4, 0.3);
            }
            remove();
        }

        @Override
        void remove() {
            if (core != null) core.discard();
        }
    }

    // ---- The black beam: his evil kamehameha, a beam of black with a blood-red glow round it ----

    static final class Beam extends Hazard {
        static final float CORE = 1.3f, GLOW = 2.1f, HAZE = 2.9f;
        static final double REACH = 52, HIT_RADIUS = 1.55;
        final DisplayEntity.BlockDisplayEntity core, glow, haze, ball;
        final int life, hitEvery;
        final float damage;
        final Map<UUID, Integer> lastHit = new HashMap<>();
        final boolean sweep;
        Vec3d from, dir;
        double length;

        Beam(ServerWorld world, Vec3d from, Vec3d dir, int life, float damage, int hitEvery, boolean sweep) {
            this.from = from;
            this.dir = dir.normalize();
            this.life = life;
            this.damage = damage;
            this.hitEvery = hitEvery;
            this.sweep = sweep;
            this.core = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), from);
            this.glow = blockDisplay(world, Blocks.RED_STAINED_GLASS.getDefaultState(), from);
            this.haze = blockDisplay(world, Blocks.BLACK_STAINED_GLASS.getDefaultState(), from);
            this.ball = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), from);
            for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{core, glow, haze, ball}) {
                if (d == null) continue;
                d.setViewRange(12f);
                d.setBrightness(new Brightness(d == glow ? 15 : 0, 15));
            }
            world.spawnParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 2, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }

        /** The boss turns it (his hands move): from here, this way. */
        void aim(Vec3d at, Vec3d way) {
            from = at;
            dir = way.normalize();
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            if (age > life) {
                remove();
                return false;
            }
            Vec3d to = from.add(dir.multiply(REACH));
            BlockHitResult w = wall(world, from, to);
            Vec3d end = w.getType() == HitResult.Type.BLOCK ? w.getPos() : to;
            length = from.distanceTo(end);
            // Fades in fast, throbs, and thins out at the end
            float k = Math.min(1f, age / 3f) * Math.min(1f, (life - age + 1) / 6f);
            float throb = 1f + 0.08f * MathHelper.sin(age * 1.9f);
            float spin = age * 0.25f;
            place(core, from, along(dir, CORE * k * throb, (float) length, spin), 1);
            place(glow, from, along(dir, GLOW * k * throb, (float) length, -spin), 1);
            place(haze, from, along(dir, HAZE * k * (1.05f - 0.08f * MathHelper.sin(age * 1.3f)), (float) length, spin * 0.5f), 1);
            place(ball, from, spinning(age * 0.5f, 2.6f * k * throb), 1);
            // Where it hits: the ground burns and spits
            if (w.getType() == HitResult.Type.BLOCK) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(w.getBlockPos())), end.x, end.y, end.z, 8, 0.6, 0.6, 0.6, 0.3);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 2, 0.4, 0.4, 0.4, 0.05);
                world.spawnParticles(BLOOD, end.x, end.y, end.z, 4, 0.8, 0.8, 0.8, 0);
                if (age % 6 == 0) world.playSound(null, end.x, end.y, end.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.0f, 0.8f);
            }
            for (int i = 0; i < 3; i++) {
                Vec3d p = from.add(dir.multiply(world.random.nextDouble() * length));
                world.spawnParticles(i == 0 ? DARK_RED : ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.6, 0.6, 0.6, 0.02);
            }
            if (age % 5 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            if (age % 8 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 1.4f);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                if (toSegment(c, from, end) > HIT_RADIUS + (sweep ? 0.3 : 0)) continue;
                if (boss.age - lastHit.getOrDefault(p.getUuid(), -100) < hitEvery) continue;
                lastHit.put(p.getUuid(), boss.age);
                if (boss.crush(p, damage)) boss.launch(p, dir, sweep ? 1.4 : 0.9, 0.35);
                world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.1);
            }
            return true;
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{core, glow, haze, ball}) if (d != null) d.discard();
        }
    }

    // ---- The spirit bomb: a great ball of white light, thrown slowly; it drags you in and its heart crushes ----

    static final class SpiritBomb extends Hazard {
        static final int LIFE = 66;
        static final double PULL_RADIUS = 11, MAX_TUG = 0.42, CORE = 2.1, BURST_RADIUS = 6;
        static final int TUG_EVERY = 4;
        static final float SIZE = 3.2f;
        final Vec3d vel;
        final float damage, burst, spin0;
        final DisplayEntity.BlockDisplayEntity heart, shell, halo;
        final Set<UUID> hit = new HashSet<>();
        Vec3d pos;

        SpiritBomb(ServerWorld world, Vec3d from, Vec3d vel, float damage, float burst, float spin0) {
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            this.burst = burst;
            this.spin0 = spin0;
            this.heart = blockDisplay(world, Blocks.WHITE_CONCRETE.getDefaultState(), from);
            this.shell = blockDisplay(world, Blocks.WHITE_STAINED_GLASS.getDefaultState(), from);
            this.halo = blockDisplay(world, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), from);
            for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{heart, shell, halo}) {
                if (d == null) continue;
                d.setBrightness(new Brightness(15, 15));
                d.setViewRange(12f);
            }
            pose(1f);
            world.spawnParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 2, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 80, 0.5, 0.5, 0.5, 0.5);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.7f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0f, 0.6f);
        }

        static void shape(DisplayEntity.BlockDisplayEntity heart, DisplayEntity.BlockDisplayEntity shell, DisplayEntity.BlockDisplayEntity halo,
                          Vec3d at, float spin, float size) {
            place(heart, at, spinning(spin, size * 0.62f), 1);
            place(shell, at, spinning(-spin * 0.7f + 0.4f, size * 0.85f), 1);
            place(halo, at, spinning(spin * 0.4f + 1.1f, size), 1);
        }

        void pose(float pulse) {
            shape(heart, shell, halo, pos, spin0 + age * 0.4f, SIZE * pulse);
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            if (heart == null) return false;
            if (age >= LIFE) {
                explode(world, boss);
                return false;
            }
            pos = pos.add(vel);
            pose(1f + 0.06f * MathHelper.sin(age * 1.1f));
            for (int ring = 0; ring < 2; ring++) {
                double r = 2.4 + ring * 0.8, spin = age * (0.45 - ring * 0.15) * (ring == 0 ? 1 : -1);
                for (int i = 0; i < 12; i++) {
                    double a = spin + i * MathHelper.TAU / 12;
                    world.spawnParticles(ring == 0 ? SKY : ParticleTypes.END_ROD, pos.x + Math.cos(a) * r, pos.y + Math.sin(a * 2 + age * 0.2) * 0.6, pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            for (int i = 0; i < 5; i++) {
                Vec3d o = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian() * 0.6, world.random.nextGaussian()).normalize().multiply(5 + world.random.nextDouble() * 4);
                Vec3d at = pos.add(o);
                world.spawnParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, at.x, at.y, at.z, 0, -o.x, -o.y, -o.z, 0.1);
            }
            if (age % 10 == 0) {
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.8f);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 1.2f);
            }
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                Vec3d in = pos.subtract(c);
                double d = in.length();
                if (d > PULL_RADIUS) continue;
                if (age % TUG_EVERY == 0 && d > CORE) {
                    double tug = Math.max(0.08, MAX_TUG * (1 - d / PULL_RADIUS));
                    Vec3d v = in.multiply(tug / d);
                    p.setVelocity(v.x, Math.max(0.04, v.y * 0.3), v.z);
                    p.velocityModified = true;
                    p.fallDistance = 0;
                }
                if (d < CORE + 0.3 && hit.add(p.getUuid())) {
                    p.timeUntilRegen = 0;
                    boss.crush(p, damage);
                    boss.launch(p, c.subtract(pos), 0.6, 0.5);
                    boss.flash(p, 0.7f, 12);
                    world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.3, 0.4, 0.3, 0.3);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 2.0f, 0.7f);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                }
            }
            return true;
        }

        private void explode(ServerWorld world, RickyEntity boss) {
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 3, 0.4, 0.4, 0.4, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 1, 1, 1, 0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 200, 0.5, 0.5, 0.5, 0.9);
            world.spawnParticles(WHITE, pos.x, pos.y, pos.z, 120, 3, 2.5, 3, 0);
            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 120, 0.4, 0.4, 0.4, 0.7);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.6f);
            boss.shake(pos, 16, 1f, 20);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                double d = c.distanceTo(pos);
                if (d > BURST_RADIUS) continue;
                boss.crush(p, burst);
                boss.launch(p, c.subtract(pos), 1.2 * (1 - d / (BURST_RADIUS + 1)), 0.55);
                boss.flash(p, 0.8f, 16);
            }
            remove();
        }

        @Override
        void remove() {
            for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{heart, shell, halo}) if (d != null) d.discard();
        }
    }

    // ---- Rubble: chunks of the city torn up round him, hanging there, then hurled one by one ----

    static final class Rubble extends Hazard {
        static final BlockState[] KINDS = {Blocks.STONE_BRICKS.getDefaultState(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
                Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState()};
        final DisplayEntity.BlockDisplayEntity block;
        final BlockState state;
        final float size, damage;
        final Vec3d rest;
        Vec3d pos, vel;
        boolean thrown;
        float spin;

        Rubble(ServerWorld world, Vec3d from, Vec3d rest, float size, float damage) {
            this.pos = from;
            this.rest = rest;
            this.size = size;
            this.damage = damage;
            this.state = KINDS[world.random.nextInt(KINDS.length)];
            this.block = blockDisplay(world, state, from);
            this.spin = world.random.nextFloat() * 6;
            if (block != null) {
                block.setViewRange(6f);
                block.setTransformation(spinning(spin, size));
            }
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), from.x, from.y, from.z, 14, 0.4, 0.2, 0.4, 0.15);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }

        void hurl(ServerWorld world, Vec3d velocity) {
            vel = velocity;
            thrown = true;
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.6f);
            world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.05);
        }

        @Override
        boolean tick(ServerWorld world, RickyEntity boss) {
            age++;
            if (block == null) return false;
            if (!thrown) {
                // Drifting up to hang round him, turning slowly
                pos = pos.lerp(rest, 0.18);
                spin += 0.08f;
                place(block, pos, spinning(spin, size), 1);
                if (age % 3 == 0) world.spawnParticles(PALE_GOLD, pos.x, pos.y - 0.4, pos.z, 1, 0.2, 0.1, 0.2, 0);
                return age < 300;
            }
            spin += 0.45f;
            Vec3d from = pos;
            vel = vel.add(0, -0.025, 0);
            Vec3d to = from.add(vel);
            BlockHitResult w = wall(world, from, to);
            pos = w.getType() == HitResult.Type.BLOCK ? w.getPos() : to;
            place(block, pos, spinning(spin, size), 1);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, state), pos.x, pos.y, pos.z, 1, 0.2, 0.2, 0.2, 0);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getBoundingBox().expand(size * 0.5).raycast(from, pos).isEmpty()) continue;
                if (boss.strike(p, damage)) boss.launch(p, vel, 0.9, 0.35);
                shatter(world, boss);
                return false;
            }
            if (w.getType() == HitResult.Type.BLOCK || age > 160) {
                shatter(world, boss);
                return false;
            }
            return true;
        }

        private void shatter(ServerWorld world, RickyEntity boss) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, 30, 0.4, 0.4, 0.4, 0.2);
            world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.03);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.4f, 0.6f);
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = p.getBoundingBox().getCenter().distanceTo(pos);
                if (d < 2.4 && boss.blast(p, damage * 0.35f)) boss.launch(p, p.getPos().subtract(pos), 0.4, 0.3);
            }
            remove();
        }

        @Override
        void remove() {
            if (block != null) block.discard();
        }
    }
}
