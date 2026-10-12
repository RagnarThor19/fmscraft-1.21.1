package net.ragnar.fmscraft.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.SaveLoading;
import net.minecraft.test.TestServer;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The vanilla game test server builds its world from an empty dimension list, so datapack dimensions
 * (our boss arenas, the twilight dimension) don't exist in tests. Hand it the real list instead.
 * Test-only: this lives in the gametest source set and never ships in the mod.
 */
@Mixin(TestServer.class)
public abstract class TestServerMixin {
    @WrapOperation(method = "method_40377", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder;toConfig(Lnet/minecraft/registry/Registry;)Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder$DimensionsConfig;"))
    private static DimensionOptionsRegistryHolder.DimensionsConfig fmscraft$keepDatapackDimensions(
            DimensionOptionsRegistryHolder holder, Registry<DimensionOptions> empty,
            Operation<DimensionOptionsRegistryHolder.DimensionsConfig> original,
            @Local(argsOnly = true) SaveLoading.LoadContextSupplierContext context) {
        return original.call(holder, context.dimensionsRegistryManager().get(RegistryKeys.DIMENSION));
    }
}
