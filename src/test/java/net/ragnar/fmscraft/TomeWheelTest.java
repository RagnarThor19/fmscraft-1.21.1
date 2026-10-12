package net.ragnar.fmscraft;

import net.ragnar.fmscraft.reward.WheelMath;
import net.ragnar.fmscraft.reward.WheelTier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomeWheelTest {
    @Test
    void wheelAlwaysStopsOnThePickedSlice() {
        for (int slice = 0; slice < WheelMath.SLICES.length; slice++) {
            for (float offset = 0.2f; offset <= 0.8f; offset += 0.05f) {
                float end = WheelMath.finalRotation(slice, offset);
                assertEquals(slice, WheelMath.sliceAt(end), "slice " + slice + " offset " + offset);
                // The easing must actually reach the end, or the wheel would stop one slice short
                assertEquals(end, end * WheelMath.ease(1f), 1e-3);
            }
        }
    }

    @Test
    void slicesMatchTheOdds() {
        Map<WheelTier, Integer> count = new EnumMap<>(WheelTier.class);
        for (WheelTier tier : WheelMath.SLICES) count.merge(tier, 1, Integer::sum);
        for (WheelTier tier : WheelTier.values()) {
            assertEquals(tier.chance, count.get(tier) / (double) WheelMath.SLICES.length, 1e-9, tier.name());
        }
        for (int i = 0; i < WheelMath.SLICES.length; i++) {
            assertTrue(WheelMath.SLICES[i] != WheelMath.SLICES[(i + 1) % WheelMath.SLICES.length], "neighbouring slices " + i);
        }
    }

    @Test
    void rollsAreFiftyThirtyTwenty() {
        Random random = new Random(7);
        Map<WheelTier, Integer> count = new EnumMap<>(WheelTier.class);
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) count.merge(WheelTier.fromRoll(random.nextDouble()), 1, Integer::sum);
        assertEquals(0.50, count.get(WheelTier.BEGINNER) / (double) rolls, 0.01);
        assertEquals(0.30, count.get(WheelTier.ADVANCED) / (double) rolls, 0.01);
        assertEquals(0.20, count.get(WheelTier.MASTER) / (double) rolls, 0.01);
    }

    @Test
    void easingNeverRunsBackwards() {
        float last = 0;
        for (int i = 1; i <= 100; i++) {
            float now = WheelMath.ease(i / 100f);
            assertTrue(now >= last);
            last = now;
        }
    }
}
