package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import net.ragnar.fmscraft.client.ClientBossMusic;
import net.ragnar.fmscraft.client.credits.CreditsMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    // Keep vanilla background music from starting on top of a boss theme or the True Ending credits
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void fmscraft$pauseForBossMusic(CallbackInfo ci) {
        if (ClientBossMusic.isPlaying() || CreditsMusic.isPlaying()) ci.cancel();
    }
}
