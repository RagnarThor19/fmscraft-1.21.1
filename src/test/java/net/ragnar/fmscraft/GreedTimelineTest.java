package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.greed.GreedTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedTimelineTest {
    private static final int INTRO = 30 * 20;

    @Test
    void introZoomsInOnGreedHalfwayAndMusicComesInAt25Seconds() {
        assertEquals(15 * 20 + 20, GreedTimeline.zoomStart(INTRO), "the zoom starts about 15 s in");
        assertEquals(0f, GreedTimeline.zoom(0, INTRO));
        assertEquals(0f, GreedTimeline.zoom(GreedTimeline.zoomStart(INTRO), INTRO));
        assertEquals(1f, GreedTimeline.zoom(INTRO - GreedTimeline.ZOOM_HOLD, INTRO), 1e-6);
        assertEquals(1f, GreedTimeline.zoom(INTRO, INTRO), 1e-6);
        assertEquals(25 * 20, GreedTimeline.musicStart(INTRO), "the song starts fading in at 25 s");
    }

    @Test
    void zoomOnlyEverMovesForward() {
        float last = 0;
        for (int t = 0; t <= INTRO; t++) {
            float z = GreedTimeline.zoom(t, INTRO);
            assertTrue(z >= last && z <= 1f, "zoom at " + t);
            last = z;
        }
    }

    @Test
    void shortIntrosStillWork() {
        // Game tests open the portal for a couple of seconds
        assertEquals(0, GreedTimeline.musicStart(40));
        assertEquals(0, GreedTimeline.zoomStart(40));
        assertEquals(1f, GreedTimeline.zoom(40, 40), 1e-6);
    }

    @Test
    void phaseChangeRunsInOrder() {
        assertTrue(GreedTimeline.BLACKOUT_FADE < GreedTimeline.VOICE_AT, "black before the voice");
        assertEquals(5 * 20, GreedTimeline.VOICE_AT, "the voice line after about 5 s of dark");
        assertTrue(GreedTimeline.VOICE_AT < GreedTimeline.RESHAPE_AT && GreedTimeline.RESHAPE_AT < GreedTimeline.REVEAL_AT,
                "he changes while it's still black");
        assertTrue(GreedTimeline.REVEAL_AT + GreedTimeline.REVEAL_TICKS >= GreedTimeline.SONG_AT,
                "the black is (nearly) gone when the song comes in");
        assertTrue(GreedTimeline.SONG_AT >= GreedTimeline.VOICE_AT + 200, "the 10 s voice line finishes first");
    }

    @Test
    void leapStartsAndEndsOnTheGround() {
        assertEquals(0, GreedTimeline.leapHeight(0), 1e-9);
        assertEquals(0, GreedTimeline.leapHeight(GreedTimeline.LEAP_TICKS), 1e-9);
        assertTrue(GreedTimeline.leapHeight(GreedTimeline.LEAP_TICKS / 2) > 15, "a big arc");
    }
}
