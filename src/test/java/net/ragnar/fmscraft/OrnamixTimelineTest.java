package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.wrath.OrnamixTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrnamixTimelineTest {
    @Test
    void theWorldBlinksOnTheBassFrom19Point25SecondsEvery0Point6() {
        assertEquals(0f, OrnamixTimeline.blink(0));
        assertEquals(0f, OrnamixTimeline.blink(19.2), "nothing before the bass comes in");
        assertEquals(OrnamixTimeline.BLINK_DEPTH, OrnamixTimeline.blink(19.25), 1e-4, "the first hit");
        assertTrue(OrnamixTimeline.blink(19.30) > 0, "fading");
        assertEquals(0f, OrnamixTimeline.blink(19.55), "back to normal between hits");
        assertEquals(OrnamixTimeline.BLINK_DEPTH, OrnamixTimeline.blink(19.85), 1e-4, "the next hit, 0.6 s later");
        assertEquals(OrnamixTimeline.BLINK_DEPTH, OrnamixTimeline.blink(19.25 + 50 * 0.6), 1e-4);
    }

    @Test
    void itRestsWhileTheBassDropsOut() {
        assertTrue(OrnamixTimeline.blink(19.25 + 66 * 0.6) > 0, "the last hit before the break, 58.85 s");
        assertEquals(0f, OrnamixTimeline.blink(19.25 + 70 * 0.6), "the break");
        assertEquals(0f, OrnamixTimeline.blink(70.0));
        assertTrue(OrnamixTimeline.blink(76.85) > 0, "the bass comes back at 76.85 s, on the same grid");
        assertTrue(OrnamixTimeline.blink(19.25 + 162 * 0.6) > 0, "its last hit");
        assertEquals(0f, OrnamixTimeline.blink(19.25 + 165 * 0.6), "and quiet to the end");
    }

    @Test
    void whenTheSongLoopsTheBlinksWaitFor19Point25SecondsAgain() {
        double loop = OrnamixTimeline.SONG_SECONDS;
        assertEquals(0f, OrnamixTimeline.blink(loop + 5), "no blinking at the start of the second time round");
        assertEquals(0f, OrnamixTimeline.blink(loop + 19.0));
        assertEquals(OrnamixTimeline.BLINK_DEPTH, OrnamixTimeline.blink(loop + 19.25), 1e-4);
        assertEquals(-1, OrnamixTimeline.bassHit(loop + 1));
    }

    @Test
    void healthIsFullForFiveToSevenAndLessForSmallGroups() {
        assertEquals(1f, OrnamixTimeline.healthScale(5), 1e-6);
        assertEquals(1f, OrnamixTimeline.healthScale(6), 1e-6);
        assertEquals(1f, OrnamixTimeline.healthScale(7), 1e-6);
        assertEquals(0.6f, OrnamixTimeline.healthScale(1), 1e-6);
        assertTrue(OrnamixTimeline.healthScale(2) < OrnamixTimeline.healthScale(3));
    }

    @Test
    void theEntranceComesDownSmoothlyAndLandsOnTime() {
        assertEquals(34.0, OrnamixTimeline.entranceHeight(0, 34), 1e-9);
        assertEquals(0.0, OrnamixTimeline.entranceHeight(OrnamixTimeline.ENTRANCE_FALL, 34), 1e-9);
        double last = 34;
        for (int t = 1; t <= OrnamixTimeline.ENTRANCE_FALL; t++) {
            double h = OrnamixTimeline.entranceHeight(t, 34);
            assertTrue(h <= last, "only ever down");
            last = h;
        }
        assertEquals(OrnamixTimeline.VOICE_AT + OrnamixTimeline.VOICE_TICKS, OrnamixTimeline.REVEAL_AT,
                "the black lifts as the voice line ends");
    }
}
