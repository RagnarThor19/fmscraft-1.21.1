package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.sloth.SlothSphereShape;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Hanging;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Part;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Spot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static net.ragnar.fmscraft.structure.sloth.SlothSphereShape.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlothSphereShapeTest {
    private static final int R = RADIUS;
    private static final int SIZE = 2 * R + 1;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static Part[] parts;
    /** Standing height in half blocks at each place a player can stand, or MIN_VALUE. */
    private static int[] stand;
    private static boolean[] reached;

    private static int index(int x, int y, int z) {
        return ((x + R) * SIZE + (y + R)) * SIZE + (z + R);
    }

    private static boolean inside(int x, int y, int z) {
        return Math.abs(x) <= R && Math.abs(y) <= R && Math.abs(z) <= R;
    }

    private static Part at(int x, int y, int z) {
        return inside(x, y, z) ? parts[index(x, y, z)] : Part.OUTSIDE;
    }

    private static boolean open(Part p) {
        return p == Part.AIR || p == Part.CARPET;
    }

    @BeforeAll
    static void build() {
        parts = new Part[SIZE * SIZE * SIZE];
        stand = new int[parts.length];
        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) parts[index(x, y, z)] = SlothSphereShape.part(x, y, z);
            }
        }
        // A player stands in cell y on a solid block (height 2y) or on a slab in that cell (2y + 1), with headroom
        for (int x = -R; x <= R; x++) {
            for (int y = -R + 1; y < R - 2; y++) {
                for (int z = -R; z <= R; z++) {
                    int i = index(x, y, z);
                    stand[i] = Integer.MIN_VALUE;
                    Part here = parts[i];
                    if (here == Part.PATH_SLAB && open(at(x, y + 1, z)) && open(at(x, y + 2, z))) stand[i] = 2 * y + 1;
                    else if (open(here) && open(at(x, y + 1, z)) && at(x, y - 1, z).solid()) stand[i] = 2 * y;
                }
            }
        }
        // Walk from the mouth, stepping up or down at most half a block (no jumping)
        reached = new boolean[parts.length];
        Deque<int[]> queue = new ArrayDeque<>();
        int[] start = {INNER + 2, MOUTH_FLOOR + 1, 0};
        assertTrue(stand[index(start[0], start[1], start[2])] != Integer.MIN_VALUE, "can stand in the mouth");
        reached[index(start[0], start[1], start[2])] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int h = stand[index(c[0], c[1], c[2])];
            for (int[] d : NEIGHBORS) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + d[0], y = c[1] + dy, z = c[2] + d[1];
                    if (!inside(x, y, z) || Math.abs(y) >= R - 2) continue;
                    int i = index(x, y, z);
                    if (reached[i] || stand[i] == Integer.MIN_VALUE || Math.abs(stand[i] - h) > 1) continue;
                    reached[i] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
    }

    @Test
    void theStairCanBeWalkedFromTheMouthToEveryBellAndTheSealWithoutAJump() {
        for (Spot bell : BELLS) {
            assertTrue(reached[index(bell.x(), bell.y(), bell.z())], "can't walk to the bell at " + bell);
        }
        for (Spot candle : CANDLES) assertTrue(reached[index(candle.x(), candle.y(), candle.z())], "can't walk to the candle at " + candle);
        for (Spot chest : CHESTS) assertTrue(reached[index(chest.x(), chest.y(), chest.z())], "can't walk to the chest at " + chest);
        assertTrue(reached[index(0, TOP, 0)], "can't walk onto the seal");
        assertEquals(4, BELLS.size());
        assertEquals(4, CANDLES.size());
        assertEquals(3, CHESTS.size());
    }

    @Test
    void theWholeStairIsWalkable() {
        int walkway = 0;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int y = -R + 1; y < R - 2; y++) {
                    if (at(x, y - 1, z) == Part.PATH_FLOOR && stand[index(x, y, z)] != Integer.MIN_VALUE && !open(at(x, y - 2, z))) {
                        assertTrue(reached[index(x, y, z)], "a stretch of walkway can't be reached at " + x + "," + y + "," + z);
                        walkway++;
                    }
                }
            }
        }
        assertTrue(walkway > 1500, "walkway cells: " + walkway);
    }

    @Test
    void theStairPassesTheMouthAtItsHeight() {
        assertEquals(MOUTH_FLOOR + 1, (int) surface(MOUTH_U));
        assertEquals(TOP, (int) surface(TURNS));
        // Turns never come within headroom of each other
        assertTrue(PITCH >= 3 + 3 + 3);
    }

    @Test
    void theStairStaysInTheGap() {
        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    Part p = at(x, y, z);
                    if (p != Part.PATH_FLOOR && p != Part.PATH_SLAB && p != Part.RAIL && p != Part.PATH_UNDERSIDE) continue;
                    double r = Math.sqrt(x * x + y * y + z * z);
                    assertTrue(r >= BALL && r < INNER, "stair block outside the gap at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void lanternsHangInTheAirFromSomethingSolid() {
        assertTrue(LANTERNS.size() > 25, "lanterns: " + LANTERNS.size());
        for (Hanging h : LANTERNS) {
            for (int y = h.y(); y <= h.y() + h.chain(); y++) {
                assertEquals(Part.AIR, at(h.x(), y, h.z()), "lantern or chain inside something at " + h + " y=" + y);
            }
            assertTrue(at(h.x(), h.y() + h.chain() + 1, h.z()).solid(), "lantern hangs from nothing: " + h);
            assertTrue(open(at(h.x(), h.y() - 1, h.z())) || at(h.x(), h.y() - 1, h.z()) == Part.AIR, "lantern rests on something: " + h);
        }
    }

    @Test
    void theSealOpensOntoADropIntoThePool() {
        for (int y = CORE; y < TOP; y++) assertEquals(Part.SEAL, at(0, y, 0));
        for (int y = CORE - 1; y > CORE_FLOOR; y--) assertEquals(Part.AIR, at(0, y, 0), "the drop is blocked at y=" + y);
        for (int x = -SEAL_HALF; x <= SEAL_HALF; x++) {
            for (int z = -SEAL_HALF; z <= SEAL_HALF; z++) assertEquals(Part.POOL, at(x, CORE_FLOOR, z), "under the shaft should be water");
        }
        assertEquals(Part.POOL, at(0, CORE_FLOOR - POOL_DEPTH + 1, 0));
        assertTrue(at(0, CORE_FLOOR - POOL_DEPTH, 0).solid(), "the pool has a bottom");
    }

    @Test
    void theShellIsClosedExceptForTheMouth() {
        // Every ray out from the center along the axes hits the shell, except the one through the mouth
        int[][] dirs = {{-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : dirs) {
            boolean shell = false;
            for (int t = INNER; t < RADIUS; t++) shell |= at(d[0] * t, d[1] * t, d[2] * t).solid();
            assertTrue(shell, "the shell is open along " + d[0] + "," + d[1] + "," + d[2]);
        }
        for (int t = INNER; t < RADIUS; t++) assertEquals(Part.AIR, at(t, MOUTH_FLOOR + 2, 0), "the mouth should be open at x=" + t);
    }
}
