package net.ragnar.fmscraft.boss.envy;

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
import java.util.Collections;
import java.util.List;

/**
 * His Covet (half health, once): everything he's ever wanted, torn out of every corner of the world and pulled together
 * over the middle of the arena by four colossal copies of him, then brought down.
 */
final class GummssCovet {
    private GummssCovet() {
    }

    /**
     * The world he pulls together: a blocky ball of everything (grass, logs, ores, netherrack, end stone, ice, wool,
     * bookshelves...) round a glowing green heart, 21 blocks across. Each block is torn from a colossus' hands and flies
     * in to its place, the whole thing turning faster and faster. Then it falls, bursts across the floor and sinks.
     * <p>
     * Every block display sits at the ball's middle and is placed by its transformation, so the whole ball turns by
     * their yaw alone (a cheap move packet, every other tick) and flies in by one interpolated transformation each.
     */
    static final class Hoard extends EnvyHazards.Hazard {
        static final BlockState[] BLOCKS = {
                Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.OAK_LOG.getDefaultState(),
                Blocks.OAK_PLANKS.getDefaultState(), Blocks.SPRUCE_LOG.getDefaultState(), Blocks.SAND.getDefaultState(),
                Blocks.GRAVEL.getDefaultState(), Blocks.IRON_ORE.getDefaultState(), Blocks.GOLD_ORE.getDefaultState(),
                Blocks.DIAMOND_ORE.getDefaultState(), Blocks.EMERALD_ORE.getDefaultState(), Blocks.GOLD_BLOCK.getDefaultState(),
                Blocks.EMERALD_BLOCK.getDefaultState(), Blocks.DIAMOND_BLOCK.getDefaultState(), Blocks.NETHERRACK.getDefaultState(),
                Blocks.NETHER_BRICKS.getDefaultState(), Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.GLOWSTONE.getDefaultState(),
                Blocks.END_STONE.getDefaultState(), Blocks.PURPUR_BLOCK.getDefaultState(), Blocks.OBSIDIAN.getDefaultState(),
                Blocks.CRYING_OBSIDIAN.getDefaultState(), Blocks.PRISMARINE.getDefaultState(), Blocks.BRICKS.getDefaultState(),
                Blocks.BOOKSHELF.getDefaultState(), Blocks.DEEPSLATE.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(),
                Blocks.AMETHYST_BLOCK.getDefaultState(), Blocks.COPPER_BLOCK.getDefaultState(), Blocks.OXIDIZED_COPPER.getDefaultState(),
                Blocks.QUARTZ_BLOCK.getDefaultState(), Blocks.PACKED_ICE.getDefaultState(), Blocks.SNOW_BLOCK.getDefaultState(),
                Blocks.HAY_BLOCK.getDefaultState(), Blocks.PUMPKIN.getDefaultState(), Blocks.MELON.getDefaultState(),
                Blocks.RED_WOOL.getDefaultState(), Blocks.TERRACOTTA.getDefaultState(), Blocks.CLAY.getDefaultState(),
                Blocks.MUD.getDefaultState(), Blocks.SCULK.getDefaultState(), Blocks.LAPIS_BLOCK.getDefaultState(),
                Blocks.REDSTONE_BLOCK.getDefaultState(), Blocks.ANCIENT_DEBRIS.getDefaultState(), Blocks.SHROOMLIGHT.getDefaultState(),
                Blocks.SLIME_BLOCK.getDefaultState(), Blocks.TNT.getDefaultState(), Blocks.VERDANT_FROGLIGHT.getDefaultState()};
        /** The outer shell and the one inside it, how many blocks each, and how big. */
        static final double RADIUS = 10.5, INNER = 6.6;
        static final int OUTER_COUNT = 150, INNER_COUNT = 56;
        static final float OUTER_SIZE = 2.7f, INNER_SIZE = 2.3f, CORE_SIZE = 6f;
        /** A block's flight from a colossus' hands to its place in the ball. */
        static final int FLY = 26;
        /** Thrown out across the floor when it lands, then sinking away into it. */
        static final int BURST = 18, SINK = 36;
        /** How often the turning ball is sent (its blocks' yaw), and how long each move takes on the client. */
        static final int TURN_EVERY = 2;

        final Vec3d hang;
        final double floorY;
        /** How far it reaches when it lands (only for the shadow and the ring on the floor). */
        final double reach;
        /** Ticks (of its own) over which its blocks arrive, and when it starts to fall. */
        final int formTicks, dropAt, fallTicks;
        final List<Piece> pieces = new ArrayList<>();
        DisplayEntity.BlockDisplayEntity core;
        Vec3d center;
        float spin;
        int landedAt = -1, nextHand;
        boolean landed;

        static final class Piece {
            final Vector3f slot;
            final float size;
            final Quaternionf rot;
            final BlockState state;
            final int at;
            DisplayEntity.BlockDisplayEntity d;
            int spawned = -1;
            /** Where it was thrown to when the ball burst. */
            AffineTransformation thrown;

            Piece(Vector3f slot, float size, Quaternionf rot, BlockState state, int at) {
                this.slot = slot;
                this.size = size;
                this.rot = rot;
                this.state = state;
                this.at = at;
            }
        }

        Hoard(Random random, Vec3d hang, double floorY, double reach, int formTicks, int dropAt, int fallTicks) {
            this.hang = hang;
            this.center = hang;
            this.floorY = floorY;
            this.reach = reach;
            this.formTicks = formTicks;
            this.dropAt = dropAt;
            this.fallTicks = fallTicks;
            // The inside first (over the first half), then the shell over it (filling in at random)
            List<Vector3f> inner = shell(INNER_COUNT, INNER, random), outer = shell(OUTER_COUNT, RADIUS, random);
            for (int i = 0; i < inner.size(); i++) {
                int at = 8 + (int) (i * formTicks * 0.42 / inner.size());
                pieces.add(new Piece(inner.get(i), INNER_SIZE * (0.85f + random.nextFloat() * 0.3f), randomTilt(random), pickBlock(random), at));
            }
            for (int i = 0; i < outer.size(); i++) {
                int at = (int) (formTicks * 0.28 + i * formTicks * 0.66 / outer.size());
                pieces.add(new Piece(outer.get(i), OUTER_SIZE * (0.8f + random.nextFloat() * 0.4f), randomTilt(random), pickBlock(random), at));
            }
        }

        /** {@code n} points spread evenly over a ball of {@code radius} (jittered so it's lumpy), in a random order. */
        static List<Vector3f> shell(int n, double radius, Random random) {
            List<Vector3f> points = new ArrayList<>();
            double golden = Math.PI * (3 - Math.sqrt(5));
            for (int i = 0; i < n; i++) {
                double y = 1 - (i + 0.5) * 2 / n, r = Math.sqrt(1 - y * y), a = i * golden;
                double d = radius + (random.nextDouble() - 0.5) * 1.4;
                points.add(new Vector3f((float) (Math.cos(a) * r * d), (float) (y * d), (float) (Math.sin(a) * r * d)));
            }
            Collections.shuffle(points, new java.util.Random(random.nextLong()));
            return points;
        }

        static Quaternionf randomTilt(Random random) {
            return new Quaternionf().rotationYXZ(random.nextFloat() * MathHelper.TAU, (random.nextFloat() - 0.5f) * 0.9f, (random.nextFloat() - 0.5f) * 0.9f);
        }

        static BlockState pickBlock(Random random) {
            return BLOCKS[random.nextInt(BLOCKS.length)];
        }

        /** How far it's formed, 0-1. */
        double formed() {
            return MathHelper.clamp(age / (double) formTicks, 0, 1);
        }

        boolean falling() {
            return age >= dropAt && !landed;
        }

        /** The transformation that puts a block of {@code size}, turned by {@code rot}, at {@code at} (relative to the ball's middle, before it turns). */
        static AffineTransformation at(Vector3f at, float size, Quaternionf rot) {
            Vector3f half = new Vector3f(-0.5f, -0.5f, -0.5f).mul(size).rotate(rot);
            return new AffineTransformation(new Vector3f(at).add(half), rot, new Vector3f(size, size, size), new Quaternionf());
        }

        /** {@code world} as the turning ball sees it: drawn turned by -yaw, so turned back by +yaw. */
        Vector3f local(Vec3d world) {
            Vector3f v = world.subtract(center).toVector3f();
            return new Quaternionf().rotationY(spin * MathHelper.RADIANS_PER_DEGREE).transform(v);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (age == 1) spawnCore(world);
            if (!landed) {
                // Turning, faster and faster as it fills
                spin += (float) (1.0 + 5.0 * formed());
                if (falling()) {
                    double u = (age - dropAt) / (double) fallTicks;
                    center = new Vec3d(hang.x, MathHelper.lerp(u * u, hang.y, floorY + RADIUS * 0.4), hang.z);
                }
                for (Piece p : pieces) {
                    if (p.spawned < 0 && age >= p.at && age < dropAt) spawn(world, boss, p);
                    else if (p.d != null && p.spawned == age - 1) {
                        // Torn loose: now it flies in to its place
                        EnvyHazards.place(p.d, center, at(p.slot, p.size, p.rot), FLY);
                        p.d.setYaw(spin);
                    } else if (p.d != null && p.spawned + 1 + FLY == age && world.random.nextInt(3) == 0) {
                        // Slammed into place
                        world.playSound(null, center.x, center.y + p.slot.y, center.z, p.state.getSoundGroup().getPlaceSound(), SoundCategory.HOSTILE, 4.0f, 0.5f);
                    }
                }
                if (age % TURN_EVERY == 0 || falling()) turn();
                if (age % 10 == 0 && core != null && !falling()) {
                    float pulse = (float) (CORE_SIZE * (0.25 + 0.75 * formed()) * (1 + 0.08 * Math.sin(age * 0.7)));
                    EnvyHazards.place(core, center, at(new Vector3f(), pulse, new Quaternionf().rotationXYZ(age * 0.05f, age * 0.08f, 0)), 10);
                }
                effects(world, boss);
                if (age >= dropAt + fallTicks) {
                    land(world, boss);
                }
                return true;
            }
            int s = age - landedAt;
            if (s == BURST) {
                // Down and gone into the floor
                for (Piece p : pieces) {
                    if (p.d == null) continue;
                    AffineTransformation t = p.thrown;
                    if (t == null) continue;
                    Vector3f to = new Vector3f(t.getTranslation()).add(0, -p.size * 0.6f, 0);
                    p.d.setTransformation(new AffineTransformation(to, t.getLeftRotation(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
                    p.d.setStartInterpolation(0);
                    p.d.setInterpolationDuration(SINK);
                }
            }
            return s < BURST + SINK;
        }

        private void spawnCore(ServerWorld world) {
            core = EntityType.BLOCK_DISPLAY.create(world);
            if (core == null) return;
            core.setBlockState(Blocks.VERDANT_FROGLIGHT.getDefaultState());
            core.setViewRange(6f);
            core.setTeleportDuration(TURN_EVERY);
            core.setBrightness(new Brightness(15, 15));
            core.setGlowing(true);
            core.setGlowColorOverride(0x63E24A);
            core.setTransformation(at(new Vector3f(), 0.5f, new Quaternionf()));
            core.refreshPositionAndAngles(center.x, center.y, center.z, 0, 0);
            world.spawnEntity(core);
        }

        /** A block torn loose in one of the colossi's hands (or out of the dark, if they're gone). */
        private void spawn(ServerWorld world, GummssEntity boss, Piece p) {
            List<Vec3d> hands = boss.covetHands();
            Vec3d from;
            if (!hands.isEmpty()) {
                Vec3d hand = hands.get(nextHand++ % hands.size());
                from = hand.add(world.random.nextGaussian() * 1.2, world.random.nextGaussian() * 1.2, world.random.nextGaussian() * 1.2);
            } else {
                double a = world.random.nextDouble() * MathHelper.TAU;
                from = center.add(Math.cos(a) * 40, -10 + world.random.nextDouble() * 30, Math.sin(a) * 40);
            }
            DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
            if (d == null) return;
            d.setBlockState(p.state);
            d.setViewRange(6f);
            d.setTeleportDuration(TURN_EVERY);
            d.setBrightness(new Brightness(13, 13));
            d.setTransformation(at(local(from), 1.0f, randomTilt(world.random)));
            d.refreshPositionAndAngles(center.x, center.y, center.z, spin, 0);
            world.spawnEntity(d);
            p.d = d;
            p.spawned = age;
            boss.emit(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, p.state), from, 8, 0.4, 0.4, 0.4, 0.1);
            if (world.random.nextInt(4) == 0) {
                world.playSound(null, from.x, from.y, from.z, p.state.getSoundGroup().getBreakSound(), SoundCategory.HOSTILE, 3.0f, 0.5f);
            }
        }

        /** Every block turned with the ball (and moved, as it falls). */
        private void turn() {
            for (Piece p : pieces) if (p.d != null) p.d.refreshPositionAndAngles(center.x, center.y, center.z, spin, 0);
            if (core != null) core.refreshPositionAndAngles(center.x, center.y, center.z, spin * 0.5f, 0);
        }

        /** Threads of his mind from every pair of hands, glyphs streaming in, a storm round it, its shadow on the floor. */
        private void effects(ServerWorld world, GummssEntity boss) {
            double f = formed(), r = RADIUS * (0.35 + 0.65 * f);
            Vec3d ground = new Vec3d(hang.x, floorY, hang.z);
            if (!falling()) {
                if (age % 2 == 0) {
                    for (Vec3d hand : boss.covetHands()) {
                        Vec3d to = center.subtract(hand);
                        double len = to.length();
                        Vec3d dir = to.multiply(1 / len), side = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
                        for (int i = 0; i < 12; i++) {
                            double u = (i + world.random.nextDouble()) / 12, wave = Math.sin(u * 9 + age * 0.6) * 0.8 * (1 - u);
                            Vec3d at = hand.add(to.multiply(u * (1 - r / len))).add(side.multiply(wave));
                            boss.emit(world, i % 3 == 0 ? EnvyHazards.TOXIC : EnvyHazards.PSYCHIC, at, 1, 0, 0, 0, 0);
                        }
                    }
                    // A storm of green round it, two rings turning against each other
                    for (int ring = 0; ring < 2; ring++) {
                        double tilt = ring == 0 ? 0.45 : -0.6, turn = (ring == 0 ? 1 : -1) * age * 0.09;
                        for (int i = 0; i < 18; i++) {
                            double a = i * MathHelper.TAU / 18 + turn, rr = r + 2.5;
                            double x = Math.cos(a) * rr, z = Math.sin(a) * rr;
                            Vec3d at = center.add(x, z * Math.sin(tilt), z * Math.cos(tilt));
                            boss.emit(world, ring == 0 ? EnvyHazards.DEEP_GREEN : EnvyHazards.SHADOW, at, 1, 0, 0, 0, 0);
                        }
                    }
                    // Its shadow spreading over the floor, and the edge of its reach
                    for (int i = 0; i < 14; i++) {
                        double a = world.random.nextDouble() * MathHelper.TAU, d = Math.sqrt(world.random.nextDouble()) * reach * (0.3 + 0.7 * f);
                        world.spawnParticles(EnvyHazards.SHADOW, ground.x + Math.cos(a) * d, floorY + 0.15, ground.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
                    }
                }
                if (age % 3 == 0) {
                    int points = (int) Math.min(120, 16 + reach * 2.6);
                    EnvyHazards.ring(world, f > 0.75 && age % 6 < 3 ? EnvyHazards.TOXIC : EnvyHazards.PSYCHIC, ground, reach, points, age * 0.004);
                }
                // Glyphs and motes drawn in from all round
                for (int i = 0; i < 4; i++) {
                    Vec3d o = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian()).normalize().multiply(r + 6);
                    boss.emit(world, ParticleTypes.ENCHANT, center, 0, o.x, o.y, o.z, 1.0);
                }
                boss.emit(world, ParticleTypes.REVERSE_PORTAL, center, 6, r * 0.6, r * 0.6, r * 0.6, 0.15);
                if (age % 3 == 0) boss.emit(world, ParticleTypes.END_ROD, center, 2, r * 0.3, r * 0.3, r * 0.3, 0.2);
                if (age % 5 == 0) boss.emit(world, ParticleTypes.SCULK_SOUL, center, 3, r * 0.5, r * 0.5, r * 0.5, 0.05);
                if (age % 28 == 0) {
                    world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 8.0f, 0.5f);
                    world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.HOSTILE, 6.0f, 0.5f);
                }
                return;
            }
            // Coming down: a wake of cloud and dust, and the air screaming
            boss.emit(world, ParticleTypes.CLOUD, center.add(0, RADIUS, 0), 20, RADIUS * 0.6, 2, RADIUS * 0.6, 0.05);
            boss.emit(world, ParticleTypes.LARGE_SMOKE, center.add(0, RADIUS * 0.8, 0), 12, RADIUS * 0.5, 2, RADIUS * 0.5, 0.02);
            boss.emit(world, EnvyHazards.DEEP_GREEN, center, 24, RADIUS * 0.7, RADIUS * 0.7, RADIUS * 0.7, 0);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, floorY + 0.2, ground.z, 12, reach * 0.4, 0.1, reach * 0.4, 0.01);
        }

        /** It hits the floor: the boss does the damage, and every block is thrown out across the floor. */
        private void land(ServerWorld world, GummssEntity boss) {
            landed = true;
            landedAt = age;
            boss.covetLands(world, new Vec3d(hang.x, floorY, hang.z));
            Random random = world.random;
            for (Piece p : pieces) {
                if (p.d == null) continue;
                // Out along where it sat, scattered over the floor
                Vector3f flat = new Vector3f(p.slot.x, 0, p.slot.z);
                if (flat.lengthSquared() < 1e-3f) flat.set(random.nextFloat() - 0.5f, 0, random.nextFloat() - 0.5f);
                flat.normalize().mul((float) (RADIUS * 0.8 + random.nextDouble() * reach * 0.75));
                float y = (float) (floorY - center.y) + p.size * 0.35f;
                Quaternionf tumble = new Quaternionf(p.rot).rotateXYZ(random.nextFloat() * 3, random.nextFloat() * 3, random.nextFloat() * 3);
                p.thrown = at(new Vector3f(flat.x, y, flat.z), p.size * 0.8f, tumble);
                EnvyHazards.place(p.d, center, p.thrown, BURST);
            }
            if (core != null) {
                core.discard();
                core = null;
            }
        }

        @Override
        void remove() {
            for (Piece p : pieces) if (p.d != null) p.d.discard();
            if (core != null) core.discard();
        }

        /** For tests: how many of its blocks are out. */
        int blocks() {
            int n = 0;
            for (Piece p : pieces) if (p.d != null && !p.d.isRemoved()) n++;
            return n;
        }
    }
}
