package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.envy.EnvyMazeLayout;
import net.ragnar.fmscraft.structure.envy.EnvyMazeLayout.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static net.ragnar.fmscraft.structure.envy.EnvyMazeLayout.CENTER;
import static net.ragnar.fmscraft.structure.envy.EnvyMazeLayout.DIRS;
import static net.ragnar.fmscraft.structure.envy.EnvyMazeLayout.N;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvyMazeLayoutTest {
    private static final int SEEDS = 200;

    @Test
    void everyCorridorIsConnected() {
        for (long seed = 0; seed < SEEDS; seed++) {
            EnvyMazeLayout maze = EnvyMazeLayout.generate(seed);
            int[] start = maze.doorstep();
            assertEquals(Kind.PATH, maze.kind(start[0], start[1]), "doorstep is a corridor, seed " + seed);

            boolean[] seen = new boolean[N * N];
            Deque<int[]> queue = new ArrayDeque<>();
            queue.add(start);
            seen[start[0] * N + start[1]] = true;
            int reached = 1;
            while (!queue.isEmpty()) {
                int[] cur = queue.poll();
                for (int[] d : DIRS) {
                    int x = cur[0] + d[0], z = cur[1] + d[1];
                    if (maze.isPath(x, z) && !seen[x * N + z]) {
                        seen[x * N + z] = true;
                        reached++;
                        queue.add(new int[]{x, z});
                    }
                }
            }
            int total = 0;
            for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) if (maze.isPath(x, z)) total++;
            assertEquals(total, reached, "unreachable corridor, seed " + seed);
            assertTrue(total > 400, "maze should be big, seed " + seed);
        }
    }

    @Test
    void safeRoomIsOnlyReachableThroughTheTunnel() {
        for (long seed = 0; seed < SEEDS; seed++) {
            EnvyMazeLayout maze = EnvyMazeLayout.generate(seed);
            int doors = 0;
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    Kind kind = maze.kind(x, z);
                    if (kind == Kind.DOOR) doors++;
                    if (kind != Kind.ROOM) continue;
                    assertTrue(Math.abs(x - CENTER) <= 1 && Math.abs(z - CENTER) <= 1, "room unit outside the middle");
                    // No corridor touches the room directly
                    for (int[] d : DIRS) assertTrue(!maze.isPath(x + d[0], z + d[1]), "corridor next to the room, seed " + seed);
                }
            }
            assertEquals(2, doors, "door units, seed " + seed);
            int[] step = maze.doorstep();
            int[] d = DIRS[maze.doorDir];
            assertEquals(Kind.DOOR, maze.kind(step[0] - d[0], step[1] - d[1]));
            assertEquals(Kind.DOOR, maze.kind(step[0] - 2 * d[0], step[1] - 2 * d[1]));
            assertEquals(Kind.ROOM, maze.kind(step[0] - 3 * d[0], step[1] - 3 * d[1]));
        }
    }

    @Test
    void outerRingIsSolid() {
        EnvyMazeLayout maze = EnvyMazeLayout.generate(7);
        for (int i = 0; i < N; i++) {
            assertEquals(Kind.WALL, maze.kind(i, 0));
            assertEquals(Kind.WALL, maze.kind(i, N - 1));
            assertEquals(Kind.WALL, maze.kind(0, i));
            assertEquals(Kind.WALL, maze.kind(N - 1, i));
        }
    }

    @Test
    void chestsSitInDeadEndsAgainstAWall() {
        for (long seed = 0; seed < SEEDS; seed++) {
            EnvyMazeLayout maze = EnvyMazeLayout.generate(seed);
            assertTrue(maze.chests.size() >= 4, "chests, seed " + seed);
            for (EnvyMazeLayout.Spot chest : maze.chests) {
                assertEquals(1, maze.openSides(chest.x(), chest.z()), "chest not in a dead end");
                int[] d = DIRS[chest.dir()];
                assertTrue(!maze.isPath(chest.x() + d[0], chest.z() + d[1]), "chest isn't against a wall");
            }
        }
    }

    @Test
    void statuesStartSpreadOutAndAwayFromTheRoom() {
        for (long seed = 0; seed < SEEDS; seed++) {
            EnvyMazeLayout maze = EnvyMazeLayout.generate(seed);
            assertEquals(EnvyMazeLayout.STATUES, maze.statues.size(), "statues, seed " + seed);
            for (EnvyMazeLayout.Spot s : maze.statues) {
                assertTrue(maze.isPath(s.x(), s.z()));
                assertTrue(maze.isPath(s.x() + DIRS[s.dir()][0], s.z() + DIRS[s.dir()][1]), "statue starts facing a wall");
                assertTrue(Math.abs(s.x() - CENTER) + Math.abs(s.z() - CENTER) >= 10, "statue too close to the room");
            }
        }
    }

    @Test
    void sameSeedSameMaze() {
        EnvyMazeLayout a = EnvyMazeLayout.generate(1234), b = EnvyMazeLayout.generate(1234);
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) assertEquals(a.kind(x, z), b.kind(x, z));
        assertEquals(a.chests, b.chests);
        assertEquals(a.statues, b.statues);
    }
}
