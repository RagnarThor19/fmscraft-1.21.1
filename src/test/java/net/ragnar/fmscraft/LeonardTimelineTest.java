package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.pride.LeonardTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeonardTimelineTest {
    @Test
    void heStrikesDownOnTheDrop() {
        // Grove St. Party: 126 BPM on 0.27 s, the drop at 8.37 s is beat 17
        assertEquals(8.37, LeonardTimeline.STRIKE / 20.0, 0.05, "the strike");
        // The same grid as LeonardEntity.PHASE_ONE (not loaded here: it's an entity)
        BossSong grove = new BossSong("fmscraft:music.pride.phase_one", LeonardTimeline.GROVE_BPM, LeonardTimeline.GROVE_FIRST_BEAT);
        assertEquals(17, grove.beatAt(LeonardTimeline.STRIKE), "the drop is beat 17");
        assertTrue(grove.isBeatTick(LeonardTimeline.STRIKE) || grove.isBeatTick(LeonardTimeline.STRIKE - 1)
                || grove.isBeatTick(LeonardTimeline.STRIKE + 1), "the strike lands on a beat");
        assertTrue(LeonardTimeline.RISEN_SECONDS * 20 < LeonardTimeline.STRIKE, "Munch is all the way up before he strikes");
    }

    @Test
    void tenSecondsOfNothingOnceTheWheelHasGone() {
        assertEquals(200, LeonardTimeline.SILENCE - LeonardTimeline.REVEAL_TICKS, "ten seconds of silence");
    }

    @Test
    void munchRisesWithTheSongsFirstFiveSeconds() {
        assertEquals(0, LeonardTimeline.munchRise(-1), 1e-9, "under the clouds before the song");
        assertEquals(0, LeonardTimeline.munchRise(0), 1e-9, "under the clouds as it starts");
        assertEquals(1, LeonardTimeline.munchRise(5.0), 1e-9, "risen at 5 s");
        assertEquals(1, LeonardTimeline.munchRise(30), 1e-9, "and stays up");
        double last = -1;
        for (double s = 0; s <= 5; s += 0.1) {
            double r = LeonardTimeline.munchRise(s);
            assertTrue(r >= last, "never sinks while rising, at " + s);
            last = r;
        }
        assertTrue(LeonardTimeline.munchRise(2.5) > 0.6, "fast out of the clouds, slowing near the top");
    }

    @Test
    void munchNodsDownOntoEveryBeat() {
        double beat = 60.0 / LeonardTimeline.GROVE_BPM;
        for (int k = 1; k < 20; k++) {
            double on = LeonardTimeline.GROVE_FIRST_BEAT_SECONDS + k * beat;
            assertEquals(1, LeonardTimeline.nod(on + 1e-6), 0.02, "head down on beat " + k);
            assertTrue(LeonardTimeline.nod(on + beat * 0.5) < 0.6, "coming up between beats");
        }
        assertEquals(0, LeonardTimeline.nod(0.1), 1e-9, "still before the first beat");
    }

    @Test
    void theConeLandsOnTheOrchestrasHit() {
        // The orchestra comes in at 8.70 s
        assertEquals(8.70, LeonardTimeline.CONE_HIT / 20.0, 0.05, "the hit");
        assertEquals(LeonardTimeline.CONE_DROP, LeonardTimeline.coneHeight(0), 1e-9, "high over him to start");
        assertEquals(0, LeonardTimeline.coneHeight(LeonardTimeline.CONE_HIT), 1e-9, "on his head on the hit");
        double last = Double.MAX_VALUE;
        for (int s = LeonardTimeline.CONE_APPEAR; s <= LeonardTimeline.CONE_HIT; s++) {
            double h = LeonardTimeline.coneHeight(s);
            assertTrue(h <= last, "always falling, at " + s);
            last = h;
        }
        // He's grown to five blocks before it lands, and he's down on the ground for it
        assertEquals(LeonardTimeline.CONE_SCALE, LeonardTimeline.growScale(LeonardTimeline.CONE_HIT), 1e-6, "five blocks tall");
        assertEquals(5.0, LeonardTimeline.CONE_SCALE * 1.8, 1e-4, "CONEMAN is five blocks tall");
        assertEquals(1f, LeonardTimeline.growScale(0), 1e-6, "his own size to start");
        assertEquals(0, LeonardTimeline.soarHeight(LeonardTimeline.CONE_HIT), 1e-9, "on the ground for the boom");
        assertEquals(LeonardTimeline.SOAR_HEIGHT, LeonardTimeline.soarHeight(LeonardTimeline.CONE_APPEAR), 1e-9, "up over the plaza while it falls");
        assertTrue(LeonardTimeline.GROW_END < LeonardTimeline.CONE_HIT, "grown before it lands");
        assertTrue(LeonardTimeline.CONE_APPEAR > LeonardTimeline.SOAR_END, "it appears once he's up");
    }

    @Test
    void conemansBarFillsAfterTheHit() {
        assertEquals(0f, LeonardTimeline.barFill(LeonardTimeline.CONE_HIT), 1e-6, "empty on the hit");
        assertEquals(0.5f, LeonardTimeline.barFill(LeonardTimeline.CONE_HIT + LeonardTimeline.BAR_FILL / 2), 1e-6, "half way");
        assertEquals(1f, LeonardTimeline.barFill(LeonardTimeline.CONE_HIT + LeonardTimeline.BAR_FILL + 50), 1e-6, "full");
    }

    @Test
    void everyoneWatchesFromTheWest() {
        for (int n = 1; n <= 10; n++) {
            for (int i = 0; i < n; i++) {
                double[] slot = LeonardTimeline.watchSlot(i, n);
                assertTrue(slot[0] < -0.3, "west of the middle: " + i + " of " + n);
                assertEquals(1, Math.hypot(slot[0], slot[1]), 1e-9, "a unit vector");
            }
        }
    }

    @Test
    void healthScalesLikeTheOthers() {
        assertEquals(0.6f, LeonardTimeline.healthScale(1), 1e-6);
        assertEquals(1f, LeonardTimeline.healthScale(5), 1e-6);
        assertEquals(1f, LeonardTimeline.healthScale(7), 1e-6);
        assertEquals(1.24f, LeonardTimeline.healthScale(10), 1e-6);
        assertEquals(2f, LeonardTimeline.healthScale(40), 1e-6);
    }

    @Test
    void bigGroupsGetMore() {
        assertEquals(15, LeonardTimeline.strikes(5, false), "fifteen bolts");
        assertEquals(24, LeonardTimeline.strikes(5, true), "more as CONEMAN");
        assertEquals(18, LeonardTimeline.strikes(8, false), "three more past seven players");
        assertEquals(3, LeonardTimeline.balls(1));
        assertEquals(6, LeonardTimeline.balls(30), "at most six");
    }
}
