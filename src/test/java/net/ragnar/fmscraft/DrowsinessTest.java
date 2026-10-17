package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.sloth.Drowsiness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrowsinessTest {
    private static int ticksUntilAsleep(boolean still) {
        Drowsiness d = new Drowsiness();
        for (int t = 1; t < 10 * 60 * 20; t++) if (d.tick(still, false)) return t;
        return -1;
    }

    @Test
    void standingStillPutsYouToSleepThreeTimesFaster() {
        int moving = ticksUntilAsleep(false), still = ticksUntilAsleep(true);
        assertEquals(Drowsiness.MOVING_TICKS, moving, 2);
        assertEquals(Drowsiness.STILL_TICKS, still, 2);
    }

    @Test
    void itGetsWorseInStagesAndYouWakeUpHalfRested() {
        Drowsiness d = new Drowsiness();
        assertEquals(-1, d.slowness());
        d.set(Drowsiness.SLOWNESS);
        assertEquals(0, d.slowness());
        assertFalse(d.nightmares());
        d.set(Drowsiness.NIGHTMARES);
        assertEquals(1, d.slowness());
        assertTrue(d.nightmares());
        assertFalse(d.darkness());
        d.set(Drowsiness.DARKNESS);
        assertTrue(d.darkness());

        d.set(Drowsiness.FULL - 0.001f);
        assertTrue(d.tick(true, false), "falls asleep when it's full");
        assertEquals(Drowsiness.AFTER_NAP, d.value());
    }

    @Test
    void theCoreLetsItDrainAndABellWakesYouRightUp() {
        Drowsiness d = new Drowsiness();
        d.set(80);
        for (int t = 0; t < Drowsiness.RESTING_TICKS; t++) assertFalse(d.tick(true, true));
        assertEquals(0, d.value(), 0.001);
        d.set(95);
        d.wake();
        assertEquals(0, d.value());
    }
}
