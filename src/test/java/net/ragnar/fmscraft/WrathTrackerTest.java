package net.ragnar.fmscraft;

import net.ragnar.fmscraft.fragment.WrathTracker;
import net.ragnar.fmscraft.fragment.WrathTracker.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrathTrackerTest {
    private static final int WINDOW = WrathTracker.WINDOW_TICKS;

    @Test
    void threeKillsInTimeComplete() {
        WrathTracker t = new WrathTracker();
        assertEquals(Result.STARTED, t.onKill(100));
        assertEquals(Result.COUNTED, t.onKill(1000));
        assertEquals(Result.COMPLETED, t.onKill(100 + WINDOW - 1));
        assertFalse(t.isActive());
    }

    @Test
    void windowIsFourMinutesFromTheFirstKill() {
        assertEquals(4 * 60 * 20, WINDOW);
        WrathTracker t = new WrathTracker();
        t.onKill(0);
        t.onKill(10);
        assertEquals(WINDOW, t.ticksLeft(0));
        assertEquals(WINDOW - 10, t.ticksLeft(10)); // later kills don't extend the clock
    }

    @Test
    void lateThirdKillStartsANewRun() {
        WrathTracker t = new WrathTracker();
        t.onKill(0);
        t.onKill(10);
        assertEquals(Result.STARTED, t.onKill(WINDOW));
        assertEquals(1, t.kills());
    }

    @Test
    void expiryFiresOnceAndResets() {
        WrathTracker t = new WrathTracker();
        t.onKill(0);
        assertFalse(t.checkExpired(WINDOW - 1));
        assertTrue(t.checkExpired(WINDOW));
        assertFalse(t.isActive());
        assertFalse(t.checkExpired(WINDOW + 1));
    }

    @Test
    void canBeDoneAgainAfterCompleting() {
        WrathTracker t = new WrathTracker();
        t.onKill(0);
        t.onKill(1);
        t.onKill(2);
        assertEquals(Result.STARTED, t.onKill(3));
    }
}
