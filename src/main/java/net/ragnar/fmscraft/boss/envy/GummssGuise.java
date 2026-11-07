package net.ragnar.fmscraft.boss.envy;

import java.util.UUID;

/**
 * Client side: whose face GUMMSS has taken, if anyone's, so that player can be drawn as him (PlayerEntityRendererMixin).
 * GUMMSS reports it every tick he's ticking on the client; a report more than a few ticks old counts for nothing, so
 * nobody is left wearing his face once he's gone.
 */
public final class GummssGuise {
    private static UUID player;
    private static int stage = GummssLook.GUISE_OFF;
    private static int bossId = -1;
    private static long seenAt;

    private GummssGuise() {
    }

    public static synchronized void report(int boss, UUID wearer, int guise, long time) {
        if (wearer == null || guise == GummssLook.GUISE_OFF) {
            if (boss == bossId) player = null;
            return;
        }
        player = wearer;
        stage = guise;
        bossId = boss;
        seenAt = time;
    }

    /** How {@code id} should be drawn at world time {@code now}: GUISE_OFF unless GUMMSS has their face right now. */
    public static synchronized int stageOf(UUID id, long now) {
        if (player == null || !player.equals(id) || Math.abs(now - seenAt) > 4) return GummssLook.GUISE_OFF;
        return stage;
    }

    /** The GUMMSS whose face they wear (his entity id), for his staff. */
    public static synchronized int bossId() {
        return bossId;
    }
}
