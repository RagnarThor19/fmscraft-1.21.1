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
    void theEntranceSlamsDownOnTheDrop() {
        assertEquals(OrnamixTimeline.DROP_SONG_TICK, OrnamixTimeline.ENTRANCE_SLAM + OrnamixTimeline.SONG_LEAD,
                "the slam is on the song's drop");
        // The drop is beat 13 of the song's own beat clock
        net.ragnar.fmscraft.boss.BossSong song = new net.ragnar.fmscraft.boss.BossSong("fmscraft:music.wrath.phase_one", 140, 1);
        assertTrue(Math.abs(song.firstBeatTick() + 13 * song.ticksPerBeat() - OrnamixTimeline.DROP_SONG_TICK) < 1);
        // The seams grow on the beats before it, all after the wheel has gone
        for (int k = 1; k <= OrnamixTimeline.CRACK_BEATS + 1; k++) {
            assertTrue(OrnamixTimeline.beatBeforeDrop(k) > 25, "beat " + k + " before the drop");
        }
        assertEquals(OrnamixTimeline.ENTRANCE_LEAP, OrnamixTimeline.beatBeforeDrop(1));
        assertEquals(0.0, OrnamixTimeline.leapHeight(OrnamixTimeline.ENTRANCE_LEAP), 1e-9);
        assertEquals(0.0, OrnamixTimeline.leapHeight(OrnamixTimeline.ENTRANCE_SLAM), 1e-9);
        double top = 0;
        for (int t = OrnamixTimeline.ENTRANCE_LEAP; t <= OrnamixTimeline.ENTRANCE_SLAM; t++) top = Math.max(top, OrnamixTimeline.leapHeight(t));
        assertTrue(top > 2.5 && top <= OrnamixTimeline.LEAP_HEIGHT, "a real leap, " + top);
    }

    @Test
    void ragemodeRisesThroughTheVoiceAndComesDownAsItEnds() {
        assertEquals(OrnamixTimeline.VOICE_AT + OrnamixTimeline.VOICE_TICKS, OrnamixTimeline.REVEAL_AT,
                "he comes down as the voice line ends");
        assertTrue(OrnamixTimeline.RESHAPE_AT < OrnamixTimeline.LIFT_AT, "he's in place before the black lifts");
        assertEquals(0.0, OrnamixTimeline.riseHeight(OrnamixTimeline.VOICE_AT), 1e-9);
        assertEquals(0.0, OrnamixTimeline.riseHeight(OrnamixTimeline.REVEAL_AT), 1e-9);
        double last = 0;
        for (int t = OrnamixTimeline.VOICE_AT; t <= OrnamixTimeline.REVEAL_AT - OrnamixTimeline.SLAM_TICKS; t++) {
            double h = OrnamixTimeline.riseHeight(t);
            assertTrue(h >= last, "only ever up while the voice plays");
            last = h;
        }
        assertEquals(OrnamixTimeline.RISE_HEIGHT, last, 1e-9);
    }
}
