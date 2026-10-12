package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import net.ragnar.fmscraft.client.ClientBossMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    // Keep vanilla background music from starting on top of a boss theme
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fmscraft$pauseForBossMusic(CallbackInfo ci) {
        if (ClientBossMusic.isPlaying()) ci.cancel();
    }
}
