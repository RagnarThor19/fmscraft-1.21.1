package net.ragnar.fmscraft;

import net.ragnar.fmscraft.world.DeathMarks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathMarkDirectionTest {
    // Minecraft's yaw: 0 faces south (+Z), 90 west (-X), 180 north, -90 east (+X)

    @Test
    void straightAheadIsNoTurn() {
        assertEquals(0f, DeathMarks.turnTo(0, 10, 0f), 0.01f);
        assertEquals(0f, DeathMarks.turnTo(-10, 0, 90f), 0.01f);
    }

    @Test
    void facingSouthWestIsOnTheRight() {
        // Facing south, your right hand points west
        assertEquals(90f, DeathMarks.turnTo(-10, 0, 0f), 0.01f);
        assertEquals(-90f, DeathMarks.turnTo(10, 0, 0f), 0.01f);
    }

    @Test
    void behindIsHalfATurnWhicheverWayYouFace() {
        assertEquals(180f, Math.abs(DeathMarks.turnTo(0, -10, 0f)), 0.01f);
        assertEquals(180f, Math.abs(DeathMarks.turnTo(0, 10, 540f)), 0.01f);
    }
}
