package net.ragnar.fmscraft.structure.lust;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.Fluids;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.lust.LustGardenPlan.Ground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a whole Lust's Garden from its {@link LustGardenPlan}. One piece covers it; each chunk places only its own
 * blocks, and every "random" choice is hashed from the position, so chunks always line up.
 *
 * Azalea hedges, flowering here and there, wall it in. Inside: lawns thick with flowers, cherry and azalea trees,
 * ponds with lily pads, gravel and dirt paths edged with low hedges, a paved tuff avenue lit by lanterns, little
 * pavilions and sheds, arches of leaves over the paths, broken old walls, and the statues' chiseled tuff plinths.
 * In the middle, the fountain, with four empty plinths round it. Under the basin, hidden by its floor, a stair goes
 * down to the crypt and the Lust lock.
 */
public class LustGardenPiece extends StructurePiece {
    public static final RegistryKey<LootTable> BARREL_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/lusts_garden_barrel"));
    public static final RegistryKey<LootTable> CRYPT_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/lusts_garden_crypt"));
    /** How far down the ground is filled in to meet the land, and how high above it everything is cleared. */
    public static final int FOUNDATION = 24, CLEAR = 40;
    /** The crypt's chests stand either side of the lock, this far off it. */
    private static final int CHEST_OFFSET = 3;

    private final long seed;
    private LustGardenPlan plan;
    /** Everything built rather than grown, by local position. */
    private Map<Long, BlockState> features;
    private final List<BlockPos> barrels = new ArrayList<>();
    private final List<BlockPos> chests = new ArrayList<>();
    private final List<BlockPos> spouts = new ArrayList<>();

    public LustGardenPiece(long seed, BlockPos origin) {
        super(ModStructures.LUST_GARDEN_PIECE, 0, box(origin));
        this.seed = seed;
    }

    public LustGardenPiece(NbtCompound nbt) {
        super(ModStructures.LUST_GARDEN_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
    }

    private static BlockBox box(BlockPos origin) {
        int r = LustGardenPlan.REACH;
        return new BlockBox(origin.getX() - r, origin.getY() - FOUNDATION, origin.getZ() - r,
                origin.getX() + r, origin.getY() + CLEAR, origin.getZ() + r);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
    }

    public LustGardenPlan plan() {
        if (plan == null) plan = new LustGardenPlan(seed);
        return plan;
    }

    // ---- Coordinates ----

    /** The middle of the fountain, on the garden's level (the first block above the grass). */
    public BlockPos origin() {
        return new BlockPos(boundingBox.getCenter().getX(), boundingBox.getMinY() + FOUNDATION, boundingBox.getCenter().getZ());
    }

    public long id() {
        return origin().asLong();
    }

    public BlockPos toWorld(int x, int y, int z) {
        return origin().add(x, y, z);
    }

    public BlockPos toLocal(BlockPos pos) {
        return pos.subtract(origin());
    }

    /** Inside the hedge (or down in the crypt): where the garden's rules hold. */
    public boolean inGarden(BlockPos pos) {
        BlockPos local = toLocal(pos);
        if (local.getY() < LustGardenPlan.CRYPT_FLOOR - 1 || local.getY() > CLEAR) return false;
        return plan().inside(local.getX(), local.getZ()) || LustGardenPlan.inCrypt(local.getX(), local.getY(), local.getZ());
    }

    public boolean inCrypt(BlockPos pos) {
        BlockPos local = toLocal(pos);
        return LustGardenPlan.inCrypt(local.getX(), local.getY(), local.getZ());
    }

    // ---- What the runtime needs ----

    /** Where each statue stands: on top of its plinth. */
    public List<BlockPos> statueHomes() {
        return plan().plinths().stream().map(p -> toWorld(p.x(), 1, p.z())).toList();
    }

    public float statueYaw(int i) {
        return plan().plinths().get(i).yaw();
    }

    /** On top of each of the fountain's four empty plinths: where a statue settles. */
    public List<BlockPos> fountainHomes() {
        return LustGardenPlan.fountainPlinths().stream().map(p -> toWorld(p[0], 1, p[1])).toList();
    }

    /** The two candles at the foot of fountain plinth {@code i}, lit once its statue is home. */
    public List<BlockPos> fountainCandles(int i) {
        int[] p = LustGardenPlan.fountainPlinths().get(i);
        int ox = Integer.signum(p[0]), oz = Integer.signum(p[1]);
        return List.of(toWorld(p[0] + ox - oz, 0, p[1] + oz + ox), toWorld(p[0] + ox + oz, 0, p[1] + oz - ox));
    }

    /** Which statues face which way when settled: toward the fountain. */
    public float fountainYaw(int i) {
        int[] p = LustGardenPlan.fountainPlinths().get(i);
        return (float) Math.toDegrees(Math.atan2(p[0], -p[1]));
    }

    /** Row {@code row} (0 first, nearest the pillar) of the basin floor over the stair. */
    public List<BlockPos> capRow(int row) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) blocks.add(toWorld(x, LustGardenPlan.CAP_Y, LustGardenPlan.CAP_Z0 - row));
        return blocks;
    }

    public static final int CAP_ROWS = LustGardenPlan.CAP_Z0 - LustGardenPlan.CAP_Z1 + 1;

    public List<BlockPos> capBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        for (int row = 0; row < CAP_ROWS; row++) blocks.addAll(capRow(row));
        return blocks;
    }

    /** What the cap block at this world position is, when shut. */
    public BlockState capState(BlockPos pos) {
        BlockPos local = toLocal(pos);
        return basinFloor(local.getX(), local.getZ());
    }

    /** Every water block of the basin (all sources, one deep). */
    public List<BlockPos> basinWater() {
        List<BlockPos> water = new ArrayList<>();
        int r = (int) Math.ceil(LustGardenPlan.BASIN);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) if (plan().ground(x, z) == Ground.BASIN) water.add(toWorld(x, -1, z));
        }
        return water;
    }

    /** The four water sources that spill from the fountain's spouts. */
    public List<BlockPos> spouts() {
        int y = LustGardenPlan.SPOUT_Y;
        return List.of(toWorld(0, y, -2), toWorld(2, y, 0), toWorld(0, y, 2), toWorld(-2, y, 0));
    }

    public BlockPos lock() {
        return toWorld(0, LustGardenPlan.CRYPT_FLOOR + 1, LustGardenPlan.LOCK_Z);
    }

    public List<BlockPos> barrels() {
        features();
        return barrels.stream().map(p -> toWorld(p.getX(), p.getY(), p.getZ())).toList();
    }

    public List<BlockPos> chests() {
        features();
        return chests.stream().map(p -> toWorld(p.getX(), p.getY(), p.getZ())).toList();
    }

    // ---- Building ----

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        LustGardenPlan plan = plan();
        features();
        BlockPos o = origin();
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int lx = x - o.getX(), lz = z - o.getZ();
                Ground g = plan.ground(lx, lz);
                if (g == Ground.OUTSIDE) continue;
                int groundTop = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1 - o.getY();
                if (g == Ground.SKIRT || (g == Ground.AVENUE && plan.skirtBlend(lx, lz) > 0)) {
                    skirt(world, pos, x, z, lx, lz, g, groundTop);
                } else {
                    column(world, pos, x, z, lx, lz, g, groundTop);
                }
            }
        }
        for (BlockPos local : barrels) fill(world, chunkBox, random, local, BARREL_LOOT);
        for (BlockPos local : chests) fill(world, chunkBox, random, local, CRYPT_LOOT);
        for (BlockPos local : spouts) {
            BlockPos at = toWorld(local.getX(), local.getY(), local.getZ());
            if (chunkBox.contains(at)) world.scheduleFluidTick(at, Fluids.WATER, 1);
        }
    }

    private void fill(StructureWorldAccess world, BlockBox chunkBox, Random random, BlockPos local, RegistryKey<LootTable> loot) {
        BlockPos at = toWorld(local.getX(), local.getY(), local.getZ());
        if (chunkBox.contains(at) && world.getBlockEntity(at) instanceof LootableContainerBlockEntity container) {
            container.setLootTable(loot, random.nextLong());
        }
    }

    private void set(StructureWorldAccess world, BlockPos.Mutable pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        if (state.isAir() && world.getBlockState(pos).isAir()) return;
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    /** Outside the hedge: the ground eases from the garden's level back to the land's, and stays natural. */
    private void skirt(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, int lx, int lz, Ground g, int groundTop) {
        int oy = origin().getY();
        double blend = plan().skirtBlend(lx, lz);
        int top = (int) Math.round(-1 + (groundTop + 1) * blend);
        top = Math.max(top, -FOUNDATION);
        BlockState surface = world.getBlockState(pos.set(x, oy + groundTop, z));
        if (surface.isAir() || !surface.getFluidState().isEmpty() || !surface.isOpaqueFullCube(world, pos) || surface.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
            surface = Blocks.GRASS_BLOCK.getDefaultState();
        }
        boolean paved = g == Ground.AVENUE && hash(lx, top, lz, 40) > blend * 0.9;
        int bottom = Math.max(Math.min(groundTop, top - 3), -FOUNDATION);
        for (int y = bottom; y <= top; y++) {
            BlockState state;
            if (y == top) state = paved ? avenue(lx, lz) : surface;
            else if (y >= top - 3) state = Blocks.DIRT.getDefaultState();
            else state = Blocks.STONE.getDefaultState();
            set(world, pos, x, oy + y, z, state);
        }
        for (int y = top + 1; y <= CLEAR; y++) {
            BlockState state = Blocks.AIR.getDefaultState();
            if (y == top + 1 && !paved && surface.isOf(Blocks.GRASS_BLOCK) && hash(lx, y, lz, 41) < 0.12) state = Blocks.SHORT_GRASS.getDefaultState();
            BlockState feature = features.get(BlockPos.asLong(lx, y, lz));
            if (feature != null && y > top) state = feature;
            else state = hedgeLump(lx, y - top - 1, lz, state);
            set(world, pos, x, oy + y, z, state);
        }
    }

    /** A column inside the skirt: ground, then whatever stands on it. */
    private void column(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, int lx, int lz, Ground g, int groundTop) {
        int oy = origin().getY();
        boolean underCrypt = inCryptBox(lx, lz);
        int bottom = Math.max(Math.min(groundTop, -5), -FOUNDATION);
        if (underCrypt) bottom = Math.min(bottom, LustGardenPlan.CRYPT_FLOOR - 1);
        for (int y = bottom; y <= -1; y++) {
            BlockState state = underCrypt ? crypt(lx, y, lz) : null;
            if (state == null) state = features.get(BlockPos.asLong(lx, y, lz));
            if (state == null) state = below(g, lx, y, lz);
            set(world, pos, x, oy + y, z, state);
        }
        for (int y = 0; y <= CLEAR; y++) {
            BlockState state = features.get(BlockPos.asLong(lx, y, lz));
            if (state == null) state = above(g, lx, y, lz);
            set(world, pos, x, oy + y, z, state);
        }
    }

    // ---- Ground ----

    private double hash(int x, int y, int z, int salt) {
        return plan().hash(x, y, z, salt);
    }

    private BlockState below(Ground g, int x, int y, int z) {
        if (y == -1) return surface(g, x, z);
        if (g == Ground.POND && y >= -1 - plan().height(x, z)) {
            return y > -1 - plan().height(x, z) ? Blocks.WATER.getDefaultState() : pondFloor(x, y, z);
        }
        if (g == Ground.BASIN && y == LustGardenPlan.CAP_Y) return basinFloor(x, z);
        if (g == Ground.BASIN || g == Ground.LEDGE || g == Ground.RIM || g == Ground.PILLAR) {
            return y >= -4 ? Blocks.TUFF.getDefaultState() : Blocks.STONE.getDefaultState();
        }
        if (y >= -4) return g == Ground.POND ? Blocks.CLAY.getDefaultState() : Blocks.DIRT.getDefaultState();
        return Blocks.STONE.getDefaultState();
    }

    private BlockState surface(Ground g, int x, int z) {
        return switch (g) {
            case AVENUE, GATE -> avenue(x, z);
            case PATH -> path(x, z);
            case PLAZA -> plaza(x, z);
            case RIM, LEDGE -> Blocks.POLISHED_TUFF.getDefaultState();
            case PILLAR -> Blocks.TUFF_BRICKS.getDefaultState();
            case BASIN -> Blocks.WATER.getDefaultState();
            case POND -> Blocks.WATER.getDefaultState();
            case SHORE -> {
                double n = hash(x, -1, z, 11);
                yield n < 0.5 ? Blocks.GRASS_BLOCK.getDefaultState() : n < 0.8 ? Blocks.MOSS_BLOCK.getDefaultState()
                        : n < 0.9 ? Blocks.MUD.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState();
            }
            case HEDGE -> hash(x, -1, z, 12) < 0.3 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            default -> plan().smooth(x, z, 6, 13) > 0.8 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
        };
    }

    private BlockState avenue(int x, int z) {
        if (plan().edge(x, z) > -1) return Blocks.POLISHED_TUFF.getDefaultState(); // the kerb
        double n = hash(x, -1, z, 14);
        if (n < 0.38) return Blocks.TUFF_BRICKS.getDefaultState();
        if (n < 0.60) return Blocks.POLISHED_TUFF.getDefaultState();
        if (n < 0.70) return Blocks.TUFF.getDefaultState();
        if (n < 0.79) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if (n < 0.87) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (n < 0.94) return Blocks.MOSS_BLOCK.getDefaultState();
        return Blocks.GRASS_BLOCK.getDefaultState();
    }

    private BlockState path(int x, int z) {
        double n = hash(x, -1, z, 15);
        boolean side = plan().edge(x, z) > -0.6;
        if (n < (side ? 0.45 : 0.62)) return Blocks.DIRT_PATH.getDefaultState();
        if (n < 0.74) return Blocks.COARSE_DIRT.getDefaultState();
        if (n < 0.84) return Blocks.GRAVEL.getDefaultState();
        if (n < 0.92) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        return side ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
    }

    private BlockState plaza(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        double n = hash(x, -1, z, 16);
        if (n < 0.05) return Blocks.MOSS_BLOCK.getDefaultState();
        if (n < 0.08) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if (r < 9 || r >= LustGardenPlan.PLAZA - 1) return Blocks.POLISHED_TUFF.getDefaultState();
        double a = Math.toDegrees(Math.atan2(z, x));
        double spoke = Math.abs(((a % 45) + 45) % 45 - 22.5);
        if (spoke > 20.5) return Blocks.TUFF.getDefaultState();
        return ((int) ((r - 9) / 2)) % 2 == 0 ? Blocks.TUFF_BRICKS.getDefaultState() : Blocks.POLISHED_TUFF.getDefaultState();
    }

    /** The basin's floor, with a 3x3 of tuff bricks and a chiseled block over the hidden stair. */
    BlockState basinFloor(int x, int z) {
        if (Math.abs(x) <= 1 && z <= LustGardenPlan.CAP_Z0 && z >= LustGardenPlan.CAP_Z1) {
            return x == 0 && z == (LustGardenPlan.CAP_Z0 + LustGardenPlan.CAP_Z1) / 2
                    ? Blocks.CHISELED_TUFF.getDefaultState() : Blocks.TUFF_BRICKS.getDefaultState();
        }
        return hash(x, LustGardenPlan.CAP_Y, z, 17) < 0.7 ? Blocks.POLISHED_TUFF.getDefaultState() : Blocks.TUFF.getDefaultState();
    }

    private BlockState pondFloor(int x, int y, int z) {
        double n = hash(x, y, z, 18);
        if (n < 0.45) return Blocks.CLAY.getDefaultState();
        if (n < 0.70) return Blocks.GRAVEL.getDefaultState();
        if (n < 0.90) return Blocks.MUD.getDefaultState();
        return Blocks.MOSS_BLOCK.getDefaultState();
    }

    // ---- On the ground ----

    private static BlockState leaves(boolean flowering) {
        return (flowering ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    private BlockState hedgeLeaves(int x, int y, int z) {
        boolean flowering = plan().smooth(x, y, z, 5, 30) > 0.68 || hash(x, y, z, 31) < 0.05;
        return leaves(flowering);
    }

    private BlockState above(Ground g, int x, int y, int z) {
        BlockState air = Blocks.AIR.getDefaultState();
        int h = plan().height(x, z);
        switch (g) {
            case HEDGE:
                if (y < h) return hedgeLeaves(x, y, z);
                // A lumpy top: leaves bulge up a block or two here and there
                if (y < h + 2 && plan().smooth(x, y, z, 4, 32) > 0.62 + (y - h) * 0.08) return hedgeLeaves(x, y, z);
                return air;
            case GATE:
                if (y >= 5 && y < h) return hedgeLeaves(x, y, z);
                return air;
            case BORDER:
                return y < h ? leaves(hash(x, y, z, 33) < 0.25) : air;
            case RIM:
                return y == 0 ? Blocks.TUFF_BRICK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM) : air;
            case POND:
                return y == 0 && hash(x, 0, z, 34) < 0.12 ? Blocks.LILY_PAD.getDefaultState() : air;
            case SHORE:
                return shore(x, y, z);
            case LAWN:
                return hedgeLump(x, y, z, flora(x, y, z));
            case AVENUE:
                return y == 0 && hash(x, 0, z, 35) < 0.05 && !surface(g, x, z).isOf(Blocks.GRASS_BLOCK)
                        ? Blocks.MOSS_CARPET.getDefaultState() : air;
            case PLAZA:
                return y == 0 && hash(x, 0, z, 36) < 0.03 ? Blocks.MOSS_CARPET.getDefaultState() : air;
            default:
                return air;
        }
    }

    /** Leaves bulging out of the hedge over the columns next to it, from a few blocks up. */
    private BlockState hedgeLump(int x, int y, int z, BlockState otherwise) {
        if (y < 3) return otherwise;
        int tallest = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (plan().ground(x + d[0], z + d[1]) == Ground.HEDGE) tallest = Math.max(tallest, plan().height(x + d[0], z + d[1]));
        }
        if (y >= tallest - 1) return otherwise;
        return plan().smooth(x, y, z, 4, 37) > 0.6 ? hedgeLeaves(x, y, z) : otherwise;
    }

    private BlockState shore(int x, int y, int z) {
        boolean byWater = false;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) byWater |= plan().ground(x + d[0], z + d[1]) == Ground.POND;
        double n = hash(x, 0, z, 38);
        boolean grassy = !surface(Ground.SHORE, x, z).isOf(Blocks.MUD) && !surface(Ground.SHORE, x, z).isOf(Blocks.COARSE_DIRT);
        if (byWater && grassy && surface(Ground.SHORE, x, z).isOf(Blocks.GRASS_BLOCK) && n < 0.14) {
            int tall = 1 + (int) (hash(x, 1, z, 38) * 3);
            return y < tall ? Blocks.SUGAR_CANE.getDefaultState().with(SugarCaneBlock.AGE, 0) : Blocks.AIR.getDefaultState();
        }
        if (y != 0) return Blocks.AIR.getDefaultState();
        if (n < 0.30) return Blocks.MOSS_CARPET.getDefaultState();
        if (n < 0.48 && grassy) return Blocks.SHORT_GRASS.getDefaultState();
        if (n < 0.58 && grassy) return petals(x, z);
        return Blocks.AIR.getDefaultState();
    }

    private BlockState petals(int x, int z) {
        return Blocks.PINK_PETALS.getDefaultState()
                .with(FlowerbedBlock.FLOWER_AMOUNT, 1 + (int) (hash(x, 0, z, 39) * 4))
                .with(FlowerbedBlock.FACING, Direction.fromHorizontal((int) (hash(x, 1, z, 39) * 4)));
    }

    private static BlockState rose() {
        Block rose = Registries.BLOCK.get(Identifier.of("ragnarstwilightdimension", "rose"));
        return rose == Blocks.AIR ? Blocks.POPPY.getDefaultState() : rose.getDefaultState();
    }

    /** Flowers from one of four colour schemes, in drifts across the lawns. Tall ones give back two blocks. */
    private BlockState[] flower(int x, int z) {
        int palette = (int) (plan().smooth(x, z, 23, 21) * 4.999);
        double n = hash(x, 0, z, 22);
        return switch (palette) {
            case 0 -> n < 0.35 ? single(Blocks.PINK_TULIP) : n < 0.55 ? tall(Blocks.PEONY) : n < 0.85 ? new BlockState[]{petals(x, z)} : single(Blocks.ALLIUM);
            case 1 -> n < 0.3 ? single(Blocks.LILY_OF_THE_VALLEY) : n < 0.55 ? single(Blocks.OXEYE_DAISY) : n < 0.8 ? single(Blocks.WHITE_TULIP) : single(Blocks.AZURE_BLUET);
            case 2 -> n < 0.35 ? single(Blocks.ALLIUM) : n < 0.6 ? tall(Blocks.LILAC) : n < 0.85 ? single(Blocks.CORNFLOWER) : single(Blocks.BLUE_ORCHID);
            case 3 -> n < 0.4 ? new BlockState[]{rose()} : n < 0.65 ? tall(Blocks.ROSE_BUSH) : n < 0.85 ? single(Blocks.POPPY) : single(Blocks.RED_TULIP);
            default -> n < 0.5 ? new BlockState[]{petals(x, z)} : n < 0.75 ? single(Blocks.PINK_TULIP) : tall(Blocks.PEONY);
        };
    }

    private static BlockState[] single(Block block) {
        return new BlockState[]{block.getDefaultState()};
    }

    private static BlockState[] tall(Block block) {
        return new BlockState[]{block.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER),
                block.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER)};
    }

    /** The lawn: flower beds in drifts, grass, ferns and moss between. */
    private BlockState flora(int x, int y, int z) {
        BlockState air = Blocks.AIR.getDefaultState();
        if (y > 1 || plan().reserved(x, z)) return air;
        if (surface(Ground.LAWN, x, z).isOf(Blocks.MOSS_BLOCK)) {
            return y == 0 && hash(x, 0, z, 23) < 0.35 ? Blocks.MOSS_CARPET.getDefaultState() : air;
        }
        double bed = plan().smooth(x, z, 9, 20), n = hash(x, 0, z, 24);
        BlockState[] plant;
        if ((bed > 0.62 && n < 0.85) || (bed > 0.55 && n < 0.35)) {
            plant = flower(x, z);
        } else if (n < 0.22) {
            plant = single(Blocks.SHORT_GRASS);
        } else if (n < 0.25) {
            plant = tall(Blocks.TALL_GRASS);
        } else if (n < 0.27) {
            plant = single(Blocks.FERN);
        } else if (n < 0.30) {
            plant = flower(x, z);
        } else if (n < 0.31) {
            plant = single(hash(x, 1, z, 24) < 0.5 ? Blocks.AZALEA : Blocks.FLOWERING_AZALEA);
        } else {
            return air;
        }
        return y < plant.length ? plant[y] : air;
    }

    // ---- Built things ----

    private void put(int x, int y, int z, BlockState state) {
        features.put(BlockPos.asLong(x, y, z), state);
    }

    private void putIfAbsent(int x, int y, int z, BlockState state) {
        features.putIfAbsent(BlockPos.asLong(x, y, z), state);
    }

    private Map<Long, BlockState> features() {
        if (features != null) return features;
        features = new HashMap<>();
        LustGardenPlan plan = plan();
        fountain();
        for (LustGardenPlan.Plinth p : plan.plinths()) put(p.x(), 0, p.z(), Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
        for (int[] p : plan.gatePillars()) gatePillar(p[0], p[1]);
        gateLantern();
        for (LustGardenPlan.Tree t : plan.trees()) {
            switch (t.kind()) {
                case CHERRY -> cherry(t);
                case AZALEA -> azalea(t);
                case TOPIARY -> topiary(t);
            }
        }
        for (LustGardenPlan.Spot s : plan.gazebos()) gazebo(s);
        for (LustGardenPlan.Spot s : plan.sheds()) shed(s);
        for (LustGardenPlan.Arbor a : plan.arbors()) arbor(a);
        for (LustGardenPlan.Spot b : plan.benches()) bench(b);
        for (LustGardenPlan.Spot l : plan.lanterns()) {
            put(l.x(), 0, l.z(), Blocks.TUFF_BRICK_WALL.getDefaultState());
            put(l.x(), 1, l.z(), Blocks.TUFF_BRICK_WALL.getDefaultState());
            put(l.x(), 2, l.z(), Blocks.LANTERN.getDefaultState());
        }
        for (LustGardenPlan.Ruin r : plan.ruins()) ruin(r);
        cryptContainers();
        return features;
    }

    private void fountain() {
        // The pillar: a base, a carved band, the spout course, and a planter with a flowering azalea on top
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                put(x, 0, z, Blocks.POLISHED_TUFF.getDefaultState());
                put(x, 1, z, Blocks.TUFF_BRICKS.getDefaultState());
                put(x, 2, z, Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
                put(x, 3, z, Blocks.TUFF_BRICKS.getDefaultState());
                put(x, 4, z, x == 0 && z == 0 ? Blocks.MOSS_BLOCK.getDefaultState()
                        : Blocks.TUFF_BRICK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
            }
        }
        put(0, 5, 0, Blocks.FLOWERING_AZALEA.getDefaultState());
        for (BlockPos spout : List.of(new BlockPos(0, LustGardenPlan.SPOUT_Y, -2), new BlockPos(2, LustGardenPlan.SPOUT_Y, 0),
                new BlockPos(0, LustGardenPlan.SPOUT_Y, 2), new BlockPos(-2, LustGardenPlan.SPOUT_Y, 0))) {
            put(spout.getX(), spout.getY(), spout.getZ(), Blocks.WATER.getDefaultState());
            spouts.add(spout);
        }
        // The four empty plinths, each on a pad, with two unlit candles at its foot
        for (int i = 0; i < 4; i++) {
            int[] p = LustGardenPlan.fountainPlinths().get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) put(p[0] + dx, -1, p[1] + dz, Blocks.POLISHED_TUFF.getDefaultState());
            }
            put(p[0], 0, p[1], Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
            int ox = Integer.signum(p[0]), oz = Integer.signum(p[1]);
            BlockState candle = Blocks.PINK_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, false);
            put(p[0] + ox - oz, 0, p[1] + oz + ox, candle);
            put(p[0] + ox + oz, 0, p[1] + oz - ox, candle);
        }
    }

    private void gatePillar(int x, int z) {
        for (int y = -3; y <= 4; y++) put(x, y, z, Blocks.TUFF_BRICKS.getDefaultState());
        put(x, 5, z, Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
        put(x, 6, z, Blocks.LANTERN.getDefaultState());
    }

    /** One lantern hanging from the leaves halfway through the gate. */
    private void gateLantern() {
        double a = plan().gateAngle(), r = (plan().inner(a) + plan().outer(a)) / 2;
        int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
        if (plan().ground(x, z) == Ground.GATE && plan().height(x, z) > 5) {
            put(x, 4, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
    }

    private void cherry(LustGardenPlan.Tree t) {
        int h = t.height();
        BlockState log = Blocks.CHERRY_LOG.getDefaultState();
        for (int y = 0; y < h; y++) put(t.x(), y, t.z(), log);
        // Two crooked branches reaching out under the crown
        for (int b = 0; b < 2; b++) {
            int dir = (int) (hash(t.x(), b, t.z(), 50) * 4);
            int[] step = LustGardenPlan.stepOf(dir);
            Direction.Axis axis = step[0] != 0 ? Direction.Axis.X : Direction.Axis.Z;
            put(t.x() + step[0], h - 2 + b, t.z() + step[1], log.with(PillarBlock.AXIS, axis));
            put(t.x() + step[0] * 2, h - 1 + b, t.z() + step[1] * 2, log.with(PillarBlock.AXIS, axis));
        }
        BlockState leaf = Blocks.CHERRY_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double shape = (dx * dx + dz * dz) / 12.25 + dy * dy / 4.8;
                    if (shape > 1 + (hash(t.x() + dx, h + dy, t.z() + dz, 51) - 0.5) * 0.45) continue;
                    putIfAbsent(t.x() + dx, h + dy, t.z() + dz, leaf);
                }
            }
        }
        // Fallen petals under the crown
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = t.x() + dx, z = t.z() + dz;
                if (dx * dx + dz * dz > 16 || plan().ground(x, z) != Ground.LAWN || plan().reserved(x, z)) continue;
                if (hash(x, 0, z, 52) < 0.5) putIfAbsent(x, 0, z, petals(x, z));
            }
        }
    }

    private void azalea(LustGardenPlan.Tree t) {
        int h = t.height();
        put(t.x(), -1, t.z(), Blocks.ROOTED_DIRT.getDefaultState());
        for (int y = 0; y < h; y++) put(t.x(), y, t.z(), Blocks.OAK_LOG.getDefaultState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    double shape = (dx * dx + dz * dz) / (dy == 1 ? 4.5 : 8.5);
                    if (shape > 1 + (hash(t.x() + dx, h + dy, t.z() + dz, 53) - 0.5) * 0.5) continue;
                    putIfAbsent(t.x() + dx, h + dy, t.z() + dz, leaves(hash(t.x() + dx, h + dy, t.z() + dz, 54) < 0.3));
                }
            }
        }
    }

    /** Clipped azalea: a round bush, or a little cone. */
    private void topiary(LustGardenPlan.Tree t) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 0; y <= 2; y++) {
                    boolean corner = dx != 0 && dz != 0;
                    boolean in = t.height() == 1 ? !(corner && y != 1) : (y < 2 && !(corner && y == 1)) || (dx == 0 && dz == 0);
                    if (in) put(t.x() + dx, y, t.z() + dz, leaves(hash(t.x() + dx, y, t.z() + dz, 55) < 0.2));
                }
            }
        }
        if (t.height() == 2) put(t.x(), 3, t.z(), leaves(true));
    }

    /** Offsets in a building's own frame: u to its right, v toward the front (the path). */
    private static int[] frame(LustGardenPlan.Spot s, int u, int v) {
        int[] f = LustGardenPlan.stepOf(s.dir());
        int rx = -f[1], rz = f[0];
        return new int[]{s.x() + u * rx + v * f[0], s.z() + u * rz + v * f[1]};
    }

    private void putAt(LustGardenPlan.Spot s, int u, int y, int v, BlockState state) {
        int[] p = frame(s, u, v);
        put(p[0], y, p[1], state);
    }

    /** A stepped roof of stairs round a square, {@code r} blocks out from the middle at its foot. */
    private void roof(int cx, int cz, int y0, int r, Block planks, Block stairs, Block slab) {
        for (int layer = 0; r - layer >= 0; layer++) {
            int s = r - layer, y = y0 + layer;
            for (int dx = -s; dx <= s; dx++) {
                for (int dz = -s; dz <= s; dz++) {
                    boolean rim = Math.abs(dx) == s || Math.abs(dz) == s;
                    BlockState state;
                    if (s == 0) {
                        state = slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
                    } else if (rim) {
                        Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                        state = stairs.getDefaultState().with(StairsBlock.FACING, in).with(StairsBlock.HALF, BlockHalf.BOTTOM);
                    } else {
                        state = planks.getDefaultState();
                    }
                    put(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    private BlockState barrel(LustGardenPlan.Spot s, int u, int y, int v) {
        int[] p = frame(s, u, v);
        barrels.add(new BlockPos(p[0], y, p[1]));
        return Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP);
    }

    /** An open pavilion: four posts, a tuff floor, a cherry roof, a lantern, a bench and two barrels. */
    private void gazebo(LustGardenPlan.Spot s) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 20) continue;
                boolean ring = dx * dx + dz * dz > 12;
                put(s.x() + dx, -1, s.z() + dz, ring ? Blocks.TUFF_BRICKS.getDefaultState() : Blocks.POLISHED_TUFF.getDefaultState());
            }
        }
        for (int[] c : new int[][]{{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) {
            for (int y = 0; y <= 2; y++) put(s.x() + c[0], y, s.z() + c[1], Blocks.TUFF_BRICK_WALL.getDefaultState());
            put(s.x() + c[0], 3, s.z() + c[1], Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
        }
        roof(s.x(), s.z(), 4, 4, Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB);
        put(s.x(), 3, s.z(), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        Direction front = Direction.fromHorizontal(s.dir());
        for (int u = -1; u <= 1; u++) {
            putAt(s, u, 0, -2, Blocks.CHERRY_STAIRS.getDefaultState().with(StairsBlock.FACING, front.getOpposite()));
        }
        putAt(s, -2, 0, -2, barrel(s, -2, 0, -2));
        putAt(s, 2, 0, -2, barrel(s, 2, 0, -2));
        putAt(s, 2, 1, -2, Blocks.POTTED_FLOWERING_AZALEA_BUSH.getDefaultState());
        putAt(s, -2, 1, -2, Blocks.PINK_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 2).with(CandleBlock.LIT, true));
    }

    /** A gardener's shed: spruce walls and roof, a door to the path, barrels, a composter and pots inside. */
    private void shed(LustGardenPlan.Spot s) {
        Direction front = Direction.fromHorizontal(s.dir());
        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                putAt(s, u, -1, v, Blocks.MOSSY_COBBLESTONE.getDefaultState());
                boolean corner = Math.abs(u) == 2 && Math.abs(v) == 2, wall = Math.abs(u) == 2 || Math.abs(v) == 2;
                for (int y = 0; y <= 2; y++) {
                    BlockState state = corner ? Blocks.SPRUCE_LOG.getDefaultState() : wall ? Blocks.SPRUCE_PLANKS.getDefaultState() : Blocks.AIR.getDefaultState();
                    putAt(s, u, y, v, state);
                }
            }
        }
        BlockState door = Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, front);
        putAt(s, 0, 0, 2, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putAt(s, 0, 1, 2, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        int[] c = frame(s, 0, 0);
        roof(c[0], c[1], 3, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
        putAt(s, -1, 0, -1, barrel(s, -1, 0, -1));
        putAt(s, 0, 0, -1, barrel(s, 0, 0, -1));
        putAt(s, 1, 0, -1, barrel(s, 1, 0, -1));
        putAt(s, -1, 1, -1, Blocks.FLOWER_POT.getDefaultState());
        putAt(s, 1, 1, -1, Blocks.POTTED_AZALEA_BUSH.getDefaultState());
        putAt(s, -1, 0, 1, Blocks.COMPOSTER.getDefaultState());
        putAt(s, 1, 2, 1, Blocks.COBWEB.getDefaultState());
        putAt(s, -1, 2, -1, Blocks.COBWEB.getDefaultState());
        putAt(s, 0, 2, 0, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
    }

    /** Two posts either side of a path and a band of leaves over it, three blocks up: room for anyone underneath. */
    private void arbor(LustGardenPlan.Arbor a) {
        for (int[] post : new int[][]{{a.x1(), a.z1()}, {a.x2(), a.z2()}}) {
            for (int y = 0; y <= 2; y++) put(post[0], y, post[1], Blocks.TUFF_BRICK_WALL.getDefaultState());
        }
        int steps = Math.max(Math.abs(a.x2() - a.x1()), Math.abs(a.z2() - a.z1()));
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(a.x1() + (a.x2() - a.x1()) * (double) i / steps);
            int z = (int) Math.round(a.z1() + (a.z2() - a.z1()) * (double) i / steps);
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {0, 1}}) {
                put(x + d[0], 3, z + d[1], leaves(hash(x + d[0], 3, z + d[1], 56) < 0.45));
                if (hash(x + d[0], 4, z + d[1], 57) < 0.6) put(x + d[0], 4, z + d[1], leaves(hash(x + d[0], 4, z + d[1], 58) < 0.45));
            }
        }
    }

    private void bench(LustGardenPlan.Spot b) {
        Direction back = Direction.fromHorizontal(b.dir()).getOpposite();
        int[] along = LustGardenPlan.alongOf(b.dir());
        BlockState seat = Blocks.POLISHED_TUFF_STAIRS.getDefaultState().with(StairsBlock.FACING, back);
        put(b.x(), 0, b.z(), seat);
        put(b.x() + along[0], 0, b.z() + along[1], seat);
    }

    /** An old wall, broken down to a block or two in places, with moss on top. */
    private void ruin(LustGardenPlan.Ruin r) {
        int[] step = LustGardenPlan.stepOf(r.dir());
        for (int s = 0; s < r.length(); s++) {
            int x = r.x() + step[0] * s, z = r.z() + step[1] * s;
            if (hash(x, 0, z, 59) < 0.12) continue;
            int tall = 1 + (int) (hash(x, 1, z, 59) * 3);
            for (int y = 0; y < tall; y++) {
                double n = hash(x, y, z, 60);
                put(x, y, z, n < 0.45 ? Blocks.TUFF_BRICKS.getDefaultState() : n < 0.75 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
                        : n < 0.9 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.TUFF.getDefaultState());
            }
            if (hash(x, tall, z, 61) < 0.5) put(x, tall, z, Blocks.MOSS_CARPET.getDefaultState());
        }
    }

    // ---- The stair and the crypt ----

    private static boolean inCryptBox(int x, int z) {
        return Math.abs(x) <= LustGardenPlan.CRYPT_HALF_WIDTH + 1 && z <= LustGardenPlan.CAP_Z0 && z >= LustGardenPlan.CRYPT_FAR - 1;
    }

    /** How high the crypt's air reaches: a vault, six blocks up in the middle and lower toward the walls. */
    private static int airTop(int x) {
        int ax = Math.abs(x);
        return ax <= 3 ? -6 : ax <= 5 ? -7 : -8;
    }

    /** The block at (x, y, z) in the stair or crypt, or null where the ordinary ground goes. */
    BlockState crypt(int x, int y, int z) {
        if (y < LustGardenPlan.CRYPT_FLOOR - 1) return null;
        int ax = Math.abs(x);
        // The stair, from under the basin's cap down to the crypt
        if (ax <= 2 && z <= LustGardenPlan.CAP_Z0 && z > LustGardenPlan.CRYPT_NEAR) {
            int top = z >= LustGardenPlan.CAP_Z1 ? -3 : -2;
            if (y > top) return null;
            int step = LustGardenPlan.stepY(z);
            if (ax <= 1 && y == step) {
                return Blocks.TUFF_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.BOTTOM);
            }
            if (ax <= 1 && y > step && y <= step + 4) {
                if (y == step + 4 && y + 1 <= top && hash(x, y, z, 70) < 0.2) return Blocks.HANGING_ROOTS.getDefaultState();
                return Blocks.AIR.getDefaultState();
            }
            return wall(x, y, z);
        }
        if (ax > LustGardenPlan.CRYPT_HALF_WIDTH + 1 || z > LustGardenPlan.CRYPT_NEAR + 1 || z < LustGardenPlan.CRYPT_FAR - 1) return null;
        if (y > -4) return null;
        boolean inside = ax <= LustGardenPlan.CRYPT_HALF_WIDTH && z <= LustGardenPlan.CRYPT_NEAR && z >= LustGardenPlan.CRYPT_FAR;
        if (!inside) return wall(x, y, z);
        BlockState frame = portalFrame(x, y, z);
        if (frame != null) return frame;
        if (y == LustGardenPlan.CRYPT_FLOOR) return cryptFloor(x, z);
        if (y < LustGardenPlan.CRYPT_FLOOR) return Blocks.TUFF.getDefaultState();
        if (y > airTop(x)) {
            // The ceiling, with roots grown down through it from the garden above
            return y == airTop(x) + 1 && hash(x, y, z, 71) < 0.12 ? Blocks.ROOTED_DIRT.getDefaultState() : Blocks.TUFF_BRICKS.getDefaultState();
        }
        BlockState feature = features.get(BlockPos.asLong(x, y, z));
        if (feature != null) return feature;
        // Pillars down each side
        if (ax == 4 && (z == -16 || z == -22)) {
            return y == airTop(x) ? Blocks.CHISELED_TUFF_BRICKS.getDefaultState() : Blocks.POLISHED_TUFF.getDefaultState();
        }
        if (y == airTop(x) && hash(x, y + 1, z, 71) < 0.12) return Blocks.HANGING_ROOTS.getDefaultState();
        if (y == airTop(x) && x == 0 && (z == -15 || z == -21)) return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        if (y == LustGardenPlan.CRYPT_FLOOR + 1) return cryptFloorDecor(x, z);
        return Blocks.AIR.getDefaultState();
    }

    private BlockState wall(int x, int y, int z) {
        double n = hash(x, y, z, 72);
        if (y == LustGardenPlan.CRYPT_FLOOR + 3) return Blocks.CHISELED_TUFF_BRICKS.getDefaultState();
        if (y == LustGardenPlan.CRYPT_FLOOR + 1) return Blocks.POLISHED_TUFF.getDefaultState();
        if (n < 0.06) return Blocks.MOSS_BLOCK.getDefaultState();
        return n < 0.18 ? Blocks.POLISHED_TUFF.getDefaultState() : Blocks.TUFF_BRICKS.getDefaultState();
    }

    private BlockState cryptFloor(int x, int z) {
        if (Math.abs(x) <= 1 && z > LustGardenPlan.LOCK_Z) return Blocks.TUFF_BRICKS.getDefaultState();
        if (hash(x, LustGardenPlan.CRYPT_FLOOR, z, 73) < 0.08) return Blocks.MOSS_BLOCK.getDefaultState();
        return (x + z) % 2 == 0 ? Blocks.POLISHED_TUFF.getDefaultState() : Blocks.TUFF.getDefaultState();
    }

    /** A pink runner up the aisle to the lock, candles along the walls, moss in the corners. */
    private BlockState cryptFloorDecor(int x, int z) {
        int ax = Math.abs(x);
        if (x == 0 && z > LustGardenPlan.LOCK_Z) return Blocks.PINK_CARPET.getDefaultState();
        if (ax == LustGardenPlan.CRYPT_HALF_WIDTH && hash(x, 0, z, 74) < 0.3) {
            Block candle = switch ((int) (hash(x, 1, z, 74) * 3)) {
                case 0 -> Blocks.PINK_CANDLE;
                case 1 -> Blocks.PURPLE_CANDLE;
                default -> Blocks.WHITE_CANDLE;
            };
            return candle.getDefaultState().with(CandleBlock.CANDLES, 1 + (int) (hash(x, 2, z, 74) * 3)).with(CandleBlock.LIT, true);
        }
        if (ax >= 2 && hash(x, 3, z, 74) < 0.08) return Blocks.MOSS_CARPET.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    /** The portal frame, 4 blocks north of the lock: polished tuff round a 3x3 opening, chiseled corners. */
    private static BlockState portalFrame(int x, int y, int z) {
        int center = LustGardenPlan.LOCK_Z - SinPortalFrameBlock.PORTAL_DISTANCE;
        if (z != center || Math.abs(x) > 2) return null;
        int bottom = LustGardenPlan.CRYPT_FLOOR, top = bottom + SinPortalFrameBlock.PORTAL_SIZE + 1;
        if (y > top) return null;
        boolean side = Math.abs(x) == 2, edge = y == bottom || y == top;
        if (side && edge) return Blocks.CHISELED_TUFF_BRICKS.getDefaultState();
        if (side || edge) return Blocks.POLISHED_TUFF.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    /** The lock, chests either side of it, barrels down the walls. */
    private void cryptContainers() {
        int floor = LustGardenPlan.CRYPT_FLOOR + 1, w = LustGardenPlan.CRYPT_HALF_WIDTH;
        put(0, floor, LustGardenPlan.LOCK_Z, SinPortalFrameBlock.of(Sin.LUST, Direction.SOUTH));
        for (int side : new int[]{-1, 1}) {
            put(CHEST_OFFSET * side, floor, LustGardenPlan.LOCK_Z, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
            chests.add(new BlockPos(CHEST_OFFSET * side, floor, LustGardenPlan.LOCK_Z));
            put(side, floor, LustGardenPlan.LOCK_Z, Blocks.POTTED_FLOWERING_AZALEA_BUSH.getDefaultState());
            for (int z : new int[]{-13, -14, -18, -19, -24, -25}) {
                put(w * side, floor, z, Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, side < 0 ? Direction.EAST : Direction.WEST));
                barrels.add(new BlockPos(w * side, floor, z));
            }
        }
    }
}
