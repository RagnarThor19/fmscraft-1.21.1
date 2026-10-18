package net.ragnar.fmscraft.structure.greed;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole Greed Vault, placed from its hand-built template ({@link #TEMPLATE}) and turned to a random side.
 * Barrels get {@link #BARREL_LOOT}; the chests, pot and bookshelf holding the clue books keep exactly what the
 * template puts in them (no loot table), and {@link GreedVaults} puts any taken clue back when the vault resets.
 * The Greed lock goes in front of the portal frame, and anything that grew over the mouth of the pipe is cleared.
 */
public class GreedVaultPiece extends SimpleStructurePiece {
    public static final Identifier TEMPLATE = Identifier.of(FmsCraft.MOD_ID, "greed_vault");
    public static final RegistryKey<LootTable> BARREL_LOOT =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(FmsCraft.MOD_ID, "chests/greed_vault_barrel"));
    /** How far above the pipe to clear trees and the like out of the way. */
    private static final int CLEAR_ABOVE = 16;

    /** A container in the template with a clue book in it, and that book. */
    public record Clue(BlockPos pos, int slot, ItemStack book) {
    }

    public GreedVaultPiece(StructureTemplateManager manager, BlockPos corner, BlockRotation rotation) {
        super(ModStructures.GREED_VAULT_PIECE, 0, manager, TEMPLATE, TEMPLATE.toString(), placement(rotation), corner);
    }

    public GreedVaultPiece(StructureTemplateManager manager, NbtCompound nbt) {
        super(ModStructures.GREED_VAULT_PIECE, nbt, manager, id -> placement(BlockRotation.valueOf(nbt.getString("Rot"))));
    }

    public static StructurePlacementData placement(BlockRotation rotation) {
        return new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE)
                .setPosition(GreedVaultLayout.PIVOT).setIgnoreEntities(true);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        super.writeNbt(context, nbt);
        nbt.putString("Rot", placementData.getRotation().name());
    }

    @Override
    protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox) {
    }

    // ---- Template to world ----

    public BlockRotation rotation() {
        return placementData.getRotation();
    }

    public BlockPos toWorld(BlockPos local) {
        return StructureTemplate.transform(placementData, local).add(pos);
    }

    public BlockPos toLocal(BlockPos world) {
        BlockRotation inverse = switch (rotation()) {
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            default -> rotation();
        };
        return StructureTemplate.transformAround(world.subtract(pos), BlockMirror.NONE, inverse, GreedVaultLayout.PIVOT);
    }

    /** Whether this block is in the buried rooms and corridors (not the pipe sticking up out of them). */
    public boolean inBody(BlockPos world) {
        return boundingBox.contains(world) && GreedVaultLayout.inBody(toLocal(world));
    }

    public Direction toWorld(Direction local) {
        return rotation().rotate(local);
    }

    /** A stable id for this vault. */
    public long id() {
        return pos.asLong();
    }

    public List<BlockPos> levers() {
        return GreedVaultLayout.LEVERS.stream().map(this::toWorld).toList();
    }

    public List<BlockPos> door() {
        return GreedVaultLayout.doorBlocks().stream().map(this::toWorld).toList();
    }

    public List<BlockPos> doorRow(int row) {
        return GreedVaultLayout.doorRow(row).stream().map(this::toWorld).toList();
    }

    public List<BlockPos> statueHomes() {
        return GreedVaultLayout.STATUE_HOMES.stream().map(this::toWorld).toList();
    }

    public BlockPos lock() {
        return toWorld(GreedVaultLayout.LOCK);
    }

    /** The clue books, read from the template so a re-saved vault with books moved around still works. */
    public List<Clue> clues(RegistryWrapper.WrapperLookup registries) {
        List<Clue> clues = new ArrayList<>();
        // Fresh placement data: generating leaves the last chunk's box on ours, and lookups skip blocks outside it
        StructurePlacementData whole = placement(rotation());
        for (Block block : List.of(Blocks.CHEST, Blocks.BARREL, Blocks.DECORATED_POT, Blocks.CHISELED_BOOKSHELF)) {
            for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(pos, whole, block)) {
                NbtCompound nbt = info.nbt();
                if (nbt == null) continue;
                if (nbt.contains("item", NbtElement.COMPOUND_TYPE)) {
                    ItemStack.fromNbt(registries, nbt.getCompound("item"))
                            .filter(stack -> stack.isOf(Items.WRITABLE_BOOK))
                            .ifPresent(book -> clues.add(new Clue(info.pos(), 0, book)));
                }
                NbtList items = nbt.getList("Items", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < items.size(); i++) {
                    NbtCompound item = items.getCompound(i);
                    int slot = item.getByte("Slot") & 255;
                    ItemStack.fromNbt(registries, item).filter(stack -> stack.isOf(Items.WRITABLE_BOOK))
                            .ifPresent(book -> clues.add(new Clue(info.pos(), slot, book)));
                }
            }
        }
        return clues;
    }

    // ---- Building ----

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        super.generate(world, accessor, generator, random, chunkBox, chunkPos, pivot);

        for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(pos, placementData, Blocks.BARREL)) {
            if (!chunkBox.contains(info.pos()) || hasBook(info.nbt())) continue;
            if (world.getBlockEntity(info.pos()) instanceof LootableContainerBlockEntity barrel) barrel.setLootTable(BARREL_LOOT, random.nextLong());
        }

        BlockPos lock = lock();
        if (chunkBox.contains(lock)) {
            world.setBlockState(lock, SinPortalFrameBlock.of(Sin.GREED, toWorld(GreedVaultLayout.LOCK_FACING)), Block.NOTIFY_LISTENERS);
        }

        // Keep the mouth of the pipe open to the sky
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int x = GreedVaultLayout.PIPE_MIN_X - 1; x <= GreedVaultLayout.PIPE_MAX_X + 1; x++) {
            for (int z = GreedVaultLayout.PIPE_MIN_Z - 1; z <= GreedVaultLayout.PIPE_MAX_Z + 1; z++) {
                BlockPos column = toWorld(new BlockPos(x, GreedVaultLayout.SIZE_Y, z));
                if (!chunkBox.contains(column.getX(), chunkBox.getMinY(), column.getZ())) continue;
                for (int y = 0; y < CLEAR_ABOVE; y++) {
                    at.set(column.getX(), column.getY() + y, column.getZ());
                    if (!world.getBlockState(at).isAir()) world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static boolean hasBook(NbtCompound nbt) {
        return nbt != null && nbt.toString().contains("minecraft:writable_book");
    }
}
