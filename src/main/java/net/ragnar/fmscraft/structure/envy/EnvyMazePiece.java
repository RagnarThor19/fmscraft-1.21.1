package net.ragnar.fmscraft.structure.envy;

import net.minecraft.block.Block;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.loot.LootTable;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
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
import net.ragnar.fmscraft.structure.envy.EnvyMazeLayout.Kind;

/**
 * Builds the whole Envy maze from its seed. One piece covers the entire maze; each chunk only places the blocks
 * inside it, and every "random" choice is hashed from the block position, so chunks always line up.
 *
 * Vertical layout (per maze column): 2 blocks of deepslate shell, floor, 5 blocks of walkway, ceiling, 2 blocks of
 * shell. The maze is generated late (after caves, mineshafts, ores...) and replaces every block in its box, and the
 * shell seals it above and below, so nothing cuts into it.
 *
 * Statues aren't placed here: {@link EnvyMazes} keeps exactly 4 roaming each maze once a player comes near.
 */
public class EnvyMazePiece extends StructurePiece {
    public static final int UNIT = 5;
    public static final int HEIGHT = 7;
    public static final int SIZE = EnvyMazeLayout.N * UNIT;
    public static final int FLOOR_Y = -40;
    public static final int SHELL = 2;
    public static final RegistryKey<LootTable> LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/envy_maze"));
    public static final RegistryKey<LootTable> SAFE_ROOM_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/envy_safe_room"));
    public static final RegistryKey<LootTable> SAFE_ROOM_PLAIN_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/envy_safe_room_plain"));
    public static final RegistryKey<LootTable> BARREL_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/envy_barrel"));
    public static final Identifier SAFE_ROOM = Identifier.of(FmsCraft.MOD_ID, "envy_portal_room");
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final long seed;
    private EnvyMazeLayout layout;

    public EnvyMazePiece(long seed, BlockPos origin) {
        super(ModStructures.ENVY_MAZE_PIECE, 0, new BlockBox(origin.getX(), FLOOR_Y - SHELL, origin.getZ(),
                origin.getX() + SIZE - 1, FLOOR_Y + HEIGHT - 1 + SHELL, origin.getZ() + SIZE - 1));
        this.seed = seed;
    }

    public EnvyMazePiece(NbtCompound nbt) {
        super(ModStructures.ENVY_MAZE_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Seed", seed);
    }

    public EnvyMazeLayout layout() {
        if (layout == null) layout = EnvyMazeLayout.generate(seed);
        return layout;
    }

    public BlockPos origin() {
        return new BlockPos(boundingBox.getMinX(), FLOOR_Y, boundingBox.getMinZ());
    }

    public long seed() {
        return seed;
    }

    public static Direction direction(int dirIndex) {
        return DIRECTIONS[dirIndex];
    }

    /** World position of the block at the center of a unit's walkway (first air block above the floor). */
    public BlockPos unitCenter(int ux, int uz) {
        return new BlockPos(boundingBox.getMinX() + ux * UNIT + 2, FLOOR_Y + 1, boundingBox.getMinZ() + uz * UNIT + 2);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        EnvyMazeLayout maze = layout();
        int minX = Math.max(boundingBox.getMinX(), chunkBox.getMinX()), maxX = Math.min(boundingBox.getMaxX(), chunkBox.getMaxX());
        int minZ = Math.max(boundingBox.getMinZ(), chunkBox.getMinZ()), maxZ = Math.min(boundingBox.getMaxZ(), chunkBox.getMaxZ());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int ly = -SHELL; ly < HEIGHT + SHELL; ly++) {
                    pos.set(x, FLOOR_Y + ly, z);
                    BlockState state = ly < 0 || ly >= HEIGHT ? Blocks.DEEPSLATE_TILES.getDefaultState()
                            : blockAt(maze, x - boundingBox.getMinX(), ly, z - boundingBox.getMinZ(), x, z);
                    world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                }
            }
        }

        for (EnvyMazeLayout.Spot chest : maze.chests) {
            Direction toWall = direction(chest.dir());
            BlockPos at = unitCenter(chest.x(), chest.z()).offset(toWall, 2);
            if (chunkBox.contains(at)) {
                addChest(world, chunkBox, random, at, LOOT, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, toWall.getOpposite()));
            }
        }

        placeSafeRoom(world, chunkBox, random, maze);
        BlockPos lock = unitCenter(EnvyMazeLayout.CENTER, EnvyMazeLayout.CENTER);
        if (chunkBox.contains(lock)) {
            world.setBlockState(lock, SinPortalFrameBlock.of(Sin.ENVY, direction(maze.doorDir)), Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * The safe room is the hand-built {@link #SAFE_ROOM} template: 17x7x17, the 15x15 room plus the ring of wall
     * around it, with the doorway in the middle of its south wall, the lock in the center and the shrine around the
     * 3x3 where the portal opens, {@link SinPortalFrameBlock#PORTAL_DISTANCE} blocks behind the lock. It is turned so
     * its doorway faces the tunnel. The containers in it get their loot here: the first double chest is the tome
     * chest (one half {@link #SAFE_ROOM_LOOT}, as before), every other chest is plain, barrels get barrel loot.
     */
    private void placeSafeRoom(StructureWorldAccess world, BlockBox chunkBox, Random random, EnvyMazeLayout maze) {
        BlockPos lock = unitCenter(EnvyMazeLayout.CENTER, EnvyMazeLayout.CENTER);
        BlockPos corner = lock.add(-8, -1, -8);
        if (!BlockBox.create(corner, lock.add(8, HEIGHT - 2, 8)).intersects(chunkBox)) return;
        StructureTemplate template = world.getServer().getStructureTemplateManager().getTemplate(SAFE_ROOM).orElse(null);
        if (template == null) {
            FmsCraft.LOGGER.error("Missing structure template {}", SAFE_ROOM);
            return;
        }

        Direction forward = direction(maze.doorDir);
        BlockRotation rotation = BlockRotation.NONE;
        for (BlockRotation r : BlockRotation.values()) if (r.rotate(Direction.SOUTH) == forward) rotation = r;
        StructurePlacementData placement = new StructurePlacementData().setRotation(rotation)
                .setPosition(new BlockPos(8, 0, 8)).setBoundingBox(chunkBox);
        template.place(world, corner, corner, placement, random, Block.NOTIFY_LISTENERS);

        // Picked by template position, not list order, so every chunk the room spans agrees on which chest it is
        BlockPos tomeChest = null;
        for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(BlockPos.ORIGIN, new StructurePlacementData(), Blocks.CHEST)) {
            if (info.state().get(ChestBlock.CHEST_TYPE) == ChestType.LEFT && (tomeChest == null || info.pos().compareTo(tomeChest) < 0)) {
                tomeChest = info.pos();
            }
        }
        BlockPos tomeChestPos = tomeChest == null ? null : StructureTemplate.transform(placement, tomeChest).add(corner);
        for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(corner, placement, Blocks.CHEST)) {
            setLoot(world, chunkBox, random, info.pos(), info.pos().equals(tomeChestPos) ? SAFE_ROOM_LOOT : SAFE_ROOM_PLAIN_LOOT);
        }
        for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(corner, placement, Blocks.BARREL)) {
            setLoot(world, chunkBox, random, info.pos(), BARREL_LOOT);
        }
    }

    private static void setLoot(StructureWorldAccess world, BlockBox chunkBox, Random random, BlockPos pos, RegistryKey<LootTable> loot) {
        if (!chunkBox.contains(pos)) return;
        if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) container.setLootTable(loot, random.nextLong());
    }

    // ---- Block choice ----

    private double noise(int x, int y, int z, int salt) {
        long h = MathHelper.hashCode(x, y * 31 + salt, z) ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private BlockState blockAt(EnvyMazeLayout maze, int lx, int ly, int lz, int x, int z) {
        int ux = lx / UNIT, uz = lz / UNIT, ox = lx % UNIT, oz = lz % UNIT;
        Kind kind = maze.kind(ux, uz);
        int y = FLOOR_Y + ly;

        if (kind == Kind.WALL) return wall(maze, ux, uz, ox, oz, ly, x, y, z);
        if (kind == Kind.DOOR) {
            boolean alongZ = maze.doorDir == 0 || maze.doorDir == 2;
            boolean tunnel = (alongZ ? ox : oz) == 2 && ly >= 1 && ly <= 2;
            if (tunnel) return Blocks.AIR.getDefaultState();
            if (ly == 0 && (alongZ ? ox : oz) == 2) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            return Blocks.STONE_BRICKS.getDefaultState();
        }
        if (kind == Kind.ROOM) return Blocks.AIR.getDefaultState(); // filled by the safe room template

        // Corridor
        if (ly == 0) {
            if (ox == 2 && oz == 2 && ux % 2 == 1 && uz % 2 == 1) return Blocks.CHISELED_STONE_BRICKS.getDefaultState();
            double n = noise(x, y, z, 1);
            if (n < 0.08) return Blocks.MOSS_BLOCK.getDefaultState();
            if (n < 0.33) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            if (n < 0.43) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            return Blocks.STONE_BRICKS.getDefaultState();
        }
        if (ly == HEIGHT - 1) {
            if (ox == 2 && oz == 2 && ux % 2 == 1 && uz % 2 == 1 && noise(x, y, z, 2) < 0.35) {
                return Blocks.VERDANT_FROGLIGHT.getDefaultState();
            }
            return noise(x, y, z, 3) < 0.3 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
        }
        // Walkway air, with vines and glow lichen only along the walls and ceiling, never in the middle
        if (ly >= 4) {
            BlockState vine = vineAgainstWall(maze, ux, uz, ox, oz, x, y, z);
            if (vine != null) return vine;
        }
        if (ly == HEIGHT - 2 && noise(x, y, z, 4) < 0.05) {
            return Blocks.GLOW_LICHEN.getDefaultState().with(MultifaceGrowthBlock.getProperty(Direction.UP), true);
        }
        return Blocks.AIR.getDefaultState();
    }

    private BlockState vineAgainstWall(EnvyMazeLayout maze, int ux, int uz, int ox, int oz, int x, int y, int z) {
        if (noise(x, 0, z, 5) >= 0.12) return null; // whole column decides together so vines hang 2 blocks
        if (ox == 0 && !open(maze, ux - 1, uz)) return Blocks.VINE.getDefaultState().with(VineBlock.WEST, true);
        if (ox == 4 && !open(maze, ux + 1, uz)) return Blocks.VINE.getDefaultState().with(VineBlock.EAST, true);
        if (oz == 0 && !open(maze, ux, uz - 1)) return Blocks.VINE.getDefaultState().with(VineBlock.NORTH, true);
        if (oz == 4 && !open(maze, ux, uz + 1)) return Blocks.VINE.getDefaultState().with(VineBlock.SOUTH, true);
        return null;
    }

    private static boolean open(EnvyMazeLayout maze, int ux, int uz) {
        Kind kind = maze.kind(ux, uz);
        return kind == Kind.PATH || kind == Kind.ROOM;
    }

    private BlockState wall(EnvyMazeLayout maze, int ux, int uz, int ox, int oz, int ly, int x, int y, int z) {
        if (ly == 0 || ly == HEIGHT - 1) return Blocks.STONE_BRICKS.getDefaultState();

        // Which walkway (if any) this block faces, and whether it's the middle column of that face
        Kind faces = null;
        boolean middle = false;
        if (ox == 0 && open(maze, ux - 1, uz)) { faces = maze.kind(ux - 1, uz); middle = oz == 2; }
        else if (ox == 4 && open(maze, ux + 1, uz)) { faces = maze.kind(ux + 1, uz); middle = oz == 2; }
        else if (oz == 0 && open(maze, ux, uz - 1)) { faces = maze.kind(ux, uz - 1); middle = ox == 2; }
        else if (oz == 4 && open(maze, ux, uz + 1)) { faces = maze.kind(ux, uz + 1); middle = ox == 2; }
        if (faces == null || faces == Kind.ROOM) return Blocks.STONE_BRICKS.getDefaultState(); // room walls: the template
        if (ly == 1) return Blocks.OXIDIZED_CUT_COPPER.getDefaultState();
        if (ly == 3 && middle) {
            double n = noise(x, y, z, 6);
            if (n < 0.18) return Blocks.OXIDIZED_CHISELED_COPPER.getDefaultState(); // the watching eyes
            if (n < 0.5) return Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        }
        double n = noise(x, y, z, 7);
        if (n < 0.30) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (n < 0.45) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if (n < 0.50) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        return Blocks.STONE_BRICKS.getDefaultState();
    }
}
