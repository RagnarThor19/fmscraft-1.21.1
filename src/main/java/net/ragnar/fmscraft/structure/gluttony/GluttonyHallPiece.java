package net.ragnar.fmscraft.structure.gluttony;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CandleCakeBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.SlabType;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.Part;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.Spot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the whole Hall of Gluttony from {@link GluttonyHallShape}. One piece covers it; each chunk places only its
 * own blocks, and every "random" choice is hashed from the block position, so chunks always line up.
 *
 * It's built the way the piglins build their bastions: polished blackstone bricks, basalt and gilded blackstone, with
 * gold where they could afford it, under roofs of nether brick. The windows are stained glass in the colours of the
 * lava outside. The banquet runs the length of the nave: a crimson table heaped with cakes, melons and pumpkins,
 * benches either side, lanterns hanging over it on long chains. The aisles are the kitchens (smokers, cooking fires,
 * pantry barrels, hay). The chapel behind the gate is quiet and lit by soul fire, which piglins can't abide.
 */
public class GluttonyHallPiece extends StructurePiece {
    public static final RegistryKey<LootTable> PANTRY_LOOT = loot("chests/hall_of_gluttony_pantry");
    public static final RegistryKey<LootTable> HOARD_LOOT = loot("chests/hall_of_gluttony");
    public static final RegistryKey<LootTable> SANCTUM_LOOT = loot("chests/hall_of_gluttony_sanctum");

    private static RegistryKey<LootTable> loot(String path) {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, path));
    }

    /** Every block that isn't plain shape, by local position, before rotation (the same for every hall). */
    private static final Map<Long, BlockState> FEATURES = new HashMap<>();

    static {
        for (Spot chest : GluttonyHallShape.HOARD_CHESTS) feature(chest, chest(chest.dir()));
        for (Spot barrel : GluttonyHallShape.PANTRIES) feature(barrel, Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, direction(barrel.dir())));
        for (Spot smoker : GluttonyHallShape.SMOKERS) {
            feature(smoker, Blocks.SMOKER.getDefaultState().with(AbstractFurnaceBlock.FACING, direction(smoker.dir())));
        }
        for (Spot hearth : GluttonyHallShape.HEARTHS) {
            feature(hearth, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, direction(hearth.dir())));
        }
        for (Spot hay : GluttonyHallShape.HAY) feature(hay, Blocks.HAY_BLOCK.getDefaultState());
        for (Spot bell : GluttonyHallShape.BELLS) {
            feature(bell, Blocks.BELL.getDefaultState().with(BellBlock.ATTACHMENT, Attachment.CEILING).with(BellBlock.FACING, Direction.EAST));
        }
        for (Spot lantern : GluttonyHallShape.CHANDELIERS) {
            int top = lantern.y() + 1;
            while (top < GluttonyHallShape.TOP && GluttonyHallShape.part(lantern.x(), top, lantern.z(), GluttonyHallShape.MIN_BRIDGE) == Part.AIR) {
                feature(lantern.x(), top, lantern.z(), Blocks.CHAIN.getDefaultState());
                top++;
            }
            feature(lantern, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }

        // His hoard round the back of the dais: heaps of gold, and lanterns at its front corners
        for (int side : new int[]{-1, 1}) {
            feature(-30, 2, 2 * side, Blocks.GOLD_BLOCK.getDefaultState());
            feature(-30, 2, 3 * side, Blocks.RAW_GOLD_BLOCK.getDefaultState());
            feature(-29, 2, 3 * side, Blocks.GOLD_BLOCK.getDefaultState());
            feature(-30, 3, 3 * side, Blocks.GOLD_BLOCK.getDefaultState());
            feature(-25, 2, 4 * side, Blocks.LANTERN.getDefaultState());
        }

        // Cooking fires either side of the way onto the bridge
        for (int side : new int[]{-1, 1}) {
            feature(GluttonyHallShape.TERRACE_EAST - 4, 1, 6 * side, Blocks.CAMPFIRE.getDefaultState());
            feature(GluttonyHallShape.FACADE_OUT + 1, 1, 4 * side, Blocks.LANTERN.getDefaultState());
        }

        furnishChapel();
    }

    private final long seed;
    private final BlockRotation rotation;
    /** How long the bridge from the terrace to the shore is. */
    private final int bridge;
    /** The middle of the hall's floor (local 0, 0, 0). The box isn't centered on it. */
    private final BlockPos center;

    public GluttonyHallPiece(long seed, BlockPos center, BlockRotation rotation, int bridge) {
        super(ModStructures.GLUTTONY_HALL_PIECE, 0, box(center, rotation, bridge));
        this.seed = seed;
        this.rotation = rotation;
        this.bridge = bridge;
        this.center = center;
    }

    public GluttonyHallPiece(NbtCompound nbt) {
        super(ModStructures.GLUTTONY_HALL_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.rotation = BlockRotation.valueOf(nbt.getString("Rot"));
        this.bridge = nbt.getInt("Bridge");
        this.center = BlockPos.fromLong(nbt.getLong("Center"));
    }

    private static BlockBox box(BlockPos center, BlockRotation rotation, int bridge) {
        int[][] corners = {{GluttonyHallShape.TERRACE_WEST, -GluttonyHallShape.TERRACE_HALF}, {GluttonyHallShape.east(bridge), GluttonyHallShape.TERRACE_HALF}};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] cx : corners) {
            for (int[] cz : corners) {
                int x = rotateX(cx[0], cz[1], rotation), z = rotateZ(cx[0], cz[1], rotation);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        return new BlockBox(center.getX() + minX, center.getY() + GluttonyHallShape.BOTTOM, center.getZ() + minZ,
                center.getX() + maxX, center.getY() + GluttonyHallShape.TOP, center.getZ() + maxZ);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putString("Rot", rotation.name());
        nbt.putInt("Bridge", bridge);
        nbt.putLong("Center", center.asLong());
    }

    // ---- Coordinates ----

    public BlockPos center() {
        return center;
    }

    public BlockRotation rotation() {
        return rotation;
    }

    public int bridge() {
        return bridge;
    }

    public BlockPos toWorld(int x, int y, int z) {
        return center.add(rotateX(x, z, rotation), y, rotateZ(x, z, rotation));
    }

    public BlockPos toWorld(Spot spot) {
        return toWorld(spot.x(), spot.y(), spot.z());
    }

    public BlockPos toLocal(BlockPos pos) {
        int dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
        BlockRotation back = inverse(rotation);
        return new BlockPos(rotateX(dx, dz, back), pos.getY() - center.getY(), rotateZ(dx, dz, back));
    }

    public Direction toWorld(int dir) {
        return rotation.rotate(direction(dir));
    }

    static int rotateX(int x, int z, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    static int rotateZ(int x, int z, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    private static BlockRotation inverse(BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    // ---- What the runtime needs ----

    /** Whether a world position is in the hall itself, where the feast is and you can't build or dig. */
    public boolean inHall(BlockPos pos) {
        BlockPos l = toLocal(pos);
        return GluttonyHallShape.inHall(l.getX(), l.getY(), l.getZ());
    }

    /** Whether a world position is in the chapel behind the gate, or the passage to it. */
    public boolean inSanctum(BlockPos pos) {
        BlockPos l = toLocal(pos);
        return GluttonyHallShape.inSanctum(l.getX(), l.getY(), l.getZ());
    }

    /** The block the Gorged Brute stands on (his feet), and which way he faces there. */
    public BlockPos gorged() {
        return toWorld(GluttonyHallShape.GORGED);
    }

    public float gorgedYaw() {
        return toWorld(GluttonyHallShape.GORGED.dir()).asRotation();
    }

    public BlockPos lock() {
        return toWorld(GluttonyHallShape.LOCK);
    }

    /** The gate's blocks, row by row from the top down. */
    public List<List<BlockPos>> gateRows() {
        List<List<BlockPos>> rows = new ArrayList<>();
        for (List<Spot> row : GluttonyHallShape.gateRows()) rows.add(row.stream().map(this::toWorld).toList());
        return rows;
    }

    public List<BlockPos> gate() {
        List<BlockPos> all = new ArrayList<>();
        for (List<BlockPos> row : gateRows()) all.addAll(row);
        return all;
    }

    /** Just in front of the gate in the hall, where anything that strays into the chapel is sent back to. */
    public BlockPos beforeGate() {
        return toWorld(GluttonyHallShape.GORGED.x() + 3, 1, 3);
    }

    /** Where the bridge's deck meets the shore. */
    public BlockPos shore() {
        return toWorld(GluttonyHallShape.TERRACE_EAST + bridge, 1, 0);
    }

    public List<BlockPos> spots(List<Spot> spots) {
        return spots.stream().map(this::toWorld).toList();
    }

    public List<BlockPos> containers() {
        List<Spot> spots = new ArrayList<>(GluttonyHallShape.PANTRIES);
        spots.addAll(GluttonyHallShape.HOARD_CHESTS);
        spots.addAll(GluttonyHallShape.ROOM_CHESTS);
        return spots(spots);
    }

    // ---- Building ----

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        int minY = Math.max(boundingBox.getMinY(), chunkBox.getMinY()), maxY = Math.min(boundingBox.getMaxY(), chunkBox.getMaxY());
        BlockRotation back = inverse(rotation);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - center.getX(), dz = z - center.getZ();
                int lx = rotateX(dx, dz, back), lz = rotateZ(dx, dz, back);
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = blockAt(lx, y - center.getY(), lz);
                    if (state == null) continue;
                    pos.set(x, y, z);
                    if (state.isAir() && world.getBlockState(pos).isAir()) continue;
                    world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                }
            }
        }
        for (Spot barrel : GluttonyHallShape.PANTRIES) fillContainer(world, chunkBox, random, toWorld(barrel), PANTRY_LOOT);
        for (Spot chest : GluttonyHallShape.HOARD_CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), HOARD_LOOT);
        for (Spot chest : GluttonyHallShape.ROOM_CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), SANCTUM_LOOT);
    }

    private static void fillContainer(StructureWorldAccess world, BlockBox chunkBox, Random random, BlockPos pos, RegistryKey<LootTable> loot) {
        if (chunkBox.contains(pos) && world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
            container.setLootTable(loot, random.nextLong());
        }
    }

    /** The block at local (x, y, z), turned the way the hall faces, or null to leave the world as it is. */
    BlockState blockAt(int x, int y, int z) {
        BlockState state = localBlockAt(x, y, z);
        return state == null ? null : state.rotate(rotation);
    }

    private BlockState localBlockAt(int x, int y, int z) {
        BlockState feature = FEATURES.get(BlockPos.asLong(x, y, z));
        if (feature != null) return feature;
        Part part = GluttonyHallShape.part(x, y, z, bridge);
        return switch (part) {
            case OUTSIDE -> null;
            case AIR -> decoration(x, y, z);
            case TERRACE -> terrace(x, y, z);
            case TERRACE_EDGE -> y == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : foundation(x, y, z);
            case PARAPET -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case FOUNDATION, FILL -> foundation(x, y, z);
            case FLOOR -> floor(x, y, z);
            case RUNNER -> Blocks.RED_NETHER_BRICKS.getDefaultState();
            case AISLE_FLOOR -> aisleFloor(x, y, z);
            case WALL -> wall(x, y, z);
            case PIER, PINNACLE -> Blocks.POLISHED_BASALT.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
            case PIER_BASE, ARCH, GABLE_TRIM -> Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
            case CAPITAL -> Blocks.GILDED_BLACKSTONE.getDefaultState();
            case VAULT -> noise(x, y, z, 3) < 0.15 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case RIB -> rib(x, y, z);
            case WINDOW -> window(x, y, z);
            case ROSE -> rose(y, z);
            case TRACERY -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case DOOR_TRIM -> ((y + Math.abs(z)) & 1) == 0 ? Blocks.GILDED_BLACKSTONE.getDefaultState()
                    : Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
            case ROOF -> stairs(Blocks.NETHER_BRICK_STAIRS, z > 0 ? 3 : 1);
            case ROOF_RIDGE, SPIRE, FLECHE -> Blocks.NETHER_BRICKS.getDefaultState();
            case AISLE_ROOF -> Blocks.NETHER_BRICKS.getDefaultState();
            case AISLE_ROOF_SLAB -> Blocks.NETHER_BRICK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            case BUTTRESS, FLYER -> noise(x, y, z, 4) < 0.15 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case FINIAL -> Blocks.GOLD_BLOCK.getDefaultState();
            case TOWER -> wall(x, y, z);
            case TOWER_TRIM -> towerTrim(x, y, z);
            case TOWER_FLOOR -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case SPIRE_TRIM -> Blocks.RED_NETHER_BRICKS.getDefaultState();
            case GATE -> Blocks.GILDED_BLACKSTONE.getDefaultState();
            case GATE_FRAME -> y == GluttonyHallShape.GATE_HEIGHT + 2 ? Blocks.GOLD_BLOCK.getDefaultState()
                    : Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
            case ROOM_FLOOR -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case ROOM_BORDER -> Blocks.GILDED_BLACKSTONE.getDefaultState();
            case ROOM_WALL -> noise(x, y, z, 5) < 0.2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case ROOM_CEILING -> Math.abs(z) <= 1 && x >= -42 && x <= -38 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case ROOM_PILLAR -> Blocks.POLISHED_BASALT.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
            case CHAPEL_ROOF -> stairs(Blocks.NETHER_BRICK_STAIRS, z > 0 ? 3 : 1);
            case CHAPEL_RIDGE_PART -> Blocks.RED_NETHER_BRICKS.getDefaultState();
            case TABLE -> Blocks.CRIMSON_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
            // A seat facing the table, its back away from it
            case BENCH -> stairs(Blocks.CRIMSON_STAIRS, z > 0 ? 1 : 3);
            case DAIS -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case DAIS_EDGE -> Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            case BRIDGE_DECK -> y == 0 ? (noise(x, y, z, 6) < 0.2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState()) : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case BRIDGE_RAIL -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case BRIDGE_PIER -> noise(x, y, z, 7) < 0.5 ? Blocks.BASALT.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
        };
    }

    // ---- Materials ----

    private BlockState foundation(int x, int y, int z) {
        double n = noise(x, y, z, 10);
        if (n < 0.3) return Blocks.BASALT.getDefaultState();
        return Blocks.BLACKSTONE.getDefaultState();
    }

    /** Walls: polished blackstone bricks, some cracked, the odd one gilded where the piglins showed off. */
    private BlockState wall(int x, int y, int z) {
        double n = noise(x, y, z, 11);
        if (n < 0.13) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.19) return Blocks.BLACKSTONE.getDefaultState();
        if (n < 0.205) return Blocks.GILDED_BLACKSTONE.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    private BlockState terrace(int x, int y, int z) {
        // Paving in squares of four, picked out in polished blackstone
        if (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) return Blocks.POLISHED_BLACKSTONE.getDefaultState();
        double n = noise(x, y, z, 12);
        if (n < 0.2) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.3) return Blocks.BLACKSTONE.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    /** The nave's floor: a checker of polished blackstone and its bricks, worn and cracked here and there. */
    private BlockState floor(int x, int y, int z) {
        double n = noise(x, y, z, 13);
        if (n < 0.02) return Blocks.GILDED_BLACKSTONE.getDefaultState();
        if (n < 0.12) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        return ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    private BlockState aisleFloor(int x, int y, int z) {
        double n = noise(x, y, z, 14);
        if (n < 0.35) return Blocks.BLACKSTONE.getDefaultState();
        if (n < 0.55) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.6) return Blocks.SOUL_SOIL.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    /** Ribs of smooth polished blackstone, a shroomlight set in at the top of each one to light the vault. */
    private BlockState rib(int x, int y, int z) {
        if (z == 0 && GluttonyHallShape.pierColumn(x) && GluttonyHallShape.part(x, y - 1, z, bridge) == Part.AIR) {
            return Blocks.SHROOMLIGHT.getDefaultState();
        }
        return Blocks.POLISHED_BLACKSTONE.getDefaultState();
    }

    /** The lancets fade from red at the bottom through orange to yellow at the top, like the lava's glow. */
    private static BlockState window(int x, int y, int z) {
        int bottom, top;
        if (y <= GluttonyHallShape.AISLE_WINDOW_TOP) {
            bottom = GluttonyHallShape.AISLE_WINDOW_BOTTOM;
            top = GluttonyHallShape.AISLE_WINDOW_TOP;
        } else if (y <= 20) {
            bottom = 14;
            top = 20;
        } else {
            bottom = GluttonyHallShape.CLERESTORY_BOTTOM;
            top = GluttonyHallShape.CLERESTORY_TOP;
        }
        double t = (y - bottom) / (double) Math.max(1, top - bottom);
        if (t < 0.4) return Blocks.RED_STAINED_GLASS.getDefaultState();
        if (t < 0.8) return Blocks.ORANGE_STAINED_GLASS.getDefaultState();
        return Blocks.YELLOW_STAINED_GLASS.getDefaultState();
    }

    /** A gold heart, an orange ring round it, and petals of red and blue between the spokes. */
    private static BlockState rose(int y, int z) {
        double r = GluttonyHallShape.roseDistance(y, Math.abs(z));
        if (r < 1.2) return Blocks.YELLOW_STAINED_GLASS.getDefaultState();
        if (r < 2.8) return Blocks.ORANGE_STAINED_GLASS.getDefaultState();
        double a = Math.toDegrees(Math.atan2(y - GluttonyHallShape.ROSE_Y, z)) + 360;
        return ((int) (a / 45)) % 2 == 0 ? Blocks.RED_STAINED_GLASS.getDefaultState() : Blocks.BLUE_STAINED_GLASS.getDefaultState();
    }

    private static BlockState towerTrim(int x, int y, int z) {
        int ax = Math.abs(x - GluttonyHallShape.TOWER_X), adz = Math.abs(Math.abs(z) - GluttonyHallShape.TOWER_Z);
        if (ax == GluttonyHallShape.TOWER_HALF && adz == GluttonyHallShape.TOWER_HALF) {
            return Blocks.POLISHED_BASALT.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        }
        return Blocks.GILDED_BLACKSTONE.getDefaultState();
    }

    /**
     * Air, mostly. The banquet table is laid with cakes, melons, pumpkins and loaves of hay, and lanterns stand here
     * and there on the parapets round the terrace and along the bridge.
     */
    private BlockState decoration(int x, int y, int z) {
        BlockState air = Blocks.AIR.getDefaultState();
        if (y == 2) {
            Part below = GluttonyHallShape.part(x, 1, z, bridge);
            if (below == Part.TABLE) return dish(noise(x, y, z, 20), noise(x, y, z, 21));
            if (below == Part.PARAPET && noise(x, y, z, 22) < 0.12) return Blocks.LANTERN.getDefaultState();
            if (below == Part.BRIDGE_RAIL && (x - GluttonyHallShape.TERRACE_EAST - 1) % 8 == 0) return Blocks.LANTERN.getDefaultState();
        }
        return air;
    }

    private static BlockState dish(double n, double m) {
        if (n < 0.36) return Blocks.AIR.getDefaultState();
        if (n < 0.5) return Blocks.CAKE.getDefaultState();
        if (n < 0.6) {
            Block cake = m < 0.4 ? Blocks.CANDLE_CAKE : m < 0.7 ? Blocks.RED_CANDLE_CAKE : Blocks.YELLOW_CANDLE_CAKE;
            return cake.getDefaultState().with(CandleCakeBlock.LIT, true);
        }
        if (n < 0.69) return Blocks.MELON.getDefaultState();
        if (n < 0.77) return Blocks.PUMPKIN.getDefaultState();
        if (n < 0.87) return Blocks.HAY_BLOCK.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
        if (n < 0.92) return Blocks.LANTERN.getDefaultState();
        if (n < 0.96) return Blocks.DECORATED_POT.getDefaultState();
        return Blocks.HONEY_BLOCK.getDefaultState();
    }

    // ---- The chapel ----

    private static void furnishChapel() {
        Spot lock = GluttonyHallShape.LOCK;
        feature(lock, SinPortalFrameBlock.of(Sin.GLUTTONY, Direction.EAST));
        for (Spot chest : GluttonyHallShape.ROOM_CHESTS) feature(chest, chest(chest.dir()));

        // The portal frame: 5x5 with a 3x3 opening, bottom row set into the floor, gold at the corners
        Spot frame = GluttonyHallShape.FRAME;
        for (int z = -2; z <= 2; z++) {
            for (int h = 0; h <= 4; h++) {
                boolean side = Math.abs(z) == 2, end = h == 0 || h == 4;
                BlockState block;
                if (side && end) block = Blocks.GOLD_BLOCK.getDefaultState();
                else if (side) block = Blocks.POLISHED_BLACKSTONE.getDefaultState();
                else if (h == 4) block = z == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.GILDED_BLACKSTONE.getDefaultState();
                else if (h == 0) block = Blocks.POLISHED_BLACKSTONE.getDefaultState();
                else block = Blocks.AIR.getDefaultState();
                feature(frame.x(), frame.y() + h, z, block);
            }
        }
        // A runner from the passage to the lock
        for (int x = lock.x() + 1; x <= GluttonyHallShape.ROOM_EAST; x++) {
            for (int z = -1; z <= 1; z++) feature(x, 1, z, Blocks.YELLOW_CARPET.getDefaultState());
        }
        // Soul fire, which no piglin will go near: campfires either side of the frame, lanterns over the runner,
        // and candles in the corners
        for (int side : new int[]{-1, 1}) {
            feature(frame.x() + 1, 1, 4 * side, Blocks.SOUL_CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, Direction.EAST));
            for (int x : new int[]{-38, -45}) {
                feature(x, GluttonyHallShape.ROOM_HEIGHT, 4 * side, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
            }
            feature(GluttonyHallShape.ROOM_EAST - 1, 1, (GluttonyHallShape.ROOM_HALF - 1) * side,
                    Blocks.YELLOW_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
        }
    }

    // ---- Helpers ----

    private static BlockState stairs(Block block, int dir) {
        return block.getDefaultState().with(StairsBlock.FACING, direction(dir));
    }

    private static BlockState chest(int dir) {
        return Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, direction(dir));
    }

    /** Direction index (0 = +x, 1 = +z, 2 = -x, 3 = -z) to a direction, in local coordinates. */
    static Direction direction(int dir) {
        return switch (dir) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static void feature(Spot spot, BlockState state) {
        feature(spot.x(), spot.y(), spot.z(), state);
    }

    private static void feature(int x, int y, int z, BlockState state) {
        FEATURES.put(BlockPos.asLong(x, y, z), state);
    }

    private double noise(int x, int y, int z, int salt) {
        long h = MathHelper.hashCode(x, y * 31 + salt, z) ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
