package net.ragnar.fmscraft.structure.wrath;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
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
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Cone;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Hanging;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Part;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Spot;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Tripwire;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the whole Den of Wrath from {@link WrathDenShape}. One piece covers it; each chunk places only its own blocks,
 * and every "random" choice is hashed from the block position, so chunks always line up.
 *
 * It's a deep dark ancient city's hall gone feral in the Nether: walls of blackstone and basalt with sculk creeping
 * up them from a floor that is nearly all sculk, crossed by glowing cracks of magma; a dome blotched with crimson
 * growth, weeping vines and the odd shroomlight; rock spires and stalactites; the ribcage of something huge; soul
 * lanterns hanging on long chains. The ledge and the gate are old deepslate, the work of whoever built the place. Her
 * nest is a ring of sculk and bone with four catalysts in it, shriekers round it (which can't summon) and three soul
 * campfires. The portal room past the little doorway is quiet blackstone and red nether brick.
 */
public class WrathDenPiece extends StructurePiece {
    public static final RegistryKey<LootTable> HALL_LOOT = loot("chests/den_of_wrath");
    public static final RegistryKey<LootTable> NEST_LOOT = loot("chests/den_of_wrath_nest");
    public static final RegistryKey<LootTable> SANCTUM_LOOT = loot("chests/den_of_wrath_sanctum");

    private static RegistryKey<LootTable> loot(String path) {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, path));
    }

    /** Every block that isn't plain shape, by local position, before rotation (the same for every den). */
    private static final Map<Long, BlockState> FEATURES = new HashMap<>();

    static {
        for (Spot chest : WrathDenShape.LEDGE_CHESTS) feature(chest, chest(chest.dir()));
        feature(WrathDenShape.FOSSIL_CHEST, chest(WrathDenShape.FOSSIL_CHEST.dir()));
        feature(WrathDenShape.NEST_CHEST, chest(WrathDenShape.NEST_CHEST.dir()));
        for (Spot plate : WrathDenShape.PLATES) {
            feature(plate, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.getDefaultState());
            feature(plate.x(), plate.y() - 1, plate.z(), Blocks.POLISHED_BLACKSTONE.getDefaultState());
        }
        for (Tripwire wire : WrathDenShape.TRIPWIRES) setTripwire(wire);
        for (Spot torch : WrathDenShape.TORCHES) {
            feature(torch, Blocks.SOUL_WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, direction(torch.dir())));
        }
        for (Spot lantern : WrathDenShape.EXIT_LANTERNS) feature(lantern, Blocks.SOUL_LANTERN.getDefaultState());
        for (Hanging h : WrathDenShape.CHAINS) {
            int top = WrathDenShape.ceilingAt(Math.hypot(h.x(), h.z()));
            for (int y = h.y() + 1; y <= top; y++) feature(h.x(), y, h.z(), Blocks.CHAIN.getDefaultState());
            feature(h.x(), h.y(), h.z(), Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
        // Lanterns on the balcony's parapet, the first thing you see coming out of the tunnel
        for (int z : new int[]{-WrathDenShape.BALCONY_HALF, WrathDenShape.BALCONY_HALF}) {
            feature(WrathDenShape.BALCONY_INNER, WrathDenShape.BALCONY + 2, z, Blocks.SOUL_LANTERN.getDefaultState());
        }

        // The nest
        for (Spot catalyst : WrathDenShape.CATALYSTS) feature(catalyst, Blocks.SCULK_CATALYST.getDefaultState());
        for (Spot shrieker : WrathDenShape.SHRIEKERS) feature(shrieker, Blocks.SCULK_SHRIEKER.getDefaultState());
        for (Spot fire : WrathDenShape.CAMPFIRES) {
            feature(fire, Blocks.SOUL_CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, direction(fire.dir())));
        }

        furnishSanctum();
    }

    private final long seed;
    private final BlockRotation rotation;
    /** How far the entrance tunnel runs past the outside of the wall. */
    private final int tunnel;

    public WrathDenPiece(long seed, BlockPos center, BlockRotation rotation, int tunnel) {
        super(ModStructures.WRATH_DEN_PIECE, 0, box(center, rotation, tunnel));
        this.seed = seed;
        this.rotation = rotation;
        this.tunnel = tunnel;
        this.center = center;
    }

    public WrathDenPiece(NbtCompound nbt) {
        super(ModStructures.WRATH_DEN_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.rotation = BlockRotation.valueOf(nbt.getString("Rot"));
        this.tunnel = nbt.getInt("Tunnel");
        this.center = BlockPos.fromLong(nbt.getLong("Center"));
    }

    /** The middle of the hall's floor (local 0, 0, 0). The box isn't centered on it. */
    private final BlockPos center;

    private static BlockBox box(BlockPos center, BlockRotation rotation, int tunnel) {
        int[][] corners = {{WrathDenShape.WEST, -WrathDenShape.OUTER}, {WrathDenShape.OUTER + tunnel, WrathDenShape.OUTER}};
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
        return new BlockBox(center.getX() + minX, center.getY() + WrathDenShape.BOTTOM, center.getZ() + minZ,
                center.getX() + maxX, center.getY() + WrathDenShape.TOP, center.getZ() + maxZ);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
        nbt.putString("Rot", rotation.name());
        nbt.putInt("Tunnel", tunnel);
        nbt.putLong("Center", center.asLong());
    }

    // ---- Coordinates ----

    public BlockPos center() {
        return center;
    }

    public BlockRotation rotation() {
        return rotation;
    }

    public int tunnel() {
        return tunnel;
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

    /** Whether a world position is in the hall itself, where she can hear you and you can't build or dig. */
    public boolean inHall(BlockPos pos) {
        BlockPos l = toLocal(pos);
        return WrathDenShape.inHall(l.getX(), l.getY(), l.getZ());
    }

    public boolean inSanctum(BlockPos pos) {
        BlockPos l = toLocal(pos);
        return WrathDenShape.inSanctum(l.getX(), l.getY(), l.getZ());
    }

    /** The block she sleeps on (her feet), and which way she faces there. */
    public BlockPos nest() {
        return toWorld(WrathDenShape.MOTHER);
    }

    public float nestYaw() {
        return toWorld(WrathDenShape.MOTHER.dir()).asRotation();
    }

    public BlockPos lock() {
        return toWorld(WrathDenShape.LOCK);
    }

    /** Where the entrance tunnel meets the Nether: the first open block past its end. */
    public BlockPos mouth() {
        return toWorld(WrathDenShape.OUTER + tunnel, WrathDenShape.BALCONY + 1, 0);
    }

    public List<BlockPos> chests() {
        List<Spot> spots = new java.util.ArrayList<>(WrathDenShape.LEDGE_CHESTS);
        spots.add(WrathDenShape.FOSSIL_CHEST);
        spots.add(WrathDenShape.NEST_CHEST);
        spots.addAll(WrathDenShape.ROOM_CHESTS);
        return spots.stream().map(this::toWorld).toList();
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
        for (Spot chest : WrathDenShape.LEDGE_CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), HALL_LOOT);
        fillContainer(world, chunkBox, random, toWorld(WrathDenShape.FOSSIL_CHEST), HALL_LOOT);
        fillContainer(world, chunkBox, random, toWorld(WrathDenShape.NEST_CHEST), NEST_LOOT);
        for (Spot chest : WrathDenShape.ROOM_CHESTS) fillContainer(world, chunkBox, random, toWorld(chest), SANCTUM_LOOT);
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
        BlockState lantern = tunnelLantern(x, y, z);
        if (lantern != null) return lantern;
        Part part = WrathDenShape.part(x, y, z, tunnel);
        return switch (part) {
            case OUTSIDE -> null;
            case AIR -> decoration(x, y, z);
            case SKIN -> skin(noise(x >> 1, y >> 1, z >> 1, 1));
            case FILL, FOUNDATION -> noise(x, y, z, 2) < 0.3 ? Blocks.BASALT.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            case FACE -> y > WrathDenShape.WALL_TOP ? ceiling(x, y, z) : wall(x, y, z);
            case FLOOR -> floor(x, y, z);
            case NEST_FLOOR -> pick(noise(x, y, z, 3), Blocks.SCULK, 0.62, Blocks.BONE_BLOCK, 0.82, Blocks.SOUL_SOIL);
            case NEST_RIM -> noise(x, y, z, 4) < 0.6 ? Blocks.SCULK.getDefaultState() : bone(noise(x, y, z, 5));
            case PLAZA -> plaza(noise(x, y, z, 6));
            case PATH_FLOOR -> pathFloor(x, y, z);
            case PATH_SLAB -> Blocks.DEEPSLATE_TILE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            case PATH_BODY -> noise(x, y, z, 7) < 0.25 ? Blocks.COBBLED_DEEPSLATE.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
            case PATH_UNDERSIDE -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case RAIL -> rail(noise(x, y, z, 8));
            case BALCONY_FLOOR -> balconyFloor(x, z);
            case BALCONY_RAIL -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case GATE_FRAME -> Blocks.REINFORCED_DEEPSLATE.getDefaultState();
            case GATE_TRIM -> (y + z) % 3 == 0 ? Blocks.CHISELED_DEEPSLATE.getDefaultState() : Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case TUNNEL_FLOOR -> noise(x, y, z, 9) < 0.2 ? Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
            case TUNNEL_WALL -> noise(x, y, z, 10) < 0.2 ? Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
            case TUNNEL_ROOF -> Blocks.DEEPSLATE_TILES.getDefaultState();
            case LINING -> Blocks.BLACKSTONE.getDefaultState();
            case MOUTH -> z == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case EXIT_FRAME -> Blocks.REINFORCED_DEEPSLATE.getDefaultState();
            case EXIT_FLOOR -> Blocks.DEEPSLATE_TILES.getDefaultState();
            case EXIT_WALL -> noise(x, y, z, 11) < 0.25 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case ROOM_FLOOR -> Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case ROOM_BORDER -> Blocks.RED_NETHER_BRICKS.getDefaultState();
            case ROOM_WALL -> noise(x, y, z, 12) < 0.2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                    : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            case ROOM_CEILING -> Math.abs(z) <= 1 && x >= -66 && x <= -62 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState()
                    : Blocks.DEEPSLATE_TILES.getDefaultState();
            case ROOM_PILLAR -> Blocks.RED_NETHER_BRICKS.getDefaultState();
            case SPIRE -> spire(x, y, z);
            case STALACTITE -> noise(x, y, z, 13) < 0.5 ? Blocks.BLACKSTONE.getDefaultState()
                    : noise(x, y, z, 14) < 0.5 ? Blocks.BASALT.getDefaultState() : Blocks.NETHERRACK.getDefaultState();
            case BONE -> boneAt(x, y, z);
        };
    }

    // ---- The hall ----

    /** The outside of the shell, where it meets the Nether: mostly netherrack, so it reads as rock from outside. */
    private static BlockState skin(double n) {
        if (n < 0.6) return Blocks.NETHERRACK.getDefaultState();
        if (n < 0.85) return Blocks.BLACKSTONE.getDefaultState();
        return Blocks.BASALT.getDefaultState();
    }

    /** How high the sculk has crept up the wall over column (x, z). */
    private int sculkLine(int x, int z) {
        return 3 + (int) (smooth(x / 9.0, z / 9.0, 20) * 12);
    }

    private BlockState wall(int x, int y, int z) {
        double n = noise(x, y, z, 21);
        int line = sculkLine(x, z);
        if (y <= line || (y <= line + 2 && n < 0.5)) return Blocks.SCULK.getDefaultState();
        double clump = noise(x >> 2, y >> 2, z >> 2, 22);
        if (clump < 0.35) return Blocks.BLACKSTONE.getDefaultState();
        if (clump < 0.6) return n < 0.15 ? Blocks.POLISHED_BASALT.getDefaultState() : Blocks.BASALT.getDefaultState();
        if (clump < 0.85) return n < 0.02 ? Blocks.GILDED_BLACKSTONE.getDefaultState() : Blocks.NETHERRACK.getDefaultState();
        return Blocks.SMOOTH_BASALT.getDefaultState();
    }

    /** Patches of crimson growth across the dome: where the weeping vines hang from. */
    private boolean crimson(int x, int z) {
        return smooth(x / 7.0, z / 7.0, 30) > 0.6;
    }

    private BlockState ceiling(int x, int y, int z) {
        double n = noise(x, y, z, 31);
        if (crimson(x, z)) {
            if (n < 0.06) return Blocks.SHROOMLIGHT.getDefaultState();
            return n < 0.7 ? Blocks.NETHER_WART_BLOCK.getDefaultState() : Blocks.CRIMSON_NYLIUM.getDefaultState();
        }
        return wall(x, y, z);
    }

    private BlockState floor(int x, int y, int z) {
        // Glowing cracks of magma winding across the floor: where a smooth noise crosses one value
        double crack = smooth(x / 11.0, z / 11.0, 40);
        if (Math.abs(crack - 0.5) < 0.025) return Blocks.MAGMA_BLOCK.getDefaultState();
        double n = noise(x, y, z, 41);
        if (n < 0.72) return Blocks.SCULK.getDefaultState();
        if (n < 0.84) return Blocks.SOUL_SOIL.getDefaultState();
        if (n < 0.93) return Blocks.BLACKSTONE.getDefaultState();
        return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
    }

    private static BlockState plaza(double n) {
        if (n < 0.4) return Blocks.POLISHED_BLACKSTONE.getDefaultState();
        if (n < 0.62) return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.78) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (n < 0.9) return Blocks.DEEPSLATE_TILES.getDefaultState();
        return Blocks.SCULK.getDefaultState();
    }

    private BlockState pathFloor(int x, int y, int z) {
        double n = noise(x, y, z, 50);
        // The sculk has crept up the lowest stretch of the ledge
        if (y < 8 && noise(x, y, z, 51) < (8 - y) / 10.0) return Blocks.SCULK.getDefaultState();
        if (n < 0.45) return Blocks.DEEPSLATE_TILES.getDefaultState();
        if (n < 0.65) return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        if (n < 0.8) return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE.getDefaultState();
    }

    /** Mostly broken away: here and there a stump of the old rail, or a slab of it. */
    private static BlockState rail(double n) {
        if (n < 0.45) return Blocks.AIR.getDefaultState();
        if (n < 0.65) return Blocks.DEEPSLATE_TILE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        return Blocks.DEEPSLATE_BRICKS.getDefaultState();
    }

    private static BlockState balconyFloor(int x, int z) {
        int dx = Math.abs(x - 39), az = Math.abs(z);
        if (dx + az <= 1) return Blocks.CHISELED_DEEPSLATE.getDefaultState();
        if (dx + az == 3) return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        return Blocks.DEEPSLATE_TILES.getDefaultState();
    }

    private BlockState spire(int x, int y, int z) {
        double n = noise(x, y, z, 60);
        if (y < 4 && n < (4 - y) / 4.0) return Blocks.SCULK.getDefaultState();
        if (n < 0.55) return Blocks.BASALT.getDefaultState();
        if (n < 0.85) return Blocks.BLACKSTONE.getDefaultState();
        return Blocks.POLISHED_BASALT.getDefaultState();
    }

    /** Ribs run along their arcs, the spine along the fossil: bone blocks turned the way the bone goes. */
    private static BlockState boneAt(int x, int y, int z) {
        boolean spine = y == WrathDenShape.RIB_RADIUS && z == WrathDenShape.FOSSIL_Z;
        Direction.Axis axis = spine ? Direction.Axis.X
                : Math.abs(z - WrathDenShape.FOSSIL_Z) > y ? Direction.Axis.Y : Direction.Axis.Z;
        return Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, axis);
    }

    private static BlockState bone(double n) {
        Direction.Axis axis = n < 0.33 ? Direction.Axis.X : n < 0.66 ? Direction.Axis.Y : Direction.Axis.Z;
        return Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, axis);
    }

    /**
     * Air, mostly. On the floor: sculk sensors here and there, and the skulls of whatever came too close round the
     * nest. Under the crimson patches of the dome: weeping vines, some of them long.
     */
    private BlockState decoration(int x, int y, int z) {
        BlockState air = Blocks.AIR.getDefaultState();
        double r = Math.sqrt(x * x + z * z);
        if (r >= WrathDenShape.RADIUS) return air;
        if (y == 1) {
            Part below = WrathDenShape.part(x, 0, z, tunnel);
            if (below == Part.FLOOR || below == Part.NEST_FLOOR) {
                double n = noise(x, y, z, 70);
                if (below == Part.FLOOR && n < 0.012 && r > 14) return Blocks.SCULK_SENSOR.getDefaultState();
                // (Never under her, where she lies)
                if (r > 5 && r < 18 && n > 0.992) {
                    Block skull = noise(x, y, z, 71) < 0.6 ? Blocks.SKELETON_SKULL : Blocks.WITHER_SKELETON_SKULL;
                    return skull.getDefaultState().with(SkullBlock.ROTATION, (int) (noise(x, y, z, 72) * 16));
                }
                if (below == Part.FLOOR && n > 0.97 && floor(x, 0, z).isOf(Blocks.SCULK)) {
                    return Blocks.SCULK_VEIN.getDefaultState().with(net.minecraft.block.MultifaceGrowthBlock.getProperty(Direction.DOWN), true);
                }
            }
            return air;
        }
        if (y > WrathDenShape.WALL_TOP - 10 && crimson(x, z)) {
            int ceiling = WrathDenShape.ceilingAt(r);
            int length = (int) (noise(x, 0, z, 73) * 14) - 4;
            int below = ceiling - y;
            if (length > 0 && below >= 0 && below < length && ceiling >= WrathDenShape.WALL_TOP
                    && WrathDenShape.part(x, ceiling + 1, z, tunnel) == Part.FACE) {
                return below == length - 1 ? Blocks.WEEPING_VINES.getDefaultState() : Blocks.WEEPING_VINES_PLANT.getDefaultState();
            }
        }
        return air;
    }

    // ---- The entrance tunnel ----

    /** A soul lantern hanging from the tunnel roof every eight blocks, and two standing at its mouth. */
    private BlockState tunnelLantern(int x, int y, int z) {
        int end = WrathDenShape.OUTER + tunnel - 1;
        if (x <= WrathDenShape.RADIUS || x > end) return null;
        int roof = WrathDenShape.BALCONY + WrathDenShape.TUNNEL_HEIGHT;
        if (y == roof && z == 0 && (end - x) % 8 == 4) return Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        if (y == WrathDenShape.BALCONY + 1 && x == end && Math.abs(z) == WrathDenShape.TUNNEL_HALF) return Blocks.SOUL_LANTERN.getDefaultState();
        return null;
    }

    // ---- The portal room ----

    private static void furnishSanctum() {
        Spot lock = WrathDenShape.LOCK;
        feature(lock, SinPortalFrameBlock.of(Sin.WRATH, Direction.EAST));
        for (Spot chest : WrathDenShape.ROOM_CHESTS) feature(chest, chest(chest.dir()));

        // The portal frame: 5x5 with a 3x3 opening, bottom row set into the floor, reinforced deepslate at the corners
        Spot frame = WrathDenShape.FRAME;
        for (int z = -2; z <= 2; z++) {
            for (int h = 0; h <= 4; h++) {
                boolean side = Math.abs(z) == 2, end = h == 0 || h == 4;
                BlockState block;
                if (side && end) block = Blocks.REINFORCED_DEEPSLATE.getDefaultState();
                else if (side) block = Blocks.POLISHED_BLACKSTONE.getDefaultState();
                else if (h == 4) block = z == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.RED_NETHER_BRICKS.getDefaultState();
                else if (h == 0) block = Blocks.POLISHED_BLACKSTONE.getDefaultState();
                else block = Blocks.AIR.getDefaultState();
                feature(frame.x(), frame.y() + h, z, block);
            }
        }
        // A red runner from the doorway to the lock
        for (int x = lock.x() + 1; x <= WrathDenShape.ROOM_EAST; x++) {
            for (int z = -1; z <= 1; z++) feature(x, 1, z, Blocks.RED_CARPET.getDefaultState());
        }
        // Red candles burning in the corners, and lanterns hanging over the runner
        for (int x : new int[]{WrathDenShape.ROOM_WEST + 1, WrathDenShape.ROOM_EAST - 1}) {
            for (int z : new int[]{-WrathDenShape.ROOM_HALF + 1, WrathDenShape.ROOM_HALF - 1}) {
                feature(x, 1, z, Blocks.RED_CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
            }
        }
        for (int x : new int[]{-61, -67}) {
            for (int z : new int[]{-4, 4}) {
                feature(x, WrathDenShape.ROOM_HEIGHT, z, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
            }
        }
    }

    // ---- Tripwires ----

    /** The post (a stub of old wall down off the ledge's edge), two hooks and the string, all hooked up. */
    private static void setTripwire(Tripwire wire) {
        Spot post = wire.post();
        for (int y = post.y() - 3; y <= post.y(); y++) feature(post.x(), y, post.z(), Blocks.DEEPSLATE_BRICKS.getDefaultState());
        feature(wire.innerHook(), hook(wire.innerHook().dir()));
        feature(wire.outerHook(), hook(wire.outerHook().dir()));
        Direction along = direction(wire.innerHook().dir());
        boolean eastWest = along.getAxis() == Direction.Axis.X;
        BlockState string = Blocks.TRIPWIRE.getDefaultState().with(TripwireBlock.ATTACHED, true)
                .with(eastWest ? TripwireBlock.EAST : TripwireBlock.NORTH, true)
                .with(eastWest ? TripwireBlock.WEST : TripwireBlock.SOUTH, true);
        for (Spot s : wire.string()) feature(s, string);
    }

    private static BlockState hook(int dir) {
        return Blocks.TRIPWIRE_HOOK.getDefaultState().with(TripwireHookBlock.FACING, direction(dir)).with(TripwireHookBlock.ATTACHED, true);
    }

    // ---- Helpers ----

    private static BlockState pick(double n, Block a, double ta, Block b, double tb, Block c) {
        if (n < ta) return a.getDefaultState();
        return n < tb ? b.getDefaultState() : c.getDefaultState();
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

    /** Smooth value noise in [0, 1): random values on a lattice, blended between with a smoothstep. */
    private double smooth(double x, double z, int salt) {
        int x0 = MathHelper.floor(x), z0 = MathHelper.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = noise(x0, 0, z0, salt), b = noise(x0 + 1, 0, z0, salt);
        double c = noise(x0, 0, z0 + 1, salt), d = noise(x0 + 1, 0, z0 + 1, salt);
        return MathHelper.lerp(fz, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
    }
}
