package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.wrath.WrathDenShape;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Hanging;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Part;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Spot;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Tripwire;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static net.ragnar.fmscraft.structure.wrath.WrathDenShape.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrathDenShapeTest {
    private static final int TUNNEL = MIN_TUNNEL;
    private static final int X0 = WEST, X1 = OUTER + TUNNEL + 1, Y0 = BOTTOM, Y1 = TOP, Z0 = -OUTER, Z1 = OUTER;
    private static final int SX = X1 - X0 + 1, SY = Y1 - Y0 + 1, SZ = Z1 - Z0 + 1;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static Part[] parts;
    /** Standing height in half blocks at each place a player can stand, or MIN_VALUE. */
    private static int[] stand;
    private static boolean[] reached;

    private static int index(int x, int y, int z) {
        return ((x - X0) * SY + (y - Y0)) * SZ + (z - Z0);
    }

    private static boolean inside(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
    }

    private static Part at(int x, int y, int z) {
        return inside(x, y, z) ? parts[index(x, y, z)] : Part.OUTSIDE;
    }

    private static boolean open(Part p) {
        return p == Part.AIR || p == Part.OUTSIDE;
    }

    private static boolean reached(int x, int y, int z) {
        return inside(x, y, z) && reached[index(x, y, z)];
    }

    @BeforeAll
    static void build() {
        parts = new Part[SX * SY * SZ];
        stand = new int[parts.length];
        for (int x = X0; x <= X1; x++) {
            for (int y = Y0; y <= Y1; y++) {
                for (int z = Z0; z <= Z1; z++) parts[index(x, y, z)] = WrathDenShape.part(x, y, z, TUNNEL);
            }
        }
        // A player stands in cell y on a solid block (height 2y) or on a slab in that cell (2y + 1), with headroom
        for (int x = X0; x <= X1; x++) {
            for (int y = Y0 + 1; y < Y1 - 2; y++) {
                for (int z = Z0; z <= Z1; z++) {
                    int i = index(x, y, z);
                    stand[i] = Integer.MIN_VALUE;
                    Part here = parts[i];
                    if (here == Part.PATH_SLAB && open(at(x, y + 1, z)) && open(at(x, y + 2, z))) stand[i] = 2 * y + 1;
                    else if (here == Part.AIR && open(at(x, y + 1, z)) && at(x, y - 1, z).solid()) stand[i] = 2 * y;
                }
            }
        }
        // Walk in from the tunnel's mouth, stepping up or down at most half a block (no jumping, no drops)
        reached = new boolean[parts.length];
        Deque<int[]> queue = new ArrayDeque<>();
        int[] start = {OUTER + TUNNEL - 1, BALCONY + 1, 0};
        assertTrue(stand[index(start[0], start[1], start[2])] != Integer.MIN_VALUE, "can stand at the tunnel's mouth");
        reached[index(start[0], start[1], start[2])] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int h = stand[index(c[0], c[1], c[2])];
            for (int[] d : NEIGHBORS) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + d[0], y = c[1] + dy, z = c[2] + d[1];
                    if (!inside(x, y, z) || y <= Y0 || y >= Y1 - 2) continue;
                    int i = index(x, y, z);
                    if (reached[i] || stand[i] == Integer.MIN_VALUE || Math.abs(stand[i] - h) > 1) continue;
                    // Stay inside the den: the cell must be part of it, not the open Nether past the mouth
                    if (parts[i] == Part.OUTSIDE) continue;
                    reached[i] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
    }

    @Test
    void theLedgeWindsAllTheWayDownWithoutAJump() {
        // Every degree of it, from the balcony to the floor, in the middle of the walkway
        for (double angle = PATH_START + 1; angle <= PATH_END - 1; angle += 1) {
            double rad = Math.toRadians(angle);
            int x = (int) Math.round(42 * Math.cos(rad)), z = (int) Math.round(42 * Math.sin(rad));
            int top = Math.floorDiv(halfSurface(angle(x, z)), 2);
            assertTrue(reached(x, top, z) || reached(x, top + 1, z), "the ledge at " + angle + " degrees can be walked to");
        }
        assertTrue(reached(36, BALCONY + 1, 0), "the balcony");
        assertTrue(reached(20, 1, -20), "the floor");
        assertTrue(halfSurface(PATH_START) == 2 * (BALCONY + 1) && halfSurface(PATH_END) == 2, "from the balcony's height to the floor");
    }

    @Test
    void theFloorLeadsToTheNestTheDoorwayAndTheLock() {
        assertTrue(reached(-3, 1, 3), "inside the nest");
        assertTrue(reached(-46, 1, 0), "through the doorway");
        Spot lock = LOCK;
        assertTrue(reached(lock.x() + 1, lock.y(), lock.z()), "in front of the lock");
        // The portal opening behind the lock is clear, and the frame stands in the room
        for (int z = -1; z <= 1; z++) {
            for (int y = 1; y <= 3; y++) assertEquals(Part.AIR, at(FRAME.x(), y, z), "portal opening");
        }
        assertEquals(LOCK.x() - 4, FRAME.x(), "the frame is 4 blocks behind the lock");
    }

    @Test
    void sheFitsInHerNestButNotThroughEitherWayOut() {
        // 2.7 wide and 8.7 tall: room for her in the middle of the hall
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 10; y++) assertEquals(Part.AIR, at(MOTHER.x() + x, y, MOTHER.z() + z), "room over the nest at " + x + "," + y + "," + z);
            }
        }
        // The doorway out is one block wide and two high
        for (int x = -RADIUS; x >= EXIT_END; x--) {
            assertTrue(at(x, 1, -1).solid() && at(x, 1, 1).solid() && at(x, 3, 0).solid(), "the doorway tunnel is 1x2 at x=" + x);
        }
        // The entrance tunnel is only 4 high
        for (int x = RADIUS; x < OUTER + TUNNEL; x++) assertTrue(at(x, BALCONY + TUNNEL_HEIGHT + 1, 0).solid(), "tunnel roof at x=" + x);
    }

    @Test
    void chestsPlatesAndTripwiresAreWhereTheyCanBeReached() {
        for (Spot chest : LEDGE_CHESTS) {
            assertEquals(Part.AIR, at(chest.x(), chest.y(), chest.z()), "a ledge chest's alcove");
            assertTrue(at(chest.x(), chest.y() - 1, chest.z()).solid(), "the chest stands on something");
            boolean beside = false;
            for (int[] d : NEIGHBORS) beside |= reached(chest.x() + d[0], chest.y(), chest.z() + d[1]);
            assertTrue(beside, "a ledge chest can be walked up to");
        }
        for (Spot chest : List.of(FOSSIL_CHEST, NEST_CHEST)) {
            assertEquals(Part.AIR, at(chest.x(), chest.y(), chest.z()));
            assertTrue(at(chest.x(), chest.y() - 1, chest.z()).solid());
        }
        for (Spot plate : PLATES) {
            assertTrue(reached(plate.x(), plate.y(), plate.z()), "a plate where people walk: " + plate);
        }
        assertEquals(TRIP_ANGLES.length, TRIPWIRES.size());
        for (Tripwire wire : TRIPWIRES) {
            for (Spot s : wire.string()) assertTrue(reached(s.x(), s.y(), s.z()), "the string is across the walkway: " + s);
            Spot outer = wire.outerHook();
            int behind = (outer.dir() + 2) % 4;
            int[] dx = {1, 0, -1, 0}, dz = {0, 1, 0, -1};
            assertTrue(at(outer.x() + dx[behind], outer.y(), outer.z() + dz[behind]).solid(), "the outer hook hangs on the wall");
            assertTrue(at(wire.innerHook().x(), wire.innerHook().y() - 1, wire.innerHook().z()).solid(), "the inner hook is over the ledge");
        }
    }

    @Test
    void nothingHangsOrStandsInTheWay() {
        for (Hanging h : CHAINS) {
            int top = ceilingAt(Math.hypot(h.x(), h.z()));
            assertTrue(top > h.y(), "a chain has length");
            for (int y = h.y(); y <= top; y++) assertEquals(Part.AIR, at(h.x(), y, h.z()), "chain at " + h + " y=" + y);
            assertTrue(at(h.x(), top + 1, h.z()).solid(), "the chain hangs from the dome");
        }
        assertTrue(TORCHES.size() >= 8, "soul torches along the ledge: " + TORCHES.size());
        for (Spot s : SHRIEKERS) assertTrue(at(s.x(), s.y(), s.z()) == Part.AIR && at(s.x(), 0, s.z()).solid());
        for (Spot s : CAMPFIRES) assertTrue(at(s.x(), s.y(), s.z()) == Part.AIR && at(s.x(), 0, s.z()).solid());
        for (Spot s : CATALYSTS) assertEquals(Part.NEST_RIM, at(s.x(), s.y(), s.z()));
    }

    @Test
    void theHallIsWhereTheRulesAre() {
        assertTrue(inHall(38, BALCONY + 1, 0), "the balcony");
        assertTrue(inHall(0, 1, 0), "the nest");
        assertTrue(inHall(0, 30, 42), "the ledge");
        Spot chest = LEDGE_CHESTS.get(0);
        assertTrue(inHall(chest.x(), chest.y(), chest.z()), "an alcove");
        assertFalse(inHall(RADIUS + 1, BALCONY + 1, 0), "the entrance tunnel");
        assertFalse(inHall(-RADIUS - 1, 1, 0), "the doorway tunnel");
        assertFalse(inHall(LOCK.x(), LOCK.y(), LOCK.z()), "the portal room");
        assertTrue(inSanctum(LOCK.x(), LOCK.y(), LOCK.z()));
        assertTrue(inSanctum(-50, 1, 0));
        assertFalse(inSanctum(0, 1, 0));
    }
}
