package net.ragnar.fmscraft;

import net.ragnar.fmscraft.structure.lust.LustGardenPlan;
import net.ragnar.fmscraft.structure.lust.LustGardenPlan.Ground;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LustGardenPlanTest {
    private static final int SEEDS = 12;
    private static final Set<Ground> PATHS = EnumSet.of(Ground.AVENUE, Ground.PATH, Ground.PLAZA, Ground.GATE);
    private static final int R = LustGardenPlan.REACH;

    /** Every path, plaza and gate column you can walk to from just outside the gate. */
    private static boolean[][] reachable(LustGardenPlan plan) {
        boolean[][] seen = new boolean[R * 2 + 1][R * 2 + 1];
        int[] start = plan.outsideGate();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start[0] + R][start[1] + R] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + d[0], z = at[1] + d[1];
                if (Math.abs(x) > R || Math.abs(z) > R || seen[x + R][z + R] || !PATHS.contains(plan.ground(x, z))) continue;
                seen[x + R][z + R] = true;
                queue.add(new int[]{x, z});
            }
        }
        return seen;
    }

    @Test
    void gardenIsAbout200BlocksAcrossAndNeverRound() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            double min = Double.MAX_VALUE, max = 0;
            for (int deg = 0; deg < 360; deg++) {
                double a = Math.toRadians(deg), across = plan.outer(a) + plan.outer(a + Math.PI);
                assertTrue(across > 170 && across < 216, "seed " + seed + ": " + across + " across at " + deg);
                min = Math.min(min, plan.outer(a));
                max = Math.max(max, plan.outer(a));
                assertTrue(plan.outer(a) + 1.5 + LustGardenPlan.SKIRT <= R, "seed " + seed + " reaches past " + R);
            }
            assertTrue(max - min > 8, "seed " + seed + " is too round: " + min + " to " + max);
        }
    }

    @Test
    void hedgeIsAtLeastTwelveHighAllRoundExceptTheGate() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            for (int deg = 0; deg < 360; deg += 2) {
                double a = Math.toRadians(deg), r = (plan.inner(a) + plan.outer(a)) / 2;
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                if (plan.ground(x, z) == Ground.GATE) continue;
                // The tallest column across the hedge here
                int tallest = 0;
                for (double d = -3; d <= 3; d += 0.5) {
                    int hx = (int) Math.round(Math.cos(a) * (r + d)), hz = (int) Math.round(Math.sin(a) * (r + d));
                    if (plan.ground(hx, hz) == Ground.HEDGE) tallest = Math.max(tallest, plan.height(hx, hz));
                }
                if (tallest == 0) continue; // a nook carved into it
                assertTrue(tallest >= LustGardenPlan.MIN_HEDGE_HEIGHT, "seed " + seed + ": hedge only " + tallest + " high at " + deg + " degrees");
            }
        }
    }

    @Test
    void theGateLeadsToTheFountain() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            boolean[][] seen = reachable(plan);
            int gateCells = 0;
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (plan.ground(x, z) == Ground.GATE) gateCells++;
            assertTrue(gateCells >= 8,"seed " + seed + ": a gate through the hedge, got " + gateCells + " columns");
            assertTrue(seen[LustGardenPlan.PLAZA - 1 + R][R], "seed " + seed + ": can't walk from the gate to the plaza");
            // ...and to every path in the garden: no path is cut off
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    Ground g = plan.ground(x, z);
                    if ((g == Ground.PATH || g == Ground.AVENUE) && plan.inside(x, z)) {
                        assertTrue(seen[x + R][z + R], "seed " + seed + ": path at " + x + ", " + z + " is cut off");
                    }
                }
            }
        }
    }

    @Test
    void everyStatueStandsBesideAPathFacingIt() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            boolean[][] seen = reachable(plan);
            assertTrue(plan.plinths().size() >= 20, "seed " + seed + ": only " + plan.plinths().size() + " statues");
            for (LustGardenPlan.Plinth p : plan.plinths()) {
                assertEquals(Ground.LAWN, plan.ground(p.x(), p.z()), "seed " + seed + ": plinth at " + p);
                assertTrue(plan.inside(p.x(), p.z()), "plinth inside the hedge: " + p);
                double r = Math.sqrt(p.x() * p.x() + p.z() * p.z());
                assertTrue(r > LustGardenPlan.FOUNTAIN_PLINTH + 8, "statues keep away from the fountain's plinths: " + p);
                // A path it can be walked to from within a few blocks, in the direction it faces
                double fx = -Math.sin(Math.toRadians(p.yaw())), fz = Math.cos(Math.toRadians(p.yaw()));
                boolean faces = false;
                for (int d = 1; d <= 5 && !faces; d++) {
                    int x = (int) Math.round(p.x() + fx * d), z = (int) Math.round(p.z() + fz * d);
                    faces = PATHS.contains(plan.ground(x, z)) && seen[x + R][z + R];
                }
                assertTrue(faces, "seed " + seed + ": plinth " + p + " doesn't face a path");
            }
        }
    }

    @Test
    void pondsKeepOffThePathsAndTheCrypt() {
        int ponds = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            int water = 0;
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    if (plan.ground(x, z) != Ground.POND) continue;
                    water++;
                    assertTrue(plan.edge(x, z) >= 3, "pond too near a path at " + x + ", " + z);
                    assertTrue(!LustGardenPlan.nearCrypt(x, z, 2), "pond over the crypt at " + x + ", " + z);
                    int depth = plan.height(x, z);
                    assertTrue(depth == 1 || depth == 2, "depth " + depth);
                    // Never right against anything but water or shore
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        Ground next = plan.ground(x + d[0], z + d[1]);
                        assertTrue(next == Ground.POND || next == Ground.SHORE, "pond meets " + next + " at " + x + ", " + z);
                    }
                }
            }
            if (water > 0) ponds++;
        }
        assertTrue(ponds >= SEEDS - 1, "most gardens have ponds: " + ponds + "/" + SEEDS);
    }

    @Test
    void theCryptIsUnderSolidGround() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            for (int x = -LustGardenPlan.CRYPT_HALF_WIDTH - 1; x <= LustGardenPlan.CRYPT_HALF_WIDTH + 1; x++) {
                for (int z = LustGardenPlan.CRYPT_FAR - 1; z <= LustGardenPlan.CAP_Z0; z++) {
                    Ground g = plan.ground(x, z);
                    assertTrue(g != Ground.POND && g != Ground.SHORE && g != Ground.HEDGE && g != Ground.OUTSIDE,
                            "seed " + seed + ": " + g + " over the crypt at " + x + ", " + z);
                }
            }
        }
        assertTrue(LustGardenPlan.inCrypt(0, -11, -20), "the crypt's middle");
        assertTrue(LustGardenPlan.inCrypt(0, -4, -4), "on the stair");
        assertTrue(!LustGardenPlan.inCrypt(0, 0, -20), "up on the lawn isn't in the crypt");
        assertEquals(-3, LustGardenPlan.stepY(LustGardenPlan.CAP_Z0), "the first step is just under the cap");
    }

    @Test
    void nothingIsBuiltOnAPath() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LustGardenPlan plan = new LustGardenPlan(seed);
            for (var t : plan.trees()) assertEquals(Ground.LAWN, plan.ground(t.x(), t.z()), "tree " + t);
            for (var l : plan.lanterns()) assertEquals(Ground.LAWN, plan.ground(l.x(), l.z()), "lantern " + l);
            for (var a : plan.arbors()) {
                assertEquals(Ground.LAWN, plan.ground(a.x1(), a.z1()), "arbor post " + a);
                assertEquals(Ground.LAWN, plan.ground(a.x2(), a.z2()), "arbor post " + a);
            }
            for (var g : plan.gazebos()) assertEquals(Ground.LAWN, plan.ground(g.x(), g.z()), "gazebo " + g);
            for (var s : plan.sheds()) assertEquals(Ground.LAWN, plan.ground(s.x(), s.z()), "shed " + s);
            assertTrue(plan.gazebos().size() + plan.sheds().size() >= 3, "seed " + seed + ": little buildings to find barrels in");
            assertTrue(plan.trees().size() >= 40, "seed " + seed + ": trees, got " + plan.trees().size());
        }
    }

    @Test
    void sameSeedSameGarden() {
        LustGardenPlan a = new LustGardenPlan(77), b = new LustGardenPlan(77);
        assertEquals(a.plinths(), b.plinths());
        assertEquals(a.trees(), b.trees());
        for (int x = -R; x <= R; x += 7) for (int z = -R; z <= R; z += 7) assertEquals(a.ground(x, z), b.ground(x, z));
    }

    /** Draws a garden from above into build/lust-garden-plan.png, to look at. */
    @Test
    void drawsAMap() throws Exception {
        LustGardenPlan plan = new LustGardenPlan(5);
        int size = R * 2 + 1, scale = 3;
        BufferedImage img = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                int rgb = switch (plan.ground(x, z)) {
                    case OUTSIDE -> 0x6f7a5c;
                    case SKIRT -> 0x7c8a63;
                    case HEDGE -> plan.hash(x, 0, z, 9) < 0.2 ? 0xc77fb5 : 0x3f6b2c;
                    case GATE -> 0x8a8a7a;
                    case AVENUE -> 0x9a9a8a;
                    case PATH -> 0xb59a6a;
                    case PLAZA, RIM, LEDGE -> 0x8f9188;
                    case BASIN, POND -> plan.height(x, z) == 2 ? 0x2c4f8a : 0x3f6fbf;
                    case PILLAR -> 0x5d5f57;
                    case SHORE -> 0x5e7a3a;
                    case LAWN -> plan.reserved(x, z) ? 0x9aa86c : 0x6e9a45;
                    case BORDER -> 0x4d7a35;
                };
                for (int i = 0; i < scale; i++) for (int j = 0; j < scale; j++) img.setRGB((x + R) * scale + i, (z + R) * scale + j, rgb);
            }
        }
        for (var t : plan.trees()) dot(img, t.x(), t.z(), scale, t.kind() == LustGardenPlan.Tree.Kind.CHERRY ? 0xf2a7c9 : 0x2f5a20, 2);
        for (var p : plan.plinths()) dot(img, p.x(), p.z(), scale, 0xffffff, 1);
        for (int[] p : LustGardenPlan.fountainPlinths()) dot(img, p[0], p[1], scale, 0xff4040, 1);
        for (var g : plan.gazebos()) dot(img, g.x(), g.z(), scale, 0xd08040, 4);
        for (var s : plan.sheds()) dot(img, s.x(), s.z(), scale, 0x805030, 3);
        for (var l : plan.lanterns()) dot(img, l.x(), l.z(), scale, 0xffe080, 0);
        for (var b : plan.benches()) dot(img, b.x(), b.z(), scale, 0x704020, 0);
        for (var a : plan.arbors()) {
            dot(img, a.x1(), a.z1(), scale, 0xe070c0, 0);
            dot(img, a.x2(), a.z2(), scale, 0xe070c0, 0);
        }
        for (var r : plan.ruins()) dot(img, r.x(), r.z(), scale, 0x505050, 1);
        File out = new File("build/lust-garden-plan.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }

    private static void dot(BufferedImage img, int x, int z, int scale, int rgb, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        int px = (x + dx + R) * scale + i, pz = (z + dz + R) * scale + j;
                        if (px >= 0 && pz >= 0 && px < img.getWidth() && pz < img.getHeight()) img.setRGB(px, pz, rgb);
                    }
                }
            }
        }
    }
}
