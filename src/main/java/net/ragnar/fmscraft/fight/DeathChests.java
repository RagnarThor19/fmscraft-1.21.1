package net.ragnar.fmscraft.fight;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** When someone dies in a boss fight, their items end up in chests next to the portal they came through. */
public final class DeathChests {
    private static final int CHEST_SIZE = 27;

    private DeathChests() {
    }

    /** Empties the player's whole inventory (armor and offhand included). */
    public static List<ItemStack> takeInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.removeStack(i);
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }

    /** Places as many chests as needed near {@code near} and fills them. Returns where the chests went. */
    public static List<BlockPos> store(ServerWorld world, BlockPos near, List<ItemStack> items) {
        List<BlockPos> chests = new ArrayList<>();
        for (int start = 0; start < items.size(); start += CHEST_SIZE) {
            BlockPos pos = findSpot(world, near, chests);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                List<ItemStack> part = items.subList(start, Math.min(items.size(), start + CHEST_SIZE));
                for (int slot = 0; slot < part.size(); slot++) chest.setStack(slot, part.get(slot));
            }
            chests.add(pos);
        }
        return chests;
    }

    /** Nearest open spot with solid ground, spiralling outwards. Chests aren't put next to each other so they never merge. */
    private static BlockPos findSpot(ServerWorld world, BlockPos near, List<BlockPos> taken) {
        for (int r = 1; r <= 6; r++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos pos = near.add(dx, dy, dz);
                        if (isGoodSpot(world, pos, taken)) return pos;
                    }
                }
            }
        }
        BlockPos fallback = near.up(2);
        while (taken.contains(fallback) || !world.getBlockState(fallback).isReplaceable()) fallback = fallback.up();
        return fallback;
    }

    private static boolean isGoodSpot(ServerWorld world, BlockPos pos, List<BlockPos> taken) {
        if (!world.getBlockState(pos).isReplaceable() || !world.getFluidState(pos).isEmpty()) return false;
        if (!world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) return false;
        for (BlockPos other : taken) {
            if (other.getManhattanDistance(pos) <= 1) return false;
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (world.getBlockState(pos.offset(dir)).isOf(Blocks.CHEST)) return false;
        }
        return true;
    }
}
