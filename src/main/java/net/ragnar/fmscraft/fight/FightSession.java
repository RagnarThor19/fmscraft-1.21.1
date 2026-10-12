package net.ragnar.fmscraft.fight;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.ragnar.fmscraft.sin.Sin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One attempt at a boss: from the key opening the portal until everyone is back out. */
public class FightSession {
    public enum Stage {
        /** Portal is open; players step through and wait in the arena. */
        JOINING,
        /** Portal closed, boss is up. Any death or disconnect ends the attempt. */
        ACTIVE,
        /** Boss is dead; the exit portal is open until everyone has left. */
        WON
    }

    public final Sin sin;
    public final GlobalPos portal;
    public final Direction.Axis axis;
    public final long openedTick;
    public final int joinTicks;
    public final Set<UUID> participants = new LinkedHashSet<>();
    final ServerBossBar countdown = new ServerBossBar(Text.empty(), BossBar.Color.WHITE, BossBar.Style.PROGRESS);

    Stage stage = Stage.JOINING;
    UUID bossId;

    FightSession(Sin sin, GlobalPos portal, Direction.Axis axis, long openedTick, int joinTicks) {
        this.sin = sin;
        this.portal = portal;
        this.axis = axis;
        this.openedTick = openedTick;
        this.joinTicks = joinTicks;
    }

    public Stage stage() {
        return stage;
    }

    public UUID bossId() {
        return bossId;
    }

    /** True while a death or disconnect would end the attempt. */
    public boolean isLive() {
        return stage == Stage.JOINING || stage == Stage.ACTIVE;
    }
}
