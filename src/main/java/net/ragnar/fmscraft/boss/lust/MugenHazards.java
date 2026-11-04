package net.ragnar.fmscraft.boss.lust;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.minecraft.block.enums.Thickness;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.VoidSpikeBlock;
import net.ragnar.fmscraft.item.ModItems;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What MUGEN leaves in the world: things that outlive the move that made them and tick on their own until they're
 * spent. They only ever hurt the players fighting him and never break or place a block. Three are Ragnars Magic Mod's
 * tomes done his way, built to look and sound like the tomes: the Tome of Logs (the log he leaves when he swaps out of
 * a hit), the Tome of Unseen Hands (the shadow, drawn by the mod itself when it's there) and the Tome of Swapping.
 * The void spikes are a block of our own (VoidSpikeBlock), shown by block displays and never placed.
 */
final class MugenHazards {
    static final DustParticleEffect SHADOW = new DustParticleEffect(new Vector3f(0.07f, 0.02f, 0.1f), 1.8f);
    static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.46f, 0.16f, 0.68f), 1.1f);
    static final DustParticleEffect CRIMSON = new DustParticleEffect(new Vector3f(0.6f, 0.04f, 0.08f), 1.4f);
    static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1f, 1f, 1f), 2.4f);
    static final DustParticleEffect STEEL = new DustParticleEffect(new Vector3f(0.75f, 0.75f, 0.8f), 0.7f);
    /** Item sprites in ModelTransformationMode.NONE, as an item display draws them (turned 180 degrees about Y first). */
    private static final Vector3f TIP_LOCAL = new Vector3f(-1, 1, 0).normalize();
    private static final Vector3f SIDE_LOCAL = new Vector3f(1, 1, 0).normalize();
    private static final Vector3f FACE_LOCAL = new Vector3f(0, 0, -1);

    private MugenHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it's spent. */
        abstract boolean tick(ServerWorld world, MugenEntity boss);

        /** Taken away early (he died, the fight ended): clean up anything it put in the world. */
        void remove() {
        }
    }

    // ---- Helpers ----

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    static DisplayEntity.ItemDisplayEntity itemDisplay(ServerWorld world, ItemStack stack, Vec3d at) {
        DisplayEntity.ItemDisplayEntity d = EntityType.ITEM_DISPLAY.create(world);
        if (d == null) return null;
        d.setItemStack(stack);
        d.setTransformationMode(ModelTransformationMode.NONE);
        d.setTeleportDuration(1);
        d.setViewRange(4f);
        d.setBrightness(new Brightness(15, 15));
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        world.spawnEntity(d);
        return d;
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
        d.setPosition(at.x, at.y, at.z);
        if (transformation != null) d.setTransformation(transformation);
        d.setStartInterpolation(0);
        d.setInterpolationDuration(interpolation);
    }

    /** An item sprite (sword, kunai) pointing along {@code dir}, {@code scale} times its size. */
    static AffineTransformation pointing(Vec3d dir, float scale) {
        Vector3f d = dir.lengthSquared() > 1e-6 ? dir.normalize().toVector3f() : new Vector3f(0, 0, 1);
        return new AffineTransformation(new Vector3f(), new Quaternionf().rotationTo(TIP_LOCAL, d), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** An item sprite pointing along {@code dir} and spun {@code spin} radians round it: a blade tumbling end over end. */
    static AffineTransformation tumbling(Vec3d dir, float spin, float scale) {
        Vec3d flat = dir.multiply(1, 0, 1);
        Vector3f axis = flat.lengthSquared() > 1e-6 ? new Vector3f((float) -flat.z, 0, (float) flat.x).normalize() : new Vector3f(1, 0, 0);
        Quaternionf q = new Quaternionf().rotationAxis(spin, axis).mul(new Quaternionf().rotationTo(TIP_LOCAL, dir.normalize().toVector3f()));
        return new AffineTransformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** An item sprite lying flat (facing up), turned {@code spin} radians: a shuriken whirling. */
    static AffineTransformation flatSpin(float spin, float scale) {
        Quaternionf q = new Quaternionf().rotationY(spin).rotateX(MathHelper.HALF_PI);
        return new AffineTransformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** An item sprite pointing straight down, its flat facing {@code face}. */
    static AffineTransformation pointingDown(Vector3f face, float scale) {
        Vector3f tip = new Vector3f(0, -1, 0);
        Vector3f side = new Vector3f(face).cross(tip).normalize();
        Matrix3f rotation = new Matrix3f(tip, side, face).mul(new Matrix3f(TIP_LOCAL, SIDE_LOCAL, FACE_LOCAL).transpose());
        return new AffineTransformation(new Matrix4f().mul(new Matrix4f(rotation)).scale(scale));
    }

    static boolean blocked(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()))
                .getType() != HitResult.Type.MISS;
    }

    static void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points, double spin) {
        for (int i = 0; i < points; i++) {
            double a = i * MathHelper.TAU / points + spin;
            world.spawnParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    /** The puff he goes up in (and comes out of): a burst of smoke and a dark swirl. {@code big} for the full poof. */
    static void poof(ServerWorld world, Vec3d c, boolean big) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, big ? 40 : 16, 0.4, 0.7, 0.4, big ? 0.06 : 0.03);
        world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, big ? 30 : 12, 0.4, 0.7, 0.4, big ? 0.05 : 0.03);
        world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, big ? 25 : 10, 0.35, 0.6, 0.35, 0.06);
        world.spawnParticles(SHADOW, c.x, c.y, c.z, big ? 20 : 8, 0.4, 0.7, 0.4, 0);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, big ? 1.4f : 0.9f, 1.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, big ? 0.9f : 0.5f, 1.7f);
        if (big) world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.2f, 1.1f);
    }

    /** Holds {@code p} near {@code at}: they hang there. */
    static void steer(ServerPlayerEntity p, Vec3d at) {
        Vec3d to = at.subtract(p.getPos());
        p.setVelocity(to.x * 0.4, to.y * 0.45, to.z * 0.4);
        p.velocityModified = true;
        p.fallDistance = 0;
    }

    /** The first fighter whose box (grown by {@code grow}) the segment {@code from}-{@code to} passes through, or null. */
    static ServerPlayerEntity firstHit(MugenEntity boss, Vec3d from, Vec3d to, double grow, Set<UUID> skip) {
        ServerPlayerEntity hit = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : boss.fighters()) {
            if (skip != null && skip.contains(p.getUuid())) continue;
            var clip = p.getBoundingBox().expand(grow).raycast(from, to);
            if (clip.isEmpty()) continue;
            double d = from.squaredDistanceTo(clip.get());
            if (d < best) {
                best = d;
                hit = p;
            }
        }
        return hit;
    }

    // ---- A circle on the ground: where something is about to land ----

    static final class Circle extends Hazard {
        Vec3d center;
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
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (age % 2 == 0) ring(world, particle, center, radius, (int) Math.min(72, 10 + radius * 5), age * 0.06);
            return age < ticks;
        }
    }

    // ---- A ring of force rushing out along the ground. With damage: only those on the ground as it passes ----

    static final class Shockwave extends Hazard {
        final Vec3d center;
        final double speed, max, push, up;
        final float damage;
        final ParticleEffect particle;
        final boolean everyone;
        final Set<UUID> settled = new HashSet<>();
        double radius;

        /** {@code everyone}: it throws back whoever it passes, on the ground or not (his landings). */
        Shockwave(Vec3d center, double speed, double max, float damage, double push, double up, ParticleEffect particle, boolean everyone) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.push = push;
            this.up = up;
            this.particle = particle;
            this.everyone = everyone;
        }

        static Shockwave visual(Vec3d center, double speed, double max, ParticleEffect particle) {
            return new Shockwave(center, speed, max, 0, 0, 0, particle, false);
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            radius += speed;
            if (radius > max) return false;
            int points = (int) Math.min(100, 10 + radius * 5);
            ring(world, particle, center, radius, points, radius * 0.1);
            if (age++ % 2 == 0) ring(world, ParticleTypes.LARGE_SMOKE, center, radius - 0.3, points / 3, 0);
            if (damage <= 0 && push <= 0) return true;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (settled.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center);
                if (d > radius || Math.abs(p.getY() - center.y) > (everyone ? 6 : 3)) continue;
                settled.add(p.getUuid());
                if (!everyone && (d < radius - speed - 1.2 || !p.isOnGround())) continue; // jumped it, or was already past
                if (damage > 0) boss.crush(p, damage);
                boss.launch(p, p.getPos().subtract(center), push, up);
            }
            return true;
        }
    }

    // ---- Things he throws: kunai, shuriken, his blade, black blaze balls ----

    enum Kind {
        /** Straight and fast, sticks in walls. */
        KUNAI,
        /** Whirling flat, a little slower. */
        SHURIKEN,
        /** One of his own blades, tumbling: it vanishes in smoke at the end and is back in his hand. */
        BLADE,
        /** A black blaze ball: sets you burning. */
        CHARGE
    }

    static final class Thrown extends Hazard {
        static final int STUCK = 30;
        final Kind kind;
        final DisplayEntity.ItemDisplayEntity display;
        final float damage;
        final double range;
        Vec3d pos, vel;
        double travelled;
        int stuck = -1;

        Thrown(ServerWorld world, Kind kind, Vec3d from, Vec3d vel, float damage, double range) {
            this.kind = kind;
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            this.range = range;
            this.display = itemDisplay(world, stack(kind), from);
            if (display != null) {
                display.setViewRange(kind == Kind.BLADE ? 6f : 3f);
                if (kind == Kind.CHARGE) display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
                display.setTransformation(transform(0));
            }
        }

        static ItemStack stack(Kind kind) {
            return new ItemStack(switch (kind) {
                case KUNAI -> ModItems.KUNAI;
                case SHURIKEN -> ModItems.SHURIKEN;
                case BLADE -> Items.NETHERITE_SWORD;
                case CHARGE -> ModItems.SHADOW_CHARGE;
            });
        }

        AffineTransformation transform(int t) {
            return switch (kind) {
                case KUNAI -> pointing(vel, 0.9f);
                case SHURIKEN -> flatSpin(t * 1.1f, 0.85f);
                case BLADE -> tumbling(vel, t * 0.9f, 1.3f);
                case CHARGE -> new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf());
            };
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (display == null) return false;
            if (stuck >= 0) {
                if (++stuck >= STUCK) return end(world, boss);
                return true;
            }
            if (travelled > range) return end(world, boss);
            Vec3d from = pos, to = pos.add(vel);
            ServerPlayerEntity hitPlayer = firstHit(boss, from, to, kind == Kind.CHARGE ? 0.45 : 0.3, null);
            double hitDist = hitPlayer == null ? Double.MAX_VALUE : from.squaredDistanceTo(hitPlayer.getBoundingBox().getCenter());
            BlockHitResult wall = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
            if (wall.getType() == HitResult.Type.BLOCK && (hitPlayer == null || from.squaredDistanceTo(wall.getPos()) < hitDist)) {
                pos = wall.getPos().subtract(vel.normalize().multiply(0.3));
                if (kind == Kind.KUNAI || kind == Kind.SHURIKEN) {
                    // Sticks where it hit, quivering
                    place(display, pos, kind == Kind.KUNAI ? pointing(vel, 0.9f) : flatSpin(age * 1.1f, 0.85f), 1);
                    stuck = 0;
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 0.9f, 1.6f);
                    world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.15);
                    return true;
                }
                return end(world, boss);
            }
            if (hitPlayer != null) {
                hit(world, boss, hitPlayer);
                return kind == Kind.BLADE ? end(world, boss) : finish();
            }
            pos = to;
            travelled += vel.length();
            place(display, pos, transform(age), 1);
            trail(world, from, to);
            return true;
        }

        private void hit(ServerWorld world, MugenEntity boss, ServerPlayerEntity p) {
            Vec3d c = p.getBoundingBox().getCenter();
            // Every one of them lands: they come faster than a player can flinch
            if (kind == Kind.KUNAI || kind == Kind.SHURIKEN) p.timeUntilRegen = 0;
            if (boss.strike(p, damage)) {
                p.addVelocity(vel.normalize().x * 0.35, 0.12, vel.normalize().z * 0.35);
                p.velocityModified = true;
                if (kind == Kind.CHARGE) p.setOnFireFor(4);
            }
            switch (kind) {
                case CHARGE -> {
                    world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.06);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 1.0f, 0.5f);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.8f, 0.6f);
                }
                case BLADE -> {
                    world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.3);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.4f, 0.8f);
                }
                default -> {
                    world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.25);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.0f, 1.5f);
                }
            }
        }

        private void trail(ServerWorld world, Vec3d from, Vec3d to) {
            switch (kind) {
                case KUNAI -> world.spawnParticles(STEEL, from.x, from.y, from.z, 1, 0.01, 0.01, 0.01, 0);
                case SHURIKEN -> {
                    world.spawnParticles(STEEL, from.x, from.y, from.z, 1, 0.05, 0.02, 0.05, 0);
                    if (age % 4 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.4f, 2.0f);
                }
                case BLADE -> {
                    world.spawnParticles(SHADOW, from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0);
                    if (age % 3 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 1.3f);
                }
                case CHARGE -> {
                    for (int i = 0; i < 2; i++) {
                        Vec3d p = from.lerp(to, i / 2.0);
                        world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.01);
                        world.spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }

        private boolean finish() {
            display.discard();
            return false;
        }

        private boolean end(ServerWorld world, MugenEntity boss) {
            if (kind == Kind.BLADE) {
                // Gone in a puff of smoke, and back in his hand
                poof(world, pos, false);
                boss.bladeReturns();
            } else if (kind == Kind.CHARGE) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.04);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.04);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.7f, 0.8f);
            } else {
                world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0.01);
            }
            return finish();
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- Get Over Here: a chain with a kunai on its end. It hooks whoever it reaches first ----

    static final class Chain extends Hazard {
        static final double SPEED = 2.2, SEGMENT = 0.9;
        final List<DisplayEntity.BlockDisplayEntity> links = new ArrayList<>();
        final DisplayEntity.ItemDisplayEntity tip;
        final Vec3d dir;
        final double reach;
        /** Pull the hooked one in to him (Get Over Here), or him down onto them (the grapple). */
        final boolean pullTarget;
        double length;
        ServerPlayerEntity hooked;
        boolean missed, retracting, done;

        Chain(ServerWorld world, Vec3d from, Vec3d dir, double reach, boolean pullTarget) {
            this.dir = dir.normalize();
            this.reach = reach;
            this.pullTarget = pullTarget;
            this.tip = itemDisplay(world, new ItemStack(ModItems.KUNAI), from);
            if (tip != null) {
                tip.setViewRange(5f);
                tip.setTransformation(pointing(this.dir, 1.0f));
            }
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }

        /** Where the chain starts: his hand. */
        Vec3d origin(MugenEntity boss) {
            return boss.handPos(true);
        }

        Vec3d end(MugenEntity boss) {
            if (hooked != null) return hooked.getBoundingBox().getCenter();
            return origin(boss).add(dir.multiply(length));
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (done || age > 120) return false;
            Vec3d from = origin(boss);
            if (hooked == null && !retracting) {
                Vec3d before = from.add(dir.multiply(length));
                length = Math.min(reach, length + SPEED);
                Vec3d after = from.add(dir.multiply(length));
                ServerPlayerEntity hit = firstHit(boss, before, after, 0.4, null);
                BlockHitResult wall = world.raycast(new RaycastContext(before, after, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
                if (wall.getType() == HitResult.Type.BLOCK && (hit == null || before.squaredDistanceTo(wall.getPos()) < before.squaredDistanceTo(hit.getPos()))) {
                    length = from.distanceTo(wall.getPos());
                    missed = retracting = true;
                    world.playSound(null, wall.getPos().x, wall.getPos().y, wall.getPos().z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.0f, 1.2f);
                    world.spawnParticles(ParticleTypes.CRIT, wall.getPos().x, wall.getPos().y, wall.getPos().z, 8, 0.1, 0.1, 0.1, 0.2);
                } else if (hit != null) {
                    hooked = hit;
                    Vec3d c = hit.getBoundingBox().getCenter();
                    world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.3);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.5f, 0.6f);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.HOSTILE, 2.0f, 0.6f);
                    boss.strike(hit, 3);
                } else if (length >= reach) {
                    missed = retracting = true;
                }
            } else if (retracting) {
                length -= SPEED * 1.3;
                if (length <= 0.5) return false;
                if (age % 3 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 1.0f, 1.2f);
            }
            draw(world, boss);
            return true;
        }

        /** Lets go (pulled all the way in, or the hook is torn out). */
        void release() {
            hooked = null;
            done = true;
        }

        private void draw(ServerWorld world, MugenEntity boss) {
            Vec3d from = origin(boss), to = end(boss);
            Vec3d d = to.subtract(from);
            double len = d.length();
            if (len < 1e-3) return;
            Vec3d n = d.multiply(1 / len);
            int count = (int) Math.ceil(len / SEGMENT);
            while (links.size() < Math.min(count, 40)) {
                var link = blockDisplay(world, Blocks.CHAIN.getDefaultState(), from);
                if (link == null) break;
                links.add(link);
            }
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), n.toVector3f());
            for (int i = 0; i < links.size(); i++) {
                var link = links.get(i);
                if (i >= count) {
                    place(link, from, new AffineTransformation(new Matrix4f().scale(0.001f)), 0);
                    continue;
                }
                double l = Math.min(SEGMENT, len - i * SEGMENT);
                Vec3d at = from.add(n.multiply(i * SEGMENT));
                place(link, at, new AffineTransformation(new Matrix4f().rotate(q).scale(1f, (float) l, 1f).translate(-0.5f, 0f, -0.5f)), 1);
            }
            if (tip != null) place(tip, to, pointing(n, 1.0f), 1);
        }

        @Override
        void remove() {
            for (var l : links) l.discard();
            if (tip != null) tip.discard();
        }
    }

    // ---- The Black Orb: slow, heavy, and it puts out your sight ----

    static final class Orb extends Hazard {
        static final double RADIUS = 2.6, RANGE = 40;
        final DisplayEntity.ItemDisplayEntity display;
        final float damage;
        Vec3d pos, vel;
        double travelled;

        Orb(ServerWorld world, Vec3d from, Vec3d vel, float damage) {
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            this.display = itemDisplay(world, new ItemStack(ModItems.SHADOW_ORB), from);
            if (display != null) {
                display.setViewRange(6f);
                display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
                display.setTransformation(new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf()));
            }
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.4f, 1.2f);
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (display == null) return false;
            Vec3d to = pos.add(vel);
            boolean wall = blocked(world, pos, to);
            ServerPlayerEntity near = firstHit(boss, pos, to, 0.8, null);
            pos = to;
            travelled += vel.length();
            float grow = Math.min(1f, age / 8f);
            float pulse = 1.9f + 0.15f * MathHelper.sin(age * 0.6f);
            place(display, pos, new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(pulse * grow, pulse * grow, pulse * grow), new Quaternionf()), 1);
            world.spawnParticles(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 2, 0.35, 0.35, 0.35, 0.01);
            world.spawnParticles(SHADOW, pos.x, pos.y, pos.z, 3, 0.45, 0.45, 0.45, 0);
            world.spawnParticles(VIOLET, pos.x, pos.y, pos.z, 1, 0.6, 0.6, 0.6, 0);
            if (age % 14 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.6f, 0.5f);
            if (wall || near != null || travelled > RANGE) {
                burst(world, boss);
                return false;
            }
            return true;
        }

        private void burst(ServerWorld world, MugenEntity boss) {
            world.spawnParticles(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 80, 1.0, 1.0, 1.0, 0.15);
            world.spawnParticles(SHADOW, pos.x, pos.y, pos.z, 60, 1.4, 1.2, 1.4, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.0f, 1.3f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.2f, 0.6f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (p.getBoundingBox().getCenter().distanceTo(pos) > RADIUS + 0.5) continue;
                boss.crush(p, damage);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0, false, true, true), boss);
                boss.launch(p, p.getPos().subtract(pos), 0.6, 0.3);
            }
            display.discard();
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- Sword Rain: netherite swords out of the night, one on each of you ----

    static final class GiantSword extends Hazard {
        static final float SCALE = 9f;
        static final double DROP = 30, BURY = 1.8, RADIUS = 3.8;
        static final int MARK = 30, FORM = 10, FALL = 6, STAND = 24, CRUMBLE = 10;
        final Vec3d target;
        final Vector3f face;
        final int delay, glow;
        final float centerDamage, edgeDamage;
        final double tipOffset = SCALE * 0.62;
        DisplayEntity.ItemDisplayEntity sword;

        GiantSword(Vec3d target, Vec3d from, int delay, float centerDamage, float edgeDamage, int glow) {
            this.target = target;
            Vec3d to = from.subtract(target);
            this.face = to.horizontalLengthSquared() > 1e-4 ? new Vector3f((float) to.x, 0, (float) to.z).normalize() : new Vector3f(0, 0, 1);
            this.delay = delay;
            this.centerDamage = centerDamage;
            this.edgeDamage = edgeDamage;
            this.glow = glow;
        }

        Vec3d center(double height) {
            return target.add(0, tipOffset + height, 0);
        }

        int impactIn() {
            return delay + MARK + FALL;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            int t = age - delay;
            if (t <= 0) return true;
            int impactAt = MARK + FALL;
            if (t < impactAt && t % 2 == 0) {
                for (double y = 0.2; y < DROP; y += 1.6) world.spawnParticles(VIOLET, target.x, target.y + y, target.z, 1, 0.02, 0.2, 0.02, 0);
                ring(world, boss.inPhaseTwo() ? CRIMSON : VIOLET, target, RADIUS, 30, t * 0.1);
            }
            if (t == MARK - FORM) {
                sword = itemDisplay(world, new ItemStack(Items.NETHERITE_SWORD), center(DROP));
                if (sword != null) {
                    sword.setViewRange(8f);
                    sword.setGlowing(true);
                    sword.setGlowColorOverride(glow);
                    sword.setTransformation(pointingDown(face, 0.01f));
                }
                world.playSound(null, target.x, target.y + DROP, target.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            }
            if (sword == null) return t < MARK;
            if (t <= MARK) {
                float grow = Math.min(1f, (t - (MARK - FORM)) / (float) FORM);
                place(sword, center(DROP + Math.sin(t * 2.5) * 0.08), pointingDown(face, SCALE * (1 - (1 - grow) * (1 - grow))), 1);
                if (t == MARK) world.playSound(null, target.x, target.y + DROP / 2, target.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 5.0f, 0.5f);
                return true;
            }
            if (t < impactAt) {
                double f = (t - MARK) / (double) FALL;
                place(sword, center(MathHelper.lerp(f * f, DROP, -BURY)), null, 1);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + DROP * (1 - f * f) + 2, target.z, 4, 0.3, 2.0, 0.3, 0.01);
                return true;
            }
            if (t == impactAt) {
                place(sword, center(-BURY), null, 1);
                sword.setGlowing(false);
                impact(world, boss);
                return true;
            }
            int standing = t - impactAt;
            if (standing < STAND) {
                if (standing % 4 == 0) world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.x, target.y + 0.5, target.z, 1, 0.5, 0.1, 0.5, 0.01);
                return true;
            }
            int crumble = standing - STAND;
            if (crumble == 0) {
                place(sword, center(-BURY - tipOffset * 0.5), pointingDown(face, 0.01f), CRUMBLE);
                world.playSound(null, target.x, target.y, target.z, SoundEvents.BLOCK_NETHERITE_BLOCK_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            }
            if (crumble < CRUMBLE) {
                world.spawnParticles(SHADOW, target.x, target.y + 2, target.z, 5, 0.5, 2, 0.5, 0);
                return true;
            }
            return false;
        }

        private void impact(ServerWorld world, MugenEntity boss) {
            Vec3d t = target;
            world.playSound(null, t.x, t.y, t.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, t.x, t.y, t.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.55f);
            world.playSound(null, t.x, t.y, t.z, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.spawnParticles(ParticleTypes.FLASH, t.x, t.y + 1, t.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 1, 0, 0, 0, 0);
            BlockState ground = world.getBlockState(BlockPos.ofFloored(t.x, t.y - 0.5, t.z));
            BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground.isAir() ? Blocks.COBBLESTONE.getDefaultState() : ground);
            for (int i = 0; i < 32; i++) {
                double a = i * MathHelper.TAU / 32;
                world.spawnParticles(debris, t.x + Math.cos(a) * 1.5, t.y + 0.2, t.z + Math.sin(a) * 1.5, 2, 0.3, 0.3, 0.3, 0.3);
                world.spawnParticles(SHADOW, t.x + Math.cos(a) * RADIUS, t.y + 0.3, t.z + Math.sin(a) * RADIUS, 1, 0.1, 0.2, 0.1, 0);
            }
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = flatDistance(p.getPos(), t);
                if (d > RADIUS + 0.3 || Math.abs(p.getY() - t.y) > 3) continue;
                float falloff = (float) MathHelper.clamp(d / RADIUS, 0, 1);
                p.timeUntilRegen = 0;
                boss.crush(p, MathHelper.lerp(falloff, centerDamage, edgeDamage));
                boss.launch(p, p.getPos().subtract(t), 0.6, 1.0 * (1.1 - falloff * 0.5));
            }
        }

        @Override
        void remove() {
            if (sword != null) sword.discard();
        }
    }

    // ---- Tome of Logs: where he was hit, a log takes the blow ----

    /**
     * The log left behind, exactly as the Tome of Logs leaves it: a block display about the size of a player. It pops up
     * in the smoke, drops to the ground with a thud, stands a moment, topples over away from whoever hit it, lies there,
     * then puffs away.
     */
    static final class Log extends Hazard {
        static final float WIDTH = 0.62f, HEIGHT = 1.7f;
        static final int STAND = 4, TOPPLE = 8, LIE = 30;
        static final BlockState LOG = Blocks.OAK_LOG.getDefaultState();
        static final ParticleEffect SPLINTERS = new BlockStateParticleEffect(ParticleTypes.BLOCK, LOG);
        final DisplayEntity.BlockDisplayEntity display;
        final Vector3f axis;
        Vec3d pos;
        double vy = 0.16;
        int landedAt = -1;

        Log(ServerWorld world, Vec3d feet, Vec3d away) {
            this.pos = feet.add(0, 0.2, 0);
            Vec3d flat = away.multiply(1, 0, 1);
            flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : new Vec3d(1, 0, 0);
            this.axis = new Vector3f((float) flat.z, 0f, (float) -flat.x).normalize();
            this.display = blockDisplay(world, LOG, pos);
            if (display != null) {
                display.setTeleportDuration(2);
                display.setViewRange(3f);
                display.setTransformation(shape(0f, new Vector3f(1, 0, 0)));
            }
            Vec3d c = feet.add(0, 0.9, 0);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 45, 0.35, 0.55, 0.35, 0.07);
            world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 25, 0.3, 0.5, 0.3, 0.09);
            world.spawnParticles(WHITE, c.x, c.y, c.z, 18, 0.45, 0.6, 0.45, 0);
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(SPLINTERS, c.x, c.y, c.z, 25, 0.3, 0.5, 0.3, 0.15);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.2f, 1.0f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 0.9f, 1.3f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }

        private static AffineTransformation shape(float angle, Vector3f axis) {
            float t = angle / MathHelper.HALF_PI;
            return new AffineTransformation(new Matrix4f()
                    .translate(0f, WIDTH / 2 * t, 0f)
                    .rotate(angle, axis)
                    .scale(WIDTH, HEIGHT, WIDTH)
                    .translate(-0.5f, 0f, -0.5f));
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            if (display == null || display.isRemoved()) return false;
            age++;
            if (landedAt < 0) {
                vy -= 0.06;
                Vec3d next = pos.add(0, vy, 0);
                BlockHitResult hit = vy < 0 ? world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent())) : null;
                if (hit != null && hit.getType() != HitResult.Type.MISS) {
                    pos = hit.getPos();
                    landedAt = age;
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_WOOD_FALL, SoundCategory.HOSTILE, 1.2f, 0.7f);
                    world.spawnParticles(SPLINTERS, pos.x, pos.y + 0.05, pos.z, 12, 0.25, 0.02, 0.25, 0.1);
                } else {
                    pos = next;
                    if (age > 80) return vanish(world);
                }
                display.setPosition(pos);
                return true;
            }
            int toppling = age - landedAt - STAND;
            if (toppling > 0 && toppling <= TOPPLE) {
                float t = toppling / (float) TOPPLE;
                place(display, pos, shape(MathHelper.HALF_PI * t * t, axis), 1);
                if (toppling == TOPPLE) {
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 0.5f, 1.4f);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_WOOD_FALL, SoundCategory.HOSTILE, 1.2f, 0.6f);
                    Vec3d along = new Vec3d(-axis.z(), 0, axis.x()).multiply(HEIGHT / 2);
                    Vec3d mid = pos.add(along);
                    world.spawnParticles(SPLINTERS, mid.x, mid.y + 0.1, mid.z, 20, Math.abs(along.x) + 0.2, 0.05, Math.abs(along.z) + 0.2, 0.1);
                }
            }
            if (toppling > TOPPLE + LIE) return vanish(world);
            return true;
        }

        private boolean vanish(ServerWorld world) {
            Vec3d along = landedAt >= 0 ? new Vec3d(-axis.z(), 0, axis.x()).multiply(HEIGHT / 2) : new Vec3d(0, HEIGHT / 2, 0);
            Vec3d c = pos.add(along).add(0, 0.3, 0);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 20, 0.5, 0.25, 0.5, 0.03);
            world.spawnParticles(WHITE, c.x, c.y, c.z, 8, 0.5, 0.25, 0.5, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 0.5f, 1.8f);
            display.discard();
            return false;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- Tome of Unseen Hands: a pool of living shadow creeps out, then the hands come up and crush what's in it ----

    /**
     * One shadow, run the way the tome runs it: the pool creeps out for {@link MugenTimeline#POOL_CREEP} ticks, a heartbeat
     * quickening under it; then black arms burst out of it and seize everyone standing in it, lift them off their feet and
     * crush them, five squeezes over four seconds, through any armour; then they sink and the pool drains. The magic mod
     * draws the pool and the hands (the boss sends its packets); without it, particles.
     * <p>
     * In his entrance it hurts nobody: its hands close on him instead and drag him up out of the ground.
     */
    static final class Pool extends Hazard {
        static final int CREEP = MugenTimeline.POOL_CREEP, SQUEEZE = MugenTimeline.POOL_HOLD, PULSES = 5, AFTER = 40;
        static final double REACH_UP = 6, REACH_DOWN = 2, LIFT = 0.5;
        private static int nextId = 0x4D554700;

        private record Victim(LivingEntity entity, Vec3d pin) {
        }

        final int id = nextId++;
        final Vec3d center;
        final float radius, damage;
        /** Null: crush everyone caught. Otherwise only this one is seized (his entrance), and nobody is hurt. */
        final LivingEntity only;
        final List<Victim> victims = new ArrayList<>();
        boolean drawn;
        int nextBeat;

        Pool(ServerWorld world, Vec3d center, float radius, float damage, LivingEntity only) {
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.only = only;
            drawn = MagicSpells.shadowPool(world, id, center, radius);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.2f, 0.5f);
            world.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y + 0.1, center.z, 10, 0.3, 0.05, 0.3, 0.04);
        }

        boolean grabbed() {
            return age >= CREEP;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            int releaseAt = CREEP + SQUEEZE;
            if (age < CREEP) creep(world);
            else if (age == CREEP) grab(world, boss);
            else if (age <= releaseAt) {
                hold(world);
                if ((age - CREEP) % (SQUEEZE / PULSES) == 0) squeeze(world, boss);
                if (age == releaseAt) release(world);
            } else if (!drawn) {
                // Draining away
                float f = 1 - (age - releaseAt) / (float) AFTER;
                if (age % 2 == 0) disc(world, radius * f, 0.5f);
            }
            if (!drawn && age <= releaseAt) disc(world, radius * Math.min(1f, frontAt(age / (float) CREEP)), 1f);
            return age < releaseAt + AFTER;
        }

        /** How far out the creeping edge is, 0..1 (the tome's own curve). */
        static float frontAt(float t) {
            t = MathHelper.clamp(t, 0f, 1f);
            return t * t * (3f - 2f * t) * 0.35f + t * 0.65f;
        }

        /** Without the mod: the pool as specks of shadow on the ground, and its purple edge. */
        private void disc(ServerWorld world, double r, float density) {
            if (r < 0.3) return;
            int n = (int) (r * r * 1.2 * density);
            for (int i = 0; i < n; i++) {
                double a = world.random.nextDouble() * MathHelper.TAU, d = Math.sqrt(world.random.nextDouble()) * r;
                world.spawnParticles(SHADOW, center.x + Math.cos(a) * d, center.y + 0.1, center.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
            }
            ring(world, VIOLET, center, r, (int) (8 + r * 5), age * 0.05);
        }

        private void creep(ServerWorld world) {
            float t = (float) age / CREEP;
            double r = radius * frontAt(t);
            for (int i = 0; i < 3; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                if (i == 0) world.spawnParticles(ParticleTypes.SQUID_INK, x, center.y + 0.15, z, 1, 0.1, 0.02, 0.1, 0.01);
                world.spawnParticles(SHADOW, x, center.y + 0.2, z, 1, 0.2, 0.05, 0.2, 0);
            }
            if (age >= nextBeat) {
                float rate = MathHelper.lerp(t, 18f, 6f);
                nextBeat = age + Math.round(rate);
                world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0f + 2f * t, 0.6f + 0.3f * t);
            }
            if (age % 12 == 0) {
                double a = world.random.nextDouble() * Math.PI * 2;
                world.playSound(null, center.x + Math.cos(a) * r, center.y, center.z + Math.sin(a) * r, SoundEvents.BLOCK_SCULK_SPREAD,
                        SoundCategory.HOSTILE, 1.5f, 0.5f + world.random.nextFloat() * 0.2f);
            }
            if (age == CREEP - 14) world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }

        private void grab(ServerWorld world, MugenEntity boss) {
            List<Integer> ids = new ArrayList<>();
            if (only != null) {
                ids.add(only.getId());
            } else {
                for (ServerPlayerEntity p : boss.fighters()) {
                    double dx = p.getX() - center.x, dz = p.getZ() - center.z;
                    if (dx * dx + dz * dz > radius * radius || p.getY() > center.y + REACH_UP || p.getY() < center.y - REACH_DOWN) continue;
                    victims.add(new Victim(p, p.getPos()));
                    ids.add(p.getId());
                    Vec3d at = p.getPos();
                    world.spawnParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.1, at.z, 8, 0.5, 0.1, 0.5, 0.06);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 8, 0.4, 0.2, 0.4, 0.02);
                    world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.5f);
                    world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 1.2f, 0.6f);
                }
            }
            MagicSpells.shadowGrab(world, id, center, ids);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 3.0f, 1.3f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.0f, 1.6f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            if (!drawn) {
                for (int i = 0; i < 24; i++) {
                    double a = i * MathHelper.TAU / 24, d = radius * world.random.nextDouble();
                    world.spawnParticles(ParticleTypes.SQUID_INK, center.x + Math.cos(a) * d, center.y + 0.5, center.z + Math.sin(a) * d, 6, 0.1, 0.8, 0.1, 0.02);
                }
            }
        }

        /** Keeps everyone caught pinned where the hands hold them, lifted a little off their feet. */
        private void hold(ServerWorld world) {
            float lift = MathHelper.clamp((age - CREEP) / 8f, 0f, 1f);
            victims.removeIf(v -> !v.entity().isAlive() || v.entity().isRemoved());
            for (Victim v : victims) {
                LivingEntity e = v.entity();
                Vec3d at = v.pin().add(0, LIFT * lift, 0);
                e.setVelocity(Vec3d.ZERO);
                e.fallDistance = 0;
                if (e instanceof ServerPlayerEntity sp && sp.getPos().squaredDistanceTo(at) > 0.0025) {
                    sp.networkHandler.requestTeleport(at.x, at.y, at.z, sp.getYaw(), sp.getPitch());
                }
                if (world.random.nextInt(3) == 0) {
                    Vec3d c = e.getBoundingBox().getCenter();
                    world.spawnParticles(SHADOW, c.x, c.y, c.z, 1, e.getWidth() * 0.5, e.getHeight() * 0.4, e.getWidth() * 0.5, 0);
                }
                if (!drawn && age % 2 == 0) {
                    Vec3d c = e.getPos();
                    for (int i = 0; i < 4; i++) {
                        double a = i * MathHelper.HALF_PI + age * 0.1;
                        world.spawnParticles(ParticleTypes.SQUID_INK, c.x + Math.cos(a) * 0.8, c.y + 0.6, c.z + Math.sin(a) * 0.8, 1, 0.05, 0.4, 0.05, 0);
                    }
                }
            }
        }

        private void squeeze(ServerWorld world, MugenEntity boss) {
            for (Victim v : victims) {
                LivingEntity e = v.entity();
                e.timeUntilRegen = 0;
                if (only == null && e instanceof ServerPlayerEntity p) boss.crush(p, damage);
                Vec3d c = e.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 3, e.getWidth() * 0.4, e.getHeight() * 0.3, e.getWidth() * 0.4, 0.02);
                world.spawnParticles(SHADOW, c.x, c.y, c.z, 8, e.getWidth() * 0.5, e.getHeight() * 0.4, e.getWidth() * 0.5, 0);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.HOSTILE, 1.4f, 0.55f + world.random.nextFloat() * 0.15f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
            if (!victims.isEmpty()) world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }

        /** Lets go of everyone, gently: nobody takes fall damage from where the hands held them. */
        private void release(ServerWorld world) {
            for (Victim v : victims) v.entity().fallDistance = 0;
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 2.5f, 0.8f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_SCULK_CATALYST_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            victims.clear();
        }

        /** Who's held right now (for tests). */
        List<LivingEntity> held() {
            return victims.stream().map(Victim::entity).toList();
        }

        @Override
        void remove() {
            for (Victim v : victims) v.entity().fallDistance = 0;
            victims.clear();
        }
    }

    // ---- Smoke Bomb: a cloud of thick smoke; inside it you're blind ----

    static final class Smoke extends Hazard {
        final Vec3d center;
        final double radius;
        final int ticks;

        Smoke(ServerWorld world, Vec3d center, double radius, int ticks) {
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
            world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, center.y + 0.5, center.z, 40, radius * 0.4, 0.4, radius * 0.4, 0.03);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.9f, 1.8f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
        }

        boolean inside(Vec3d pos) {
            return pos.squaredDistanceTo(center.add(0, 1, 0)) < radius * radius;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            float fade = age > ticks - 30 ? (ticks - age) / 30f : 1f;
            int n = (int) (14 * fade);
            for (int i = 0; i < n; i++) {
                double a = world.random.nextDouble() * MathHelper.TAU, d = Math.sqrt(world.random.nextDouble()) * radius;
                double y = world.random.nextDouble() * radius * 0.7;
                world.spawnParticles(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE,
                        center.x + Math.cos(a) * d, center.y + y, center.z + Math.sin(a) * d, 1, 0.3, 0.3, 0.3, 0.005);
            }
            if (age % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = world.random.nextDouble() * MathHelper.TAU, d = Math.sqrt(world.random.nextDouble()) * radius;
                    world.spawnParticles(ParticleTypes.CLOUD, center.x + Math.cos(a) * d, center.y + world.random.nextDouble() * 2.5, center.z + Math.sin(a) * d, 1, 0.2, 0.2, 0.2, 0.002);
                }
            }
            if (age % 5 == 0 && fade > 0.3f) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (inside(p.getEyePos())) p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, false, true), boss);
                }
            }
            return age < ticks;
        }
    }

    // ---- Shadow Step: the cuts he leaves hanging in the air where he passed, falling a moment later ----

    static final class Slash extends Hazard {
        final Vec3d from, to;
        final int delay;
        final float damage;

        Slash(Vec3d from, Vec3d to, int delay, float damage) {
            this.from = from;
            this.to = to;
            this.delay = delay;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            double len = from.distanceTo(to);
            if (age < delay) {
                // The cut hangs in the air, a thin black line, trembling
                for (double s = 0; s < len; s += 0.5) {
                    Vec3d p = from.lerp(to, s / len);
                    world.spawnParticles(SHADOW, p.x, p.y + 1.1, p.z, 1, 0.02, 0.02, 0.02, 0);
                    if (world.random.nextInt(4) == 0) world.spawnParticles(CRIMSON, p.x, p.y + 1.1, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
                return true;
            }
            for (double s = 0; s < len; s += 1.2) {
                Vec3d p = from.lerp(to, s / len);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.1, p.z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y + 1.1, p.z, 3, 0.2, 0.2, 0.2, 0.05);
            }
            Vec3d mid = from.lerp(to, 0.5);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.5f, 1.4f);
            Vec3d d = to.subtract(from);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d rel = p.getPos().subtract(from);
                double u = len < 1e-3 ? 0 : MathHelper.clamp(rel.dotProduct(d) / (len * len), 0, 1);
                Vec3d closest = from.add(d.multiply(u));
                if (flatDistance(p.getPos(), closest) > 1.3 || Math.abs(p.getY() - closest.y) > 2.2) continue;
                if (boss.cut(p, damage)) boss.launch(p, new Vec3d(-d.z, 0, d.x), 0.4, 0.35);
            }
            return false;
        }
    }

    // ---- Void Spikes: the ground goes black under where one will come, then a spike of the void bursts up out of it ----

    static final class VoidSpike extends Hazard {
        /** The black spreading over the block, the spike bursting up, standing, then sinking back. */
        static final int SPREAD = 22, RISE = 4, STAND = 24, SINK = 8;
        static final int HEIGHT = 4;
        static final float WIDTH = 1.35f;
        static final Thickness[] PARTS = {Thickness.BASE, Thickness.MIDDLE, Thickness.FRUSTUM, Thickness.TIP};
        final BlockPos block;
        final Vec3d top;
        final int delay;
        final float damage;
        DisplayEntity.BlockDisplayEntity stain;
        final DisplayEntity.BlockDisplayEntity[] parts = new DisplayEntity.BlockDisplayEntity[HEIGHT];

        /** {@code top}: the top of the ground where it comes up. */
        VoidSpike(Vec3d top, int delay, float damage) {
            this.top = top;
            this.block = BlockPos.ofFloored(top.x, top.y - 0.01, top.z);
            this.delay = delay;
            this.damage = damage;
        }

        /** The block under it, going black from the middle out ({@code grow} 0-1). */
        private static AffineTransformation stainShape(float grow) {
            float g = 0.05f + 0.954f * grow;
            return new AffineTransformation(new Matrix4f().translate(0.5f, 0.5f, 0.5f).scale(g, 1.004f, g).translate(-0.5f, -0.5f, -0.5f));
        }

        private static AffineTransformation partShape() {
            return new AffineTransformation(new Matrix4f().translate(0.5f, 0f, 0.5f).scale(WIDTH, 1f, WIDTH).translate(-0.5f, 0f, -0.5f));
        }

        private Vec3d partAt(int k, double sunk) {
            return new Vec3d(block.getX(), top.y + k - sunk, block.getZ());
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            int t = age - delay;
            if (t <= 0) return true;
            Vec3d c = new Vec3d(block.getX() + 0.5, top.y, block.getZ() + 0.5);
            if (t == 1) {
                stain = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), Vec3d.of(block));
                if (stain != null) {
                    stain.setBrightness(new Brightness(0, 0));
                    stain.setTransformation(stainShape(0));
                }
                world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
            if (t <= SPREAD) {
                if (stain != null) place(stain, Vec3d.of(block), stainShape(t / (float) SPREAD), 1);
                world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.1, c.z, 1, 0.3, 0.02, 0.3, 0.01);
                world.spawnParticles(SHADOW, c.x, c.y + 0.15, c.z, 2, 0.35, 0.05, 0.35, 0);
                if (t > SPREAD - 8 && t % 2 == 0) world.spawnParticles(VIOLET, c.x, c.y + 0.3, c.z, 3, 0.25, 0.2, 0.25, 0);
                if (t == SPREAD - 6) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.5f, 0.5f);
                return true;
            }
            int u = t - SPREAD;
            if (u == 1) {
                for (int k = 0; k < HEIGHT; k++) {
                    parts[k] = blockDisplay(world, ModBlocks.VOID_SPIKE.getDefaultState().with(VoidSpikeBlock.THICKNESS, PARTS[k]), partAt(k, HEIGHT));
                    if (parts[k] != null) {
                        parts[k].setViewRange(5f);
                        parts[k].setTransformation(partShape());
                    }
                }
                world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.6f, 0.6f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.5f, 0.5f);
                world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.5, c.z, 20, 0.4, 0.6, 0.4, 0.15);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.getDefaultState()), c.x, c.y + 0.3, c.z, 20, 0.5, 0.3, 0.5, 0.2);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (flatDistance(p.getPos(), c) > 1.15 || p.getY() < top.y - 1 || p.getY() > top.y + HEIGHT) continue;
                    p.timeUntilRegen = 0;
                    boss.crush(p, damage);
                    p.setVelocity(p.getVelocity().x * 0.3, 1.05, p.getVelocity().z * 0.3);
                    p.velocityModified = true;
                }
            }
            if (u <= RISE) {
                double f = u / (double) RISE;
                for (int k = 0; k < HEIGHT; k++) if (parts[k] != null) place(parts[k], partAt(k, HEIGHT * (1 - f * (2 - f))), null, 1);
                return true;
            }
            if (u <= RISE + STAND) {
                if (u % 3 == 0) world.spawnParticles(VIOLET, c.x, c.y + 1 + world.random.nextDouble() * 2.5, c.z, 1, 0.3, 0.3, 0.3, 0);
                if (u % 4 == 0) world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 3.5, c.z, 1, 0.1, 0.2, 0.1, 0.01);
                return true;
            }
            int sink = u - RISE - STAND;
            if (sink == 1) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA, SoundCategory.HOSTILE, 1.0f, 0.5f);
            if (sink <= SINK) {
                double f = sink / (double) SINK;
                for (int k = 0; k < HEIGHT; k++) if (parts[k] != null) place(parts[k], partAt(k, HEIGHT * f * f), null, 1);
                if (stain != null) place(stain, Vec3d.of(block), stainShape(1 - (float) f), 1);
                return true;
            }
            return false;
        }

        @Override
        void remove() {
            if (stain != null) stain.discard();
            for (var p : parts) if (p != null) p.discard();
        }
    }

    // ---- The Black Hole: a great block of pure black, spinning fast, thrown slowly at you. It tugs; its heart hits hard, once ----

    static final class BlackHole extends Hazard {
        /** How long it flies before it bursts. */
        static final int LIFE = 60;
        /** How far it tugs at you, how often, and how hard right next to it (a sprinting player gets away from all but the last few blocks). */
        static final double PULL_RADIUS = 10, MAX_TUG = 0.42;
        static final int TUG_EVERY = 4;
        /** Its heart hits whoever it reaches, once; when it bursts, everyone near takes {@link #BURST}. */
        static final double CORE = 1.6, BURST_RADIUS = 4.5;
        static final float SIZE = 2f, BURST = 6;
        final Vec3d vel;
        final float damage, spin0;
        final DisplayEntity.BlockDisplayEntity block;
        final Set<UUID> hit = new HashSet<>();
        Vec3d pos;

        BlackHole(ServerWorld world, Vec3d from, Vec3d vel, float damage, float spin0) {
            this.pos = from;
            this.vel = vel;
            this.damage = damage;
            this.spin0 = spin0;
            this.block = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), from);
            if (block != null) {
                block.setBrightness(new Brightness(0, 0));
                block.setViewRange(10f);
                block.setTransformation(spinning(spin0, SIZE));
            }
            world.spawnParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 80, 0.5, 0.5, 0.5, 0.6);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }

        /** A cube {@code size} across, centred, turned {@code a} round three axes at once: tumbling fast. */
        static AffineTransformation spinning(float a, float size) {
            return new AffineTransformation(new Matrix4f().rotateXYZ(a * 0.9f, a * 1.3f, a * 0.6f).scale(size).translate(-0.5f, -0.5f, -0.5f));
        }

        Vec3d pos() {
            return pos;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (block == null) return false;
            if (age >= LIFE) {
                burst(world, boss);
                return false;
            }
            pos = pos.add(vel);
            float pulse = 1f + 0.06f * MathHelper.sin(age * 1.1f);
            place(block, pos, spinning(spin0 + age * 0.6f, SIZE * pulse), 1);
            // Purple light whirling round it, the dark sucked into it, a trail of shadow behind
            for (int ring = 0; ring < 2; ring++) {
                double r = 1.9 + ring * 0.7, spin = age * (0.5 - ring * 0.18) * (ring == 0 ? 1 : -1);
                for (int i = 0; i < 12; i++) {
                    double a = spin + i * MathHelper.TAU / 12;
                    double x = Math.cos(a) * r, z = Math.sin(a) * r, y = Math.sin(a * 2 + age * 0.2) * 0.5;
                    world.spawnParticles(ring == 0 ? VIOLET : ParticleTypes.REVERSE_PORTAL, pos.x + x, pos.y + y, pos.z + z, 1, 0, 0, 0, 0);
                }
            }
            for (int i = 0; i < 5; i++) {
                Vec3d o = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian() * 0.6, world.random.nextGaussian()).normalize().multiply(4 + world.random.nextDouble() * 4);
                Vec3d at = pos.add(o);
                world.spawnParticles(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.WITCH, at.x, at.y, at.z, 0, -o.x, -o.y, -o.z, 0.1);
            }
            Vec3d back = pos.subtract(vel.multiply(4));
            world.spawnParticles(SHADOW, back.x, back.y, back.z, 4, 0.5, 0.5, 0.5, 0);
            if (age % 4 == 0) world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 1.2, 1.2, 1.2, 0.02);
            if (age % 10 == 0) {
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.5f);
            }
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                Vec3d in = pos.subtract(c);
                double d = in.length();
                if (d > PULL_RADIUS) continue;
                // A tug every few ticks, stronger close in; between them you're free to run
                if (age % TUG_EVERY == 0 && d > CORE) {
                    double tug = Math.max(0.08, MAX_TUG * (1 - d / PULL_RADIUS));
                    Vec3d v = in.multiply(tug / d);
                    p.setVelocity(v.x, Math.max(0.04, v.y * 0.3), v.z);
                    p.velocityModified = true;
                    p.fallDistance = 0;
                }
                // Its heart: one heavy hit, then it's done with you
                if (d < CORE + 0.3 && hit.add(p.getUuid())) {
                    p.timeUntilRegen = 0;
                    boss.crush(p, damage);
                    boss.launch(p, c.subtract(pos), 0.5, 0.45);
                    boss.flash(p, 0.5f, 10);
                    world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 30, 0.3, 0.4, 0.3, 0.2);
                    world.spawnParticles(VIOLET, c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 2.0f, 0.5f);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.0f, 1.6f);
                }
            }
            return true;
        }

        /** Its time is up: it falls in on itself and bursts. */
        private void burst(ServerWorld world, MugenEntity boss) {
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 120, 0.4, 0.4, 0.4, 0.6);
            world.spawnParticles(VIOLET, pos.x, pos.y, pos.z, 100, 2.5, 2, 2.5, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 150, 0.5, 0.5, 0.5, 1.4);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.getDefaultState()), pos.x, pos.y, pos.z, 60, 0.8, 0.8, 0.8, 0.4);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.4f);
            for (ServerPlayerEntity p : boss.fighters()) {
                Vec3d c = p.getBoundingBox().getCenter();
                double d = c.distanceTo(pos);
                if (d > BURST_RADIUS) continue;
                boss.crush(p, BURST);
                boss.launch(p, c.subtract(pos), 1.0 * (1 - d / (BURST_RADIUS + 1)), 0.45);
                boss.flash(p, 0.5f, 12);
            }
            block.discard();
        }

        @Override
        void remove() {
            if (block != null) block.discard();
        }
    }

    // ---- Shadow Bind: a band of shadow sweeping across the ground; whoever's standing in it as it passes is held fast ----

    static final class Band extends Hazard {
        static final double WIDTH = 3, LENGTH = 22;
        static final int TILE_LIFE = 26, SHRINK = 8;
        final Vec3d from, dir, side;
        final double speed;
        final float yaw;
        final List<DisplayEntity.BlockDisplayEntity> tiles = new ArrayList<>();
        final List<Integer> born = new ArrayList<>();
        final Set<UUID> passed = new HashSet<>();
        double front;
        int laid;

        Band(Vec3d from, Vec3d dir, double speed) {
            this.from = from;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.speed = speed;
            this.yaw = (float) Math.atan2(this.dir.x, this.dir.z);
        }

        private AffineTransformation tile(float grow) {
            return new AffineTransformation(new Matrix4f().rotateY(yaw).scale((float) WIDTH * grow, 0.03f, 1.05f * grow).translate(-0.5f, 0f, -0.5f));
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            double prev = front;
            front = Math.min(LENGTH, front + speed);
            while (laid < front && front < LENGTH + 1) {
                Vec3d at = boss.ground(from.add(dir.multiply(laid + 0.5)).add(0, 1.5, 0)).add(0, 0.02, 0);
                var d = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), at);
                if (d != null) {
                    d.setBrightness(new Brightness(0, 0));
                    d.setTransformation(tile(1f));
                    tiles.add(d);
                    born.add(age);
                }
                laid++;
            }
            if (front < LENGTH) {
                Vec3d f = from.add(dir.multiply(front));
                for (double w = -WIDTH / 2; w <= WIDTH / 2; w += 0.5) {
                    Vec3d p = boss.ground(f.add(side.multiply(w)).add(0, 1.5, 0));
                    world.spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.15, p.z, 1, 0.1, 0.05, 0.1, 0.02);
                    world.spawnParticles(SHADOW, p.x, p.y + 0.3, p.z, 1, 0.1, 0.15, 0.1, 0);
                }
                if (age % 4 == 0) world.playSound(null, f.x, f.y, f.z, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 1.4f, 0.6f);
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (passed.contains(p.getUuid())) continue;
                    Vec3d rel = p.getPos().subtract(from);
                    double along = rel.dotProduct(dir), across = rel.dotProduct(side);
                    if (along < prev - 0.8 || along > front + 0.6 || Math.abs(across) > WIDTH / 2 + 0.3) continue;
                    if (Math.abs(p.getY() - boss.ground(p.getPos().add(0, 0.5, 0)).y) > 0.6 && !p.isOnGround()) continue; // jumped it
                    passed.add(p.getUuid());
                    boss.bind(p, 60);
                }
            }
            // The band fades from the back as it goes
            boolean any = false;
            for (int i = 0; i < tiles.size(); i++) {
                var t = tiles.get(i);
                if (t == null) continue;
                int life = age - born.get(i);
                if (life > TILE_LIFE + SHRINK) {
                    t.discard();
                    tiles.set(i, null);
                    continue;
                }
                any = true;
                if (life > TILE_LIFE) place(t, t.getPos(), tile(1f - (life - TILE_LIFE) / (float) SHRINK), 1);
            }
            return front < LENGTH || any;
        }

        @Override
        void remove() {
            for (var t : tiles) if (t != null) t.discard();
        }
    }

    /** Held fast by the shadow: pinned where you stand, dark wisps crawling up you. */
    static final class Bound extends Hazard {
        final ServerPlayerEntity player;
        final Vec3d pin;
        int ticks;

        Bound(ServerPlayerEntity player, int ticks) {
            this.player = player;
            this.pin = player.getPos();
            this.ticks = ticks;
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (!player.isAlive() || player.isRemoved() || player.getWorld() != world) return false;
            if (age == 1) {
                world.playSound(null, pin.x, pin.y, pin.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 1.2f, 0.6f);
                world.playSound(null, pin.x, pin.y, pin.z, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.4f, 0.5f);
            }
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            if (player.getPos().squaredDistanceTo(pin) > 0.0025) player.networkHandler.requestTeleport(pin.x, pin.y, pin.z, player.getYaw(), player.getPitch());
            for (int i = 0; i < 2; i++) {
                double a = age * 0.4 + i * Math.PI, y = (age % 20) / 20.0 * 1.6;
                world.spawnParticles(SHADOW, pin.x + Math.cos(a) * 0.45, pin.y + y, pin.z + Math.sin(a) * 0.45, 1, 0, 0, 0, 0);
            }
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.SQUID_INK, pin.x, pin.y + 0.2, pin.z, 1, 0.3, 0.05, 0.3, 0.01);
            return age < ticks;
        }
    }

    /** Shadow Sneak: the patch of shadow on the ground he's moving under. */
    static final class Shade extends Hazard {
        final DisplayEntity.BlockDisplayEntity display;
        Vec3d pos;
        boolean done, trembling;

        Shade(ServerWorld world, Vec3d pos) {
            this.pos = pos;
            this.display = blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), pos);
            if (display != null) {
                display.setBrightness(new Brightness(0, 0));
                display.setViewRange(5f);
                display.setTransformation(shape(0.2f));
            }
        }

        private static AffineTransformation shape(float size) {
            return new AffineTransformation(new Matrix4f().scale(size, 0.03f, size).translate(-0.5f, 0f, -0.5f));
        }

        @Override
        boolean tick(ServerWorld world, MugenEntity boss) {
            age++;
            if (display == null || done) return false;
            float size = Math.min(2.6f, 0.2f + age * 0.2f) + (trembling ? 0.25f * MathHelper.sin(age * 2.2f) + 0.3f : 0.1f * MathHelper.sin(age * 0.5f));
            place(display, pos.add(0, 0.02, 0), shape(size), 1);
            world.spawnParticles(SHADOW, pos.x, pos.y + 0.2, pos.z, trembling ? 8 : 3, size * 0.35, 0.05, size * 0.35, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 0.1, pos.z, trembling ? 4 : 1, size * 0.3, 0.02, size * 0.3, trembling ? 0.08 : 0.01);
            if (age % 6 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 1.2f, trembling ? 1.2f : 0.6f);
            return true;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }
}
