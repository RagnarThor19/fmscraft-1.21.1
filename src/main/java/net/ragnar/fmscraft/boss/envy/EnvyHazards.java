package net.ragnar.fmscraft.boss.envy;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What GUMMSS conjures: things that outlive the attack that made them and tick on their own until they're spent.
 * They only ever hurt the players fighting him, and never break a block. Several are Ragnars Magic Mod's tomes cast by
 * him (Swords, Judgement, the Moon, Reckoning, the Quiver), built to look and sound like the tomes do.
 */
final class EnvyHazards {
    static final DustParticleEffect TOXIC = new DustParticleEffect(new Vector3f(0.22f, 0.62f, 0.18f), 1.2f);
    static final DustParticleEffect DEEP_GREEN = new DustParticleEffect(new Vector3f(0.05f, 0.28f, 0.1f), 1.5f);
    static final DustParticleEffect PSYCHIC = new DustParticleEffect(new Vector3f(0.55f, 0.95f, 0.45f), 1.0f);
    static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.45f, 0.2f, 0.6f), 1.1f);
    static final DustParticleEffect SHADOW = new DustParticleEffect(new Vector3f(0.02f, 0.04f, 0.02f), 2.0f);
    /** Item sprites in ModelTransformationMode.NONE, as an item display draws them (turned 180 degrees about Y first). */
    private static final Vector3f TIP_LOCAL = new Vector3f(-1, 1, 0).normalize();
    private static final Vector3f SIDE_LOCAL = new Vector3f(1, 1, 0).normalize();
    private static final Vector3f FACE_LOCAL = new Vector3f(0, 0, -1);

    private EnvyHazards() {
    }

    abstract static class Hazard {
        int age;

        /** One tick; false once it's spent. */
        abstract boolean tick(ServerWorld world, GummssEntity boss);

        /** Taken away early (he died, the fight ended): clean up anything it put in the world. */
        void remove() {
        }
    }

    // ---- Helpers ----

    static double flatDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(MathHelper.square(a.x - b.x) + MathHelper.square(a.z - b.z));
    }

    static DisplayEntity.ItemDisplayEntity itemDisplay(ServerWorld world, ItemStack stack, Vec3d at, ModelTransformationMode mode) {
        DisplayEntity.ItemDisplayEntity d = EntityType.ITEM_DISPLAY.create(world);
        if (d == null) return null;
        d.setItemStack(stack);
        d.setTransformationMode(mode);
        d.setTeleportDuration(1);
        d.setViewRange(4f);
        d.setBrightness(new Brightness(15, 15));
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

    /** An item sprite (sword, arrow) pointing along {@code dir}, {@code scale} times its size. */
    static AffineTransformation pointing(Vec3d dir, float scale) {
        Vector3f d = dir.lengthSquared() > 1e-6 ? dir.normalize().toVector3f() : new Vector3f(0, 0, 1);
        return new AffineTransformation(new Vector3f(), new Quaternionf().rotationTo(TIP_LOCAL, d), new Vector3f(scale, scale, scale), new Quaternionf());
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

    /** Holds {@code p} near {@code at}, shaking by {@code shake}: they hang there, carried by his mind. */
    static void steer(ServerPlayerEntity p, Vec3d at, double shake) {
        var r = p.getRandom();
        double jx = shake > 0 ? (r.nextDouble() - 0.5) * shake : 0, jz = shake > 0 ? (r.nextDouble() - 0.5) * shake : 0;
        Vec3d to = at.subtract(p.getPos());
        p.setVelocity(to.x * 0.35 + jx, to.y * 0.45 + jx * 0.5, to.z * 0.35 + jz);
        p.velocityModified = true;
        p.fallDistance = 0;
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
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (age % 2 == 0) ring(world, particle, center, radius, (int) Math.min(72, 12 + radius * 4), age * 0.03);
            return age < ticks;
        }
    }

    // ---- A ring of force rushing out along the floor. With damage: only those standing on the ground as it passes ----

    static final class Shockwave extends Hazard {
        final Vec3d center;
        final double speed, max;
        final float damage;
        final double push;
        final Set<UUID> settled = new HashSet<>();
        double radius;

        Shockwave(Vec3d center, double speed, double max, float damage, double push) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.damage = damage;
            this.push = push;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            radius += speed;
            if (radius > max) return false;
            int points = (int) Math.min(90, 10 + radius * 5);
            ring(world, PSYCHIC, center, radius, points, radius * 0.1);
            if (age++ % 2 == 0) ring(world, ParticleTypes.ENCHANTED_HIT, center, radius - 0.3, points / 2, 0);
            if (damage <= 0) return true;
            for (ServerPlayerEntity p : boss.fighters()) {
                if (settled.contains(p.getUuid())) continue;
                double d = flatDistance(p.getPos(), center);
                if (d > radius || Math.abs(p.getY() - center.y) > 3) continue;
                settled.add(p.getUuid());
                if (d < radius - speed - 1.2 || !p.isOnGround()) continue; // jumped it, or was already past
                boss.crush(p, damage);
                boss.launch(p, p.getPos().subtract(center), push, 0.45);
            }
            return true;
        }
    }

    // ---- Psychic Slash: a crescent of mind cut loose from his staff. Waist high, so jumping won't clear it ----

    static final class Crescent extends Hazard {
        static final double HEIGHT = 2.0, BOW = 0.09;
        final Vec3d dir, side;
        final double speed, range, halfWidth;
        final float damage;
        final boolean poison;
        final Set<UUID> hit = new HashSet<>();
        Vec3d pos;
        double travelled;

        Crescent(Vec3d from, Vec3d dir, double speed, double range, double halfWidth, float damage, boolean poison) {
            this.pos = from;
            this.dir = dir.multiply(1, 0, 1).normalize();
            this.side = new Vec3d(-this.dir.z, 0, this.dir.x);
            this.speed = speed;
            this.range = range;
            this.halfWidth = halfWidth;
            this.damage = damage;
            this.poison = poison;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            pos = pos.add(dir.multiply(speed));
            travelled += speed;
            BlockPos ahead = BlockPos.ofFloored(pos.add(dir.multiply(0.6)).add(0, 0.8, 0));
            if (travelled > range || !world.getBlockState(ahead).getCollisionShape(world, ahead).isEmpty()) {
                world.spawnParticles(poison ? TOXIC : PSYCHIC, pos.x, pos.y + 1, pos.z, 30, halfWidth * 0.4, 0.5, halfWidth * 0.4, 0);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 1, pos.z, 12, halfWidth * 0.3, 0.4, halfWidth * 0.3, 0.2);
                return false;
            }
            for (double s = -halfWidth; s <= halfWidth + 1e-6; s += 0.45) {
                Vec3d p = pos.add(side.multiply(s)).subtract(dir.multiply(s * s * BOW));
                double edge = 1 - Math.abs(s) / halfWidth;
                world.spawnParticles(poison ? TOXIC : PSYCHIC, p.x, p.y + 0.4 + HEIGHT * 0.5, p.z, 2, 0.05, HEIGHT * 0.25 * (0.4 + edge), 0.05, 0);
                if (world.random.nextInt(3) == 0) world.spawnParticles(poison ? ParticleTypes.ITEM_SLIME : VIOLET, p.x, p.y + 1.1, p.z, 1, 0.05, 0.3, 0.05, 0);
            }
            if (age++ % 3 == 0) world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.1, pos.z, 1, 0, 0, 0, 0);
            if (age % 5 == 0) world.playSound(null, pos.x, pos.y + 1, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 1.6f);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (hit.contains(p.getUuid())) continue;
                Vec3d rel = p.getPos().subtract(pos);
                double across = rel.dotProduct(side), along = rel.dotProduct(dir) + across * across * BOW;
                if (Math.abs(across) > halfWidth + 0.4 || Math.abs(along) > 0.9 || rel.y < -1 || rel.y > HEIGHT + 0.4) continue;
                hit.add(p.getUuid());
                if (boss.strike(p, damage)) {
                    if (poison) boss.poison(p, 80, 1);
                    else boss.daze(p);
                }
                boss.launch(p, dir, 0.7, 0.25);
            }
            return true;
        }
    }

    // ---- Swords: five iron swords fanned out behind him, thrown one by one. Straight where you were: no aiming after ----

    static final class SwordFan extends Hazard {
        static final int COUNT = 5, TIMEOUT = 200;
        final DisplayEntity.ItemDisplayEntity[] slots = new DisplayEntity.ItemDisplayEntity[COUNT];
        Vec3d aim;

        SwordFan(ServerWorld world, GummssEntity boss) {
            for (int i = 0; i < COUNT; i++) {
                Vec3d p = slot(boss, i, 0);
                slots[i] = itemDisplay(world, new ItemStack(Items.IRON_SWORD), p, ModelTransformationMode.NONE);
                if (slots[i] != null) slots[i].setTransformation(pointing(boss.getRotationVector(), 0.001f));
                world.spawnParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 14, 0.2, 0.3, 0.2, 0.6);
                world.spawnParticles(PSYCHIC, p.x, p.y, p.z, 4, 0.1, 0.2, 0.1, 0);
            }
        }

        /** A fan of five arched behind his shoulders, gently bobbing. */
        static Vec3d slot(GummssEntity boss, int i, int age) {
            Vec3d forward = Vec3d.fromPolar(0, boss.getBodyYaw());
            Vec3d right = new Vec3d(-forward.z, 0, forward.x);
            double theta = Math.toRadians(-64 + i * 32.0);
            double bob = Math.sin(age * 0.12 + i * 1.3) * 0.08;
            return boss.getPos().add(0, 2.0 + Math.cos(theta) * 0.6 + bob, 0).add(right.multiply(Math.sin(theta) * 1.5)).add(forward.multiply(-0.6));
        }

        int remaining() {
            int n = 0;
            for (var s : slots) if (s != null) n++;
            return n;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            Vec3d look = aim != null ? aim : boss.getEyePos().add(boss.getRotationVector().multiply(20));
            float grow = Math.min(1f, age / 8f);
            for (int i = 0; i < COUNT; i++) {
                if (slots[i] == null) continue;
                Vec3d p = slot(boss, i, age);
                place(slots[i], p, pointing(look.subtract(p), 1.3f * grow), 1);
                if (world.random.nextInt(6) == 0) world.spawnParticles(PSYCHIC, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0);
            }
            return remaining() > 0 && age < TIMEOUT;
        }

        /** Throws the sword nearest {@code target} at it, in a straight line. Null if none are left. */
        FlyingSword fire(ServerWorld world, Vec3d target, float damage) {
            int pick = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < COUNT; i++) {
                if (slots[i] == null) continue;
                double d = slots[i].getPos().squaredDistanceTo(target);
                if (d < best) {
                    best = d;
                    pick = i;
                }
            }
            if (pick < 0) return null;
            DisplayEntity.ItemDisplayEntity d = slots[pick];
            slots[pick] = null;
            Vec3d pos = d.getPos();
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 1.4f, 0.8f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 0.6f);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            return new FlyingSword(d, pos, target.subtract(pos).normalize().multiply(FlyingSword.SPEED), damage);
        }

        @Override
        void remove() {
            for (var s : slots) if (s != null) s.discard();
        }
    }

    static final class FlyingSword extends Hazard {
        static final double SPEED = 1.7;
        static final int FLIGHT = 40, STUCK = 20;
        final DisplayEntity.ItemDisplayEntity display;
        final float damage;
        Vec3d pos, vel;
        int stuck = -1;

        FlyingSword(DisplayEntity.ItemDisplayEntity display, Vec3d pos, Vec3d vel, float damage) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (stuck >= 0) {
                if (++stuck >= STUCK) {
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.2);
                    world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 0.6f, 1.4f);
                    return false;
                }
                return true;
            }
            if (age > FLIGHT) return false;
            Vec3d from = pos, to = pos.add(vel);
            ServerPlayerEntity hitPlayer = null;
            double hitDist = Double.MAX_VALUE;
            for (ServerPlayerEntity p : boss.fighters()) {
                var clip = p.getBoundingBox().expand(0.3).raycast(from, to);
                if (clip.isEmpty()) continue;
                double d = from.squaredDistanceTo(clip.get());
                if (d < hitDist) {
                    hitDist = d;
                    hitPlayer = p;
                }
            }
            BlockHitResult wall = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
            if (wall.getType() == HitResult.Type.BLOCK && (hitPlayer == null || from.squaredDistanceTo(wall.getPos()) < hitDist)) {
                pos = wall.getPos().subtract(vel.normalize().multiply(0.35));
                place(display, pos, pointing(vel, 1.3f), 1);
                stuck = 0;
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.2f, 0.8f);
                world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.2);
                return true;
            }
            if (hitPlayer != null) {
                boss.strike(hitPlayer, damage, display);
                hitPlayer.addVelocity(vel.normalize().x * 0.6, 0.2, vel.normalize().z * 0.6);
                hitPlayer.velocityModified = true;
                Vec3d c = hitPlayer.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.4);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 1.5f, 0.7f);
                return false;
            }
            pos = to;
            place(display, pos, pointing(vel, 1.3f), 1);
            for (int i = 0; i < 3; i++) {
                Vec3d p = from.lerp(to, i / 3.0);
                world.spawnParticles(i == 0 ? PSYCHIC : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
            return true;
        }

        @Override
        void remove() {
            display.discard();
        }
    }

    // ---- Tome of the Quiver: seven spectral arrows circling over his head, loosed one by one ----

    static final class QuiverRing extends Hazard {
        static final int COUNT = 7, TIMEOUT = 200;
        final DisplayEntity.ItemDisplayEntity[] slots = new DisplayEntity.ItemDisplayEntity[COUNT];
        Vec3d aim;

        QuiverRing(ServerWorld world, GummssEntity boss) {
            for (int i = 0; i < COUNT; i++) {
                Vec3d p = slot(boss, i, 0);
                slots[i] = itemDisplay(world, new ItemStack(Items.SPECTRAL_ARROW), p, ModelTransformationMode.NONE);
                world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            }
        }

        static Vec3d slot(GummssEntity boss, int i, int age) {
            double a = i * MathHelper.TAU / COUNT + age * 0.08;
            return boss.getPos().add(Math.cos(a) * 1.3, 2.6 + Math.sin(age * 0.1 + i) * 0.06, Math.sin(a) * 1.3);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            Vec3d look = aim != null ? aim : boss.getEyePos().add(boss.getRotationVector().multiply(20));
            boolean any = false;
            for (int i = 0; i < COUNT; i++) {
                if (slots[i] == null) continue;
                any = true;
                Vec3d p = slot(boss, i, age);
                place(slots[i], p, pointing(look.subtract(p), 0.85f * Math.min(1f, age / 6f)), 1);
            }
            return any && age < TIMEOUT;
        }

        /** Takes one arrow off the ring and says where it was: the boss looses a real one from there. */
        Vec3d take() {
            for (int i = 0; i < COUNT; i++) {
                if (slots[i] == null) continue;
                Vec3d p = slots[i].getPos();
                slots[i].discard();
                slots[i] = null;
                return p;
            }
            return null;
        }

        @Override
        void remove() {
            for (var s : slots) if (s != null) s.discard();
        }
    }

    // ---- Giant swords out of the dark: a rain of iron ones (his own way), or the full Tome of Judgement ----

    static final class GiantSword extends Hazard {
        /**
         * How one drops: its sword and size, how far up it forms and how deep it buries, the blast, and how long the
         * beam marks the spot before it falls (it forms in the last {@code form} ticks of that).
         */
        record Spec(net.minecraft.item.Item item, float scale, double drop, double bury, double radius, float centerDamage,
                    float edgeDamage, double launch, int mark, int form, int fall, int stand, int glow) {
        }

        /** Sword Rain: iron swords, one on each of you. */
        static final Spec RAIN = new Spec(Items.IRON_SWORD, 10f, 34, 2.0, 4.5, 22f, 10f, 1.1, 26, 10, 7, 30, 0x9CFF8A);
        /** The Tome of Judgement as the magic mod casts it: a colossal netherite sword, the gold beam, a huge blast. */
        static final Spec JUDGEMENT = new Spec(Items.NETHERITE_SWORD, 16f, 48, 3.0, 7.0, 60f, 20f, 1.7, 42, 12, 7, 40, 0xFFD27F);
        static final int CRUMBLE = 12;
        static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.35f), 1.6f);

        final Spec spec;
        final Vec3d target;
        final Vector3f face;
        final int delay;
        final double tipOffset;
        DisplayEntity.ItemDisplayEntity sword;

        GiantSword(Vec3d target, Vec3d from, int delay) {
            this(target, from, delay, RAIN);
        }

        GiantSword(Vec3d target, Vec3d from, int delay, Spec spec) {
            this.spec = spec;
            this.target = target;
            Vec3d to = from.subtract(target);
            this.face = to.horizontalLengthSquared() > 1e-4 ? new Vector3f((float) to.x, 0, (float) to.z).normalize() : new Vector3f(0, 0, 1);
            this.delay = delay;
            this.tipOffset = spec.scale() * 0.62;
        }

        Vec3d center(double height) {
            return target.add(0, tipOffset + height, 0);
        }

        boolean judgement() {
            return spec == JUDGEMENT;
        }

        /** Ticks from when it starts until it hits. */
        int impactIn() {
            return delay + spec.mark() + spec.fall();
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            int t = age - delay;
            if (t <= 0) return true;
            int mark = spec.mark(), impactAt = mark + spec.fall();
            if (t < impactAt && t % 2 == 0) {
                // A thin beam of light where it'll land, and a ring the size of the blast
                for (double y = 0.2; y < spec.drop(); y += 1.5) world.spawnParticles(ParticleTypes.END_ROD, target.x, target.y + y, target.z, 1, 0.02, 0.2, 0.02, 0);
                ring(world, judgement() ? GOLD : PSYCHIC, target, spec.radius(), judgement() ? 56 : 28, t * 0.1);
            }
            if (t == mark - spec.form()) {
                sword = itemDisplay(world, new ItemStack(spec.item()), center(spec.drop()), ModelTransformationMode.NONE);
                if (sword != null) {
                    sword.setViewRange(judgement() ? 12f : 8f);
                    sword.setGlowing(true);
                    sword.setGlowColorOverride(spec.glow());
                    sword.setTransformation(pointingDown(face, 0.01f));
                }
                world.playSound(null, target.x, target.y + spec.drop(), target.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, judgement() ? 6.0f : 4.0f, 0.5f);
            }
            if (sword == null) return t < mark;
            if (t <= mark) {
                float grow = Math.min(1f, (t - (mark - spec.form())) / (float) spec.form());
                place(sword, center(spec.drop() + Math.sin(t * 2.5) * 0.08), pointingDown(face, spec.scale() * (1 - (1 - grow) * (1 - grow))), 1);
                if (judgement()) {
                    Vec3d c = center(spec.drop());
                    world.spawnParticles(GOLD, c.x, c.y, c.z, 8, 1.5, tipOffset * 0.6, 1.5, 0);
                }
                if (t == mark) {
                    world.playSound(null, target.x, target.y + spec.drop() / 2, target.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 5.0f, 0.5f);
                    if (judgement()) world.playSound(null, target.x, target.y, target.z, SoundEvents.ITEM_MACE_SMASH_AIR, SoundCategory.HOSTILE, 4.0f, 0.5f);
                }
                return true;
            }
            if (t < impactAt) {
                double f = (t - mark) / (double) spec.fall();
                place(sword, center(MathHelper.lerp(f * f, spec.drop(), -spec.bury())), null, 1);
                world.spawnParticles(ParticleTypes.END_ROD, target.x, target.y + spec.drop() * (1 - f * f) + 2, target.z, 8, 0.4, 2.5, 0.4, 0.02);
                if (judgement()) world.spawnParticles(ParticleTypes.CLOUD, target.x, target.y + spec.drop() * (1 - f * f) + 4, target.z, 6, 0.8, 4.0, 0.8, 0.01);
                return true;
            }
            if (t == impactAt) {
                place(sword, center(-spec.bury()), null, 1);
                sword.setGlowing(false);
                impact(world, boss);
                return true;
            }
            int standing = t - impactAt;
            if (standing < spec.stand()) {
                if (standing % 4 == 0) world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.x, target.y + 0.5, target.z, 1, 0.6, 0.1, 0.6, 0.01);
                return true;
            }
            int crumble = standing - spec.stand();
            if (crumble == 0) {
                place(sword, center(-spec.bury() - tipOffset * 0.5), pointingDown(face, 0.01f), CRUMBLE);
                world.playSound(null, target.x, target.y, target.z, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                if (judgement()) world.playSound(null, target.x, target.y, target.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            }
            if (crumble < CRUMBLE) {
                world.spawnParticles(judgement() ? GOLD : PSYCHIC, target.x, target.y + 2, target.z, 6, 0.6, 2, 0.6, 0);
                return true;
            }
            return false;
        }

        private void impact(ServerWorld world, GummssEntity boss) {
            Vec3d t = target;
            double radius = spec.radius();
            float loud = judgement() ? 5.0f : 3.0f;
            world.playSound(null, t.x, t.y, t.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, loud + 1, 0.5f);
            world.playSound(null, t.x, t.y, t.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, loud, 0.55f);
            world.playSound(null, t.x, t.y, t.z, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, loud, 0.6f);
            if (judgement()) world.playSound(null, t.x, t.y, t.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.spawnParticles(ParticleTypes.FLASH, t.x, t.y + 1, t.z, judgement() ? 2 : 1, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 1, 0, 0, 0, 0);
            if (judgement()) world.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, t.x, t.y + 0.5, t.z, 1, 0, 0, 0, 0);
            BlockState ground = world.getBlockState(BlockPos.ofFloored(t.x, t.y - 0.5, t.z));
            BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DEEPSLATE.getDefaultState() : ground);
            int spokes = judgement() ? 64 : 32;
            for (int i = 0; i < spokes; i++) {
                double a = i * MathHelper.TAU / spokes;
                for (double r = 1.5; r <= radius; r += judgement() ? 1.5 : radius) {
                    world.spawnParticles(debris, t.x + Math.cos(a) * r, t.y + 0.2, t.z + Math.sin(a) * r, 2, 0.3, 0.3, 0.3, 0.3);
                }
                world.spawnParticles(judgement() ? GOLD : PSYCHIC, t.x + Math.cos(a) * radius, t.y + 0.3, t.z + Math.sin(a) * radius, 1, 0.1, 0.2, 0.1, 0);
                if (judgement()) world.spawnParticles(ParticleTypes.CLOUD, t.x + Math.cos(a) * 2, t.y + 0.3, t.z + Math.sin(a) * 2, 0, Math.cos(a), 0.05, Math.sin(a), 0.6);
            }
            if (judgement()) world.spawnParticles(ParticleTypes.END_ROD, t.x, t.y + 2, t.z, 40, 1.0, 2.0, 1.0, 0.4);
            for (ServerPlayerEntity p : boss.fighters()) {
                double d = flatDistance(p.getPos(), t);
                if (d > radius + 0.3 || Math.abs(p.getY() - t.y) > (judgement() ? 7 : 3)) continue;
                float falloff = (float) MathHelper.clamp(d / radius, 0, 1);
                p.timeUntilRegen = 0;
                boss.crush(p, MathHelper.lerp(falloff, spec.centerDamage(), spec.edgeDamage()));
                boss.launch(p, p.getPos().subtract(t), judgement() ? 0.7 : 0.6, spec.launch() * (1.1 - falloff * 0.5));
                boss.flash(p, judgement() ? 0.6f : 0.35f, judgement() ? 16 : 10);
            }
            for (ServerPlayerEntity p : boss.audience(world)) {
                double far = judgement() ? 60 : 24;
                if (p.squaredDistanceTo(t) < far * far) {
                    p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.4f, 0.5f);
                    if (judgement()) boss.flash(p, 0.25f, 12);
                }
            }
        }

        @Override
        void remove() {
            if (sword != null) sword.discard();
        }
    }

    // ---- Tome of Upside Down: everyone's gravity flips for a second (they fall up into the dark), then back ----

    static final class Flip extends Hazard {
        final java.util.List<UUID> players;
        final int ticks;
        /** Those the magic mod's tome flipped, to flip back however this ends. */
        final java.util.List<ServerPlayerEntity> byTome = new java.util.ArrayList<>();
        final Set<UUID> thrown = new HashSet<>();

        Flip(java.util.List<UUID> players, int ticks) {
            this.players = players;
            this.ticks = ticks;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            for (UUID id : players) {
                ServerPlayerEntity p = boss.fighter(id);
                if (p == null) continue;
                if (age == 1) {
                    // The magic mod's own flip (their view rolls over, everyone sees them hanging); without it he throws them up himself
                    if (net.ragnar.fmscraft.compat.MagicSpells.setFlipped(p, true)) byTome.add(p);
                    else thrown.add(id);
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
                    world.spawnParticles(PSYCHIC, p.getX(), p.getY() + 0.1, p.getZ(), 20, 0.4, 0.1, 0.4, 0);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 1.0f, 0.6f);
                }
                if (thrown.contains(id) && age <= ticks) {
                    // Falling up: faster and faster
                    Vec3d v = p.getVelocity();
                    p.setVelocity(v.x, Math.min(1.6, Math.max(0.1, v.y) + 0.08), v.z);
                    p.velocityModified = true;
                }
                if (age % 3 == 0) world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY(), p.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                if (age == ticks) {
                    p.playSoundToPlayer(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.0f, 0.5f);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.0f, 0.6f);
                }
            }
            return age < ticks;
        }

        /** Done, or cut short (he died, the fight ended): everyone the right way up again, nobody left falling into the sky. */
        @Override
        void remove() {
            for (ServerPlayerEntity p : byTome) net.ragnar.fmscraft.compat.MagicSpells.setFlipped(p, false);
            byTome.clear();
        }
    }

    // ---- Tome of the Moon, his way: a pale moon sinks over you; it lifts whoever is under it and slams them down ----

    static final class Moon extends Hazard {
        static final int FORM = 20, SINK = 100, PULL_AT = 46;
        static final double START = 34, RADIUS = 8, SIZE = 6;
        final Vec3d spot;
        final Map<UUID, Double> lifted = new LinkedHashMap<>();
        DisplayEntity.BlockDisplayEntity moon;
        double spin;

        Moon(Vec3d spot) {
            this.spot = spot;
        }

        double height(int t) {
            double u = Math.min(1, t / (double) SINK);
            return MathHelper.lerp(Math.pow(u, 2.4), START, SIZE * 0.5);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (age == 1) {
                moon = EntityType.BLOCK_DISPLAY.create(world);
                if (moon != null) {
                    moon.setBlockState(Blocks.END_STONE.getDefaultState());
                    moon.setTeleportDuration(1);
                    moon.setViewRange(8f);
                    moon.setBrightness(new Brightness(15, 15));
                    moon.setGlowing(true);
                    moon.setGlowColorOverride(0xD8F5C8);
                    moon.refreshPositionAndAngles(spot.x, spot.y + START, spot.z, 0, 0);
                    world.spawnEntity(moon);
                }
                world.playSound(null, spot.x, spot.y + START, spot.z, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 4.0f, 0.5f);
                world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0f, 0.4f);
            }
            int t = age;
            double h = height(t);
            float size = (float) (SIZE * Math.min(1, t / (double) FORM));
            spin += 0.02 + t * 0.0004;
            if (moon != null) {
                Quaternionf rot = new Quaternionf().rotationYXZ((float) spin, (float) (spin * 0.6), 0.2f);
                Vector3f half = new Vector3f(-0.5f, -0.5f, -0.5f).mul(size).rotate(rot);
                place(moon, spot.add(0, h, 0), new AffineTransformation(half, rot, new Vector3f(size, size, size), new Quaternionf()), 1);
            }
            // Its shadow on the ground, spreading as it comes down; the edge marks how far its pull reaches
            double shadow = RADIUS * (0.35 + 0.65 * Math.min(1, t / (double) SINK));
            if (t % 2 == 0) {
                ring(world, t < PULL_AT ? PSYCHIC : SHADOW, spot, RADIUS, 48, t * 0.02);
                for (int i = 0; i < 10; i++) {
                    double a = world.random.nextDouble() * MathHelper.TAU, r = Math.sqrt(world.random.nextDouble()) * shadow;
                    world.spawnParticles(SHADOW, spot.x + Math.cos(a) * r, spot.y + 0.1, spot.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                world.spawnParticles(ParticleTypes.END_ROD, spot.x, spot.y + h, spot.z, 2, size * 0.4, size * 0.4, size * 0.4, 0.01);
            }
            if (t % 20 == 0 && t < SINK) world.playSound(null, spot.x, spot.y + h, spot.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            // Its pull: whoever is still under it is lifted off their feet and swept round beneath it
            if (t >= PULL_AT && t < SINK) {
                if (t == PULL_AT) world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.6f);
                for (ServerPlayerEntity p : boss.fighters()) {
                    double d = flatDistance(p.getPos(), spot);
                    if (!lifted.containsKey(p.getUuid()) && (d > RADIUS || p.getY() > spot.y + h)) continue;
                    double angle = lifted.computeIfAbsent(p.getUuid(), id -> Math.atan2(p.getZ() - spot.z, p.getX() - spot.x));
                    angle += 0.05;
                    lifted.put(p.getUuid(), angle);
                    double r = Math.max(2.2, Math.min(d, RADIUS) * 0.97);
                    steer(p, spot.add(Math.cos(angle) * r, Math.min(3.0, h - SIZE * 0.5 - 1.5), Math.sin(angle) * r), 0.05);
                    if (t % 4 == 0) world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.5, 0.3, 0.01);
                }
            }
            if (t == SINK) {
                impact(world, boss);
                if (moon != null) moon.discard();
                moon = null;
                boss.addHazard(new Shockwave(spot, 0.9, 15, 8, 1.1));
                return false;
            }
            return true;
        }

        private void impact(ServerWorld world, GummssEntity boss) {
            Vec3d c = spot;
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 5.0f, 0.4f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 2, c.z, 2, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 2, 1, 0.3, 1, 0);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.END_STONE.getDefaultState()), c.x, c.y + 2, c.z, 160, 2.5, 1.5, 2.5, 0.3);
            world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + 2, c.z, 80, 1.5, 1.5, 1.5, 0.35);
            for (ServerPlayerEntity p : boss.fighters()) {
                if (!lifted.containsKey(p.getUuid())) continue;
                p.timeUntilRegen = 0;
                boss.crush(p, 18);
                p.setVelocity(0, -1.6, 0);
                p.velocityModified = true;
                p.fallDistance = 0;
                boss.flash(p, 0.5f, 14);
            }
            for (ServerPlayerEntity p : boss.audience(world)) {
                if (p.squaredDistanceTo(c) < 40 * 40) boss.flash(p, 0.25f, 10);
            }
        }

        @Override
        void remove() {
            if (moon != null) moon.discard();
        }
    }

    // ---- Tome of Reckoning: a silent ripple; whoever is on the ground as it passes is lifted, shaken and slammed ----

    static final class Reckoning extends Hazard {
        static final double RADIUS = 22, SPEED = 0.5, BAND = 0.9;
        static final int RISE = 14, SHAKE = 14, FALL_LIMIT = 16;
        static final double RISE_HEIGHT = 4.5;
        static final float MAX_DAMAGE = 24f, MIN_DAMAGE = 14f;

        enum Stage { WAVE, RISE, SHAKE, FALL }

        final Vec3d center;
        final Set<UUID> settled = new HashSet<>();
        final Map<UUID, Float> caught = new LinkedHashMap<>();
        final Map<UUID, Double> baseY = new LinkedHashMap<>();
        final Set<UUID> landed = new HashSet<>();
        Stage stage = Stage.WAVE;
        int stageAge;

        Reckoning(Vec3d center) {
            this.center = center;
        }

        static float damageAt(double distance) {
            return MathHelper.lerp((float) MathHelper.clamp(distance / RADIUS, 0, 1), MAX_DAMAGE, MIN_DAMAGE);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            stageAge++;
            switch (stage) {
                case WAVE -> {
                    double r = age * SPEED;
                    ring(world, ParticleTypes.SCULK_SOUL, center, r, (int) Math.min(100, 8 + r * 4), 0);
                    ring(world, DEEP_GREEN, center, Math.max(0, r - 0.5), (int) Math.min(80, 6 + r * 3), 0.05);
                    if (age % 5 == 0) world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 1.5f, 0.6f);
                    for (ServerPlayerEntity p : boss.fighters()) {
                        if (settled.contains(p.getUuid())) continue;
                        double d = flatDistance(p.getPos(), center);
                        if (d - 0.3 > r || Math.abs(p.getY() - center.y) > 4) continue;
                        settled.add(p.getUuid());
                        if (d + 0.3 < r - BAND - SPEED || !p.isOnGround()) continue;
                        caught.put(p.getUuid(), damageAt(d));
                        world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.01);
                        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_SCULK_CHARGE, SoundCategory.HOSTILE, 1.0f, 0.6f);
                    }
                    if (r >= RADIUS) {
                        if (caught.isEmpty()) return false;
                        next(Stage.RISE);
                        for (ServerPlayerEntity p : victims(boss)) baseY.put(p.getUuid(), p.getY());
                        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 2.0f, 0.6f);
                    }
                }
                case RISE -> {
                    double t = Math.min(1.0, stageAge / (double) RISE), eased = 1 - (1 - t) * (1 - t);
                    for (ServerPlayerEntity p : victims(boss)) steer(p, new Vec3d(p.getX(), base(p) + RISE_HEIGHT * eased, p.getZ()), 0);
                    if (stageAge >= RISE) {
                        next(Stage.SHAKE);
                        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, SoundCategory.HOSTILE, 2.0f, 0.5f);
                    }
                }
                case SHAKE -> {
                    double k = 0.08 + 0.22 * stageAge / SHAKE;
                    for (ServerPlayerEntity p : victims(boss)) {
                        steer(p, new Vec3d(p.getX(), base(p) + RISE_HEIGHT, p.getZ()), k);
                        if (stageAge % 3 == 0) world.spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getBodyY(0.5), p.getZ(), 1, 0.3, 0.3, 0.3, 0.01);
                    }
                    if (stageAge % 5 == 0) world.playSound(null, center.x, center.y, center.z, SoundEvents.EVENT_MOB_EFFECT_BAD_OMEN, SoundCategory.HOSTILE, 1.0f, 0.5f + stageAge * 0.03f);
                    if (stageAge >= SHAKE) {
                        next(Stage.FALL);
                        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.6f);
                    }
                }
                case FALL -> {
                    boolean left = false;
                    for (ServerPlayerEntity p : victims(boss)) {
                        if (landed.contains(p.getUuid())) continue;
                        p.fallDistance = 0;
                        if (p.isOnGround() && stageAge > 1 || stageAge >= FALL_LIMIT) {
                            landed.add(p.getUuid());
                            p.timeUntilRegen = 0;
                            boss.crush(p, caught.get(p.getUuid()));
                            BlockState ground = world.getBlockState(BlockPos.ofFloored(p.getX(), p.getY() - 0.2, p.getZ()));
                            if (!ground.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.8, 0.1, 0.8, 0.25);
                            world.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.3, p.getZ(), 1, 0, 0, 0, 0);
                            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_HEAVY_CORE_PLACE, SoundCategory.HOSTILE, 1.6f, 0.5f);
                            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.HOSTILE, 1.0f, 0.6f);
                        } else {
                            p.setVelocity(0, -3.2, 0);
                            p.velocityModified = true;
                            left = true;
                        }
                    }
                    if (!left) return false;
                }
            }
            return true;
        }

        private double base(ServerPlayerEntity p) {
            return baseY.getOrDefault(p.getUuid(), p.getY());
        }

        private java.util.List<ServerPlayerEntity> victims(GummssEntity boss) {
            java.util.List<ServerPlayerEntity> out = new java.util.ArrayList<>();
            for (ServerPlayerEntity p : boss.fighters()) if (caught.containsKey(p.getUuid())) out.add(p);
            return out;
        }

        private void next(Stage s) {
            stage = s;
            stageAge = 0;
        }
    }

    // ---- Telekinetic Slam: one of you, lifted high and dashed against the floor ----

    static final class Lift extends Hazard {
        static final int RISE = 12, HOLD = 16, FALL_LIMIT = 14;
        static final double HEIGHT = 6;
        final UUID victim;
        final float damage;
        double baseY = Double.NaN;
        int fallAge = -1;
        boolean dropped, slammed;

        Lift(UUID victim, float damage) {
            this.victim = victim;
            this.damage = damage;
        }

        /** His hold breaks (enough damage to him): they drop, unhurt. */
        void drop() {
            dropped = true;
        }

        boolean holding() {
            return !dropped && fallAge < 0;
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            ServerPlayerEntity p = boss.fighter(victim);
            if (p == null) return false;
            if (Double.isNaN(baseY)) {
                baseY = p.getY();
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            if (dropped) {
                p.fallDistance = 0;
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.5f, 1.2f);
                // A soft landing: the slowness of being let go
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING, 30, 0, false, false));
                return false;
            }
            if (age <= RISE + HOLD) {
                double t = Math.min(1, age / (double) RISE), eased = 1 - (1 - t) * (1 - t);
                steer(p, new Vec3d(p.getX(), baseY + HEIGHT * eased, p.getZ()), age > RISE ? 0.05 + 0.2 * (age - RISE) / HOLD : 0);
                world.spawnParticles(PSYCHIC, p.getX(), p.getY() + 1, p.getZ(), 3, 0.35, 0.6, 0.35, 0);
                if (age % 3 == 0) world.spawnParticles(VIOLET, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.7, 0.4, 0);
                // The thread of his mind holding them
                Vec3d hand = boss.handPos(false);
                Vec3d to = p.getBoundingBox().getCenter();
                if (age % 2 == 0) for (double s = 0; s < 1; s += 0.08) {
                    Vec3d q = hand.lerp(to, s);
                    world.spawnParticles(PSYCHIC, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                }
                if (age == RISE + HOLD) {
                    fallAge = 0;
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_MACE_SMASH_AIR, SoundCategory.HOSTILE, 2.0f, 0.6f);
                }
                return true;
            }
            fallAge++;
            p.fallDistance = 0;
            if (p.isOnGround() && fallAge > 1 || fallAge >= FALL_LIMIT) {
                slammed = true;
                p.timeUntilRegen = 0;
                boss.crush(p, damage);
                BlockState ground = world.getBlockState(BlockPos.ofFloored(p.getX(), p.getY() - 0.2, p.getZ()));
                if (!ground.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1, p.getZ(), 50, 0.9, 0.1, 0.9, 0.25);
                world.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.4, 0, 0.4, 0);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.0f, 0.7f);
                boss.flash(p, 0.3f, 8);
                // The ground shakes round where they hit
                for (ServerPlayerEntity o : boss.fighters()) {
                    if (o == p || o.squaredDistanceTo(p) > 3.5 * 3.5 || !o.isOnGround()) continue;
                    boss.crush(o, damage * 0.4f);
                    boss.launch(o, o.getPos().subtract(p.getPos()), 0.8, 0.4);
                }
                return false;
            }
            p.setVelocity(0, -3.0, 0);
            p.velocityModified = true;
            return true;
        }
    }

    // ---- An arrow he caught out of the air: it circles him once, then goes back to whoever shot it ----

    static final class CaughtArrow extends Hazard {
        static final int GRAB = 8, ORBIT = 18;
        final DisplayEntity.ItemDisplayEntity display;
        final Vec3d start;
        final UUID shooter;
        final double startAngle;
        Vec3d pos;

        CaughtArrow(ServerWorld world, GummssEntity boss, Vec3d at, UUID shooter, ItemStack stack) {
            this.start = at;
            this.pos = at;
            this.shooter = shooter;
            this.display = itemDisplay(world, stack, at, ModelTransformationMode.NONE);
            this.startAngle = Math.atan2(at.z - boss.getZ(), at.x - boss.getX());
        }

        Vec3d orbit(GummssEntity boss, double angle) {
            return boss.getPos().add(Math.cos(angle) * 1.7, 1.4 + Math.sin(angle * 2) * 0.2, Math.sin(angle) * 1.7);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (display == null) return false;
            Vec3d prev = pos;
            if (age <= GRAB) {
                // Stopped dead in the air, then drawn in to him
                double t = age / (double) GRAB;
                pos = start.lerp(orbit(boss, startAngle), t * t);
            } else if (age <= GRAB + ORBIT) {
                double a = startAngle + (age - GRAB) * (MathHelper.TAU * 1.25 / ORBIT);
                pos = orbit(boss, a);
            }
            Vec3d dir = pos.subtract(prev);
            if (age <= GRAB + ORBIT) {
                place(display, pos, pointing(dir.lengthSquared() > 1e-4 ? dir : new Vec3d(1, 0, 0), 0.9f), 1);
                world.spawnParticles(PSYCHIC, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0);
                if (age % 2 == 0) world.spawnParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.3);
                if (age == GRAB + 2) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.HOSTILE, 1.4f, 1.2f);
                return true;
            }
            // Loosed back at them, fast and true
            ServerPlayerEntity target = boss.fighter(shooter);
            if (target == null) target = boss.nearestFighter();
            display.discard();
            if (target == null) return false;
            Vec3d to = target.getBoundingBox().getCenter().subtract(pos);
            ArrowEntity arrow = boss.newArrow(world, new ItemStack(Items.ARROW));
            arrow.setPosition(pos.x, pos.y, pos.z);
            arrow.setNoGravity(true);
            arrow.setVelocity(to.x, to.y, to.z, 3.2f, 0f);
            arrow.setDamage(2.2 * GummssEntity.DAMAGE_DEALT);
            arrow.setCritical(true);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            world.spawnEntity(arrow);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.0f, 1.5f);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.3);
            return false;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- An ender pearl he's thrown: when it lands, so does he ----

    static final class Pearl extends Hazard {
        final DisplayEntity.ItemDisplayEntity display;
        final Vec3d from, to;
        final int ticks;

        Pearl(ServerWorld world, Vec3d from, Vec3d to) {
            this.from = from;
            this.to = to;
            this.ticks = (int) MathHelper.clamp(from.distanceTo(to) * 0.9, 10, 22);
            this.display = itemDisplay(world, new ItemStack(Items.ENDER_PEARL), from, ModelTransformationMode.GROUND);
            if (display != null) display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            double t = Math.min(1, age / (double) ticks);
            Vec3d at = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * (2 + from.distanceTo(to) * 0.18), 0);
            if (display != null) place(display, at, null, 1);
            world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.1);
            if (age < ticks) return true;
            if (display != null) display.discard();
            boss.pearlLanded(to);
            return false;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }

    // ---- The tome he casts from, floating open before him while he does ----

    static final class Tome extends Hazard {
        final DisplayEntity.ItemDisplayEntity display;
        final int ticks;

        Tome(ServerWorld world, GummssEntity boss, ItemStack tome, int ticks) {
            this.ticks = ticks;
            this.display = itemDisplay(world, tome, where(boss, 0), ModelTransformationMode.FIXED);
            if (display != null) display.setTransformation(transform(0, 0.01f));
            world.spawnParticles(ParticleTypes.ENCHANT, boss.getX(), boss.getY() + 1.6, boss.getZ(), 30, 0.6, 0.6, 0.6, 0.8);
        }

        static Vec3d where(GummssEntity boss, int age) {
            return boss.getPos().add(Vec3d.fromPolar(0, boss.getBodyYaw()).multiply(0.95)).add(0, 1.7 + Math.sin(age * 0.15) * 0.08, 0);
        }

        static AffineTransformation transform(int age, float size) {
            Quaternionf rot = new Quaternionf().rotationY(age * 0.12f).rotateX(-0.35f);
            return new AffineTransformation(new Vector3f(), rot, new Vector3f(size, size, size), new Quaternionf());
        }

        @Override
        boolean tick(ServerWorld world, GummssEntity boss) {
            age++;
            if (display == null) return false;
            Vec3d at = where(boss, age);
            place(display, at, transform(age, 0.7f * Math.min(1f, age / 6f)), 1);
            // Glyphs drawn in to its pages from all round
            for (int i = 0; i < 2; i++) {
                Vec3d off = new Vec3d(world.random.nextDouble() - 0.5, world.random.nextDouble() * 0.6, world.random.nextDouble() - 0.5).multiply(3);
                world.spawnParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 0, off.x, off.y, off.z, 1.0);
            }
            if (age % 3 == 0) world.spawnParticles(PSYCHIC, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0);
            if (age % 25 == 1) world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.HOSTILE, 1.2f, 0.7f);
            if (age >= ticks) {
                world.spawnParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.6);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 1.5f, 0.8f);
                display.discard();
                return false;
            }
            return true;
        }

        @Override
        void remove() {
            if (display != null) display.discard();
        }
    }
}
