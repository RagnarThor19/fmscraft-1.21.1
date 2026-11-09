package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
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
import net.ragnar.fmscraft.compat.MagicSpells;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ORNAMIX's Tome of Boulders, made worse: a meteor's worth of burning rock (a heart of magma, crags of blackstone,
 * netherrack and basalt round it, the same look as the Tome of Meteor's), held over his head while it gathers, then
 * thrown down in front of him to roll across the pit, faster and faster, until it hits the wall. It turns as it rolls,
 * throws up sand and flame, leaves a line of fire behind it, and anyone it runs over is hurt badly, set alight and
 * thrown. At the wall it bursts: the rock flies apart, a blast of fire throws back (and hurts) anyone close. It breaks
 * nothing.
 */
final class FlamingBoulder extends WrathHazards.Hazard {
    /** How big it is (its radius, roughly), and how it hurts: run over, and caught in the burst at the wall. */
    static final float RADIUS = 2.6f, RUN_OVER = 20f, RUN_OVER_BURN = 8f, BURST = 12f, BURST_BURN = 5f;
    static final double BURST_REACH = 6;
    /** Thrown down out of his hands, then rolling: from this fast to this fast (blocks a tick), and at most this far. */
    static final double THROW_SPEED = 0.5, THROW_DROP = -0.2, GRAVITY = 0.1, ROLL_START = 0.55, ROLL_TOP = 0.9, ROLL_GAIN = 0.012, MAX_ROLL = 110;
    static final int TRAIL_LIFE = 36, BREAK_LIFE = 34, MAX_AGE = 400;

    record Chunk(DisplayEntity.BlockDisplayEntity display, Vector3f offset, float size, Vector3f spin) {
    }

    static final class Flame {
        final DisplayEntity.BlockDisplayEntity display;
        final Vec3d at;
        int age;

        Flame(DisplayEntity.BlockDisplayEntity display, Vec3d at) {
            this.display = display;
            this.at = at;
        }
    }

    final List<Chunk> chunks = new ArrayList<>();
    final List<Vec3d> flying = new ArrayList<>();
    final List<Flame> trail = new ArrayList<>();
    final Set<UUID> hit = new HashSet<>();
    final Quaternionf roll = new Quaternionf();
    Vec3d pos, vel = Vec3d.ZERO, dir = new Vec3d(1, 0, 0);
    float grow = 0.15f;
    double speed, rolled, sinceFlame;
    /** -1 held, 0 thrown (in the air), 1 rolling, 2 broken against the wall. */
    int state = -1, brokenAge;
    boolean hitWall;

    FlamingBoulder(ServerWorld world, Vec3d at) {
        this.pos = at;
        Random rnd = world.random;
        addChunk(world, WrathFlames.MAGMA, new Vector3f(), 4.0f, rnd);
        BlockState[] rocks = {Blocks.BLACKSTONE.getDefaultState(), Blocks.NETHERRACK.getDefaultState(),
                Blocks.BASALT.getDefaultState(), WrathFlames.MAGMA, Blocks.GILDED_BLACKSTONE.getDefaultState()};
        for (int i = 0; i < 9; i++) {
            Vector3f d = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
            addChunk(world, rocks[i % rocks.length], d.mul(1.55f + rnd.nextFloat() * 0.45f), 1.7f + rnd.nextFloat() * 0.9f, rnd);
        }
        // Flames licking off it
        for (int i = 0; i < 4; i++) {
            Vector3f d = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() * 0.5f, rnd.nextFloat() - 0.5f).normalize();
            addChunk(world, WrathFlames.FIRE, d.mul(2.3f), 1.8f, rnd);
        }
    }

    private void addChunk(ServerWorld world, BlockState state, Vector3f offset, float size, Random rnd) {
        Chunk c = new Chunk(null, offset, size, new Vector3f(rnd.nextFloat() * 0.2f - 0.1f, rnd.nextFloat() * 0.2f - 0.1f, rnd.nextFloat() * 0.2f - 0.1f));
        DisplayEntity.BlockDisplayEntity d = WrathFlames.spawn(world, pos, state, transform(c, 0));
        if (d == null) return;
        d.setViewRange(8f);
        chunks.add(new Chunk(d, offset, size, c.spin()));
    }

    private AffineTransformation transform(Chunk c, int t) {
        boolean fire = c.display() != null && c.display().getBlockState().isOf(Blocks.FIRE);
        Matrix4f m = new Matrix4f();
        if (fire) {
            // Flames stay upright on the outside of it, flickering
            Vector3f o = new Quaternionf(roll).transform(new Vector3f(c.offset()));
            float s = c.size() * grow * (0.8f + 0.25f * MathHelper.sin(t * 0.9f + c.offset().x * 5));
            return new AffineTransformation(m.translate(o.mul(grow)).scale(s).translate(-0.5f, -0.3f, -0.5f));
        }
        return new AffineTransformation(m.rotate(roll).translate(new Vector3f(c.offset()).mul(grow))
                .rotateXYZ(t * c.spin().x, t * c.spin().y, t * c.spin().z)
                .scale(c.size() * grow).translate(-0.5f, -0.5f, -0.5f));
    }

    float radius() {
        return RADIUS * grow;
    }

    boolean held() {
        return state < 0;
    }

    boolean rolling() {
        return state == 1;
    }

    /** Over his head at {@code at}, {@code grow} (0 to 1) of its full size, turning slowly. */
    void hold(Vec3d at, float grow) {
        this.pos = at;
        this.grow = MathHelper.clamp(grow, 0.15f, 1f);
    }

    /** Thrown down along {@code dir} (flattened). */
    void launch(Vec3d dir) {
        Vec3d flat = dir.multiply(1, 0, 1);
        this.dir = flat.lengthSquared() > 1e-4 ? flat.normalize() : new Vec3d(1, 0, 0);
        this.vel = this.dir.multiply(THROW_SPEED).add(0, THROW_DROP, 0);
        this.grow = 1f;
        state = 0;
    }

    @Override
    boolean tick(ServerWorld world, OrnamixEntity boss) {
        age++;
        tickTrail(world, boss);
        if (state == 2) return tickBroken(world);
        if (age > MAX_AGE) {
            burst(world, boss);
            return true;
        }
        if (state < 0) {
            // Held: gathering, turning in the air, flames roaring off it
            roll.rotateY(0.06f).rotateX(0.025f);
            draw();
            boss.emit(world, ParticleTypes.FLAME, pos, 6, radius() * 0.6, radius() * 0.6, radius() * 0.6, 0.03);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 1, radius() * 0.5, radius() * 0.5, radius() * 0.5, 0);
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + radius(), pos.z, 1, 0.5, 0.3, 0.5, 0.02);
            return true;
        }
        double ground = boss.groundUnder(pos, pos.y - RADIUS);
        if (state == 0) {
            // Down out of his hands onto the sand
            vel = vel.add(0, -GRAVITY, 0);
            pos = pos.add(vel);
            spinBy(vel.horizontalLength());
            if (pos.y <= ground + RADIUS) {
                pos = new Vec3d(pos.x, ground + RADIUS, pos.z);
                state = 1;
                speed = ROLL_START;
                land(world, boss);
            }
        } else {
            speed = Math.min(ROLL_TOP, speed + ROLL_GAIN);
            Vec3d next = pos.add(dir.multiply(speed));
            double under = boss.groundUnder(next, pos.y - RADIUS);
            // Over a little step it bumps on; over nothing (off the edge) it drops on down
            pos = new Vec3d(next.x, MathHelper.lerp(0.5, pos.y, under + RADIUS), next.z);
            rolled += speed;
            sinceFlame += speed;
            spinBy(speed);
            rollingEffects(world, boss, under);
        }
        draw();
        runOver(world, boss);
        if (wallAhead(world, boss) || rolled > MAX_ROLL) burst(world, boss);
        return true;
    }

    /** Turns it as much as rolling {@code distance} along the ground would. */
    private void spinBy(double distance) {
        Vector3f axis = new Vector3f((float) dir.z, 0, (float) -dir.x);
        roll.premul(new Quaternionf().rotateAxis((float) (distance / RADIUS), axis));
    }

    private void draw() {
        for (Chunk c : chunks) {
            c.display().setPosition(pos.x, pos.y, pos.z);
            c.display().setTransformation(transform(c, age));
            c.display().setStartInterpolation(0);
            c.display().setInterpolationDuration(1);
        }
    }

    private void land(ServerWorld world, OrnamixEntity boss) {
        Vec3d g = pos.subtract(0, RADIUS, 0);
        boss.emit(world, ParticleTypes.EXPLOSION, g.add(0, 0.5, 0), 3, 1.5, 0.3, 1.5, 0);
        BlockState sand = world.getBlockState(BlockPos.ofFloored(g).down());
        if (!sand.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, sand), g.x, g.y + 0.3, g.z, 80, 2, 0.3, 2, 0.3);
        world.playSound(null, g.x, g.y, g.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.5f, 0.5f);
        world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
        MagicSpells.shake(world, g, 24, 0.6f, 10);
    }

    private void rollingEffects(ServerWorld world, OrnamixEntity boss, double ground) {
        Vec3d g = new Vec3d(pos.x, ground, pos.z), back = dir.multiply(-RADIUS);
        boss.emit(world, ParticleTypes.FLAME, pos, 14, RADIUS * 0.6, RADIUS * 0.6, RADIUS * 0.6, 0.06);
        boss.emit(world, ParticleTypes.LARGE_SMOKE, pos.add(back).add(0, RADIUS * 0.5, 0), 4, 0.8, 0.8, 0.8, 0.03);
        if (age % 2 == 0) boss.emit(world, ParticleTypes.LAVA, pos, 2, RADIUS * 0.5, RADIUS * 0.5, RADIUS * 0.5, 0);
        BlockState sand = world.getBlockState(BlockPos.ofFloored(g).down());
        if (!sand.isAir()) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, sand), g.x + back.x * 0.5, g.y + 0.2, g.z + back.z * 0.5, 12, 1.2, 0.2, 1.2, 0.25);
        }
        if (age % 4 == 0) world.playSound(null, g.x, g.y, g.z, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        if (age % 6 == 0) world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 3.0f, 0.5f);
        if (age % 10 == 0) world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.5f, 0.4f);
        if (age % 8 == 0) MagicSpells.shake(world, g, 16, 0.3f, 8);
        // A line of fire left burning behind it
        if (sinceFlame >= 1.3) {
            sinceFlame = 0;
            for (int side = -1; side <= 1; side += 2) {
                Vec3d at = g.add(dir.z * side * RADIUS * 0.45, 0, -dir.x * side * RADIUS * 0.45).add(back.multiply(0.6));
                DisplayEntity.BlockDisplayEntity d = WrathFlames.spawn(world, at, WrathFlames.FIRE, WrathFlames.shape(0, 0, 0, 0.9f, 0.01f, 0.9f));
                if (d != null) {
                    WrathFlames.animate(d, WrathFlames.shape(0, 0, 0, 1.1f, 1.6f, 1.1f), 4);
                    trail.add(new Flame(d, at));
                }
            }
        }
    }

    /** The line of fire: up quickly, burning a while (and burning you), then down into the sand. */
    private void tickTrail(ServerWorld world, OrnamixEntity boss) {
        var it = trail.iterator();
        while (it.hasNext()) {
            Flame f = it.next();
            f.age++;
            if (f.age == TRAIL_LIFE - 8) WrathFlames.animate(f.display, WrathFlames.shape(0, 0, 0, 0.7f, 0.01f, 0.7f), 8);
            if (f.age % 6 == 0) {
                for (ServerPlayerEntity p : boss.fighters()) {
                    if (WrathHazards.flatDistance(p.getPos(), f.at) < 0.9 && Math.abs(p.getY() - f.at.y) < 1.5) p.setOnFireFor(3);
                }
            }
            if (f.age >= TRAIL_LIFE) {
                f.display.discard();
                it.remove();
            }
        }
    }

    /** Anyone it runs over: hurt badly, set alight and thrown on ahead of it and up. Once each. */
    private void runOver(ServerWorld world, OrnamixEntity boss) {
        double reach = RADIUS + 0.7;
        for (ServerPlayerEntity p : boss.fighters()) {
            if (hit.contains(p.getUuid())) continue;
            if (WrathHazards.flatDistance(p.getPos(), pos) > reach) continue;
            double dy = p.getY() - pos.y;
            if (dy < -RADIUS - 1 || dy > RADIUS) continue;
            hit.add(p.getUuid());
            p.timeUntilRegen = 0;
            boss.hurt(p, RUN_OVER, RUN_OVER_BURN);
            Vec3d away = p.getPos().subtract(pos).multiply(1, 0, 1);
            Vec3d side = away.lengthSquared() > 1e-4 ? away.normalize().multiply(0.6) : Vec3d.ZERO;
            boss.launch(p, dir.add(side), 2.0, 0.9);
            boss.flash(p, 0.5f, 10);
            boss.boulderHit();
            world.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.5, 0.4, 0.3);
            world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.5, 0.4, 0.15);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
    }

    /** Something solid right in front of it (the wall of the pit), or the edge of the pit itself. */
    private boolean wallAhead(ServerWorld world, OrnamixEntity boss) {
        Vec3d ahead = pos.add(dir.multiply(RADIUS + 0.4 + Math.max(speed, vel.horizontalLength())));
        double foot = pos.y - RADIUS;
        Vec3d side = new Vec3d(dir.z, 0, -dir.x);
        for (int s = -1; s <= 1; s++) {
            Vec3d col = ahead.add(side.multiply(s * RADIUS * 0.5));
            for (double h = 1.2; h <= 3.2; h += 1.0) {
                BlockPos at = BlockPos.ofFloored(col.x, foot + h, col.z);
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) return true;
            }
        }
        return boss.inColosseum(world) && WrathHazards.flatDistance(ahead, WrathArena.CENTER) > WrathArena.PIT_RADIUS;
    }

    /** Into the wall: it bursts, the rock flying apart, a blast of fire throwing back anyone near. */
    private void burst(ServerWorld world, OrnamixEntity boss) {
        state = 2;
        brokenAge = 0;
        hitWall = true;
        boss.boulderBurst(rolled);
        Random r = world.random;
        for (Chunk c : chunks) {
            Vector3f o = new Quaternionf(roll).transform(new Vector3f(c.offset()));
            Vec3d out = new Vec3d(o.x, Math.abs(o.y) + 0.8, o.z).normalize();
            flying.add(out.multiply(0.35 + r.nextDouble() * 0.35).add(dir.multiply(-0.25)).add(0, 0.3 + r.nextDouble() * 0.3, 0));
        }
        for (ServerPlayerEntity p : boss.fighters()) {
            double d = p.getPos().distanceTo(pos);
            if (d > BURST_REACH) continue;
            p.timeUntilRegen = 0;
            boss.hurt(p, (float) (BURST * (0.5 + 0.5 * (1 - d / BURST_REACH))), BURST_BURN);
            boss.launch(p, p.getPos().subtract(pos), 1.4 * (1 - d / BURST_REACH) + 0.4, 0.6);
        }
        boss.emit(world, ParticleTypes.EXPLOSION_EMITTER, pos, 3, 1.5, 1.0, 1.5, 0);
        boss.emit(world, ParticleTypes.FLASH, pos, 1, 0, 0, 0, 0);
        boss.emit(world, ParticleTypes.FLAME, pos, 220, 1.5, 1.5, 1.5, 0.45);
        boss.emit(world, ParticleTypes.LAVA, pos, 40, 1.5, 1.0, 1.5, 0);
        boss.emit(world, ParticleTypes.LARGE_SMOKE, pos, 70, 2, 2, 2, 0.12);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACKSTONE.getDefaultState()), pos.x, pos.y, pos.z, 120, 1.5, 1.5, 1.5, 0.4);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 5.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.4f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 4.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 4.0f, 0.4f);
        for (ServerPlayerEntity p : boss.audience(world)) {
            double d = p.getPos().distanceTo(pos);
            if (d < 40) boss.flash(p, (float) (0.6 * (1 - d / 40)) + 0.1f, 14);
        }
        MagicSpells.shake(world, pos, 40, 1.0f, 16);
    }

    /** Its pieces flying apart and falling, shrinking, gone. */
    private boolean tickBroken(ServerWorld world) {
        brokenAge++;
        float shrink = 1 - brokenAge / (float) BREAK_LIFE;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            Vec3d v = flying.get(i);
            Vec3d at = c.display().getPos().add(v);
            flying.set(i, v.multiply(0.96).add(0, -0.06, 0));
            c.display().setPosition(at.x, at.y, at.z);
            float s = c.size() * Math.max(0.01f, shrink);
            c.display().setTransformation(new AffineTransformation(new Matrix4f()
                    .rotateXYZ(brokenAge * 0.3f + i, brokenAge * 0.2f, i).scale(s).translate(-0.5f, -0.5f, -0.5f)));
            c.display().setStartInterpolation(0);
            c.display().setInterpolationDuration(1);
            if (brokenAge % 3 == 0) world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.02);
            if (brokenAge % 4 == 0) world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (brokenAge >= BREAK_LIFE) {
            for (Chunk c : chunks) c.display().discard();
            chunks.clear();
        }
        return brokenAge < BREAK_LIFE || !trail.isEmpty();
    }

    @Override
    void remove() {
        for (Chunk c : chunks) c.display().discard();
        chunks.clear();
        for (Flame f : trail) f.display.discard();
        trail.clear();
    }
}
