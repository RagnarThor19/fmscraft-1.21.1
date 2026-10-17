package net.ragnar.fmscraft.structure.sloth;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BedPart;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.ModStructures;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Hanging;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Part;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Spot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the whole Sphere of Sloth from {@link SlothSphereShape}. One piece covers it; each chunk places only its own
 * blocks, and every "random" choice is hashed from the block position, so chunks always line up.
 *
 * Outside, the shell is shaded like a lit planet: grey deepslate on the side facing the main island, through
 * blackstone and coal to black concrete on the night side, with obsidian creeping up from underneath, where it weeps.
 * Inside, the walls are speckled with crying obsidian like a night sky, the stair is old deepslate with cobwebs in its
 * corners and soul lanterns hanging under it, and the core is a quiet obsidian room: a pool to land in, a ring of grey
 * beds (which explode in the End, of course) and the Sloth lock before its portal frame.
 */
public class SlothSpherePiece extends StructurePiece {
    public static final RegistryKey<LootTable> STAIR_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/sphere_of_sloth"));
    public static final RegistryKey<LootTable> CORE_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/sphere_of_sloth_core"));

    /** The light falling on the shell, in local coordinates: from above and from the mouth's side. */
    private static final double LX = 0.55, LY = 0.7, LZ = -0.45;
    private static final double L_LENGTH = Math.sqrt(LX * LX + LY * LY + LZ * LZ);
    /** The lit side of the shell, darkest (by the night side) to lightest. */
    private static final BlockState[] LIT = {
            Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.COAL_BLOCK.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.POLISHED_DEEPSLATE.getDefaultState()};
    /** Above 1, most of the lit side stays dark and only the part facing the light turns grey. */
    private static final double GAMMA = 1.6;

    // ---- The core's furniture (local coordinates) ----
    public static final Spot LOCK = new Spot(-12, SlothSphereShape.CORE_FLOOR + 1, 0, 0);
    static final Spot[] CORE_CHESTS = {new Spot(-12, LOCK.y(), -2, 0), new Spot(-12, LOCK.y(), 2, 0)};
    /** The middle of the portal frame's bottom row, {@link SinPortalFrameBlock#PORTAL_DISTANCE} behind the lock. */
    static final BlockPos FRAME = new BlockPos(LOCK.x() - SinPortalFrameBlock.PORTAL_DISTANCE, SlothSphereShape.CORE_FLOOR, 0);
    public static final BlockPos GATEWAY = new BlockPos(12, SlothSphereShape.CORE_FLOOR + 2, 0);
    /** Where the gateway sends you: out on the mouth's bridge. */
    public static final BlockPos EXIT = new BlockPos(40, SlothSphereShape.MOUTH_FLOOR + 1, 0);

    /** Every block that isn't plain shape, by local position, before rotation. */
    private static final Map<Long, BlockState> FEATURES = new HashMap<>();

    static {
        for (Hanging h : SlothSphereShape.LANTERNS) {
            for (int y = h.y() + 1; y <= h.y() + h.chain(); y++) feature(h.x(), y, h.z(), Blocks.CHAIN.getDefaultState());
            feature(h.x(), h.y(), h.z(), Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
        for (Spot bell : SlothSphereShape.BELLS) {
            feature(bell.x(), bell.y(), bell.z(), Blocks.BELL.getDefaultState()
                    .with(BellBlock.ATTACHMENT, Attachment.FLOOR).with(BellBlock.FACING, direction(bell.dir())));
        }
        for (Spot candle : SlothSphereShape.CANDLES) feature(candle.x(), candle.y(), candle.z(), timerCandle(4, false));
        for (Spot chest : SlothSphereShape.CHESTS) feature(chest.x(), chest.y(), chest.z(), chest(chest.dir()));
        // A soul lantern either side of the mouth, so it can be found on all that black from a long way off
        for (int side : new int[]{-3, 3}) {
            feature(SlothSphereShape.INNER + 1, SlothSphereShape.MOUTH_FLOOR + 1, side, Blocks.SOUL_LANTERN.getDefaultState());
        }
        furnishCore();
    }

    private final long seed;
    private final BlockRotation rotation;

    public SlothSpherePiece(long seed, BlockPos center, BlockRotation rotation) {
        super(ModStructures.SLOTH_SPHERE_PIECE, 0, box(center));
        this.seed = seed;
        this.rotation = rotation;
    }

    public SlothSpherePiece(NbtCompound nbt) {
        super(ModStructures.SLOTH_SPHERE_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.rotation = BlockRotation.valueOf(nbt.getString("Rot"));
    }

    private static BlockBox box(BlockPos center) {
        int r = SlothSphereShape.RADIUS;
        return new BlockBox(center.getX() - r, center.getY() - r, center.getZ() - r, center.getX() + r, center.getY() + r, center.getZ() + r);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putString("Rot", rotation.name());
    }

    // ---- Coordinates ----

    public BlockPos center() {
        return boundingBox.getCenter();
    }

    public BlockRotation rotation() {
        return rotation;
    }

    /** World position of local (x, y, z). */
    public BlockPos toWorld(int x, int y, int z) {
        return center().add(rotateX(x, z, rotation), y, rotateZ(x, z, rotation));
    }

    public BlockPos toWorld(BlockPos local) {
        return toWorld(local.getX(), local.getY(), local.getZ());
    }

    public BlockPos toWorld(Spot spot) {
        return toWorld(spot.x(), spot.y(), spot.z());
    }

    /** Local position of a world position. */
    public BlockPos toLocal(BlockPos pos) {
        BlockPos c = center();
        int dx = pos.getX() - c.getX(), dz = pos.getZ() - c.getZ();
        BlockRotation back = inverse(rotation);
        return new BlockPos(rotateX(dx, dz, back), pos.getY() - c.getY(), rotateZ(dx, dz, back));
    }

    public double distanceFromCenter(Vec3d pos) {
        return pos.distanceTo(Vec3d.ofCenter(center()));
    }

    private static int rotateX(int x, int z, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    private static int rotateZ(int x, int z, BlockRotation rotation) {
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

    public List<BlockPos> bells() {
        return SlothSphereShape.BELLS.stream().map(this::toWorld).toList();
    }

    public List<BlockPos> candles() {
        return SlothSphereShape.CANDLES.stream().map(this::toWorld).toList();
    }

    /** Which bell (0 at the bottom of the stair to 3 on the landing) stands at {@code pos}, or -1. */
    public int bellIndex(BlockPos pos) {
        List<BlockPos> bells = bells();
        for (int i = 0; i < bells.size(); i++) if (bells.get(i).equals(pos)) return i;
        return -1;
    }

    public List<BlockPos> sealBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        int h = SlothSphereShape.SEAL_HALF;
        for (int x = -h; x <= h; x++) {
            for (int z = -h; z <= h; z++) {
                for (int y = SlothSphereShape.CORE; y < SlothSphereShape.TOP; y++) blocks.add(toWorld(x, y, z));
            }
        }
        return blocks;
    }

    /** A block in the middle of the seal: crying obsidian while it's shut. */
    public BlockPos sealProbe() {
        return toWorld(0, (SlothSphereShape.CORE + SlothSphereShape.TOP) / 2, 0);
    }

    public BlockPos lock() {
        return toWorld(LOCK);
    }

    public BlockPos gateway() {
        return toWorld(GATEWAY);
    }

    public BlockPos exit() {
        return toWorld(EXIT);
    }

    // ---- Building ----

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos c = center();
        int r = SlothSphereShape.RADIUS;
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        BlockRotation back = inverse(rotation);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - c.getX(), dz = z - c.getZ();
                int lx = rotateX(dx, dz, back), lz = rotateZ(dx, dz, back);
                int flat = lx * lx + lz * lz;
                if (flat >= r * r) continue;
                int reach = (int) Math.ceil(Math.sqrt(r * r - flat));
                for (int y = -reach; y <= reach; y++) {
                    if (!chunkBox.contains(x, c.getY() + y, z)) continue;
                    BlockState state = blockAt(lx, y, lz);
                    if (state == null) continue;
                    pos.set(x, c.getY() + y, z);
                    if (state.isAir() && world.getBlockState(pos).isAir()) continue;
                    world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                }
            }
        }
        for (Spot chest : SlothSphereShape.CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), STAIR_LOOT);
        for (Spot chest : CORE_CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), CORE_LOOT);
        BlockPos gateway = gateway();
        if (chunkBox.contains(gateway) && world.getBlockEntity(gateway) instanceof EndGatewayBlockEntity entity) {
            entity.setExitPortalPos(exit(), true);
        }
    }

    private static void fillContainer(StructureWorldAccess world, BlockBox chunkBox, Random random, BlockPos pos, RegistryKey<LootTable> loot) {
        if (chunkBox.contains(pos) && world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
            container.setLootTable(loot, random.nextLong());
        }
    }

    /** The block at local (x, y, z), or null to leave the world as it is. */
    BlockState blockAt(int x, int y, int z) {
        BlockState feature = FEATURES.get(BlockPos.asLong(x, y, z));
        if (feature != null) return feature.rotate(rotation);
        Part part = SlothSphereShape.part(x, y, z);
        return switch (part) {
            case OUTSIDE -> null;
            case AIR -> decoration(x, y, z);
            case SURFACE -> surface(x, y, z);
            case SHELL_FILL -> noise(x, y, z, 1) < 0.35 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            case SHELL_FACE -> shellFace(noise(x, y, z, 2));
            case MOUTH_FLOOR -> noise(x, y, z, 3) < 0.2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case MOUTH_LIP -> (z == 0 || y == SlothSphereShape.MOUTH_FLOOR + 4)
                    ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case PATH_FLOOR -> pathFloor(noise(x, y, z, 4));
            case PATH_SLAB -> Blocks.DEEPSLATE_TILE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            case PATH_BODY -> noise(x, y, z, 5) < 0.2 ? Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
            case PATH_UNDERSIDE -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case RAIL -> rail(noise(x, y, z, 6));
            case BAY_FLOOR -> noise(x, y, z, 7) < 0.7 ? Blocks.POLISHED_DEEPSLATE.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
            case LANDING_FLOOR -> landingFloor(x, z);
            case CHAIN -> Blocks.CHAIN.getDefaultState();
            case BALL_FACE -> tears(x, y, z) ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
            case BALL_FILL -> Blocks.OBSIDIAN.getDefaultState();
            case CORE_FACE -> noise(x, y, z, 8) < 0.03 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
            case SEAL -> Blocks.CRYING_OBSIDIAN.getDefaultState();
            case CORE_FLOOR -> coreFloor(x, y, z);
            case CORE_FILL -> Blocks.BLACKSTONE.getDefaultState();
            case POOL -> Blocks.WATER.getDefaultState();
            case CARPET -> Blocks.GRAY_CARPET.getDefaultState();
        };
    }

    // ---- The shell ----

    /**
     * Shaded like a planet with the light on it: a dithered gradient through the lit side, so it reads as round from
     * far off, then a night side of black concrete with specks of coal, and purple-sheened obsidian underneath.
     */
    private BlockState surface(int x, int y, int z) {
        double r = Math.sqrt(x * x + y * y + z * z);
        double light = (x * LX + y * LY + z * LZ) / (r * L_LENGTH);
        // Dithered in 2x2x2 clumps with a little single-block grain, like a builder's gradient
        double dither = 0.6 * noise(x >> 1, y >> 1, z >> 1, 10) + 0.4 * noise(x, y, z, 11);
        if (light > 0) {
            int i = (int) Math.floor(Math.pow(light, GAMMA) * (LIT.length - 1) + dither);
            return LIT[MathHelper.clamp(i, 0, LIT.length - 1)];
        }
        double ny = y / r;
        if (ny < -0.9 && noise(x, y, z, 12) < 0.04) return Blocks.CRYING_OBSIDIAN.getDefaultState();
        double underneath = MathHelper.clamp((-ny - 0.3) / 0.5, 0, 1);
        if (dither < underneath) return Blocks.OBSIDIAN.getDefaultState();
        return dither > 0.9 ? Blocks.COAL_BLOCK.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState();
    }

    /** The inside of the shell: old deepslate and blackstone, speckled with crying obsidian like stars. */
    private static BlockState shellFace(double n) {
        if (n < 0.45) return Blocks.DEEPSLATE_TILES.getDefaultState();
        if (n < 0.6) return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
        if (n < 0.85) return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.985) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        return Blocks.CRYING_OBSIDIAN.getDefaultState();
    }

    /** Streaks of crying obsidian running down the outside of the core, like tears. */
    private boolean tears(int x, int y, int z) {
        if (noise(x, 0, z, 20) >= 0.05) return false;
        int start = -2 + (int) (noise(x, 1, z, 21) * 16);
        int length = 4 + (int) (noise(x, 2, z, 22) * 12);
        return y <= start && y > start - length;
    }

    // ---- The stair ----

    private static BlockState pathFloor(double n) {
        if (n < 0.6) return Blocks.DEEPSLATE_TILES.getDefaultState();
        if (n < 0.75) return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
        return Blocks.POLISHED_DEEPSLATE.getDefaultState();
    }

    /** Mostly whole, but here and there crumbled to a slab or gone, nobody having bothered to mend it. */
    private static BlockState rail(double n) {
        if (n < 0.08) return Blocks.AIR.getDefaultState();
        if (n < 0.16) return Blocks.DEEPSLATE_TILE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        return Blocks.DEEPSLATE_TILES.getDefaultState();
    }

    private static BlockState landingFloor(int x, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr >= 5.5 && hr < 6.5) return Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    /** Cobwebs and old unlit candles in the corners of the walkway, and cobwebs in the bottom of the shell. */
    private BlockState decoration(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr >= SlothSphereShape.PATH_INNER + 1 && hr < SlothSphereShape.PATH_OUTER - 1 && (hr < 28 || hr >= 29)
                && SlothSphereShape.part(x, y - 1, z) == Part.PATH_FLOOR) {
            double n = noise(x, y, z, 30);
            if (n < 0.05) return Blocks.COBWEB.getDefaultState();
            if (n < 0.065) return Blocks.GRAY_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + (int) (noise(x, y, z, 31) * 3));
        }
        if (y < -30 && SlothSphereShape.part(x, y - 1, z) == Part.SHELL_FACE && noise(x, y, z, 32) < 0.02) {
            return Blocks.COBWEB.getDefaultState();
        }
        return Blocks.AIR.getDefaultState();
    }

    // ---- The core ----

    private BlockState coreFloor(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr < SlothSphereShape.POOL_RADIUS + 1) return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (hr < SlothSphereShape.POOL_RADIUS + 2) return Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
        if (hr >= 17) return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        double n = noise(x, y, z, 40);
        if (n < 0.7) return Blocks.DEEPSLATE_TILES.getDefaultState();
        if (n < 0.9) return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
    }

    /**
     * The room at the heart of the sphere: the lock on the -x side before its frame, the gateway home on the +x side,
     * and two rows of grey beds either side of the pool, with candles burning between them.
     */
    private static void furnishCore() {
        feature(LOCK.x(), LOCK.y(), LOCK.z(), SinPortalFrameBlock.of(Sin.SLOTH, Direction.EAST));
        for (Spot chest : CORE_CHESTS) feature(chest.x(), chest.y(), chest.z(), chest(chest.dir()));

        // The portal frame: 5x5 with a 3x3 opening, bottom row set into the floor
        for (int z = -2; z <= 2; z++) {
            for (int h = 0; h <= 4; h++) {
                boolean side = Math.abs(z) == 2, end = h == 0 || h == 4;
                BlockState block;
                if (side && end) block = Blocks.CRYING_OBSIDIAN.getDefaultState();
                else if (side) block = Blocks.POLISHED_BASALT.getDefaultState();
                else if (h == 4) block = z == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
                else if (h == 0) block = Blocks.POLISHED_BLACKSTONE.getDefaultState();
                else block = Blocks.AIR.getDefaultState();
                feature(FRAME.getX(), FRAME.getY() + h, z, block);
            }
        }

        // A vanilla end gateway, bedrock and all, sending you back out to the mouth
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = GATEWAY.getX() + dx, y = GATEWAY.getY() + dy, z = GATEWAY.getZ() + dz;
                    boolean middle = dx == 0 && dz == 0;
                    if (dy == 0) feature(x, y, z, middle ? Blocks.END_GATEWAY.getDefaultState() : Blocks.AIR.getDefaultState());
                    else if (Math.abs(dy) == 2) {
                        if (middle) feature(x, y, z, Blocks.BEDROCK.getDefaultState());
                    } else if (dx == 0 || dz == 0) feature(x, y, z, Blocks.BEDROCK.getDefaultState());
                }
            }
        }

        // Beds either side of the pool, heads toward it, with candles burning between them
        for (int side : new int[]{1, -1}) {
            Direction toPool = side > 0 ? Direction.NORTH : Direction.SOUTH;
            for (int x = -3; x <= 3; x += 2) {
                BlockState bed = Blocks.GRAY_BED.getDefaultState().with(BedBlock.FACING, toPool);
                feature(x, LOCK.y(), 9 * side, bed.with(BedBlock.PART, BedPart.HEAD));
                feature(x, LOCK.y(), 10 * side, bed.with(BedBlock.PART, BedPart.FOOT));
            }
            for (int x = -2; x <= 2; x += 2) {
                feature(x, LOCK.y(), 9 * side, Blocks.GRAY_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + Math.floorMod(x + side, 3))
                        .with(CandleBlock.LIT, true));
            }
        }
        // A pinwheel of candles round the pool
        int[][] rim = {{4, 3}, {-3, 4}, {-4, -3}, {3, -4}};
        for (int[] at : rim) {
            feature(at[0], LOCK.y(), at[1], Blocks.WHITE_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
        }
    }

    // ---- Helpers ----

    /** The candles in front of a bell: lit while it rings, burning down one at a time. */
    public static BlockState timerCandle(int candles, boolean lit) {
        return Blocks.GRAY_CANDLE.getDefaultState().with(CandleBlock.CANDLES, candles).with(CandleBlock.LIT, lit);
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
