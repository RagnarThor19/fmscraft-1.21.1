package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.envy.GummssTimeline;
import net.ragnar.fmscraft.fight.BossFights;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GummssTimelineTest {
    @Test
    void theDropIsOnTheBeat() {
        assertEquals(GummssTimeline.DROP_TICK, Math.round(GummssTimeline.DROP_SECONDS * 20), "21.575 s in ticks");
        BossSong song = new BossSong("test", GummssTimeline.BPM, GummssTimeline.FIRST_BEAT_TICK);
        assertTrue(song.isBeatTick(GummssTimeline.DROP_TICK), "a beat starts as the bass drops");
        assertEquals(32, song.beatAt(GummssTimeline.DROP_TICK));
        assertEquals(40.0 / 3, song.ticksPerBeat(), 1e-9, "90 BPM");
    }

    @Test
    void everyoneLandsAsTheBassDrops() {
        int join = BossFights.JOIN_TICKS;
        int songAtFightStart = join - GummssTimeline.musicStart(join);
        assertEquals(418, GummssTimeline.musicStart(join), "the song starts 9.1 s before the portal closes, during the zoom");
        int fall = GummssTimeline.fallTicks(songAtFightStart);
        assertEquals(GummssTimeline.FALL_TICKS, fall);
        assertEquals(GummssTimeline.DROP_TICK, songAtFightStart + GummssTimeline.HOVER_TICKS + fall, "they land on the drop");
    }

    @Test
    void aShortIntroStillGetsAFall() {
        assertEquals(0, GummssTimeline.musicStart(40), "the song can't start before the portal opens");
        assertEquals(GummssTimeline.DROP_TICK - 40 - GummssTimeline.HOVER_TICKS, GummssTimeline.fallTicks(40), "still lands on the drop");
        assertEquals(GummssTimeline.MIN_FALL, GummssTimeline.fallTicks(GummssTimeline.DROP_TICK), "never shorter than this");
        assertEquals(GummssTimeline.MIN_FALL, GummssTimeline.fallTicks(5000));
    }

    @Test
    void theFallStartsSlowAndHitsTheGroundMoving() {
        int length = GummssTimeline.FALL_TICKS;
        double h = GummssTimeline.FALL_HEIGHT;
        assertEquals(h, GummssTimeline.fallHeight(0, length, h), 1e-9);
        assertEquals(0, GummssTimeline.fallHeight(length, length, h), 1e-9);
        double start = h - GummssTimeline.fallHeight(1, length, h);
        double end = GummssTimeline.fallHeight(length - 1, length, h);
        assertTrue(start < 0.05, "drifting at first, " + start);
        assertTrue(end > 0.4, "still falling fast at the end, " + end);
        for (int t = 1; t <= length; t++) {
            assertTrue(GummssTimeline.fallHeight(t, length, h) < GummssTimeline.fallHeight(t - 1, length, h), "always going down");
        }
    }

    @Test
    void theRingSpreadsEveryoneEvenlyRoundHim() {
        for (int n = 1; n <= 7; n++) {
            for (int i = 0; i < n; i++) {
                double[] a = GummssTimeline.ringSlot(i, n);
                assertEquals(1, Math.hypot(a[0], a[1]), 1e-9);
                for (int j = i + 1; j < n; j++) {
                    double[] b = GummssTimeline.ringSlot(j, n);
                    assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) > 0.8, "slots " + i + " and " + j + " of " + n + " too close");
                }
            }
        }
        double[] first = GummssTimeline.ringSlot(0, 3);
        assertEquals(1, first[1], 1e-9, "the first to the south, where they arrive");
    }

    @Test
    void healthScalesWithTheGroup() {
        assertEquals(0.6f, GummssTimeline.healthScale(1), 1e-6);
        assertEquals(1f, GummssTimeline.healthScale(5), 1e-6);
        assertEquals(1f, GummssTimeline.healthScale(7), 1e-6);
        assertEquals(1.24f, GummssTimeline.healthScale(10), 1e-6, "more of you, more of him");
        assertEquals(2f, GummssTimeline.healthScale(40), 1e-6, "at most double");
        for (int n = 1; n < 30; n++) assertTrue(GummssTimeline.healthScale(n + 1) >= GummssTimeline.healthScale(n));
    }

    @Test
    void theRingWidensSoABigGroupIsntCrowded() {
        assertEquals(GummssTimeline.RING_RADIUS, GummssTimeline.ringRadius(1), 1e-9);
        assertEquals(GummssTimeline.RING_RADIUS, GummssTimeline.ringRadius(10), 1e-9, "ten still fit");
        for (int n = 2; n <= 24; n++) {
            double r = GummssTimeline.ringRadius(n);
            double[] a = GummssTimeline.ringSlot(0, n), b = GummssTimeline.ringSlot(1, n);
            assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) * r > 3.4, n + " players: neighbours too close");
            assertTrue(r + 1 < 40, "and the ring stays well over the floor");
        }
    }
}
