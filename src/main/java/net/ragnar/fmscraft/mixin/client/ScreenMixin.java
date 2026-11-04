package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.client.TitlePanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    // One of our panorama sets, picked at random each launch, behind the title screen and menus
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/CubeMapRenderer;<init>(Lnet/minecraft/util/Identifier;)V"))
    private static Identifier fmscraft$randomPanorama(Identifier vanilla) {
        return TitlePanorama.pick(vanilla);
    }
}
