package net.ragnar.fmscraft.boss.sloth;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.fight.ArenaLayout;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Optional;

/**
 * RICKY's arena: the heart of the Abandoned City, a flooded ruin at sunset (data/fmscraft/structure/arena/sloth.nbt,
 * cut out of the world save by tools/structures/sloth_arena.py, which uses the same CORNER). One block of water over
 * gravel as far as the eye can see, six moss-streaked skyscrapers 190 blocks tall, and a green hill in the middle with a
 * glasshouse and a ruined village. The fight is in the clearing on the hill's south-west slope: flat grass at y 59 (you
 * stand at 60) with a dirt path through it, the flooded plaza and three of the towers to the west under the setting sun,
 * the hill and its big building to the north, ruins and trees south and east.
 * <p>
 * Like LalliLarge's Earth it's too big to rebuild before every fight: the whole of it (the template and the flooded
 * plain round it, out to the fog) is built once, when the server starts. Before each fight only the fighting ground is
 * wiped and put back; nothing RICKY does breaks a block, so that's the only place anyone can have dug or built.
 * Everyone arrives on the east side of the clearing looking west, into the sunset. The dimension's time stands at
 * 12500 (dimension_type/sloth_arena.json): the sun a hand's width over the western horizon, the sky gold and red.
 */
public final class SlothArena {
    /** The template's corner (its size is 381 x 198 x 501): the save's x 340, y 58, z 400. */
    public static final BlockPos CORNER = new BlockPos(-232, 58, -240);
    public static final int SIZE_X = 381, SIZE_Y = 198, SIZE_Z = 501;
    /**
     * The water's surface block (one deep, over gravel at 57 and stone at 56), and the clearing's ground: grass, a
     * trampled field of wheat and a dirt path, level with the water and a block over it here and there.
     */
    public static final int WATER_Y = 58, GROUND_Y = 58;
    /** Feet height in the middle of the clearing. */
    public static final int FLOOR_Y = 59;
    public static final Vec3d CENTER = new Vec3d(0.5, FLOOR_Y, 0.5);
    /** Everyone arrives east of the middle (on ground a block higher), looking west into the sunset over the flooded plaza. */
    public static final Vec3d PLAYER_SPAWN = new Vec3d(16.5, FLOOR_Y + 1, 0.5);
    public static final float PLAYER_YAW = 90f;
    /** He's asleep on the grass west of the middle, by the water's edge, when the wheel fades. */
    public static final Vec3d BOSS_SPAWN = new Vec3d(-9.5, FLOOR_Y, 0.5);
    /** Bottom-left of the exit portal, north of the middle (on grass at 59). An empty frame of stone bricks stands there all fight. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(1, FLOOR_Y + 1, -18);
    /** How far out from the middle the fight goes; past it (for half a second) the city drags you back. */
    public static final double RADIUS = 44;
    /** Below this you've dug down; above this you're up on a roof or a tree you've no business on. */
    public static final double MIN_Y = WATER_Y - 3, MAX_Y = FLOOR_Y + 28;
    /**
     * The flooded plain is the dimension's own ground (dimension/sloth_arena.json: a flat world of stone, gravel and one
     * block of water at these heights), endless in every direction. A world whose Sloth arena was made before it (a void,
     * with the stand-in's platform) gets it laid this far round the city when the city is first built.
     */
    public static final int PLAIN = 208;
    /** The fighting ground wiped and put back before each fight: this far round the middle, from just under the water up. */
    private static final int CLEAR = 50, CLEAR_DOWN_TO = WATER_Y - 2, CLEAR_UP_TO = FLOOR_Y + 34;

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, CORNER,
            new Box(-CLEAR, CLEAR_DOWN_TO, -CLEAR, CLEAR + 1, CLEAR_UP_TO, CLEAR + 1),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState());

    private SlothArena() {
    }

    // ---- Where things are ----

    public static double flatDistance(Vec3d pos, Vec3d to) {
        return Math.sqrt(MathHelper.square(pos.x - to.x) + MathHelper.square(pos.z - to.z));
    }

    /** Whether {@code pos} is where the fight is: in the clearing and round it, not up a tower or down a hole. */
    public static boolean inBounds(Vec3d pos) {
        return pos.y >= MIN_Y && pos.y <= MAX_Y && flatDistance(pos, CENTER) <= RADIUS;
    }

    /** Feet height on whatever's at {@code x}, {@code z} round the clearing (the ground, a slab, a wall's top). */
    public static double feetAt(ServerWorld world, double x, double z) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(x), FLOOR_Y + 8, MathHelper.floor(z));
        for (int i = 0; i < 16; i++) {
            var shape = world.getBlockState(pos).getCollisionShape(world, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
            pos.move(net.minecraft.util.math.Direction.DOWN);
        }
        return FLOOR_Y;
    }

    /** Where someone the city drags back from {@code pos} is set down: 10 in from the edge, towards where they were. */
    public static Vec3d returnSpot(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) return CENTER;
        double r = Math.min(d, RADIUS - 10);
        return new Vec3d(CENTER.x + dx / d * r, FLOOR_Y + 6, CENTER.z + dz / d * r);
    }

    // ---- Building it ----

    /** Builds the whole city when the server starts, if it isn't there yet, so no fight has to wait for it. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld world = Arenas.world(server, Sin.SLOTH);
            if (world == null || built(world)) return;
            long start = System.currentTimeMillis();
            build(world);
            FmsCraft.LOGGER.info("Built RICKY's city in {} ms", System.currentTimeMillis() - start);
        });
    }

    /** Whether the city is there (built before, on this world): the clearing, the flooded plain, a tower's mossy top. */
    public static boolean built(ServerWorld world) {
        return world.getBlockState(new BlockPos(0, GROUND_Y, 1)).isOf(Blocks.GRASS_BLOCK)
                && world.getBlockState(new BlockPos(PLAIN - 8, WATER_Y, 0)).isOf(Blocks.WATER)
                && world.getBlockState(new BlockPos(-195, 233, -85)).isOf(Blocks.GRASS_BLOCK);
    }

    /**
     * Wipes what players left on the fighting ground and puts it back as it was; builds the whole city if it isn't there
     * yet. Called by {@link Arenas#reset} before every fight.
     */
    public static void build(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        boolean whole = !built(world);
        for (Entity e : world.getEntitiesByClass(Entity.class, LAYOUT.clearBox().expand(40, 100, 40), e -> !(e instanceof PlayerEntity))) {
            e.discard();
        }
        if (whole) {
            // A world from before RICKY had the stand-in's deepslate platform here, in the middle of the clearing
            clear(world, Arenas.RADIUS + 4, Arenas.FLOOR_Y - 4, Arenas.FLOOR_Y + 40, flags);
        }
        long start = System.currentTimeMillis();
        clear(world, CLEAR, CLEAR_DOWN_TO, CLEAR_UP_TO, flags);
        long cleared = System.currentTimeMillis();
        // A world made before RICKY has void round the middle (the stand-in's): lay the plain over it. A newer one has it
        // from the dimension itself, out to the horizon, and only the fighting ground needs putting back
        boolean voidAround = world.getBlockState(new BlockPos(CLEAR + 12, WATER_Y - 1, 0)).isAir();
        plain(world, whole && voidAround ? PLAIN : CLEAR + 1, flags);
        long plain = System.currentTimeMillis();
        BlockBox ground = new BlockBox(-CLEAR, CLEAR_DOWN_TO, -CLEAR, CLEAR, CLEAR_UP_TO, CLEAR);
        place(world, whole ? null : ground);
        exitFrame(world, flags);
        if (whole) {
            FmsCraft.LOGGER.info("RICKY's city: cleared in {} ms, the plain in {} ms, the city placed in {} ms",
                    cleared - start, plain - cleared, System.currentTimeMillis() - plain);
        }
        // Plants the build left without anything to stand on pop off as it's placed: don't leave them lying round
        for (Entity dropped : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, LAYOUT.clearBox().expand(40, 100, 40), e -> true)) {
            dropped.discard();
        }
    }

    private static void clear(ServerWorld world, int radius, int fromY, int toY, int flags) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = fromY; y < toY; y++) {
                    if (!world.getBlockState(pos.set(x, y, z)).isAir()) world.setBlockState(pos, air, flags);
                }
            }
        }
    }

    /**
     * The flooded plain, {@code radius} round the middle: stone, gravel, one block of water, as the dimension makes it.
     * Only touches what isn't so already (on a world made after RICKY, nothing past the fighting ground).
     */
    private static void plain(ServerWorld world, int radius, int flags) {
        BlockState stone = Blocks.STONE.getDefaultState(), gravel = Blocks.GRAVEL.getDefaultState(), water = Blocks.WATER.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                set(world, pos.set(x, WATER_Y - 2, z), stone, flags);
                set(world, pos.set(x, WATER_Y - 1, z), gravel, flags);
                set(world, pos.set(x, WATER_Y, z), water, flags);
            }
        }
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        if (world.getBlockState(pos) != state) world.setBlockState(pos, state, flags);
    }

    /** Places the city's template, only within {@code within} if that isn't null. */
    private static void place(ServerWorld world, BlockBox within) {
        Optional<StructureTemplate> template = world.getStructureTemplateManager().getTemplate(Identifier.of(FmsCraft.MOD_ID, "arena/sloth"));
        if (template.isEmpty()) {
            FmsCraft.LOGGER.error("RICKY's arena template arena/sloth is missing");
            return;
        }
        StructurePlacementData data = new StructurePlacementData();
        if (within != null) data.setBoundingBox(within);
        template.get().place(world, CORNER, CORNER, data, world.getRandom(), Block.NOTIFY_LISTENERS);
    }

    /** An empty 4x5 frame of mossy stone bricks (cracked corners) round where the 2x3 exit portal (along x) will open. */
    private static void exitFrame(ServerWorld world, int flags) {
        for (int w = -1; w <= 2; w++) {
            for (int h = -1; h <= 3; h++) {
                boolean side = w == -1 || w == 2, end = h == -1 || h == 3;
                BlockState state = side && end ? LAYOUT.exitFrameCorner() : side || end ? LAYOUT.exitFrame() : Blocks.AIR.getDefaultState();
                world.setBlockState(EXIT_PORTAL.add(w, h, 0), state, flags);
            }
        }
    }
}
