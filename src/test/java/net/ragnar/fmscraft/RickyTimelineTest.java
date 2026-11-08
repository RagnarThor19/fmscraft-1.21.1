package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.sloth.RickyTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RickyTimelineTest {
    @Test
    void heWakesOnOverseasDrop() {
        // overseas: 151.4 BPM on 0.187 s, the beat drops at 25.6 s (beat 64)
        assertEquals(25.6, RickyTimeline.DROP / 20.0, 0.05, "the drop");
        BossSong overseas = new BossSong("fmscraft:music.sloth.phase_one", RickyTimeline.OVERSEAS_BPM, RickyTimeline.OVERSEAS_FIRST_BEAT);
        assertEquals(64, overseas.beatAt(RickyTimeline.DROP), "the drop is beat 64");
        // He sleeps, stirs, sits up, stands and gathers the dark, in that order, all before the drop
        assertTrue(RickyTimeline.REVEAL_TICKS < RickyTimeline.STIR, "asleep when the wheel fades");
        assertTrue(RickyTimeline.STIR < RickyTimeline.SIT && RickyTimeline.SIT < RickyTimeline.STAND
                && RickyTimeline.STAND < RickyTimeline.GATHER && RickyTimeline.GATHER < RickyTimeline.DROP, "in order");
    }

    @Test
    void killsDropsAsTheTransformationBursts() {
        // Kills: 74.92 BPM on 0.233 s; the slow intro breaks into the beat at 17.05 s (beat 21)
        assertEquals(17.05, RickyTimeline.KILLS_DROP / 20.0, 0.05, "the drop");
        BossSong kills = new BossSong("fmscraft:music.sloth.phase_two", RickyTimeline.KILLS_BPM, RickyTimeline.KILLS_FIRST_BEAT);
        // (the grid's start is rounded to a whole tick, so beat 21 comes within a tick of it)
        assertEquals(21, kills.beatAt(RickyTimeline.KILLS_DROP + 1), "the drop is beat 21");
        assertTrue(kills.isBeatTick(RickyTimeline.KILLS_DROP) || kills.isBeatTick(RickyTimeline.KILLS_DROP + 1), "the drop lands on a beat");
        // The power-up sound (8.19 s) ends right on the drop
        assertEquals(8.19, RickyTimeline.POWERUP_TICKS / 20.0, 0.05, "the power-up's length");
        assertEquals(RickyTimeline.KILLS_DROP, RickyTimeline.POWERUP_AT + RickyTimeline.POWERUP_TICKS, "it ends on the drop");
        assertTrue(RickyTimeline.FREE_AT < RickyTimeline.POWERUP_AT, "everyone can move before he really powers up");
    }

    @Test
    void heRisesWhilePoweringUpAndIsDownOnTheDrop() {
        assertEquals(0, RickyTimeline.riseHeight(0), 1e-9, "on the ground as Kills starts");
        assertEquals(0, RickyTimeline.riseHeight(RickyTimeline.KILLS_DROP), 1e-9, "down again on the drop");
        double last = -1;
        for (int s = 0; s < RickyTimeline.RISE_END; s++) {
            double h = RickyTimeline.riseHeight(s);
            assertTrue(h >= last - 1e-9, "never sinks while rising, at " + s);
            last = h;
        }
        assertEquals(RickyTimeline.HOVER, RickyTimeline.riseHeight(RickyTimeline.RISE_END), 1e-6, "hovering");
        assertTrue(RickyTimeline.riseHeight(RickyTimeline.KILLS_DROP - 1) > RickyTimeline.HOVER, "straining higher just before");
        assertEquals(0, RickyTimeline.strain(0), 1e-9, "calm as it starts");
        assertEquals(1, RickyTimeline.strain(RickyTimeline.KILLS_DROP), 1e-9, "everything at the drop");
    }

    @Test
    void hisHairFlickersFasterAndFasterThenStaysGold() {
        for (int s = 0; s < RickyTimeline.POWERUP_AT + 20; s++) assertFalse(RickyTimeline.flicker(s), "dark before the power-up, at " + s);
        for (int s = RickyTimeline.KILLS_DROP; s < RickyTimeline.KILLS_DROP + 200; s++) assertTrue(RickyTimeline.flicker(s), "gold for good from the drop, at " + s);
        // More of the time gold in the last second than in the first second of flickering
        int early = 0, late = 0;
        for (int s = RickyTimeline.POWERUP_AT + 20; s < RickyTimeline.POWERUP_AT + 40; s++) if (RickyTimeline.flicker(s)) early++;
        for (int s = RickyTimeline.KILLS_DROP - 20; s < RickyTimeline.KILLS_DROP; s++) if (RickyTimeline.flicker(s)) late++;
        assertTrue(early > 0, "it does flicker early on");
        assertTrue(late > early, "gold more and more of the time: " + early + " then " + late);
        // And it really flickers: off again at some point in the last second
        boolean off = false;
        for (int s = RickyTimeline.KILLS_DROP - 20; s < RickyTimeline.KILLS_DROP; s++) off |= !RickyTimeline.flicker(s);
        assertTrue(off, "still flickering just before the drop");
    }

    @Test
    void healthScalesLikeTheOthers() {
        assertEquals(0.6f, RickyTimeline.healthScale(1), 1e-6);
        assertEquals(1f, RickyTimeline.healthScale(5), 1e-6);
        assertEquals(1f, RickyTimeline.healthScale(7), 1e-6);
        assertEquals(1.24f, RickyTimeline.healthScale(10), 1e-6);
        assertEquals(2f, RickyTimeline.healthScale(40), 1e-6);
    }

    @Test
    void handsAndSnakes() {
        assertEquals(1, RickyTimeline.hands(1, false), "one hand alone");
        assertEquals(6, RickyTimeline.hands(10, false), "at most six");
        assertEquals(7, RickyTimeline.hands(10, true), "seven once he's a Super Saiyan");
        assertEquals(4, RickyTimeline.snakes(false), "four rows of spikes");
        assertEquals(6, RickyTimeline.snakes(true), "six as a Super Saiyan");
    }

    @Test
    void everyoneArrivesInAnArcEastOfTheMiddle() {
        for (int n = 1; n <= 10; n++) {
            for (int i = 0; i < n; i++) {
                double[] slot = RickyTimeline.watchSlot(i, n);
                assertTrue(slot[0] > 0.3, "east of the middle, " + i + " of " + n);
                assertEquals(1, Math.hypot(slot[0], slot[1]), 1e-9, "a unit vector");
            }
        }
        assertTrue(RickyTimeline.watchRadius(10) >= RickyTimeline.WATCH_RADIUS, "never closer for a big group");
    }
}
