package net.ragnar.fmscraft.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Template every sin boss extends. Handles the boss bar, the phase switch at half health and the beat clock;
 * subclasses only describe their attacks via {@link #onBeat} / {@link #tickPhase}.
 *
 * Not registered as an entity yet - each concrete boss gets registered (with a renderer) once it's designed.
 */
public abstract class SinBossEntity extends HostileEntity {
    private final ServerBossBar bossBar;
    private int phase = 1;
    private int songTicks;

    protected SinBossEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.bossBar = new ServerBossBar(Text.literal(getSin().bossName()), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        this.experiencePoints = 500;
    }

    public abstract Sin getSin();

    /** Song for the given phase (1 or 2). Return the same song for both if the boss doesn't switch music. */
    protected abstract BossSong songForPhase(int phase);

    /** Called on the tick each beat lands. Put on-beat attacks here. */
    protected abstract void onBeat(int phase, int beat);

    /** Called every server tick for continuous behaviour. */
    protected void tickPhase(int phase, int songTicks) {
    }

    /** Called once when health drops to half. Restart music, change the arena, etc. */
    protected void onPhaseTwo() {
    }

    public int getPhase() {
        return phase;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        songTicks++;

        if (phase == 1 && getHealth() <= getMaxHealth() / 2) {
            phase = 2;
            songTicks = 0;
            onPhaseTwo();
        }

        BossSong song = songForPhase(phase);
        if (song.isBeatTick(songTicks)) onBeat(phase, song.beatAt(songTicks));
        tickPhase(phase, songTicks);

        bossBar.setPercent(getHealth() / getMaxHealth());
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
