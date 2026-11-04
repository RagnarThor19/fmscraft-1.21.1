package net.ragnar.fmscraft.boss.gluttony;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SnowyBlock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.fight.ArenaLayout;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LalliLarge's two worlds, both in the Gluttony arena dimension, far apart.
 * <ul>
 *   <li><b>The Earth</b> (phase 1): a hollow globe 258 blocks across hanging in space (structure/arena/gluttony.nbt,
 *       tools/structures/gluttony_arena.py). The fight is on its north pole, a snowy ice cap that falls away gently
 *       (2 blocks lower 20 out, 7 lower 40 out). Space is drawn round it by GluttonySky: black, stars, the sun.</li>
 *   <li><b>The snowfield</b> (phase 2): flat snowy ground 1000 blocks south, built in code, under a grey sky with snow
 *       falling. The ruined castle (structure/arena/gluttony_castle.nbt, snowed over) stands in the distance to the
 *       north; seven giant statues (drawn by GluttonySky, they aren't really there) stand round the fight, nodding to
 *       SIRENS.</li>
 * </ul>
 * The whole of both is built once, when the server starts (it's big: a few seconds the first time, while the chunks are
 * made). Before each fight only the fighting ground is wiped and put back: nothing he does breaks a block, so that's
 * the only place players can have dug or built. Checked by the gluttonyFight game test.
 */
public final class GluttonyArena {
    // ---- The Earth ----
    /** The Earth template's corner: its north pole (template column 128, 128, top y 257) comes out at 0, 265, 0. */
    public static final BlockPos EARTH_CORNER = new BlockPos(-128, 8, -128);
    public static final int POLE_BLOCK_Y = 265, POLE_Y = POLE_BLOCK_Y + 1;
    public static final Vec3d POLE = new Vec3d(0.5, POLE_Y, 0.5);
    public static final Vec3d EARTH_CENTER = new Vec3d(0.5, EARTH_CORNER.getY() + 128.5, 0.5);
    /** Players wait behind the wheel on the pole, a little south of the middle. */
    public static final Vec3d PLAYER_SPAWN = POLE.add(0, 0, 10);
    public static final float PLAYER_YAW = 180f;
    /** He starts high over the pole, unseen until he appears (see {@link LalliTimeline}). */
    public static final Vec3d BOSS_SPAWN = POLE.add(0, LalliTimeline.BOSS_HEIGHT, 0);
    /** Off the pole further than this (flat), or this far under it, and the cold wind carries you back. */
    public static final double EARTH_RADIUS = 58, EARTH_DEPTH = 24;
    /** Where the cold wind sets you down again: this far out from the pole. */
    public static final double EARTH_RETURN = 36;
    /** The patch of the pole that's wiped and put back before a fight (players may have built or dug there). */
    private static final int EARTH_CLEAR = 64, EARTH_CLEAR_DOWN = 30, EARTH_CLEAR_UP = 60;

    // ---- The snowfield ----
    public static final int SNOW_Z = 1000;
    public static final int SNOW_BLOCK_Y = 63, SNOW_Y = 64;
    public static final Vec3d SNOW_CENTER = new Vec3d(0.5, SNOW_Y, SNOW_Z + 0.5);
    /** How far the snowfield reaches (well past the fog), and how far out you can go before the wind turns you back. */
    public static final int SNOW_FLOOR = 170;
    public static final double SNOW_RADIUS = 62, SNOW_RETURN = 48;
    private static final int SNOW_CLEAR = 72, SNOW_CLEAR_UP = 48;
    /** Players are set down round him this far out when the fight comes here. */
    public static final double SNOW_RING = 14;
    /** The castle in the distance, north (its template is 74 x 53 x 70; its ground is at template y 2-3). */
    public static final BlockPos CASTLE_CORNER = new BlockPos(-37, SNOW_BLOCK_Y - 2, SNOW_Z - 112 - 35);
    /** The giant statues standing round the fight, 45 blocks tall, all facing it. */
    public static final float STATUE_HEIGHT = 45f;
    /** Where each statue stands round the snowfield's middle: compass angle (0 east, 90 south) and how far out. */
    private static final double[][] STATUE_SPOTS = {{5, 84}, {52, 96}, {97, 80}, {141, 92}, {187, 86}, {222, 100}, {328, 90}};
    public static final List<Statue> STATUES = statues();

    /** A statue: where its feet are and which way it faces (yaw as for a mob: 0 faces south, 90 west). */
    public record Statue(Vec3d pos, float yaw) {
    }

    private static List<Statue> statues() {
        List<Statue> out = new ArrayList<>();
        for (double[] spot : STATUE_SPOTS) {
            double a = Math.toRadians(spot[0]);
            double x = SNOW_CENTER.x + Math.cos(a) * spot[1], z = SNOW_CENTER.z + Math.sin(a) * spot[1];
            float yaw = (float) Math.toDegrees(Math.atan2(SNOW_CENTER.z - z, SNOW_CENTER.x - x)) - 90f;
            out.add(new Statue(new Vec3d(x, SNOW_Y, z), yaw));
        }
        return List.copyOf(out);
    }
    /** Bottom-left of the exit portal, north of the middle of the snowfield. An empty frame stands there all fight. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(0, SNOW_Y, SNOW_Z - 30);
    /** The client tells which world it's in by this: anywhere past it is the snowfield. */
    public static final double SNOW_FROM_Z = SNOW_Z / 2.0;

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, EARTH_CORNER,
            new Box(-140, 0, -140, 141, 512, 141), Blocks.PACKED_ICE.getDefaultState(), Blocks.BLUE_ICE.getDefaultState());

    private GluttonyArena() {
    }

    // ---- Where things are ----

    public static double flatDistance(double x, double z, Vec3d center) {
        return Math.sqrt((x - center.x) * (x - center.x) + (z - center.z) * (z - center.z));
    }

    public static boolean inSnowfield(Vec3d pos) {
        return pos.z > SNOW_FROM_Z;
    }

    /** The middle of the world {@code pos} is in: the pole or the middle of the snowfield. */
    public static Vec3d centerFor(Vec3d pos) {
        return inSnowfield(pos) ? SNOW_CENTER : POLE;
    }

    /** Whether {@code pos} is where the fight is (not blown off the pole or out into the snowfield's blizzard). */
    public static boolean inBounds(Vec3d pos) {
        if (inSnowfield(pos)) return flatDistance(pos.x, pos.z, SNOW_CENTER) <= SNOW_RADIUS && pos.y > SNOW_Y - 4;
        return flatDistance(pos.x, pos.z, POLE) <= EARTH_RADIUS && pos.y > POLE_Y - EARTH_DEPTH;
    }

    // ---- Building it ----

    /** Builds both worlds when the server starts, if they aren't there yet, so no fight has to wait for it. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld world = Arenas.world(server, Sin.GLUTTONY);
            if (world == null || built(world)) return;
            long start = System.currentTimeMillis();
            build(world);
            FmsCraft.LOGGER.info("Built LalliLarge's Earth and snowfield in {} ms", System.currentTimeMillis() - start);
        });
    }

    /** Whether the Earth and the snowfield are both there (built before, on this world). */
    public static boolean built(ServerWorld world) {
        return !world.getBlockState(new BlockPos(0, POLE_BLOCK_Y, 0)).isAir()
                && !world.getBlockState(new BlockPos(0, EARTH_CORNER.getY(), 0)).isAir()
                && world.getBlockState(new BlockPos(0, SNOW_BLOCK_Y, SNOW_Z)).isOf(Blocks.GRASS_BLOCK)
                && !world.getBlockState(new BlockPos(SNOW_FLOOR - 2, SNOW_BLOCK_Y, SNOW_Z)).isAir();
    }

    /**
     * Wipes what players left on the fighting ground and puts it back as it was; builds both worlds whole if they
     * aren't there yet.
     */
    public static void build(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        boolean whole = !built(world);
        clearEntities(world);
        // A world from before LalliLarge had the stand-in's deepslate platform here (it'd hang inside the hollow Earth)
        if (whole) clear(world, new Vec3d(0.5, 0, 0.5), Arenas.RADIUS + 4, Arenas.FLOOR_Y - 4, Arenas.FLOOR_Y + 40, flags);
        clear(world, POLE, EARTH_CLEAR, POLE_BLOCK_Y - EARTH_CLEAR_DOWN, POLE_BLOCK_Y + EARTH_CLEAR_UP, flags);
        clear(world, SNOW_CENTER, SNOW_CLEAR, SNOW_Y, SNOW_Y + SNOW_CLEAR_UP, flags);
        // The Earth whole the first time; after that just the patch round the pole that was wiped
        BlockBox pole = new BlockBox(-EARTH_CLEAR, POLE_BLOCK_Y - EARTH_CLEAR_DOWN, -EARTH_CLEAR,
                EARTH_CLEAR + 1, POLE_BLOCK_Y + EARTH_CLEAR_UP, EARTH_CLEAR + 1);
        place(world, "arena/gluttony", EARTH_CORNER, whole ? null : pole);
        snowfield(world, flags);
        place(world, "arena/gluttony_castle", CASTLE_CORNER, null);
        // The castle's old glowstone corner markers (since dropped from the template) may still stand in older worlds
        for (BlockPos marker : new BlockPos[]{CASTLE_CORNER.add(0, 52, 69), CASTLE_CORNER.add(73, 0, 0)}) {
            if (world.getBlockState(marker).isOf(Blocks.GLOWSTONE)) world.setBlockState(marker, Blocks.AIR.getDefaultState(), flags);
            if (world.getBlockState(marker.up()).isOf(Blocks.SNOW)) world.setBlockState(marker.up(), Blocks.AIR.getDefaultState(), flags);
        }
        exitFrame(world, flags);
    }

    private static void clearEntities(ServerWorld world) {
        for (Box box : new Box[]{LAYOUT.clearBox(), new Box(-SNOW_FLOOR, 0, SNOW_Z - SNOW_FLOOR, SNOW_FLOOR + 1, 512, SNOW_Z + SNOW_FLOOR + 1)}) {
            for (Entity e : world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity))) e.discard();
        }
    }

    private static void clear(ServerWorld world, Vec3d center, int radius, int fromY, int toY, int flags) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > radius * radius) continue;
                for (int y = fromY; y < toY; y++) {
                    if (!world.getBlockState(pos.set(x, y, z)).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
                }
            }
        }
    }

    /** Places a template, only within {@code within} if that isn't null. */
    private static void place(ServerWorld world, String name, BlockPos corner, BlockBox within) {
        Optional<StructureTemplate> template = world.getStructureTemplateManager().getTemplate(Identifier.of(FmsCraft.MOD_ID, name));
        if (template.isEmpty()) {
            FmsCraft.LOGGER.error("LalliLarge's arena template {} is missing", name);
            return;
        }
        StructurePlacementData data = new StructurePlacementData();
        if (within != null) data.setBoundingBox(within);
        template.get().place(world, corner, corner, data, world.getRandom(), Block.NOTIFY_LISTENERS);
    }

    /** Snowy grass under a thin layer of snow, as far as the fog lets anyone see (the superflat snowy kingdom). */
    private static void snowfield(ServerWorld world, int flags) {
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState grass = Blocks.GRASS_BLOCK.getDefaultState().with(SnowyBlock.SNOWY, true);
        BlockState snow = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -SNOW_FLOOR; x <= SNOW_FLOOR; x++) {
            for (int z = -SNOW_FLOOR; z <= SNOW_FLOOR; z++) {
                if (x * x + z * z > SNOW_FLOOR * SNOW_FLOOR) continue;
                set(world, pos.set(x, SNOW_BLOCK_Y - 1, SNOW_Z + z), dirt, flags);
                set(world, pos.set(x, SNOW_BLOCK_Y, SNOW_Z + z), grass, flags);
                set(world, pos.set(x, SNOW_Y, SNOW_Z + z), snow, flags);
            }
        }
    }

    /** Only touches blocks that changed, so rebuilding a snowfield that's already there costs next to nothing. */
    private static void set(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        if (world.getBlockState(pos) != state) world.setBlockState(pos, state, flags);
    }

    /** An empty 4x5 frame of packed ice (blue ice corners) round where the 2x3 exit portal (along x) will open. */
    private static void exitFrame(ServerWorld world, int flags) {
        BlockPos base = EXIT_PORTAL;
        for (int w = -1; w <= 2; w++) {
            for (int h = -1; h <= 3; h++) {
                boolean side = w == -1 || w == 2, end = h == -1 || h == 3;
                BlockState state = side && end ? LAYOUT.exitFrameCorner() : side || end ? LAYOUT.exitFrame() : Blocks.AIR.getDefaultState();
                world.setBlockState(base.add(w, h, 0), state, flags);
            }
        }
    }
}
