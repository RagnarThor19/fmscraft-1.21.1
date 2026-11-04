package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.lust.MugenTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MugenTimelineTest {
    @Test
    void theEntranceFillsTheReversedIntroAndEndsOnItsHit() {
        // COMË N GO's reversed intro ends on a hit at 34.91 s; the beat grid starts at 35.0 s
        assertEquals(34.91, MugenTimeline.BOOM / 20.0, 0.05, "the boom");
        assertEquals(35.0, MugenTimeline.COMENGO_FIRST_BEAT / 20.0, 1e-9, "the first beat");
        assertTrue(MugenTimeline.BOOM <= MugenTimeline.COMENGO_FIRST_BEAT, "he's loose on the hit, as the beat comes in");
        // Everything in the entrance happens in order, before the hit
        int[] order = {MugenTimeline.POOLS[0], MugenTimeline.RISE_START, MugenTimeline.RISEN, MugenTimeline.LOOK_UP, MugenTimeline.DRAW,
                MugenTimeline.FLICKER, MugenTimeline.FLICKER + MugenTimeline.FLICKERS * MugenTimeline.FLICKER_STEP, MugenTimeline.STANCE,
                MugenTimeline.BOOM};
        for (int i = 1; i < order.length; i++) assertTrue(order[i - 1] <= order[i], "step " + i + " in order: " + order[i - 1] + " <= " + order[i]);
        assertTrue(MugenTimeline.POOLS[0] >= MugenTimeline.REVEAL_TICKS, "the shadow starts once the wheel has gone");
    }

    @Test
    void theHandsDragHimUpSlowlyFromUnderTheGround() {
        assertEquals(MugenTimeline.RISE_DEPTH, MugenTimeline.riseDepth(0), 1e-9, "under the ground to start");
        assertEquals(MugenTimeline.RISE_DEPTH, MugenTimeline.riseDepth(MugenTimeline.RISE_START), 1e-9, "until the first hands come up");
        assertEquals(0, MugenTimeline.riseDepth(MugenTimeline.RISEN), 1e-9, "standing on it once risen");
        assertEquals(0, MugenTimeline.riseDepth(MugenTimeline.BOOM), 1e-9, "and stays up");
        // Only ever upwards, and never in a jump
        double prev = MugenTimeline.riseDepth(0);
        for (int t = 1; t <= MugenTimeline.BOOM; t++) {
            double d = MugenTimeline.riseDepth(t);
            assertTrue(d <= prev + 1e-9, "never sinks back, at " + t);
            assertTrue(prev - d < 0.02, "slowly, at " + t);
            prev = d;
        }
        // His head starts under the ground: a player is 1.8 tall
        assertTrue(MugenTimeline.RISE_DEPTH > 1.8, "out of sight to start");
        // Every pool's hands come up while he's still being dragged
        for (int i = 0; i < MugenTimeline.POOLS.length; i++) {
            int grab = MugenTimeline.POOLS[i] + MugenTimeline.POOL_CREEP;
            assertEquals(i, MugenTimeline.grabAt(grab), "pool " + i + " grabs");
            assertTrue(grab >= MugenTimeline.RISE_START && grab < MugenTimeline.RISEN, "pool " + i + " grabs while he rises");
        }
        assertEquals(-1, MugenTimeline.grabAt(MugenTimeline.POOLS[0]), "nothing grabs while a pool creeps");
    }

    @Test
    void heFlickersRoundEveryoneOnceEach() {
        assertEquals(-1, MugenTimeline.flicker(MugenTimeline.FLICKER - 1), "not before");
        for (int k = 0; k < MugenTimeline.FLICKERS; k++) {
            assertEquals(k, MugenTimeline.flicker(MugenTimeline.FLICKER + k * MugenTimeline.FLICKER_STEP), "flicker " + k);
        }
        assertEquals(-1, MugenTimeline.flicker(MugenTimeline.FLICKER + MugenTimeline.FLICKERS * MugenTimeline.FLICKER_STEP), "not after");
    }

    @Test
    void heLandsOnToxicsDrop() {
        // Toxic's reversed intro cuts to silence at 14.68 s; the beat slams in at 15.60 s
        assertEquals(15.60, MugenTimeline.TOXIC_DROP / 20.0, 0.05, "the drop");
        assertEquals(14.68, MugenTimeline.DIVE_AT / 20.0, 0.05, "the dive, in the silence");
        assertTrue(MugenTimeline.REVEAL_AT < MugenTimeline.DIVE_AT, "the black lifts on him hanging there before he dives");
        assertEquals(MugenTimeline.HANG_TOP, MugenTimeline.diveHeight(MugenTimeline.DIVE_AT), 1e-9, "dives from where he hung");
        assertEquals(MugenTimeline.hangHeight(MugenTimeline.DIVE_AT), MugenTimeline.diveHeight(MugenTimeline.DIVE_AT), 1e-9, "no jump between");
        assertEquals(0, MugenTimeline.diveHeight(MugenTimeline.TOXIC_DROP), 1e-9, "on the ground on the drop");
        double prev = MugenTimeline.diveHeight(MugenTimeline.DIVE_AT), speed = 0;
        for (int s = MugenTimeline.DIVE_AT + 1; s <= MugenTimeline.TOXIC_DROP; s++) {
            double h = MugenTimeline.diveHeight(s);
            assertTrue(prev - h >= speed - 1e-9, "faster and faster, at " + s);
            speed = prev - h;
            prev = h;
        }
        // The beat grid starts on the drop
        BossSong toxic = new BossSong("toxic", MugenTimeline.TOXIC_BPM, MugenTimeline.TOXIC_DROP);
        assertTrue(toxic.isBeatTick(MugenTimeline.TOXIC_DROP), "a beat on the drop");
        assertEquals(0, toxic.beatAt(MugenTimeline.TOXIC_DROP), "the first one");
        // Measured at 61.4 BPM: the grid is twice that, half-time
        assertEquals(61.4 * 2, MugenTimeline.TOXIC_BPM, 0.01, "122.8 BPM");
    }

    @Test
    void comeNGosBeatsLineUpWithWhatWasMeasured() {
        BossSong song = new BossSong("comengo", MugenTimeline.COMENGO_BPM, MugenTimeline.COMENGO_FIRST_BEAT);
        // Measured from the audio: a beat at 36.11 s and at 70.09 s (where the big drop is)
        for (double measured : new double[]{36.111, 70.086}) {
            double ticks = measured * 20;
            double beats = (ticks - MugenTimeline.COMENGO_FIRST_BEAT) / song.ticksPerBeat();
            double off = Math.abs(beats - Math.round(beats)) * song.ticksPerBeat();
            assertTrue(off < 2.5, "a beat within 2.5 ticks of " + measured + " s, off by " + off);
        }
    }

    @Test
    void everyoneWatchesFromTheEastAndNobodyIsCrowded() {
        for (int n = 1; n <= 12; n++) {
            double r = MugenTimeline.watchRadius(n);
            assertTrue(r >= MugenTimeline.WATCH_RADIUS, "never closer than " + MugenTimeline.WATCH_RADIUS);
            for (int i = 0; i < n; i++) {
                double[] a = MugenTimeline.watchSlot(i, n);
                assertEquals(1, Math.hypot(a[0], a[1]), 1e-9, "a unit vector");
                assertTrue(a[0] > 0.2, "east of him (the way everyone arrives), slot " + i + " of " + n);
                // Within the courtyard, which is 12 wide north to south
                assertTrue(Math.abs(a[1] * r) <= 10, "inside the courtyard, slot " + i + " of " + n + ": " + a[1] * r);
                for (int j = 0; j < i; j++) {
                    double[] b = MugenTimeline.watchSlot(j, n);
                    double rowI = n > 7 && i % 2 == 1 ? 2.5 : 0, rowJ = n > 7 && j % 2 == 1 ? 2.5 : 0;
                    double d = Math.hypot(a[0] * (r + rowI) - b[0] * (r + rowJ), a[1] * (r + rowI) - b[1] * (r + rowJ));
                    assertTrue(d > 1.2, "slots " + i + " and " + j + " of " + n + " apart: " + d);
                }
            }
        }
    }

    @Test
    void healthAndClonesScaleWithThePlayers() {
        assertEquals(0.6f, MugenTimeline.healthScale(1), 1e-6);
        assertEquals(1f, MugenTimeline.healthScale(5), 1e-6);
        assertEquals(1f, MugenTimeline.healthScale(7), 1e-6);
        assertEquals(1.24f, MugenTimeline.healthScale(10), 1e-6);
        assertEquals(2f, MugenTimeline.healthScale(40), 1e-6);
        assertEquals(3, MugenTimeline.clones(4, false));
        assertEquals(5, MugenTimeline.clones(4, true));
        assertEquals(6, MugenTimeline.clones(9, true));
    }
}
