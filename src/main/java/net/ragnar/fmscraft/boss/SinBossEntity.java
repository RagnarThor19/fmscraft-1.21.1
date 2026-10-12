package net.ragnar.fmscraft.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Template every sin boss extends. Handles the boss bar, the phase switch at half health, the beat clock,
 * starting/switching the music and dropping the soul. Subclasses describe their attacks via {@link #onBeat}
 * and {@link #tickPhase}, and register in {@link ModBossEntities}.
 */
public abstract class SinBossEntity extends HostileEntity {
    private final ServerBossBar bossBar = new ServerBossBar(Text.empty(), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
    private Sin sin = Sin.GREED;
    private int phase = 1;
    private int songTicks;
    private boolean musicStarted;

    protected SinBossEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 500;
    }

    public Sin getSin() {
        return sin;
    }

    public void setSin(Sin sin) {
        this.sin = sin;
        bossBar.setName(Text.literal(sin.bossName()));
    }

    /** Song for the given phase (1 or 2). Return the same song for both if the boss doesn't switch music. */
    protected abstract BossSong songForPhase(int phase);

    /** Called on the tick each beat lands. Put on-beat attacks here. */
    protected abstract void onBeat(int phase, int beat);

    /** Called every server tick for continuous behaviour. */
    protected void tickPhase(int phase, int songTicks) {
    }

    /** Called once when health drops to half. Change the arena, summon things, etc. */
    protected void onPhaseTwo() {
    }

    public int getPhase() {
        return phase;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!musicStarted) {
            musicStarted = true;
            BossFights.onSongChange(this, songForPhase(1));
        }
        songTicks++;

        if (phase == 1 && getHealth() <= getMaxHealth() / 2) {
            phase = 2;
            BossSong newSong = songForPhase(2);
            if (!newSong.equals(songForPhase(1))) {
                songTicks = 0;
                BossFights.onSongChange(this, newSong);
            }
            onPhaseTwo();
        }

        BossSong song = songForPhase(phase);
        if (song.isBeatTick(songTicks)) onBeat(phase, song.beatAt(songTicks));
        tickPhase(phase, songTicks);

        bossBar.setPercent(getHealth() / getMaxHealth());
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!getWorld().isClient) {
            dropStack(new ItemStack(ModItems.SOULS.get(sin)));
            BossFights.onBossDied(this);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Sin", sin.id());
        nbt.putInt("Phase", phase);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        Sin saved = Sin.byId(nbt.getString("Sin"));
        if (saved != null) setSin(saved);
        if (nbt.contains("Phase")) phase = nbt.getInt("Phase");
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
