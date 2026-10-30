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
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Template every sin boss extends. Handles the boss bar, the phase switch (at half health unless
 * {@link #phaseTwoHealth} says otherwise), the beat clock, starting/switching the music and dropping the soul.
 * Subclasses describe their attacks via {@link #onBeat} and {@link #tickPhase}, and register in {@link ModBossEntities}.
 */
public abstract class SinBossEntity extends HostileEntity {
    private final ServerBossBar bossBar = new ServerBossBar(Text.empty(), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
    private Sin sin = Sin.GREED;
    private int phase = 1;
    /** The song the beat clock follows; null while no song is playing. */
    private BossSong song;
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
        bossBar.setName(bossBarName());
    }

    /** What the boss bar says. The friend's name unless the boss goes by something else. */
    protected Text bossBarName() {
        return Text.literal(sin.bossName());
    }

    protected ServerBossBar bossBar() {
        return bossBar;
    }

    /** Phase 2 starts once health drops to this fraction of max health. */
    protected float phaseTwoHealth() {
        return 0.5f;
    }

    /**
     * Whether the phase 2 song starts the moment phase 2 does. Bosses with a transition return false and start it
     * themselves with {@link #playSong} when they're ready; the beat clock is silent until then.
     */
    protected boolean startsPhaseTwoSongAtOnce() {
        return true;
    }

    /** Starts {@code song} for everyone in the fight (fading in over {@code fadeTicks}) and restarts the beat clock on it. */
    protected void playSong(BossSong song, int fadeTicks) {
        this.song = song;
        songTicks = 0;
        musicStarted = true;
        BossFights.onSongChange(this, song, fadeTicks);
    }

    /** Stops the music; the beat clock goes quiet until the next {@link #playSong}. */
    protected void stopSong() {
        song = null;
        musicStarted = true;
        BossFights.onSongChange(this, null, 0);
    }

    /** The fight started {@code song} before the boss spawned (an intro), {@code ticksPlayed} ago: keep the beat on it. */
    public void continueSong(BossSong song, int ticksPlayed) {
        this.song = song;
        songTicks = ticksPlayed;
        musicStarted = true;
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
        if (!musicStarted) playSong(songForPhase(1), 0);
        songTicks++;

        if (phase == 1 && getHealth() <= getMaxHealth() * phaseTwoHealth()) {
            phase = 2;
            BossSong newSong = songForPhase(2);
            if (!startsPhaseTwoSongAtOnce()) song = null;
            else if (!newSong.equals(song)) playSong(newSong, 0);
            onPhaseTwo();
        }

        if (song != null && song.isBeatTick(songTicks)) onBeat(phase, song.beatAt(songTicks));
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
    public void tick() {
        super.tick();
        // Only the boss of the fight that's running belongs in an arena. One left over from before a crash (or
        // anything else that slipped past the arena reset) leaves, so there's never a second one.
        if (!getWorld().isClient && age % 20 == 0 && Arenas.sinOf(getWorld()) != null && !BossFights.isCurrentBoss(this)) {
            discard();
        }
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
