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
        assertTrue(GreedTimeline.SKY_AT < GreedTimeline.WAVE_AT, "the sky turns before the wave");
        assertTrue(GreedTimeline.WAVE_AT < GreedTimeline.WAVE_HIT && GreedTimeline.WAVE_HIT < GreedTimeline.RESHAPE_AT,
                "the wave reaches everyone, then (under the water) they're carried off");
        assertTrue(GreedTimeline.RESHAPE_AT < GreedTimeline.SURFACE_AT, "carried off before the water lifts");
        assertTrue(GreedTimeline.SURFACE_AT + GreedTimeline.SURFACE_TICKS <= GreedTimeline.SONG_AT, "the garden is in view before the burst");
        assertTrue(GreedTimeline.SONG_AT >= GreedTimeline.VOICE_AT + GreedTimeline.VOICE_TICKS, "the 10 s voice line finishes first");
        assertTrue(GreedTimeline.TRANSITION_END > GreedTimeline.SONG_AT);
    }

    @Test
    void theSpoutLiftsHimSmoothlyThenFlingsHimUp() {
        assertEquals(0, GreedTimeline.spoutLift(0), 1e-9);
        double last = 0;
        for (int t = 1; t < GreedTimeline.SONG_AT; t++) {
            double h = GreedTimeline.spoutLift(t);
            assertTrue(h >= last - 1e-9, "only ever up, at " + t);
            last = h;
        }
        assertEquals(GreedTimeline.SPOUT_LIFT, GreedTimeline.spoutLift(GreedTimeline.SPOUT_TICKS), 1e-9);
        assertEquals(0, GreedTimeline.spoutLift(GreedTimeline.SONG_AT), 1e-9, "down on the burst");
    }

    @Test
    void healthScalesWithTheGroupLikeTheOthers() {
        assertEquals(0.6f, GreedTimeline.healthScale(1), 1e-6);
        assertEquals(1f, GreedTimeline.healthScale(5), 1e-6);
        assertEquals(1f, GreedTimeline.healthScale(7), 1e-6);
        assertEquals(1.24f, GreedTimeline.healthScale(10), 1e-6, "more of you, more of him");
        assertEquals(2f, GreedTimeline.healthScale(40), 1e-6, "at most double");
        for (int n = 1; n < 30; n++) assertTrue(GreedTimeline.healthScale(n + 1) >= GreedTimeline.healthScale(n));
    }
}
