package net.ragnar.fmscraft;

import net.minecraft.util.math.BlockPos;
import net.ragnar.fmscraft.structure.greed.GreedVaultLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedVaultLayoutTest {
    @Test
    void onlyLevers157OpenTheDoor() {
        int opening = 0;
        for (int mask = 0; mask < 256; mask++) {
            Set<Integer> on = new HashSet<>();
            for (int lever = 1; lever <= 8; lever++) if ((mask & (1 << (lever - 1))) != 0) on.add(lever);
            if (GreedVaultLayout.opens(on)) {
                opening++;
                assertEquals(Set.of(1, 5, 7), on);
            }
        }
        assertEquals(1, opening, "exactly one of the 256 combinations opens it");
        assertFalse(GreedVaultLayout.opens(Set.of(1, 5)));
        assertFalse(GreedVaultLayout.opens(Set.of(1, 5, 7, 8)));
        assertFalse(GreedVaultLayout.opens(Set.of(1, 2, 3, 4, 5, 6, 7, 8)));
    }

    @Test
    void leversReadLikeABookFromInFrontOfTheWall() {
        List<BlockPos> levers = GreedVaultLayout.LEVERS;
        assertEquals(8, levers.size());
        // Facing the wall (south), east is on your left: numbers go down x along a row, the top row first
        for (int i = 0; i < 3; i++) {
            assertTrue(levers.get(i).getX() > levers.get(i + 1).getX());
            assertTrue(levers.get(i + 4).getX() > levers.get(i + 5).getX());
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(levers.get(i).getY() > levers.get(i + 4).getY(), "1-4 above 5-8");
            assertEquals(levers.get(i).getX(), levers.get(i + 4).getX(), "5-8 right under 1-4");
        }
    }

    @Test
    void theDoorIsA4x4WallThatSinksFromTheTop() {
        List<BlockPos> door = GreedVaultLayout.doorBlocks();
        assertEquals(16, new HashSet<>(door).size());
        for (BlockPos pos : door) assertEquals(GreedVaultLayout.DOOR_X, pos.getX());
        for (int row = 0; row < 4; row++) {
            for (BlockPos pos : GreedVaultLayout.doorRow(row)) assertEquals(GreedVaultLayout.DOOR_MIN_Y + 3 - row, pos.getY());
        }
    }

    @Test
    void theRoomsAreTheBodyButThePipeAboveThemIsNot() {
        assertTrue(GreedVaultLayout.inBody(GreedVaultLayout.LOCK));
        for (BlockPos lever : GreedVaultLayout.LEVERS) assertTrue(GreedVaultLayout.inBody(lever));
        assertTrue(GreedVaultLayout.inBody(new BlockPos(74, 3, 15)), "the room under the pipe");
        assertFalse(GreedVaultLayout.inBody(new BlockPos(74, 9, 15)), "up the pipe");
        assertFalse(GreedVaultLayout.inBody(GreedVaultLayout.PIPE_CENTER), "the mouth of the pipe");
        assertFalse(GreedVaultLayout.inBody(new BlockPos(74, 3, 40)), "outside the vault");
    }

    @Test
    void theLockAndThePortalRoomAreBehindTheDoor() {
        assertTrue(GreedVaultLayout.inner(GreedVaultLayout.LOCK));
        for (BlockPos home : GreedVaultLayout.STATUE_HOMES) assertFalse(GreedVaultLayout.inner(home), "statues start outside");
        for (BlockPos lever : GreedVaultLayout.LEVERS) assertFalse(GreedVaultLayout.inner(lever), "the levers are on the near side");
    }
}
