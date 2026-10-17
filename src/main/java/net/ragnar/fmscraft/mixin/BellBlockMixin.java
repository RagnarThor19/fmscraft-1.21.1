package net.ragnar.fmscraft.mixin;

import net.minecraft.block.BellBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.ragnar.fmscraft.structure.sloth.SlothSpheres;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every way of ringing a bell (by hand, arrow or redstone) ends up here; the Sphere of Sloth listens for its own. */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
    @Inject(method = "ring(Lnet/minecraft/entity/Entity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("RETURN"))
    private void fmscraft$onRing(Entity entity, World world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && world instanceof ServerWorld serverWorld) SlothSpheres.onBellRung(serverWorld, pos);
    }
}
