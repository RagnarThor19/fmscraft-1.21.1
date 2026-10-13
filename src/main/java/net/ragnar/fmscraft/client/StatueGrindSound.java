package net.ragnar.fmscraft.client;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.ragnar.fmscraft.entity.EnvyStatueEntity;

/**
 * The low rumble of stone grinding over stone that follows the statue around: a minecart's rolling sound pitched
 * way down. Louder while sliding, softer and deeper while it turns.
 */
public class StatueGrindSound extends MovingSoundInstance {
    private final EnvyStatueEntity statue;

    public StatueGrindSound(EnvyStatueEntity statue) {
        super(SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.HOSTILE, SoundInstance.createRandom());
        this.statue = statue;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.0f;
        this.pitch = 0.42f;
        this.x = statue.getX();
        this.y = statue.getY() + 1;
        this.z = statue.getZ();
    }

    @Override
    public boolean canPlay() {
        return !statue.isSilent();
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }

    @Override
    public void tick() {
        if (statue.isRemoved()) {
            setDone();
            return;
        }
        x = statue.getX();
        y = statue.getY() + 1;
        z = statue.getZ();
        int state = statue.getState();
        float target = state == EnvyStatueEntity.STATE_SLIDING ? 1.4f : state == EnvyStatueEntity.STATE_TURNING ? 0.9f : 0f;
        volume += (target - volume) * 0.2f;
        pitch = state == EnvyStatueEntity.STATE_TURNING ? 0.3f : 0.42f;
    }
}
