package net.ragnar.fmscraft.fragment;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Vector3f;

/** Spawning fragments into the world, with a small vanilla-style burst of particles and sound. */
public final class FragmentEffects {
    private FragmentEffects() {
    }

    public static ItemStack fragments(Sin sin, int count) {
        return new ItemStack(ModItems.FRAGMENTS.get(sin), count);
    }

    /** Drops fragments as an item entity at {@code pos}. */
    public static void drop(ServerWorld world, Vec3d pos, Sin sin, int count) {
        ItemEntity item = new ItemEntity(world, pos.x, pos.y, pos.z, fragments(sin, count));
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
        burst(world, pos, sin);
    }

    /** Puts fragments straight into a player's inventory (dropped at their feet if it's full). */
    public static void give(PlayerEntity player, Sin sin, int count) {
        player.getInventory().offerOrDrop(fragments(sin, count));
        if (player.getWorld() instanceof ServerWorld world) {
            burst(world, player.getPos().add(0, 1, 0), sin);
        }
    }

    public static void burst(ServerWorld world, Vec3d pos, Sin sin) {
        int c = sin.color();
        DustParticleEffect dust = new DustParticleEffect(
                new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.2f);
        world.spawnParticles(dust, pos.x, pos.y + 0.3, pos.z, 40, 0.5, 0.5, 0.5, 0.0);
        world.spawnParticles(accentParticle(sin), pos.x, pos.y + 0.3, pos.z, 12, 0.4, 0.4, 0.4, 0.02);
        world.playSound(null, pos.x, pos.y, pos.z, sound(sin), SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.6f);
    }

    private static ParticleEffect accentParticle(Sin sin) {
        return switch (sin) {
            case GREED -> ParticleTypes.GLOW;
            case WRATH -> ParticleTypes.LAVA;
            case PRIDE -> ParticleTypes.WAX_ON;
            case GLUTTONY -> ParticleTypes.SPLASH;
            case ENVY -> ParticleTypes.COMPOSTER;
            case LUST -> ParticleTypes.WITCH;
            case SLOTH -> ParticleTypes.ASH;
        };
    }

    private static SoundEvent sound(Sin sin) {
        return switch (sin) {
            case GREED -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case WRATH -> SoundEvents.ENTITY_WARDEN_ROAR;
            case PRIDE -> SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value();
            case GLUTTONY -> SoundEvents.ENTITY_PLAYER_BURP;
            case ENVY -> SoundEvents.BLOCK_CHEST_LOCKED;
            case LUST -> SoundEvents.ENTITY_ALLAY_ITEM_GIVEN;
            case SLOTH -> SoundEvents.ENTITY_WOLF_HOWL;
        };
    }
}
