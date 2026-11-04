package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CubeMapRenderer.class)
public interface CubeMapRendererAccessor {
    /** The six face textures; the array is filled in place to switch panorama sets. */
    @Accessor("faces")
    Identifier[] fmscraft$faces();
}
