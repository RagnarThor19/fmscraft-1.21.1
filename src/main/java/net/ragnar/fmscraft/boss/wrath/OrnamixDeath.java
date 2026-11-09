package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * How ORNAMIX dies: Burnout. The fire that's been on him all fight goes out.
 * <ol>
 *   <li>The last blow: the music fades, one last flash of firelight, and he staggers, burning. His sword slips out of
 *       his hand and comes down point first into the sand in front of him.</li>
 *   <li>He sinks to one knee where he stands, head bowed, as he knelt when the fight began. Seams of magma spread
 *       out round him one last time.</li>
 *   <li>A heartbeat, slower each time. On each beat the glow pulls back towards him: the outer seams go dark (to
 *       blackstone, a puff of smoke). The sky lightens back to its red, the bass stops blinking it. His fire gutters:
 *       roaring, then flickering, then a few small flames, then smoke.</li>
 *   <li>He reaches out for the sword, shaking, and can't reach it; the hand falls. The fire in his eyes goes out.</li>
 *   <li>He crumbles from the feet up into ash, blown away on a wind; a little pile of ash is left at the sword's foot.
 *       Last of all a single ember drifts up from where his head was, and goes out.</li>
 * </ol>
 * Then he really dies ({@link OrnamixEntity}), and a couple of seconds after that the portal opens and the wheel
 * spins. Nothing in it hurts anyone. Everything but the sword and the ash goes with him; those two stay until the
 * arena is next reset ({@link #REMAINS_TAG}).
 */
final class OrnamixDeath {
    /** On the sword and the ash he leaves (not his fire: they stay after him, and the arena reset clears them). */
    static final String REMAINS_TAG = "fmscraft.wrath_remains";

    /** Ticks from the last blow. */
    static final int SWORD_DROP = 12, SWORD_FALL = 10, KNEEL = 24, SEAMS_GROW = 16, HONOUR = 40, REACH = 72, REACH_END = 100,
            EYES_OUT = 112, CRUMBLE = 120, GONE = 150, EMBER_OUT = 178, END = 190;
    /** The heartbeats, further apart each time. */
    static final int[] HEARTBEATS = {44, 64, 86, 110, 136};
    /** After he's gone, how long until the portal opens and the wheel spins. */
    static final int REWARDS_AFTER = 40;
    static final int SEAMS = 7, SEGMENTS = 8;
    static final float SEGMENT = 1.1f;

    static final DustParticleEffect ASH_DUST = new DustParticleEffect(new Vector3f(0.34f, 0.32f, 0.31f), 1.6f);
    static final DustParticleEffect PALE_ASH = new DustParticleEffect(new Vector3f(0.62f, 0.6f, 0.58f), 1.2f);
    static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0f, 0.4f, 0.08f), 1.0f);

    final Vec3d wind;
    final float[] seamYaw = new float[SEAMS];
    final DisplayEntity.BlockDisplayEntity[][] seams = new DisplayEntity.BlockDisplayEntity[SEAMS][SEGMENTS];
    final boolean[][] lit = new boolean[SEAMS][SEGMENTS];
    final List<DisplayEntity.BlockDisplayEntity> ash = new ArrayList<>();
    DisplayEntity.ItemDisplayEntity sword;
    Vec3d kneelAt, swordFrom, swordAt;
    float swordYaw;
    int t, darkRings;

    OrnamixDeath(Random random) {
        double a = random.nextDouble() * MathHelper.TAU;
        wind = new Vec3d(Math.cos(a), 0, Math.sin(a));
        float start = random.nextFloat() * MathHelper.TAU;
        for (int i = 0; i < SEAMS; i++) seamYaw[i] = start + i * MathHelper.TAU / SEAMS + (random.nextFloat() - 0.5f) * 0.5f;
    }

    /** How much fire is left on him, 1 to 0. */
    float fire() {
        if (t < KNEEL) return 1f;
        return MathHelper.clamp(1f - (t - KNEEL) / (float) (CRUMBLE - KNEEL), 0f, 1f);
    }

    /** One tick; true once he's done and can die. */
    boolean tick(ServerWorld world, OrnamixEntity boss) {
        t++;
        Random r = world.random;
        if (t == 1) lastBlow(world, boss);
        if (t < KNEEL) {
            // Staggering back, burning
            Vec3d back = Vec3d.fromPolar(0, boss.getYaw()).multiply(-0.05);
            boss.setVelocity(back.x, boss.getVelocity().y, back.z);
            if (t % 5 == 0) world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 1.5f, 0.5f - t * 0.005f);
        } else {
            boss.setVelocity(0, boss.getVelocity().y, 0);
        }
        if (t == SWORD_DROP) dropSword(world, boss);
        tickSword(world, boss);
        if (t == KNEEL) kneel(world, boss);
        if (t > KNEEL && t <= KNEEL + SEAMS_GROW) growSeams(world, (t - KNEEL) / (float) SEAMS_GROW);
        if (t == HONOUR) honour(world, boss);
        for (int i = 0; i < HEARTBEATS.length; i++) if (t == HEARTBEATS[i]) heartbeat(world, boss, i);
        if (t == REACH) {
            boss.setDeathPose(OrnamixEntity.POSE_REACH);
            world.playSound(null, boss.getX(), boss.getEyeY(), boss.getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.4f);
        }
        if (t == REACH_END) {
            boss.setDeathPose(OrnamixEntity.POSE_DYING);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_SAND_FALL, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        if (t == EYES_OUT) {
            boss.burnOut();
            Vec3d eyes = boss.getEyePos();
            world.spawnParticles(ParticleTypes.SMOKE, eyes.x, eyes.y, eyes.z, 6, 0.12, 0.05, 0.12, 0.005);
            world.playSound(null, eyes.x, eyes.y, eyes.z, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t < GONE) dyingFire(world, boss, r);
        if (t >= KNEEL && t % 3 == 0) {
            // The seams still lit simmer
            for (int i = 0; i < SEAMS; i++) {
                for (int k = 0; k < SEGMENTS - darkRings; k++) {
                    if (r.nextInt(12) != 0) continue;
                    Vec3d at = segment(i, k + 0.5f);
                    world.spawnParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.1, at.z, 1, 0.15, 0, 0.15, 0.005);
                }
            }
        }
        if (t >= CRUMBLE && t < GONE) crumble(world, boss, (t - CRUMBLE) / (float) (GONE - CRUMBLE));
        if (t == GONE) gone(world, boss);
        if (t > GONE && t <= EMBER_OUT) ember(world, (t - GONE) / (float) (EMBER_OUT - GONE));
        // Ash on the air all through it, thicker as he goes
        if (t % 2 == 0) {
            Vec3d c = kneelAt != null ? kneelAt : boss.getPos();
            world.spawnParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 4, c.z, 4 + t / 30, 8, 3, 8, 0);
            world.spawnParticles(ParticleTypes.ASH, c.x, c.y + 4, c.z, 6 + t / 20, 10, 3, 10, 0);
        }
        return t >= END;
    }

    private void lastBlow(ServerWorld world, OrnamixEntity boss) {
        Vec3d c = boss.getPos().add(0, 1, 0);
        boss.emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
        boss.emit(world, ParticleTypes.FLAME, c, 120, 0.5, 1.0, 0.5, 0.25);
        boss.emit(world, ParticleTypes.LAVA, c, 20, 0.4, 0.6, 0.4, 0);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 3.0f, 0.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.HOSTILE, 0.6f, 0.5f);
        for (ServerPlayerEntity p : boss.audience(world)) {
            boss.flash(p, 0.7f, 30);
            // Nothing of his burns on after him
            p.extinguish();
        }
        boss.setDeathPose(OrnamixEntity.POSE_STUNNED);
    }

    // ---- The sword: out of his hand, end over end, point first into the sand ----

    private void dropSword(ServerWorld world, OrnamixEntity boss) {
        Vec3d forward = Vec3d.fromPolar(0, boss.getYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(-0.4);
        swordFrom = boss.getPos().add(0, boss.getHeight() * 0.55, 0).add(forward.multiply(0.5)).add(side);
        Vec3d spot = boss.getPos().add(forward.multiply(1.7));
        swordAt = new Vec3d(spot.x, boss.groundUnder(spot, boss.getY()) + 0.5, spot.z);
        swordYaw = boss.getYaw();
        boss.letGoOfSword();
        sword = EntityType.ITEM_DISPLAY.create(world);
        if (sword == null) return;
        sword.setItemStack(new ItemStack(Items.IRON_SWORD));
        sword.setTransformationMode(ModelTransformationMode.NONE);
        sword.setTeleportDuration(1);
        sword.setViewRange(4f);
        sword.setBrightness(new Brightness(15, 15));
        sword.addCommandTag(REMAINS_TAG);
        sword.refreshPositionAndAngles(swordFrom.x, swordFrom.y, swordFrom.z, 0f, 0f);
        sword.setTransformation(swordShape(0));
        world.spawnEntity(sword);
        world.playSound(null, swordFrom.x, swordFrom.y, swordFrom.z, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    /** {@code u} of the way down: turning over from level in his hand to point down in the sand, facing the way he did. */
    private AffineTransformation swordShape(float u) {
        // An item display's sprite points its tip along (-1, 1) (EnvyHazards.TIP_LOCAL): level at a quarter turn, down at 3/4
        float over = MathHelper.lerp(u * u, 0.25f * MathHelper.PI, 0.75f * MathHelper.PI);
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(-swordYaw)).rotateZ(over);
        return new AffineTransformation(new Vector3f(), rot, new Vector3f(1.6f), null);
    }

    private void tickSword(ServerWorld world, OrnamixEntity boss) {
        if (sword == null || swordAt == null) return;
        int s = t - SWORD_DROP;
        if (s <= SWORD_FALL) {
            float u = s / (float) SWORD_FALL;
            Vec3d at = swordFrom.lerp(swordAt, u).add(0, Math.sin(u * Math.PI) * 0.8, 0);
            sword.setPosition(at.x, at.y, at.z);
            sword.setTransformation(swordShape(u));
            sword.setStartInterpolation(0);
            sword.setInterpolationDuration(1);
            world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
            if (s == SWORD_FALL) {
                // Into the sand
                world.spawnParticles(ParticleTypes.LAVA, swordAt.x, swordAt.y - 0.4, swordAt.z, 4, 0.1, 0.05, 0.1, 0);
                world.spawnParticles(ParticleTypes.CRIT, swordAt.x, swordAt.y - 0.3, swordAt.z, 12, 0.2, 0.1, 0.2, 0.15);
                world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()),
                        swordAt.x, swordAt.y - 0.4, swordAt.z, 20, 0.25, 0.05, 0.25, 0.1);
                world.playSound(null, swordAt.x, swordAt.y, swordAt.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.6f, 1.6f);
                world.playSound(null, swordAt.x, swordAt.y, swordAt.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            return;
        }
        // Planted: its blade still burning while he does, then only smoke rising off it
        if (t % 3 != 0 || t > EMBER_OUT) return;
        Vec3d blade = swordAt.add(0, 0.1 + world.random.nextDouble() * 0.7, 0);
        if (world.random.nextFloat() < fire() + 0.1f) world.spawnParticles(ParticleTypes.SMALL_FLAME, blade.x, blade.y, blade.z, 1, 0.03, 0.05, 0.03, 0.003);
        else world.spawnParticles(ParticleTypes.SMOKE, blade.x, blade.y, blade.z, 1, 0.03, 0.05, 0.03, 0.01);
    }

    // ---- Kneeling, the seams spreading one last time ----

    private void kneel(ServerWorld world, OrnamixEntity boss) {
        kneelAt = boss.getPos();
        boss.setDeathPose(OrnamixEntity.POSE_DYING);
        world.playSound(null, kneelAt.x, kneelAt.y, kneelAt.z, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.HOSTILE, 1.5f, 0.5f);
        world.playSound(null, kneelAt.x, kneelAt.y, kneelAt.z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.HOSTILE, 1.5f, 0.6f);
        world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()),
                kneelAt.x, kneelAt.y + 0.1, kneelAt.z, 30, 0.5, 0.05, 0.5, 0.1);
        for (int i = 0; i < SEAMS; i++) {
            for (int k = 0; k < SEGMENTS; k++) {
                Vec3d at = segment(i, k + 0.5f);
                seams[i][k] = WrathFlames.spawn(world, at, WrathFlames.MAGMA, WrathFlames.shape(seamYaw[i], 0, 0, 0.01f, 0.03f, 0.01f));
                if (seams[i][k] != null) seams[i][k].setTeleportDuration(0);
            }
        }
        world.playSound(null, kneelAt.x, kneelAt.y, kneelAt.z, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    /** Where segment {@code k} (from the middle out) of seam {@code i} starts, on the ground. */
    private Vec3d segment(int i, float k) {
        Vec3d c = kneelAt;
        double d = 0.9 + k * SEGMENT;
        // Yaw as WrathFlames.shape uses it: its x along (cos, -sin)
        return new Vec3d(c.x + Math.cos(seamYaw[i]) * d, c.y + 0.01, c.z - Math.sin(seamYaw[i]) * d);
    }

    private void growSeams(ServerWorld world, float u) {
        int shown = Math.round(u * SEGMENTS);
        for (int i = 0; i < SEAMS; i++) {
            for (int k = 0; k < SEGMENTS; k++) {
                DisplayEntity.BlockDisplayEntity d = seams[i][k];
                if (d == null || k >= shown || lit[i][k]) continue;
                lit[i][k] = true;
                float wide = 0.38f - 0.025f * k;
                WrathFlames.animate(d, WrathFlames.shape(seamYaw[i], 0, 0, SEGMENT, 0.05f, wide), 2);
                Vec3d at = segment(i, k + 0.5f);
                world.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 2, 0.2, 0.05, 0.2, 0.02);
            }
        }
        if (Math.round(u * SEGMENTS) % 2 == 0) world.playSound(null, kneelAt.x, kneelAt.y, kneelAt.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    /** Everyone who beat him: Defeated ORNAMIX. The sky starts lightening back to its red. */
    private void honour(ServerWorld world, OrnamixEntity boss) {
        for (ServerPlayerEntity p : boss.audience(world)) {
            if (!p.isSpectator()) boss.grantDefeated(p);
            OrnamixEntity.sky(p, 1);
        }
    }

    /** A heartbeat, slower and fainter each time; the glow pulls back towards him and his fire sinks. */
    private void heartbeat(ServerWorld world, OrnamixEntity boss, int beat) {
        float left = 1f - beat / (float) HEARTBEATS.length;
        for (ServerPlayerEntity p : boss.audience(world)) {
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 0.5f + 0.6f * left, 0.6f - beat * 0.04f);
        }
        boss.emit(world, ParticleTypes.FLAME, boss.getPos().add(0, 0.8, 0), Math.round(20 * left), 0.3, 0.5, 0.3, 0.04);
        // The outer seams go dark, a couple of rings a beat, the last ones by him on the last beat
        int rings = beat == HEARTBEATS.length - 1 ? SEGMENTS : Math.min(SEGMENTS, (beat + 1) * 2);
        for (; darkRings < rings; darkRings++) {
            int k = SEGMENTS - 1 - darkRings;
            for (int i = 0; i < SEAMS; i++) {
                DisplayEntity.BlockDisplayEntity d = seams[i][k];
                if (d == null) continue;
                d.setBlockState(k % 3 == 0 ? Blocks.BASALT.getDefaultState() : Blocks.BLACKSTONE.getDefaultState());
                Vec3d at = segment(i, k + 0.5f);
                world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 3, 0.2, 0.05, 0.2, 0.01);
            }
        }
        world.playSound(null, kneelAt.x, kneelAt.y, kneelAt.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.5f + 0.5f * left, 0.6f);
    }

    /** His fire, guttering: roaring at first, then flickering, a few small flames, then only smoke. */
    private void dyingFire(ServerWorld world, OrnamixEntity boss, Random r) {
        float f = fire();
        double w = boss.getWidth() * 0.5, h = boss.getHeight();
        if (r.nextFloat() < f) world.spawnParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + h * 0.5, boss.getZ(), 1 + Math.round(3 * f), w, h * 0.35, w, 0.01);
        if (r.nextFloat() < f * 0.6f + 0.05f) world.spawnParticles(ParticleTypes.SMALL_FLAME, boss.getX(), boss.getY() + h * 0.6, boss.getZ(), 1, w, h * 0.3, w, 0.005);
        if (t % 3 == 0 && r.nextFloat() < f) world.spawnParticles(EMBER, boss.getX(), boss.getY() + h * 0.6, boss.getZ(), 1, w, h * 0.3, w, 0);
        if (t % Math.max(1, Math.round(2 + 4 * f)) == 0) {
            world.spawnParticles(f < 0.4f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, boss.getX(), boss.getY() + h * 0.9, boss.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
        }
        if (t > KNEEL && t % 16 == 0 && f > 0.1f) world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 0.4f + f, 0.5f);
    }

    /** Crumbling from the feet up: ash pouring off him where it's reached, blown away; a pile of it at his knees. */
    private void crumble(ServerWorld world, OrnamixEntity boss, float u) {
        double h = boss.getHeight() * 0.85, line = boss.getY() + h * u, w = boss.getWidth() * 0.45;
        Random r = world.random;
        world.spawnParticles(ASH_DUST, boss.getX(), line, boss.getZ(), 10, w, 0.12, w, 0);
        world.spawnParticles(PALE_ASH, boss.getX(), line, boss.getZ(), 4, w, 0.12, w, 0);
        // Whatever's gone to ash, blown off on the wind
        for (int i = 0; i < 5; i++) {
            double y = boss.getY() + r.nextDouble() * (line - boss.getY());
            world.spawnParticles(ParticleTypes.SMOKE, boss.getX() + (r.nextDouble() - 0.5) * w * 2, y, boss.getZ() + (r.nextDouble() - 0.5) * w * 2,
                    0, wind.x, 0.15 + r.nextDouble() * 0.2, wind.z, 0.08 + r.nextDouble() * 0.06);
            world.spawnParticles(ParticleTypes.WHITE_ASH, boss.getX(), y, boss.getZ(), 2, w, 0.1, w, 0);
        }
        if (t % 4 == 0) world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.HOSTILE, 1.0f, 0.5f + u * 0.3f);
        if (t % 9 == 0) world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_SUSPICIOUS_SAND_BREAK, SoundCategory.HOSTILE, 1.0f, 0.6f);
        if (t == CRUMBLE) {
            // The seams, all dark now, sink into the sand
            for (DisplayEntity.BlockDisplayEntity[] seam : seams) {
                for (int k = 0; k < SEGMENTS; k++) {
                    if (seam[k] != null) WrathFlames.animate(seam[k], WrathFlames.shape(0, 0, 0, 0.01f, 0.001f, 0.01f), 20 + k * 3);
                }
            }
            ash.add(remains(world, Blocks.GRAY_CONCRETE_POWDER.getDefaultState()));
            ash.add(remains(world, Blocks.LIGHT_GRAY_CONCRETE_POWDER.getDefaultState()));
            ash.add(remains(world, Blocks.GRAY_CONCRETE_POWDER.getDefaultState()));
        }
        for (int i = 0; i < ash.size(); i++) {
            DisplayEntity.BlockDisplayEntity d = ash.get(i);
            if (d == null) continue;
            float size = (0.45f + 0.3f * (i % 2)) * (0.3f + 0.7f * u), tall = (i == 1 ? 0.18f : 0.3f) * (0.2f + 0.8f * u);
            float yaw = i * 1.9f + 0.4f;
            WrathFlames.animate(d, WrathFlames.shape(yaw, (i - 1) * 0.25f, 0, size, tall, size * 0.85f), 2);
        }
    }

    private DisplayEntity.BlockDisplayEntity remains(ServerWorld world, BlockState state) {
        Vec3d at = (kneelAt != null ? kneelAt : swordAt).add(0, 0.01, 0);
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState(state);
        d.setViewRange(4f);
        d.addCommandTag(REMAINS_TAG);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        d.setTransformation(WrathFlames.shape(0, 0, 0, 0.01f, 0.01f, 0.01f));
        world.spawnEntity(d);
        return d;
    }

    /** The last of him blown away; one ember left. */
    private void gone(ServerWorld world, OrnamixEntity boss) {
        double h = boss.getHeight() * 0.85, w = boss.getWidth() * 0.45;
        Vec3d c = boss.getPos().add(0, h * 0.5, 0);
        world.spawnParticles(ASH_DUST, c.x, c.y, c.z, 80, w, h * 0.45, w, 0);
        world.spawnParticles(PALE_ASH, c.x, c.y, c.z, 30, w, h * 0.45, w, 0);
        Random r = world.random;
        for (int i = 0; i < 40; i++) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x + (r.nextDouble() - 0.5) * w * 2, c.y + (r.nextDouble() - 0.5) * h, c.z + (r.nextDouble() - 0.5) * w * 2,
                    0, wind.x, 0.1 + r.nextDouble() * 0.2, wind.z, 0.1 + r.nextDouble() * 0.1);
        }
        boss.setInvisible(true);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.HOSTILE, 2.0f, 0.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 1.5f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        emberFrom = boss.getPos().add(0, boss.getHeight() * 0.8, 0);
    }

    Vec3d emberFrom;

    /** A single ember, drifting up and away on the wind, and out. */
    private void ember(ServerWorld world, float u) {
        if (emberFrom == null) return;
        Vec3d at = emberFrom.add(wind.multiply(u * 1.5)).add(0, u * 2.5 + 0.15 * Math.sin(u * 12), 0);
        if (u < 1) {
            world.spawnParticles(EMBER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        } else {
            world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.02, 0.02, 0.02, 0.005);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }
    }

    /** Taken away (he's dead, or the fight's gone): his fire goes; the sword and the ash stay. */
    void remove() {
        for (DisplayEntity.BlockDisplayEntity[] seam : seams) {
            for (int k = 0; k < SEGMENTS; k++) {
                if (seam[k] != null) seam[k].discard();
                seam[k] = null;
            }
        }
    }
}
