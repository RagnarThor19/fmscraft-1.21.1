package net.ragnar.fmscraft.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.ragnar.fmscraft.fragment.LuckyStrike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Greed's lucky strike: afterBreak is where a block a player harvested drops its loot (only in survival, only with a
 * tool that can harvest it), with the tool that broke it, so the fragment comes alongside the ore's own drops.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "afterBreak", at = @At("TAIL"))
    private void fmscraft$luckyStrike(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                      ItemStack tool, CallbackInfo ci) {
        LuckyStrike.onHarvested(world, player, pos, state, tool);
    }
}
