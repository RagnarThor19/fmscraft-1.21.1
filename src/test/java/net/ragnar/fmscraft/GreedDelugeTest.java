package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.greed.GreedDeluge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Deluge's orbs: hardest right where they land, less and less out to the edge at 10 blocks, nothing past it. */
class GreedDelugeTest {
    @Test
    void hardestInTheMiddle() {
        assertEquals(GreedDeluge.CENTER_DAMAGE, GreedDeluge.damageAt(0), 1e-4);
    }

    @Test
    void lessTowardsTheEdge() {
        float last = Float.MAX_VALUE;
        for (double d = 0; d <= GreedDeluge.RADIUS; d += 0.5) {
            float now = GreedDeluge.damageAt(d);
            assertTrue(now < last || d == 0, "falls off with distance at " + d);
            last = now;
        }
        assertEquals(GreedDeluge.CENTER_DAMAGE * GreedDeluge.EDGE_SHARE, GreedDeluge.damageAt(GreedDeluge.RADIUS), 1e-4);
        assertTrue(GreedDeluge.damageAt(5) < GreedDeluge.CENTER_DAMAGE * 0.6f, "halfway out is well under the middle");
    }

    @Test
    void nothingPastTheRadius() {
        assertEquals(0, GreedDeluge.damageAt(GreedDeluge.RADIUS + 0.01), 1e-6);
        assertEquals(0, GreedDeluge.damageAt(40), 1e-6);
    }

    @Test
    void throwsHarderNearTheMiddle() {
        assertTrue(GreedDeluge.throwAt(0) > GreedDeluge.throwAt(9));
        assertTrue(GreedDeluge.liftAt(0) > GreedDeluge.liftAt(9));
    }
}
