package net.ragnar.fmscraft;

import net.ragnar.fmscraft.boss.BossSong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossSongTest {
    @Test
    void beatsLandOnTheRightTicks() {
        BossSong song = new BossSong("test", 120, 10); // 120 BPM = a beat every 10 ticks
        assertEquals(10.0, song.ticksPerBeat());
        assertFalse(song.isBeatTick(9));
        assertTrue(song.isBeatTick(10));
        assertFalse(song.isBeatTick(11));
        assertTrue(song.isBeatTick(20));
        assertEquals(3, song.beatAt(45));
    }

    @Test
    void fractionalTempoFiresOncePerBeat() {
        BossSong song = new BossSong("test", 140, 0);
        int beats = 0;
        for (int tick = 0; tick < 20 * 60; tick++) {
            if (song.isBeatTick(tick)) beats++;
        }
        assertEquals(140, beats);
    }
}
