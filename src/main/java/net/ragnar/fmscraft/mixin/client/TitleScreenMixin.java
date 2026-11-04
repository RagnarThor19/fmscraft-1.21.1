package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.text.Text;
import net.ragnar.fmscraft.client.TitlePanorama;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    // A fresh random panorama every time the title screen opens (launch, leaving a world, ...)
    @Inject(method = "<init>(ZLnet/minecraft/client/gui/LogoDrawer;)V", at = @At("TAIL"))
    private void fmscraft$shufflePanorama(boolean doBackgroundFade, @Nullable LogoDrawer logoDrawer, CallbackInfo ci) {
        TitlePanorama.shuffle(((CubeMapRendererAccessor) PANORAMA_RENDERER).fmscraft$faces());
    }
}
