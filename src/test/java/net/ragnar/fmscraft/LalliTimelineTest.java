package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.gluttony.GluttonyIntro;
import net.ragnar.fmscraft.boss.gluttony.LalliTimeline;
import net.ragnar.fmscraft.fight.BossFights;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LalliTimelineTest {
    @Test
    void theStrumsAreWhereTheyWereHeard() {
        // As counted by ear: 3.6, 4.1, 4.9, 5.5, 6.0, 6.8 s (measured a touch earlier from the audio)
        double[] heard = {3.6, 4.1, 4.9, 5.5, 6.0, 6.8};
        for (int i = 0; i < heard.length; i++) {
            assertEquals(heard[i], LalliTimeline.strum(i), 0.2, "strum " + i);
        }
        assertEquals(48, LalliTimeline.strums(), "eight times round");
        double last = LalliTimeline.strum(LalliTimeline.strums() - 1);
        assertTrue(last > 33 && last < 35, "they fall out at about 35 s, last at " + last);
    }

    @Test
    void theSkyTurnsAThirdOfAWayOnEveryStrumAndThenHolds() {
        double start = LalliTimeline.SKY_START;
        assertEquals(start, LalliTimeline.skyTurn(0), 1e-9, "still before the first strum");
        assertEquals(start, LalliTimeline.skyTurn(3.4), 1e-9, "still under the wheel");
        double first = LalliTimeline.strum(0);
        assertEquals(start + 0.3, LalliTimeline.skyTurn(first + LalliTimeline.TURN_SECONDS), 1e-9, "one strum: 30% of its cycle");
        double mid = LalliTimeline.skyTurn(first + LalliTimeline.TURN_SECONDS / 2);
        assertTrue(mid > start && mid < start + 0.3, "swinging round, not jumping");
        assertEquals(start + 0.6, LalliTimeline.skyTurn(LalliTimeline.strum(1) + LalliTimeline.TURN_SECONDS), 1e-9, "two strums");
        double end = start + 48 * 0.3;
        assertEquals(end, LalliTimeline.skyTurn(36), 1e-9, "all 48 by 36 s");
        assertEquals(end, LalliTimeline.skyTurn(120), 1e-9, "and then it holds still");
        // Never goes back
        double prev = -1;
        for (double s = 0; s < 40; s += 0.01) {
            double turn = LalliTimeline.skyTurn(s);
            assertTrue(turn >= prev - 1e-12, "turns one way only, at " + s);
            prev = turn;
        }
    }

    @Test
    void theSongStartsForTheLastOfTheWheelAndEveryoneLandsOnTime() {
        int join = BossFights.JOIN_TICKS;
        assertEquals(join - 70, GluttonyIntro.musicStart(join), "3.5 s before the portal closes");
        int start = join - GluttonyIntro.musicStart(join);
        assertEquals(70, start);
        assertEquals(500, LalliTimeline.landTick(start), "players land 25 s into the song");
        assertEquals(400, LalliTimeline.landTick(start) - LalliTimeline.APPEAR_BEFORE, "he appears at 20 s");
        assertEquals(700, LalliTimeline.BOSS_LANDS, "and lands at 35 s, as the strums fall out");
        // A short intro (tests): never a float shorter than MIN_FALL
        assertEquals(600 + LalliTimeline.MIN_FALL, LalliTimeline.landTick(600), "a fight starting late still floats down");
    }

    @Test
    void theFloatDownStartsHighAndSettlesOnTheGround() {
        int start = 70, land = 500;
        assertEquals(LalliTimeline.SKY_HEIGHT, LalliTimeline.floatHeight(start, start, land), 1e-9);
        assertEquals(0, LalliTimeline.floatHeight(land, start, land), 1e-9);
        double prev = Double.MAX_VALUE;
        for (int t = start; t <= land; t++) {
            double h = LalliTimeline.floatHeight(t, start, land);
            assertTrue(h <= prev + 1e-9, "only ever down");
            prev = h;
        }
        assertTrue(LalliTimeline.floatHeight(land - 5, start, land) < 0.5, "settling softly at the end");
        assertEquals(LalliTimeline.BOSS_HEIGHT, LalliTimeline.bossHeight(400, 400, 700), 1e-9, "he appears high");
        assertTrue(LalliTimeline.bossHeight(650, 400, 700) > 30, "still well up 2.5 s before he lands");
        assertEquals(0, LalliTimeline.bossHeight(700, 400, 700), 1e-9, "and crashes down at 35 s");
    }

    @Test
    void theSwitchUpIsOnTheBeat() {
        assertEquals(LalliTimeline.SWITCH_TICK, Math.round(LalliTimeline.SWITCH_SECONDS * 20), "26 s in ticks");
        BossSong sirens = new BossSong("test", LalliTimeline.SIRENS_BPM, LalliTimeline.SWITCH_TICK);
        assertTrue(sirens.isBeatTick(LalliTimeline.SWITCH_TICK), "the first beat of phase 2 is the switch-up");
        assertTrue(sirens.beatAt(LalliTimeline.SWITCH_TICK - 1) < 0, "nothing on the beat before it");
        assertTrue(LalliTimeline.WHITE_AT < LalliTimeline.MOVE_AT && LalliTimeline.MOVE_AT < LalliTimeline.SWITCH_TICK,
                "white, then moved under the white, then the reveal");
        assertTrue(LalliTimeline.WHITE_AT + LalliTimeline.WHITE_FADE <= LalliTimeline.MOVE_AT, "the white is full before anyone moves");
    }

    @Test
    void theStatueNodsOnEveryBeatAfterTheSwitchUpOnly() {
        assertEquals(0, LalliTimeline.nod(10), 1e-9, "still in the opening");
        assertEquals(0, LalliTimeline.nod(25.9), 1e-9, "still just before the switch-up");
        double beat = 60.0 / LalliTimeline.SIRENS_BPM;
        for (int k = 0; k < 40; k++) {
            double t = LalliTimeline.SWITCH_SECONDS + k * beat;
            assertEquals(1, LalliTimeline.nod(t), 1e-6, "head down on beat " + k);
            assertTrue(LalliTimeline.nod(t + beat * 0.72) < 0.01, "and up between, beat " + k);
        }
        assertEquals(0, LalliTimeline.nod(LalliTimeline.NOD_END + 0.5), 1e-9, "still once the song runs out");
        assertEquals(1, LalliTimeline.nod(LalliTimeline.SIRENS_SECONDS + LalliTimeline.SWITCH_SECONDS + 1e-6), 1e-4, "and again when it loops");
    }

    @Test
    void healthAndCrowdsScale() {
        assertEquals(0.6f, LalliTimeline.healthScale(1), 1e-6);
        assertEquals(1f, LalliTimeline.healthScale(6), 1e-6);
        assertEquals(1.24f, LalliTimeline.healthScale(10), 1e-6);
        assertEquals(2f, LalliTimeline.healthScale(40), 1e-6);
        assertEquals(1, LalliTimeline.perPlayer(1, 6));
        assertEquals(6, LalliTimeline.perPlayer(10, 6));
        assertTrue(LalliTimeline.ringRadius(12) * 2 * Math.PI / 12 >= 4 - 1e-9, "a ring of 12 isn't crowded");
    }
}
