package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One of the two fire tornadoes ORNAMIX calls up in his Twin Twisters: RAGGIMEL's Twister ({@code GreedWater.Twister})
 * set alight. While he casts, a ring of fire opens on the sand, a thin rope of flame climbs out of it and widens into a
 * funnel 24 blocks high (stacked rings of red and orange glass and magma, white hot at the foot, black smoke at the top,
 * each ring spinning, the whole thing bending and swaying, the top lagging behind the base), real flames and chunks of
 * the pit whirling up it, a cap of black smoke on top raining embers and lava, a whirl of fire round its foot. Once it's
 * half up it drags everyone within 18 blocks towards it, harder the closer they are (sprint away and you'll get clear).
 * Once he lets it go it wanders: somewhere new every second or two (often one of you, often not), weaving as it goes,
 * now and then lurching off sideways. Too close and it takes you: up and round the funnel, burning, and out, thrown a
 * long way (see {@link OrnamixEntity#twisterFling}). The whirl of fire at its foot sets you alight.
 */
final class FireTwister extends WrathHazards.Hazard {
    static final int LAYERS = 10, PANELS = 7, LAG = 2, SKIRT = 10, DEBRIS = 6, CAP = 5, FLAMES = 8, FADE = 20;
    static final float HEIGHT = 24f, R0 = 1.1f, RTOP = 6.5f;
    /** Ticks a caught player is carried round before being thrown; ticks until the same one can be caught again. */
    static final int CARRY = 16, RECATCH = 30;
    /** How far out it drags you in, and how hard at its foot (added to your own speed every tick). */
    static final double PULL_RANGE = 18, PULL = 0.05;
    /** How far out from its foot the whirl of fire sets you alight. */
    static final double SCORCH = 3.2;

    static final BlockState RED = Blocks.RED_STAINED_GLASS.getDefaultState();
    static final BlockState ORANGE = Blocks.ORANGE_STAINED_GLASS.getDefaultState();
    static final BlockState YELLOW = Blocks.YELLOW_STAINED_GLASS.getDefaultState();
    static final BlockState SMOKE = Blocks.GRAY_STAINED_GLASS.getDefaultState();
    static final BlockState SOOT = Blocks.BLACK_STAINED_GLASS.getDefaultState();
    static final BlockState GLOW = Blocks.SHROOMLIGHT.getDefaultState();
    static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(0.9f, 0.12f, 0.05f), 2.0f);
    static final DustParticleEffect HOT_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.1f), 1.5f);

    final List<DisplayEntity.BlockDisplayEntity> panels = new ArrayList<>();
    final List<DisplayEntity.BlockDisplayEntity> skirt = new ArrayList<>();
    final List<DisplayEntity.BlockDisplayEntity> debris = new ArrayList<>();
    final List<DisplayEntity.BlockDisplayEntity> cap = new ArrayList<>();
    final List<DisplayEntity.BlockDisplayEntity> flames = new ArrayList<>();
    /** Where its foot was, newest first: the funnel above follows it a little behind. */
    final ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    final Map<UUID, Carry> carried = new HashMap<>();
    final Map<UUID, Integer> recatch = new HashMap<>();
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
     * Forming at {@code spot} over {@code form} ticks (it stays put until {@link #release}); once loose it never strays
     * further than {@code leash} from {@code leashCenter}.
     */
    FireTwister(ServerWorld world, Vec3d spot, int form, Vec3d leashCenter, double leash) {
        this.pos = spot;
        this.form = form;
        this.leashCenter = leashCenter;
        this.leash = leash;
        this.goal = spot;
        this.seed = world.random.nextFloat() * 100f;
        BlockState ground = world.getBlockState(BlockPos.ofFloored(spot).down());
        if (ground.isAir() || ground.isOf(Blocks.BARRIER)) ground = Blocks.SAND.getDefaultState();
        for (int i = 0; i < LAYERS; i++) {
            for (int k = 0; k < PANELS; k++) {
                BlockState state;
                if (i >= LAYERS - 2) state = k % 2 == 0 ? SMOKE : SOOT;
                else if (i < 2) state = k % 3 == 0 ? YELLOW : k % 3 == 1 ? ORANGE : GLOW;
                else state = (i + k) % 4 == 0 ? WrathFlames.MAGMA : k % 3 == 0 ? ORANGE : RED;
                panels.add(piece(world, spot, state));
            }
        }
        for (int k = 0; k < SKIRT; k++) skirt.add(piece(world, spot, k % 3 == 0 ? WrathFlames.FIRE : k % 3 == 1 ? WrathFlames.MAGMA : ORANGE));
        for (int d = 0; d < DEBRIS; d++) {
            debris.add(piece(world, spot, d % 3 == 0 ? ground : d % 3 == 1 ? Blocks.NETHERRACK.getDefaultState()
                    : d == 2 ? WrathFlames.MAGMA : Blocks.BLACKSTONE.getDefaultState()));
        }
        for (int c = 0; c < CAP; c++) cap.add(piece(world, spot, c % 2 == 0 ? SOOT : SMOKE));
        for (int f = 0; f < FLAMES; f++) flames.add(piece(world, spot, WrathFlames.FIRE));
        trail.addFirst(spot);
    }

    private static DisplayEntity.BlockDisplayEntity piece(ServerWorld world, Vec3d at, BlockState state) {
        DisplayEntity.BlockDisplayEntity d = WrathFlames.spawn(world, at, state, box(new Vector3f(), new Quaternionf(), 0.01f, 0.01f, 0.01f));
        if (d != null) d.setTeleportDuration(2);
        return d;
    }

    /** A box {@code sx} x {@code sy} x {@code sz} centred on {@code center} (from its entity's spot), turned {@code rot}. */
    static AffineTransformation box(Vector3f center, Quaternionf rot, float sx, float sy, float sz) {
        Vector3f half = rot.transform(new Vector3f(-0.5f * sx, -0.5f * Math.max(0.001f, sy), -0.5f * sz));
        return new AffineTransformation(half.add(center), rot, new Vector3f(sx, Math.max(0.001f, sy), sz), null);
    }

    /** A cube {@code size} across, centred on its spot, tumbled by {@code turn}. */
    static AffineTransformation cube(Quaternionf turn, float size) {
        Vector3f half = turn.transform(new Vector3f(-0.5f * size, -0.5f * size, -0.5f * size));
        return new AffineTransformation(half, turn, new Vector3f(size), null);
    }

    /** Let loose: it wanders for {@code ticks}, at up to {@code speed} blocks a tick, then burns out. */
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

    /** Where it stands now, and the top of it. */
    Vec3d foot() {
        return pos;
    }

    Vec3d top() {
        return centerAt(1);
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
    boolean tick(ServerWorld world, OrnamixEntity boss) {
        age++;
        Random r = world.random;
        if (fading >= 0) return fade(world);
        if (roam < 0) {
            // Forming: the ring of fire first, then the funnel rising out of it, spinning faster and faster
            float u = MathHelper.clamp((age - 18) / (float) Math.max(1, form - 22), 0, 1);
            grow = u * u * (3 - 2 * u);
            spinSpeed = 0.04f + 0.26f * Math.min(1f, age / (float) form);
            if (age % 2 == 0) {
                // Flame sucked in along the sand towards the eye of it
                for (int i = 0; i < 4; i++) {
                    double a = r.nextDouble() * MathHelper.TAU, d = 3 + r.nextDouble() * 2.5;
                    double x = pos.x + Math.cos(a) * d, z = pos.z + Math.sin(a) * d;
                    world.spawnParticles(ParticleTypes.FLAME, x, pos.y + 0.15, z, 0, -Math.cos(a) - Math.sin(a), 0.3, -Math.sin(a) + Math.cos(a), 0.18);
                    world.spawnParticles(ParticleTypes.SMOKE, x, pos.y + 0.2, z, 0, -Math.cos(a) * 0.6 - Math.sin(a), 0.1, -Math.sin(a) * 0.6 + Math.cos(a), 0.12);
                }
            }
            if (age % 15 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.4f + age / (float) form * 0.4f);
            if (age % 20 == 5) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, 2.5f, 0.5f + age / (float) form * 0.3f);
            if (age == 20) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        } else {
            roam++;
            grow = 1;
            spinSpeed = 0.3f;
            wander(boss, r);
            if (roam >= roamTicks) {
                fading = 0;
                world.playSound(null, pos.x, pos.y + 4, pos.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 4.0f, 0.4f);
                world.playSound(null, pos.x, pos.y + 4, pos.z, SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 3.0f, 0.5f);
                for (DisplayEntity.BlockDisplayEntity d : all()) {
                    if (d != null) WrathFlames.animate(d, collapsed(d), FADE);
                }
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
    private void wander(OrnamixEntity boss, Random r) {
        if (--retarget <= 0) {
            retarget = 15 + r.nextInt(40);
            List<ServerPlayerEntity> fighters = boss.fighters();
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
            // Turned back at the edge of the pit
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
        all.addAll(flames);
        return all;
    }

    /** Every piece where it should be: the funnel's rings, the whirl of fire at its foot, the debris, the flames, the smoke on top. */
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
                WrathFlames.animate(d, box(center, rot, 0.3f, layerH * 1.35f * shown, w * (0.4f + 0.6f * shown)), 2);
            }
        }
        // The whirl of fire and magma round its foot
        for (int k = 0; k < SKIRT; k++) {
            DisplayEntity.BlockDisplayEntity d = skirt.get(k);
            if (d == null) continue;
            d.setPosition(pos.x, pos.y, pos.z);
            float open = Math.min(1f, age / 25f);
            float rad = (0.6f + 2.6f * open) * (0.85f + 0.15f * MathHelper.sin(age * 0.3f + k));
            float a = k * MathHelper.TAU / SKIRT + spin * 1.4f;
            boolean fire = k % 3 == 0;
            Quaternionf rot = new Quaternionf().rotateY(a).rotateZ(fire ? 0.15f : 0.5f);
            float tall = (fire ? 2.2f : 0.5f) * open * (0.7f + 0.3f * MathHelper.sin(age * 0.5f + k * 2));
            float wide = fire ? 1.6f * open : MathHelper.TAU * rad / SKIRT * 1.05f;
            WrathFlames.animate(d, box(new Vector3f(MathHelper.cos(a) * rad, tall * 0.5f, -MathHelper.sin(a) * rad), rot, fire ? 1.6f * open : 0.5f, tall, wide), 2);
        }
        // The pit torn up, whirling up the funnel
        for (int k = 0; k < DEBRIS; k++) {
            DisplayEntity.BlockDisplayEntity d = debris.get(k);
            if (d == null) continue;
            float f = ((age * 0.008f + k / (float) DEBRIS) % 1f) * grow;
            Vec3d c = centerAt(f);
            float a = spin * 1.25f + k * MathHelper.TAU / DEBRIS, rad = radiusAt(f) + 0.7f;
            d.setPosition(c.x + MathHelper.cos(a) * rad, c.y, c.z - MathHelper.sin(a) * rad);
            Quaternionf tumble = new Quaternionf().rotateXYZ(age * 0.31f + k, age * 0.23f, age * 0.17f + k * 2);
            WrathFlames.animate(d, cube(tumble, grow * (k % 2 == 0 ? 0.8f : 0.55f)), 2);
        }
        // Real flames spiralling up round the outside of it, faster than it turns
        for (int k = 0; k < FLAMES; k++) {
            DisplayEntity.BlockDisplayEntity d = flames.get(k);
            if (d == null) continue;
            float f = ((age * 0.012f + k / (float) FLAMES) % 1f) * 0.85f * grow;
            Vec3d c = centerAt(f);
            float a = spin * 1.6f + k * MathHelper.TAU / FLAMES * 2.5f, rad = radiusAt(f) + 0.35f;
            d.setPosition(c.x + MathHelper.cos(a) * rad, c.y, c.z - MathHelper.sin(a) * rad);
            float size = grow * (1.4f + 1.4f * f) * (0.85f + 0.15f * MathHelper.sin(age * 0.7f + k));
            WrathFlames.animate(d, box(new Vector3f(0, size * 0.5f, 0), new Quaternionf().rotateY(-a), size, size * 1.3f, size), 2);
        }
        // The smoke on top
        Vec3d top = centerAt(1);
        for (int k = 0; k < CAP; k++) {
            DisplayEntity.BlockDisplayEntity d = cap.get(k);
            if (d == null) continue;
            d.setPosition(top.x, top.y, top.z);
            float a = k * MathHelper.TAU / CAP + spin * 0.5f, rad = RTOP * 0.75f * grow;
            Quaternionf rot = new Quaternionf().rotateY(a);
            float size = (3.2f + 0.4f * MathHelper.sin(age * 0.1f + k)) * grow;
            WrathFlames.animate(d, box(new Vector3f(MathHelper.cos(a) * rad, 0.2f + (k % 2) * 0.5f, -MathHelper.sin(a) * rad), rot, size, 1.0f * grow, size * 0.8f), 2);
        }
    }

    /** Where a piece ends up as it burns out: flat on the ground under where it is. */
    private AffineTransformation collapsed(DisplayEntity.BlockDisplayEntity d) {
        float drop = (float) (pos.y - d.getY());
        return box(new Vector3f(0, drop, 0), new Quaternionf().rotateY(spin), 0.4f, 0.001f, 0.4f);
    }

    /** Anyone near enough is taken up and round, burning, then thrown out of it; anyone in the fire at its foot burns. */
    private void catchPlayers(ServerWorld world, OrnamixEntity boss) {
        recatch.replaceAll((id, t) -> t - 1);
        recatch.values().removeIf(t -> t <= 0);
        float h = height();
        if (grow >= 0.5f) {
            pull(boss, h);
            for (ServerPlayerEntity p : boss.fighters()) {
                UUID id = p.getUuid();
                double dy = p.getY() - pos.y;
                if (age % 10 == 0 && dy > -1.5 && dy < 3 && WrathHazards.flatDistance(p.getPos(), pos) < SCORCH * grow) p.setOnFireFor(4);
                if (carried.containsKey(id) || recatch.containsKey(id)) continue;
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
                p.playSoundToPlayer(SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.7f);
            }
        }
        var it = carried.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayerEntity p = boss.fighter(e.getKey());
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
            if (carry.t % 5 == 1) p.setOnFireFor(6);
            world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.5, 0.4, 0.08);
            if (carry.t % 2 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.4, 0.3, 0.02);
            if (carry.t >= CARRY) {
                // Out of the spin: outwards and on round the way it was going
                Vec3d radial = new Vec3d(Math.cos(carry.angle), 0, -Math.sin(carry.angle));
                Vec3d tangent = new Vec3d(-Math.sin(carry.angle), 0, -Math.cos(carry.angle));
                boss.twisterFling(p, radial.multiply(0.6).add(tangent.multiply(0.8)).normalize());
                world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.5, 0.5, 0.25);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                world.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.3, 0.3, 0);
                it.remove();
                recatch.put(e.getKey(), RECATCH);
            }
        }
    }

    /** Everyone near is dragged in, and a little round the way it spins. */
    private void pull(OrnamixEntity boss, float h) {
        for (ServerPlayerEntity p : boss.fighters()) {
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

    /** The particles that go with it: flame and smoke streaking round the funnel, embers and lava off the top, the fire at its foot. */
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
                if (i % 2 == 0) world.spawnParticles(f > 0.75f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, x, c.y, z, 0, tx, 0.15, tz, f > 0.75f ? 0.25 : 0.4);
                else world.spawnParticles(f < 0.5f ? HOT_DUST : RED_DUST, x, c.y, z, 1, 0.2, 0.2, 0.2, 0);
            }
            Vec3d top = centerAt(1);
            world.spawnParticles(ParticleTypes.FALLING_LAVA, top.x, top.y - 1, top.z, 2, RTOP * 0.6 * grow, 0.5, RTOP * 0.6 * grow, 0);
            world.spawnParticles(ParticleTypes.ASH, top.x, top.y - 2, top.z, 6, RTOP * 0.8 * grow, 2, RTOP * 0.8 * grow, 0);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 0.5, top.z, 3, RTOP * 0.6, 0.4, RTOP * 0.6, 0.02);
            if (age % 4 == 0) world.spawnParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 1, RTOP * 0.5, 0.3, RTOP * 0.5, 0);
        }
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.2, pos.z, 5, 2.2, 0.1, 2.2, 0.05);
        if (age % 2 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y + 0.2, pos.z, 3, 1.5, 0.1, 1.5, 0.02);
        if (age % 4 == 0 && grow > 0.3f) {
            BlockState ground = world.getBlockState(BlockPos.ofFloored(pos).down());
            if (!ground.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), pos.x, pos.y + 0.3, pos.z, 6, 1.5, 0.2, 1.5, 0.2);
            world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3, pos.z, 1, 1.2, 0.1, 1.2, 0);
        }
        if (loose()) {
            if (age % 20 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 4.0f, 0.5f);
            if (age % 26 == 0) world.playSound(null, pos.x, pos.y + 6, pos.z, SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 4.0f, 0.4f);
            if (age % 34 == 0) world.playSound(null, pos.x, pos.y + 3, pos.z, SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }
    }

    /** Burning out: all of it comes down at once, a burst of flame and smoke where it stood. */
    private boolean fade(ServerWorld world) {
        fading++;
        float h = HEIGHT * (1 - fading / (float) FADE);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + h * 0.5, pos.z, 12, RTOP * 0.5, h * 0.4 + 0.5, RTOP * 0.5, 0.02);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.2, pos.z, 20, 3.5, 0.1, 3.5, 0.1);
        if (fading % 3 == 0) world.spawnParticles(ParticleTypes.FALLING_LAVA, pos.x, pos.y + h * 0.5, pos.z, 8, RTOP * 0.5, h * 0.4 + 0.5, RTOP * 0.5, 0);
        if (fading == FADE - 4) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 50, 3, 0.3, 3, 0.12);
            world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5, pos.z, 15, 2, 0.3, 2, 0);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.HOSTILE, 4.0f, 0.5f);
        }
        return fading < FADE;
    }

    @Override
    void remove() {
        WrathFlames.discard(panels);
        WrathFlames.discard(skirt);
        WrathFlames.discard(debris);
        WrathFlames.discard(cap);
        WrathFlames.discard(flames);
        carried.clear();
    }
}
