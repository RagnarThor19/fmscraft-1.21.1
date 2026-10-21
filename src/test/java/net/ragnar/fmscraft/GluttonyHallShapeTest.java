package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.Part;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.Spot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GluttonyHallShapeTest {
    private static final int BRIDGE = 12;
    private static final int X0 = TERRACE_WEST, X1 = east(BRIDGE), Y0 = BOTTOM, Y1 = TOP, Z0 = -TERRACE_HALF, Z1 = TERRACE_HALF;
    private static final int SX = X1 - X0 + 1, SY = Y1 - Y0 + 1, SZ = Z1 - Z0 + 1;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static Part[] parts;

    private static int index(int x, int y, int z) {
        return ((x - X0) * SY + (y - Y0)) * SZ + (z - Z0);
    }

    private static boolean inside(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
    }

    private static Part at(int x, int y, int z) {
        return inside(x, y, z) ? parts[index(x, y, z)] : Part.OUTSIDE;
    }

    /** Whether a player can be in this cell; with {@code gateOpen}, the gate has sunk away. */
    private static boolean open(int x, int y, int z, boolean gateOpen) {
        Part p = at(x, y, z);
        return p == Part.AIR || p == Part.OUTSIDE || (gateOpen && p == Part.GATE);
    }

    private static boolean bottomSlab(Part p) {
        return p == Part.DAIS_EDGE || p == Part.AISLE_ROOF_SLAB;
    }

    /** Standing height in half blocks for a player whose feet are in cell (x, y, z), or MIN_VALUE if they can't stand there. */
    private static int stand(int x, int y, int z, boolean gateOpen) {
        if (!inside(x, y, z)) return Integer.MIN_VALUE;
        Part p = at(x, y, z);
        if (bottomSlab(p) && open(x, y + 1, z, gateOpen) && open(x, y + 2, z, gateOpen)) return 2 * y + 1;
        if (!open(x, y, z, gateOpen) || !open(x, y + 1, z, gateOpen)) return Integer.MIN_VALUE;
        Part below = at(x, y - 1, z);
        if (!below.solid() || bottomSlab(below) || (gateOpen && below == Part.GATE)) return Integer.MIN_VALUE;
        return 2 * y;
    }

    @BeforeAll
    static void build() {
        parts = new Part[SX * SY * SZ];
        for (int x = X0; x <= X1; x++) {
            for (int y = Y0; y <= Y1; y++) {
                for (int z = Z0; z <= Z1; z++) parts[index(x, y, z)] = GluttonyHallShape.part(x, y, z, BRIDGE);
            }
        }
    }

    /** Every cell a player can walk to from {@code start} without jumping (up half a block at a time, down any drop of up to three). */
    private static boolean[] walk(int sx, int sy, int sz, boolean gateOpen) {
        boolean[] reached = new boolean[parts.length];
        Deque<int[]> queue = new ArrayDeque<>();
        assertTrue(stand(sx, sy, sz, gateOpen) != Integer.MIN_VALUE, "can't stand at the start " + sx + "," + sy + "," + sz);
        reached[index(sx, sy, sz)] = true;
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int h = stand(c[0], c[1], c[2], gateOpen);
            for (int[] d : NEIGHBORS) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                for (int ny = c[1] - 3; ny <= c[1] + 1; ny++) {
                    if (!inside(nx, ny, nz) || reached[index(nx, ny, nz)]) continue;
                    int nh = stand(nx, ny, nz, gateOpen);
                    if (nh == Integer.MIN_VALUE || nh - h > 1 || h - nh > 6) continue;
                    // Room overhead to step across
                    if (nh > h && !open(c[0], c[1] + 2, c[2], gateOpen)) continue;
                    reached[index(nx, ny, nz)] = true;
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return reached;
    }

    private static int landingX() {
        return TERRACE_EAST + BRIDGE + 1;
    }

    @Test
    void fromTheShoreYouCanWalkToTheGorgedBruteWithoutJumping() {
        boolean[] reached = walk(landingX(), 1, 0, false);
        // Right up to him, on the dais
        assertTrue(reached[index(GORGED.x() + 2, GORGED.y(), 0)], "the dais in front of the Gorged Brute");
        assertTrue(reached[index(10, 1, 13)], "the aisles");
        // But not past the gate while it's shut
        assertFalse(reached[index(LOCK.x() + 1, 1, 0)], "the chapel, with the gate shut");
    }

    @Test
    void withTheGateDownTheChapelIsAWalkAway() {
        boolean[] reached = walk(landingX(), 1, 0, true);
        assertTrue(reached[index(LOCK.x() + 1, 1, 0)], "in front of the lock");
    }

    @Test
    void theGorgedBruteHasRoomToSwell() {
        // Up to 2.6 times a brute's size: about 1.6 wide and 5.1 tall, standing on the dais
        for (int x = GORGED.x() - 1; x <= GORGED.x() + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertTrue(at(x, GORGED.y() - 1, z).solid(), "the dais under him");
                for (int y = GORGED.y(); y <= GORGED.y() + 5; y++) assertEquals(Part.AIR, at(x, y, z), "room over the dais at " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void theGateStandsBetweenTheHallAndTheChapel() {
        for (List<Spot> row : gateRows()) {
            for (Spot s : row) {
                assertEquals(Part.GATE, at(s.x(), s.y(), s.z()));
                assertFalse(inHall(s.x(), s.y(), s.z()) || inSanctum(s.x(), s.y(), s.z()), "the gate is neither hall nor chapel");
            }
        }
        assertEquals(GATE_HEIGHT, gateRows().size());
        assertTrue(inHall(GORGED.x(), GORGED.y(), GORGED.z()), "he stands in the hall");
        assertTrue(inSanctum(LOCK.x(), LOCK.y(), LOCK.z()), "the lock is in the chapel");
        for (Spot chest : ROOM_CHESTS) assertTrue(inSanctum(chest.x(), chest.y(), chest.z()));
        assertTrue(inSanctum(WEST_WALL_OUT, 1, 0) && inSanctum(PASSAGE_WEST, 2, 0), "the passage is part of the chapel");
        // The lock faces the passage, with the frame's opening clear four blocks behind it
        for (int z = -1; z <= 1; z++) {
            for (int y = 1; y <= 3; y++) assertEquals(Part.AIR, at(FRAME.x(), y, z), "the frame's opening");
        }
        assertEquals(4, LOCK.x() - FRAME.x());
    }

    @Test
    void theFeastStandsOnTheFloorOfTheHall() {
        for (List<Spot> spots : List.of(PIGLIN_SPOTS, BRUTE_SPOTS, REINFORCE_SPOTS)) {
            for (Spot s : spots) {
                assertTrue(inHall(s.x(), s.y(), s.z()), "in the hall: " + s);
                assertTrue(stand(s.x(), s.y(), s.z(), false) != Integer.MIN_VALUE, "room to stand: " + s);
            }
        }
        assertTrue(PIGLIN_SPOTS.size() >= 10 && BRUTE_SPOTS.size() >= 4);
        for (Spot lantern : CHANDELIERS) assertEquals(Part.AIR, at(lantern.x(), lantern.y(), lantern.z()), "a lantern hangs in the air: " + lantern);
        for (Spot barrel : PANTRIES) {
            assertEquals(Part.AIR, at(barrel.x(), barrel.y(), barrel.z()), "a pantry on the aisle floor: " + barrel);
            assertTrue(at(barrel.x(), barrel.y(), barrel.z() + (barrel.z() > 0 ? 1 : -1)).solid(), "against the outer wall: " + barrel);
        }
        for (Spot bell : BELLS) {
            assertEquals(Part.AIR, at(bell.x(), bell.y(), bell.z()), "the bell hangs in the belfry");
            assertTrue(at(bell.x(), bell.y() + 1, bell.z()).solid(), "from its ceiling");
        }
    }

    @Test
    void theHallIsUnderCover() {
        // Straight up from anywhere on the hall's floor there's a vault, then the roof
        for (int x = HALL_WEST; x <= NAVE_EAST; x++) {
            for (int z = -AISLE_OUT; z <= AISLE_OUT; z++) {
                if (!inHall(x, 1, z)) continue;
                boolean covered = false;
                for (int y = 2; y <= TOP && !covered; y++) covered = at(x, y, z).solid();
                assertTrue(covered, "open to the sky at " + x + "," + z);
            }
        }
    }

    @Test
    void theBridgeRunsFromTheTerraceToTheShore() {
        for (int x = TERRACE_EAST; x <= landingX() + 1; x++) {
            assertTrue(at(x, 0, 0).solid(), "deck at " + x);
            for (int y = 1; y <= 3; y++) assertTrue(open(x, y, 0, false), "headroom at " + x + "," + y);
        }
        assertEquals(Part.OUTSIDE, at(east(BRIDGE) + 1, 0, 0), "and ends there");
    }
}
