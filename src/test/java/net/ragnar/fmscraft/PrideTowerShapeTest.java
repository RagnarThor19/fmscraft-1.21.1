package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.pride.PrideTowerShape;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape.Column;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrideTowerShapeTest {
    private static final int R = PrideTowerShape.RADIUS + 4;
    private static final int SIZE = 2 * R + 1;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static Column[] columns(PrideTowerShape shape) {
        Column[] columns = new Column[SIZE * SIZE];
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) columns[(dx + R) * SIZE + dz + R] = shape.column(dx, dz);
        }
        return columns;
    }

    /** Ground around the tower counts as walkable at height 0. */
    private static boolean walkable(Column c) {
        return c.walkable() || c.kind() == Kind.OUTSIDE;
    }

    @Test
    void theRampClimbsFromTheGroundToTheSummitWithoutAJump() {
        for (int height : new int[]{PrideTowerShape.FULL_HEIGHT, 150}) {
            PrideTowerShape shape = new PrideTowerShape(height);
            Column[] columns = columns(shape);
            boolean[] seen = new boolean[columns.length];
            Deque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{-R, -R}); // a corner, out on the ground
            seen[0] = true;
            int highest = 0;
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                Column here = columns[(at[0] + R) * SIZE + at[1] + R];
                highest = Math.max(highest, here.halfFloor());
                for (int[] d : NEIGHBORS) {
                    int x = at[0] + d[0], z = at[1] + d[1];
                    if (Math.abs(x) > R || Math.abs(z) > R) continue;
                    int i = (x + R) * SIZE + z + R;
                    // Players step up half a block without jumping
                    if (seen[i] || !walkable(columns[i]) || Math.abs(columns[i].halfFloor() - here.halfFloor()) > 1) continue;
                    seen[i] = true;
                    queue.add(new int[]{x, z});
                }
            }
            assertEquals(height * 2, highest, "the summit can be walked to, height " + height);
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].walkable()) assertTrue(seen[i], "a stretch of walkway can't be reached, height " + height);
            }
        }
    }

    @Test
    void noWalkwayEndsInADrop() {
        PrideTowerShape shape = new PrideTowerShape(PrideTowerShape.FULL_HEIGHT);
        Column[] columns = columns(shape);
        for (int dx = -R + 1; dx < R; dx++) {
            for (int dz = -R + 1; dz < R; dz++) {
                Column here = columns[(dx + R) * SIZE + dz + R];
                if (!here.walkable()) continue;
                for (int[] d : NEIGHBORS) {
                    Column next = columns[(dx + d[0] + R) * SIZE + dz + d[1] + R];
                    assertTrue(next.halfFloor() >= here.halfFloor() - 1 || next.kind() == Kind.PARAPET,
                            "unguarded drop at " + dx + "," + dz);
                }
            }
        }
    }

    @Test
    void itTapersTurnByTurn() {
        PrideTowerShape shape = new PrideTowerShape(PrideTowerShape.FULL_HEIGHT);
        assertEquals(Kind.SUMMIT, shape.column(0, 0).kind());
        assertEquals(PrideTowerShape.FULL_HEIGHT * 2, shape.column(0, 0).halfFloor());
        assertEquals(Kind.OUTSIDE, shape.column(PrideTowerShape.RADIUS + 1, 0).kind());
        // Each turn is lower than the one inside it
        double previous = -1;
        for (int m = 0; m < PrideTowerShape.TURNS; m++) {
            double floor = shape.rise(Math.PI + 2 * Math.PI * m);
            assertTrue(floor > previous + 20, "turn " + m + " should be well above the one before");
            previous = floor;
        }
        assertTrue(shape.grade < 0.3, "the ramp is too steep: " + shape.grade);
    }
}
