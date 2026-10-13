package net.ragnar.fmscraft.structure.envy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The Envy maze as a grid of 5x5-block "units", built from a seed. Pure logic, no Minecraft classes, so the same
 * seed always gives the same maze in every chunk and it can be unit tested.
 *
 * Units with both coordinates odd are maze cells; the units between them are walls or corridor. Every open unit
 * is a full 5x5 walkway, every wall unit is 5 blocks of solid stone. The 3x3 units in the middle are the safe room,
 * walled off twice over and reached only by a 1-wide tunnel the statue can't fit through.
 */
public final class EnvyMazeLayout {
    public static final int CELLS = 15;
    public static final int N = CELLS * 2 + 1;
    public static final int CENTER = N / 2;
    /** Safe room interior units (inclusive). */
    public static final int ROOM_MIN = CENTER - 1;
    public static final int ROOM_MAX = CENTER + 1;
    public static final int STATUES = 4;
    public static final int MAX_CHESTS = 10;

    public enum Kind { WALL, PATH, ROOM, DOOR }

    /** dx/dz for north, east, south, west. Index matches {@link #DIR_NAMES}. */
    public static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    public static final String[] DIR_NAMES = {"north", "east", "south", "west"};

    /** A spot in the maze: unit coordinates and a direction (index into {@link #DIRS}). */
    public record Spot(int x, int z, int dir) {
    }

    private final Kind[] grid = new Kind[N * N];
    /** Direction from the room to its door. */
    public final int doorDir;
    /** Chests: dead-end cells, dir points at the wall the chest stands against. */
    public final List<Spot> chests = new ArrayList<>();
    /** Statue starting cells, dir is an open direction to start sliding in. */
    public final List<Spot> statues = new ArrayList<>();

    private EnvyMazeLayout(Random random) {
        for (int i = 0; i < grid.length; i++) grid[i] = Kind.WALL;
        carveMaze(random);
        braid(random, 0.35);

        for (int x = ROOM_MIN; x <= ROOM_MAX; x++) {
            for (int z = ROOM_MIN; z <= ROOM_MAX; z++) set(x, z, Kind.ROOM);
        }
        doorDir = random.nextInt(4);
        // Two door units: the room's own wall ring and the wall between that and the nearest maze cell
        for (int step = 2; step <= 3; step++) {
            set(CENTER + DIRS[doorDir][0] * step, CENTER + DIRS[doorDir][1] * step, Kind.DOOR);
        }

        placeChests(random);
        placeStatues(random);
    }

    public static EnvyMazeLayout generate(long seed) {
        return new EnvyMazeLayout(new Random(seed));
    }

    public Kind kind(int x, int z) {
        if (x < 0 || z < 0 || x >= N || z >= N) return Kind.WALL;
        return grid[x * N + z];
    }

    /** Open for the statue: a full 5x5 walkway. Room and door units are not. */
    public boolean isPath(int x, int z) {
        return kind(x, z) == Kind.PATH;
    }

    /** The maze cell just outside the door. */
    public int[] doorstep() {
        return new int[]{CENTER + DIRS[doorDir][0] * 4, CENTER + DIRS[doorDir][1] * 4};
    }

    private void set(int x, int z, Kind kind) {
        grid[x * N + z] = kind;
    }

    private static boolean isReservedCell(int x, int z) {
        return x >= CENTER - 2 && x <= CENTER + 2 && z >= CENTER - 2 && z <= CENTER + 2;
    }

    private static boolean isCell(int x, int z) {
        return x % 2 == 1 && z % 2 == 1 && x > 0 && z > 0 && x < N - 1 && z < N - 1 && !isReservedCell(x, z);
    }

    /** Recursive backtracker over the cells: long, winding corridors. */
    private void carveMaze(Random random) {
        Deque<int[]> stack = new ArrayDeque<>();
        int[] start = {1, 1};
        set(start[0], start[1], Kind.PATH);
        stack.push(start);
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            Collections.shuffle(order, random);
            boolean moved = false;
            for (int d : order) {
                int nx = cur[0] + DIRS[d][0] * 2, nz = cur[1] + DIRS[d][1] * 2;
                if (isCell(nx, nz) && kind(nx, nz) == Kind.WALL) {
                    set(cur[0] + DIRS[d][0], cur[1] + DIRS[d][1], Kind.PATH);
                    set(nx, nz, Kind.PATH);
                    stack.push(new int[]{nx, nz});
                    moved = true;
                    break;
                }
            }
            if (!moved) stack.pop();
        }
    }

    /** Knocks through some dead ends so the maze has loops: the statue roams instead of shuttling, and you can be cut off. */
    private void braid(Random random, double chance) {
        for (int x = 1; x < N - 1; x += 2) {
            for (int z = 1; z < N - 1; z += 2) {
                if (!isCell(x, z) || openSides(x, z) != 1 || random.nextDouble() >= chance) continue;
                List<Integer> options = new ArrayList<>();
                for (int d = 0; d < 4; d++) {
                    int wx = x + DIRS[d][0], wz = z + DIRS[d][1];
                    if (kind(wx, wz) == Kind.WALL && isCell(x + DIRS[d][0] * 2, z + DIRS[d][1] * 2)) options.add(d);
                }
                if (options.isEmpty()) continue;
                int d = options.get(random.nextInt(options.size()));
                set(x + DIRS[d][0], z + DIRS[d][1], Kind.PATH);
            }
        }
    }

    public int openSides(int x, int z) {
        int open = 0;
        for (int[] d : DIRS) if (isPath(x + d[0], z + d[1])) open++;
        return open;
    }

    private void placeChests(Random random) {
        int[] doorstep = doorstep();
        List<Spot> deadEnds = new ArrayList<>();
        for (int x = 1; x < N - 1; x += 2) {
            for (int z = 1; z < N - 1; z += 2) {
                if (!isPath(x, z) || openSides(x, z) != 1 || (x == doorstep[0] && z == doorstep[1])) continue;
                for (int d = 0; d < 4; d++) {
                    // The chest stands against the wall opposite the only way in
                    if (isPath(x + DIRS[d][0], z + DIRS[d][1])) deadEnds.add(new Spot(x, z, (d + 2) % 4));
                }
            }
        }
        Collections.shuffle(deadEnds, random);
        chests.addAll(deadEnds.subList(0, Math.min(MAX_CHESTS, deadEnds.size())));
    }

    private void placeStatues(Random random) {
        List<Spot> options = new ArrayList<>();
        for (int x = 1; x < N - 1; x += 2) {
            for (int z = 1; z < N - 1; z += 2) {
                if (!isPath(x, z) || Math.abs(x - CENTER) + Math.abs(z - CENTER) < 10) continue;
                for (int d = 0; d < 4; d++) {
                    if (isPath(x + DIRS[d][0], z + DIRS[d][1])) {
                        options.add(new Spot(x, z, d));
                        break;
                    }
                }
            }
        }
        Collections.shuffle(options, random);
        for (Spot spot : options) {
            if (statues.size() >= STATUES) break;
            // Keep them spread out
            boolean tooClose = statues.stream().anyMatch(s -> Math.abs(s.x() - spot.x()) + Math.abs(s.z() - spot.z()) < 12);
            if (!tooClose) statues.add(spot);
        }
    }
}
