package net.ragnar.fmscraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ragnar.fmscraft.fight.Arenas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ragnars Magic Mod's Tome of TPA ({@code /tpa <player>}, {@code /acc}) never takes anyone into or out of a boss's
 * arena: the only way in is the sin's portal while it's open, and the only way out is winning or dying. Its request
 * and accept return the reason they couldn't (shown to whoever typed the command), so that's what we return.
 * {@code @Pseudo}: the magic mod isn't a compile dependency, and without it this does nothing.
 */
@Pseudo
@Mixin(targets = "net.ragnar.ragnarsmagicmod.tpa.Tpa")
public abstract class TpaMixin {
    private static final String HERE = "You can't use /tpa in a boss's arena.";

    @Inject(method = "request", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void fmscraft$noRequestsInArenas(ServerPlayerEntity requester, ServerPlayerEntity target, CallbackInfoReturnable<String> cir) {
        if (inArena(requester)) cir.setReturnValue(HERE);
        else if (inArena(target)) cir.setReturnValue(target.getName().getString() + " is in a boss's arena.");
    }

    @Inject(method = "accept", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void fmscraft$noAcceptingInArenas(ServerPlayerEntity target, CallbackInfoReturnable<String> cir) {
        if (inArena(target)) cir.setReturnValue(HERE);
    }

    /** Whoever asked may have gone through a sin's portal since: then they stay where they are. */
    @Inject(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;stopRiding()V"),
            cancellable = true, require = 0)
    private static void fmscraft$requesterInArena(ServerPlayerEntity target, CallbackInfoReturnable<String> cir,
                                                  @Local(ordinal = 1) ServerPlayerEntity requester) {
        if (inArena(requester)) cir.setReturnValue(requester.getName().getString() + " is in a boss's arena.");
    }

    private static boolean inArena(ServerPlayerEntity player) {
        return Arenas.sinOf(player.getWorld()) != null;
    }
}
