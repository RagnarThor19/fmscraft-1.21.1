package net.ragnar.fmscraft.structure.pride;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape.Column;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape.Kind;

/**
 * Builds the whole Tower of Pride. One piece covers it; each chunk places only its own blocks, and every "random"
 * choice is hashed from the block position, so chunks always line up.
 *
 * The stone gets prouder the higher you climb: mossy, cracked stone bricks at the foot, then sandstone, then white
 * quartz, and a crown of gold at the summit. The walls between the turns of the ramp are arcades: storeys of arches
 * with dark recesses and a ledge between storeys, after Bruegel's painting. Lanterns stand on the parapet, one
 * chest waits on each turn of the ramp, and the Pride key lock stands in the middle of the summit before its portal frame.
 */
public class PrideTowerPiece extends StructurePiece {
    /** Paved and levelled ground around the foot. */
    public static final int SKIRT = 8;
    /** How far down the foundation reaches to meet uneven ground. */
    public static final int FOUNDATION = 40;
    /** Room above the summit floor for the crown. */
    public static final int CROWN = 16;
    public static final int STOREY = 8;
    public static final int BAY = 6;
    public static final RegistryKey<LootTable> RAMP_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/tower_of_pride"));
    public static final RegistryKey<LootTable> SUMMIT_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/tower_of_pride_summit"));

    private final long seed;
    private final int height;
    private final PrideTowerShape shape;

    public PrideTowerPiece(long seed, BlockPos foot, int height) {
        super(ModStructures.PRIDE_TOWER_PIECE, 0, box(foot, height));
        this.seed = seed;
        this.height = height;
        this.shape = new PrideTowerShape(height);
    }

    public PrideTowerPiece(NbtCompound nbt) {
        super(ModStructures.PRIDE_TOWER_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.height = nbt.getInt("Height");
        this.shape = new PrideTowerShape(height);
    }

    private static BlockBox box(BlockPos foot, int height) {
        int reach = PrideTowerShape.RADIUS + SKIRT;
        return new BlockBox(foot.getX() - reach, foot.getY() - FOUNDATION, foot.getZ() - reach,
                foot.getX() + reach, foot.getY() + height + CROWN, foot.getZ() + reach);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putInt("Height", height);
    }

    /** The center of the tower at ground level (the first block above the foundation). */
    public BlockPos foot() {
        return new BlockPos(boundingBox.getCenter().getX(), boundingBox.getMinY() + FOUNDATION, boundingBox.getCenter().getZ());
    }

    public int height() {
        return height;
    }

    public PrideTowerShape shape() {
        return shape;
    }

    /** Where the key lock stands: the middle of the summit. Its portal opens to the north of it. */
    public BlockPos lock() {
        return foot().add(0, height, 0);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos foot = foot();
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - foot.getX(), dz = z - foot.getZ();
                double r = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dz + 0.5) * (dz + 0.5));
                if (r > PrideTowerShape.RADIUS + SKIRT) continue;
                Column column = shape.column(dx, dz);
                int groundTop = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1 - foot.getY();
                if (column.kind() == Kind.OUTSIDE) {
                    ground(world, pos, x, z, r, groundTop);
                } else {
                    tower(world, pos, x, z, dx, dz, column, groundTop);
                }
            }
        }
        furnishSummit(world, chunkBox, random);
        placeRampChests(world, chunkBox, random);
    }

    // ---- Ground ----

    /** Levels the ground round the foot: paving near the tower, blending back into the land at the edge. */
    private void ground(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, double r, int groundTop) {
        BlockPos foot = foot();
        double t = MathHelper.clamp((PrideTowerShape.RADIUS + SKIRT - r) / (SKIRT - 2), 0, 1);
        int top = (int) Math.round(groundTop + (-1 - groundTop) * t * t * (3 - 2 * t));
        top = Math.max(top, -FOUNDATION);
        BlockState surface = world.getBlockState(pos.set(x, foot.getY() + groundTop, z));
        if (surface.isAir() || !surface.getFluidState().isEmpty() || !surface.isOpaqueFullCube(world, pos)) {
            surface = Blocks.GRASS_BLOCK.getDefaultState();
        }
        BlockState under = world.getBlockState(pos.set(x, foot.getY() + groundTop - 1, z));
        if (under.isAir() || !under.getFluidState().isEmpty() || !under.isOpaqueFullCube(world, pos)) under = Blocks.DIRT.getDefaultState();
        boolean paved = r <= PrideTowerShape.RADIUS + 3;

        int bottom = Math.max(Math.min(groundTop, top), -FOUNDATION);
        for (int y = bottom; y <= top; y++) {
            BlockState state;
            if (y == top) state = paved ? paving(x, y, z) : surface;
            else if (y >= top - 3) state = under;
            else state = Blocks.STONE.getDefaultState();
            world.setBlockState(pos.set(x, foot.getY() + y, z), state, Block.NOTIFY_LISTENERS);
        }
        // Clear what stood above the new ground (hillsides, trees)
        for (int y = top + 1; y <= Math.max(groundTop + 32, 2); y++) {
            pos.set(x, foot.getY() + y, z);
            if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private BlockState paving(int x, int y, int z) {
        double n = noise(x, y, z, 20);
        if (n < 0.35) return Blocks.STONE_BRICKS.getDefaultState();
        if (n < 0.55) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (n < 0.70) return Blocks.COBBLESTONE.getDefaultState();
        if (n < 0.82) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if (n < 0.92) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        return Blocks.COARSE_DIRT.getDefaultState();
    }

    // ---- The tower ----

    private void tower(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, int dx, int dz, Column column, int groundTop) {
        BlockPos foot = foot();
        // Foundation down to the ground
        for (int y = Math.max(Math.min(groundTop, -1), -FOUNDATION); y <= -1; y++) {
            BlockState state = y == -1 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.STONE.getDefaultState();
            world.setBlockState(pos.set(x, foot.getY() + y, z), state, Block.NOTIFY_LISTENERS);
        }
        int top = height + CROWN;
        for (int y = 0; y <= top; y++) {
            BlockState state = blockAt(dx, y, dz, column);
            pos.set(x, foot.getY() + y, z);
            if (state.isAir() && world.getBlockState(pos).isAir()) continue;
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        }
    }

    /** The block at (dx, y, dz) from the foot of the tower. */
    BlockState blockAt(int dx, int y, int dz, Column column) {
        int solid = column.fullBlocks();
        int x = foot().getX() + dx, z = foot().getZ() + dz;
        Palette p = palette(x, y, z);

        if (column.kind() == Kind.SUMMIT && y >= solid) return summit(dx, y, dz);
        if (column.kind() == Kind.PARAPET && y >= solid) return parapet(dx, y, dz, column, p);
        if (y >= solid) {
            if (y == solid && column.slab()) return p.slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            return besideWall(dx, y, dz, column);
        }
        if (y == solid - 1) {
            // The top of each column is the walkway (or the summit floor)
            if (column.kind() == Kind.SUMMIT) return summitFloor(dx, y, dz);
            return p.floor(noise(x, y, z, 1));
        }
        return wall(dx, y, dz, column, p);
    }

    /** Solid tower below the walkway: arcades on the outer face, plain stone inside. */
    private BlockState wall(int dx, int y, int dz, Column column, Palette p) {
        double cx = dx + 0.5, cz = dz + 0.5;
        int x = foot().getX() + dx, z = foot().getZ() + dz;
        double depth = shape.depth(cx, cz, y);
        if (depth >= 3) return p.inside;
        if (depth < 0) return p.main(noise(x, y, z, 2)); // corner of a voxel circle poking out: keep it solid

        int row = Math.floorMod(y, STOREY);
        int bay = Math.floorMod((int) Math.floor(shape.along(cx, cz, y)), BAY);
        boolean underWalkway = y >= column.fullBlocks() - 3;
        if (!underWalkway && opening(row, bay)) return depth < 2 ? Blocks.AIR.getDefaultState() : p.recess;
        if (depth >= 1) return p.main(noise(x, y, z, 2));
        if (row == 0) return p.ledge;
        if (row == 7 && bay == 3) return p.keystone;
        return p.main(noise(x, y, z, 2));
    }

    /** A round-topped arch three blocks wide in the middle of each bay. */
    private static boolean opening(int row, int bay) {
        if (bay == 3) return row >= 2 && row <= 6;
        return (bay == 2 || bay == 4) && row >= 2 && row <= 5;
    }

    /** Open air over the walkway, right in front of a wall: ledges between storeys, and vines on the old stone. */
    private BlockState besideWall(int dx, int y, int dz, Column column) {
        double cx = dx + 0.5, cz = dz + 0.5;
        if (y >= height) return Blocks.AIR.getDefaultState();
        double depth = shape.depth(cx, cz, y);
        if (depth < -1 || depth >= 0) return Blocks.AIR.getDefaultState();
        int x = foot().getX() + dx, z = foot().getZ() + dz;
        int above = y - column.fullBlocks();
        Palette p = palette(x, y, z);
        if (Math.floorMod(y, STOREY) == 0 && above >= 4) return p.ledgeSlab.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);

        if (p == Palette.STONE && above >= 2 && noise(x, 0, z, 3) < 0.22 && noise(x, y >> 2, z, 4) < 0.6) {
            Direction toWall = inward(dx, dz);
            BlockPos.Mutable n = new BlockPos.Mutable(dx + toWall.getOffsetX(), y, dz + toWall.getOffsetZ());
            Column next = shape.column(n.getX(), n.getZ());
            if (y < next.fullBlocks() - 1 && wall(n.getX(), y, n.getZ(), next, p).isOpaque()) {
                return Blocks.VINE.getDefaultState().with(vineSide(toWall), true);
            }
        }
        return Blocks.AIR.getDefaultState();
    }

    private static BooleanProperty vineSide(Direction toWall) {
        return switch (toWall) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST;
            default -> VineBlock.WEST;
        };
    }

    /** The horizontal direction pointing most nearly at the tower's center. */
    private static Direction inward(int dx, int dz) {
        double cx = dx + 0.5, cz = dz + 0.5;
        if (Math.abs(cx) > Math.abs(cz)) return cx > 0 ? Direction.WEST : Direction.EAST;
        return cz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** The low wall along the open edge of the ramp: battlements, with a lantern on every third merlon. */
    private BlockState parapet(int dx, int y, int dz, Column column, Palette p) {
        int above = y - column.fullBlocks();
        double cx = dx + 0.5, cz = dz + 0.5;
        int step = (int) Math.floor(PrideTowerShape.angle(cx, cz) * Math.sqrt(cx * cx + cz * cz));
        boolean merlon = Math.floorMod(step, 5) < 2;
        boolean lantern = merlon && Math.floorMod(step, 15) == 0;
        if (above <= 1) return p.parapet;
        if (above == 2 && merlon) return p.parapet;
        if (above == 3 && lantern) return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        return Blocks.AIR.getDefaultState();
    }

    // ---- The summit ----

    private BlockState summitFloor(int dx, int y, int dz) {
        double r = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dz + 0.5) * (dz + 0.5));
        if (r < 2.5 || (r >= 6 && r < 7)) return Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
        if (r > PrideTowerShape.SUMMIT_RADIUS - 2) return Blocks.QUARTZ_BRICKS.getDefaultState();
        return (Math.floorMod(dx + dz, 2) == 0) ? Blocks.SMOOTH_QUARTZ.getDefaultState() : Blocks.QUARTZ_BLOCK.getDefaultState();
    }

    /**
     * Above the summit floor: a ring of quartz columns carrying a lintel, every other one rising on into a point of
     * the crown, capped with gold and a glowing rod you can see from far off.
     */
    private BlockState summit(int dx, int y, int dz) {
        double cx = dx + 0.5, cz = dz + 0.5;
        double r = Math.sqrt(cx * cx + cz * cz);
        int above = y - height;
        double ring = PrideTowerShape.SUMMIT_RADIUS - 2.5; // just inside the parapet
        if (Math.abs(r - ring) >= 0.75) return Blocks.AIR.getDefaultState();

        double degrees = Math.toDegrees(PrideTowerShape.angle(cx, cz));
        int index = (int) Math.round(degrees / 30) % 12;
        boolean column = Math.abs(degrees - Math.round(degrees / 30) * 30) * Math.PI / 180 * r < 0.75;
        boolean point = column && index % 2 == 0;
        if (above == 5) return Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState().with(SlabBlock.TYPE, column ? SlabType.DOUBLE : SlabType.TOP);
        if (!column) return Blocks.AIR.getDefaultState();
        if (above < 5) return Blocks.QUARTZ_PILLAR.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        if (!point) return Blocks.AIR.getDefaultState();
        if (above < 10) return Blocks.QUARTZ_PILLAR.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        if (above == 10) return Blocks.GOLD_BLOCK.getDefaultState();
        if (above == 11) return Blocks.END_ROD.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    /** The key lock before a gold-cornered portal frame, with a chest either side of it. */
    private void furnishSummit(StructureWorldAccess world, BlockBox chunkBox, Random random) {
        BlockPos lock = lock();
        if (chunkBox.contains(lock)) {
            world.setBlockState(lock, SinPortalFrameBlock.of(Sin.PRIDE, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        }
        BlockState chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH);
        placeContainer(world, chunkBox, random, lock.west(2), chest, SUMMIT_LOOT);
        placeContainer(world, chunkBox, random, lock.east(2), chest, SUMMIT_LOOT);

        // The frame: 5x5 with a 3x3 opening, bottom row set into the floor
        BlockPos frameCenter = lock.north(SinPortalFrameBlock.PORTAL_DISTANCE).down();
        for (int l = -2; l <= 2; l++) {
            for (int h = 0; h <= 4; h++) {
                boolean edge = Math.abs(l) == 2 || h == 0 || h == 4;
                BlockPos at = frameCenter.east(l).up(h);
                if (!edge || !chunkBox.contains(at)) continue;
                BlockState block;
                if (Math.abs(l) == 2 && (h == 0 || h == 4)) block = Blocks.GOLD_BLOCK.getDefaultState();
                else if (Math.abs(l) == 2) block = Blocks.QUARTZ_PILLAR.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
                else if (h == 4) block = Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
                else block = Blocks.SMOOTH_QUARTZ.getDefaultState();
                world.setBlockState(at, block, Block.NOTIFY_LISTENERS);
            }
        }
    }

    /** One chest on every turn of the ramp, halfway round, against the parapet. */
    private void placeRampChests(StructureWorldAccess world, BlockBox chunkBox, Random random) {
        BlockPos foot = foot();
        for (int m = 0; m < PrideTowerShape.TURNS; m++) {
            int[] spot = rampChest(m);
            if (spot == null) continue;
            int dx = spot[0], dz = spot[1];
            Column column = shape.column(dx, dz);
            BlockPos at = foot.add(dx, column.fullBlocks(), dz);
            if (!chunkBox.contains(at) || !chunkBox.contains(at.up())) continue;
            if (column.slab()) {
                world.setBlockState(at, palette(at.getX(), at.getY() - foot.getY(), at.getZ()).floor(0.5), Block.NOTIFY_LISTENERS);
                at = at.up();
            }
            placeContainer(world, chunkBox, random, at, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST), RAMP_LOOT);
        }
    }

    /**
     * Where turn {@code m}'s chest stands, relative to the foot: on the west side of the tower, on the first walkway
     * block in from the parapet, so its back is right up against it. Null if that turn has no such spot.
     */
    public int[] rampChest(int m) {
        int dz = 0;
        for (int dx = -PrideTowerShape.RADIUS - 1; dx < 0; dx++) {
            if (PrideTowerShape.turn(dx + 0.5, dz + 0.5) != m) continue;
            if (shape.column(dx, dz).kind() == Kind.RAMP && shape.column(dx - 1, dz).kind() == Kind.PARAPET) return new int[]{dx, dz};
        }
        return null;
    }

    private static void placeContainer(StructureWorldAccess world, BlockBox chunkBox, Random random, BlockPos pos, BlockState state,
                                       RegistryKey<LootTable> loot) {
        if (!chunkBox.contains(pos)) return;
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) container.setLootTable(loot, random.nextLong());
    }

    // ---- Materials ----

    /** The stone used at each height. The bands blend into each other a little, like a builder's gradient. */
    Palette palette(int x, int y, int z) {
        // Dithered in 2x2x2 clumps, so the change reads as weathering rather than speckle
        double f = (double) y / height + (noise(x >> 1, y >> 1, z >> 1, 10) - 0.5) * 0.07;
        if (f < 0.34) return Palette.STONE;
        if (f < 0.68) return Palette.SANDSTONE;
        return Palette.QUARTZ;
    }

    enum Palette {
        STONE(Blocks.CHISELED_STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB, Blocks.TUFF_BRICKS,
                Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE),
        SANDSTONE(Blocks.CHISELED_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.TERRACOTTA,
                Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE),
        QUARTZ(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_SLAB, Blocks.POLISHED_BLACKSTONE,
                Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ);

        final BlockState keystone;
        final BlockState ledge;
        final Block ledgeSlab;
        final BlockState recess;
        final BlockState parapet;
        final Block slab;
        final BlockState inside;

        Palette(Block keystone, Block ledge, Block ledgeSlab, Block recess, Block parapet, Block slab, Block inside) {
            this.keystone = keystone.getDefaultState();
            this.ledge = ledge.getDefaultState();
            this.ledgeSlab = ledgeSlab;
            this.recess = recess.getDefaultState();
            this.parapet = parapet.getDefaultState();
            this.slab = slab;
            this.inside = inside.getDefaultState();
        }

        BlockState main(double n) {
            return switch (this) {
                case STONE -> n < 0.28 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
                        : n < 0.42 ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
                case SANDSTONE -> n < 0.6 ? Blocks.SMOOTH_SANDSTONE.getDefaultState()
                        : n < 0.85 ? Blocks.CUT_SANDSTONE.getDefaultState() : Blocks.SANDSTONE.getDefaultState();
                case QUARTZ -> n < 0.5 ? Blocks.QUARTZ_BRICKS.getDefaultState()
                        : n < 0.95 ? Blocks.SMOOTH_QUARTZ.getDefaultState() : Blocks.QUARTZ_BLOCK.getDefaultState();
            };
        }

        BlockState floor(double n) {
            return switch (this) {
                case STONE -> n < 0.25 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
                case SANDSTONE -> Blocks.SMOOTH_SANDSTONE.getDefaultState();
                case QUARTZ -> Blocks.SMOOTH_QUARTZ.getDefaultState();
            };
        }
    }

    private double noise(int x, int y, int z, int salt) {
        long h = MathHelper.hashCode(x, y * 31 + salt, z) ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
