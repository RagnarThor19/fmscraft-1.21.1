package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.wrath.Stirring;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StirringTest {
    @Test
    void threeNoisesWakeHer() {
        Stirring s = new Stirring();
        assertTrue(s.hear(0));
        assertTrue(s.hear(Stirring.LISTEN_TICKS));
        assertFalse(s.awake());
        assertTrue(s.hear(2L * Stirring.LISTEN_TICKS));
        assertTrue(s.awake());
    }

    @Test
    void noiseWhileSheListensIsTheSameDisturbance() {
        Stirring s = new Stirring();
        s.hear(100);
        for (long t = 101; t < 100 + Stirring.LISTEN_TICKS; t += 5) assertFalse(s.hear(t));
        assertEquals(1, s.stirs());
        assertTrue(s.hear(100 + Stirring.LISTEN_TICKS));
        assertEquals(2, s.stirs());
    }

    @Test
    void stirsFadeOneAtATimeInTheQuiet() {
        Stirring s = new Stirring();
        s.hear(0);
        s.hear(Stirring.LISTEN_TICKS);
        long last = Stirring.LISTEN_TICKS;
        s.tick(last + Stirring.FORGET_TICKS - 1);
        assertEquals(2, s.stirs());
        s.tick(last + Stirring.FORGET_TICKS);
        assertEquals(1, s.stirs());
        s.tick(last + Stirring.FORGET_TICKS + 1);
        assertEquals(1, s.stirs(), "only one at a time");
        s.tick(last + 2L * Stirring.FORGET_TICKS);
        assertEquals(0, s.stirs());
    }

    @Test
    void ongoingNoiseKeepsTheStirsFresh() {
        Stirring s = new Stirring();
        s.hear(0);
        // Doesn't stir her again (she's still listening), but it isn't quiet either
        assertFalse(s.hear(30));
        s.tick(Stirring.FORGET_TICKS + 10);
        assertEquals(1, s.stirs());
        s.tick(Stirring.FORGET_TICKS + 30);
        assertEquals(0, s.stirs());
    }
}
