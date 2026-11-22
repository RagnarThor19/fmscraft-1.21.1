package net.ragnar.fmscraft.mixin;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.ragnar.fmscraft.world.FastMinecarts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The minecartSpeed gamerule (FastMinecarts): after a cart's rail movement for the tick, it gets that many more goes
 * at it, each one exactly what vanilla's tick does on a rail (gravity, find the rail it's on, move along it, an
 * activator rail's effect, then touching the blocks it's in, which is what powers detector rails). It stops early on
 * leaving the rails, being removed, or reaching a chunk that isn't ticking.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends Entity {
    @Unique
    private boolean fmscraft$fastStep;

    protected AbstractMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    protected abstract void moveOnRail(BlockPos pos, BlockState state);

    @Shadow
    public abstract void onActivatorRail(int x, int y, int z, boolean powered);

    @Inject(method = "moveOnRail", at = @At("TAIL"))
    private void fmscraft$moreRailSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (fmscraft$fastStep || !(getWorld() instanceof ServerWorld world)) return;
        int steps = Math.min(world.getGameRules().getInt(FastMinecarts.SPEED), FastMinecarts.MAX);
        if (steps <= 1) return;

        fmscraft$fastStep = true;
        try {
            for (int step = 1; step < steps; step++) {
                checkBlockCollision(); // what it passed over on the step before
                if (isRemoved()) return;

                int x = MathHelper.floor(getX()), y = MathHelper.floor(getY()), z = MathHelper.floor(getZ());
                if (world.getBlockState(new BlockPos(x, y - 1, z)).isIn(BlockTags.RAILS)) y--;
                BlockPos at = new BlockPos(x, y, z);
                if (!world.shouldTickEntity(at)) return; // never on into chunks that aren't loaded and ticking
                BlockState rail = world.getBlockState(at);
                if (!AbstractRailBlock.isRail(rail)) return;

                applyGravity();
                moveOnRail(at, rail);
                if (rail.isOf(Blocks.ACTIVATOR_RAIL)) onActivatorRail(x, y, z, rail.get(PoweredRailBlock.POWERED));
                if (isRemoved()) return;
            }
        } finally {
            fmscraft$fastStep = false;
        }
    }
}
