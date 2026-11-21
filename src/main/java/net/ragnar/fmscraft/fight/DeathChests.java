package net.ragnar.fmscraft.fight;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** When someone dies in a boss fight, their items end up in chests next to the portal they came through. */
public final class DeathChests {
    private static final int CHEST_SIZE = 27;
    /** Players whose open screens are being emptied right now, and where what falls out of them goes. */
    private static final Map<PlayerEntity, List<ItemStack>> CAPTURING = new WeakHashMap<>();

    private DeathChests() {
    }

    /**
     * Empties everything the player has on them: the whole inventory (armour and offhand included), and whatever is in
     * their open screens - on the cursor, in the 2x2 crafting grid, in a crafting table's or anvil's slots - which the
     * game would otherwise drop at their feet once they're dead.
     */
    public static List<ItemStack> takeInventory(ServerPlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        // Closing the screens hands their items back: dropped (and caught here) if they're dead, into the inventory if not
        CAPTURING.put(player, items);
        try {
            if (player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();
            player.playerScreenHandler.onClosed(player);
        } finally {
            CAPTURING.remove(player);
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.removeStack(i);
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }

    /** Called as a player drops an item: true if it was caught for their death chests (so nothing is dropped). */
    public static boolean capture(PlayerEntity player, ItemStack stack) {
        List<ItemStack> items = CAPTURING.get(player);
        if (items == null) return false;
        if (!stack.isEmpty()) items.add(stack.copy());
        return true;
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
