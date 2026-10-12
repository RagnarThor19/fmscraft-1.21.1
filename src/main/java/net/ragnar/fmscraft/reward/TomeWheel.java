package net.ragnar.fmscraft.reward;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.network.TomeWheelPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * After a sin is defeated, everyone in the fight spins their own wheel: 50% beginner, 30% advanced, 20% master,
 * then a random tome of that tier. The spin lasts 10 seconds. The server rolls everything up front and hands the tome over when the spin
 * ends; the client only animates. So lag, a closed game or a missing client mod can never change or lose a prize.
 */
public final class TomeWheel {
    public static final int SPIN_TICKS = 200;
    /** Small grace period so the tome arrives just after the wheel visibly stops. */
    private static final int GIVE_DELAY = 5;

    private record Pending(ItemStack prize, long giveAt) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private TomeWheel() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TomeWheel::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> giveNow(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) giveNow(player);
            PENDING.clear();
        });
    }

    /** Rolls and starts a spin for this player. Does nothing if the magic mod's tomes aren't available. */
    public static void spin(ServerPlayerEntity player, Sin sin) {
        if (!MagicTomes.available()) return;
        Random random = player.getRandom();

        WheelTier tier = WheelTier.fromRoll(random.nextDouble());
        if (MagicTomes.of(tier).isEmpty()) tier = fallback(tier);
        List<Item> pool = MagicTomes.of(tier);
        ItemStack prize = new ItemStack(pool.get(random.nextInt(pool.size())));

        List<Integer> slices = new ArrayList<>();
        for (int i = 0; i < WheelMath.SLICES.length; i++) if (WheelMath.SLICES[i] == tier) slices.add(i);
        int slice = slices.get(random.nextInt(slices.size()));
        float offset = 0.2f + random.nextFloat() * 0.6f; // never stops right on a line

        giveNow(player); // a previous spin still running is paid out first
        if (ServerPlayNetworking.canSend(player, TomeWheelPayload.ID)) {
            PENDING.put(player.getUuid(), new Pending(prize, player.getServer().getTicks() + SPIN_TICKS + GIVE_DELAY));
            ServerPlayNetworking.send(player, new TomeWheelPayload(sin.ordinal(), slice, offset, SPIN_TICKS, prize.copy()));
        } else {
            player.getInventory().offerOrDrop(prize);
        }
    }

    private static WheelTier fallback(WheelTier tier) {
        for (WheelTier other : WheelTier.values()) if (!MagicTomes.of(other).isEmpty()) return other;
        return tier;
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTicks();
        for (UUID id : new ArrayList<>(PENDING.keySet())) {
            Pending pending = PENDING.get(id);
            if (now < pending.giveAt()) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) giveNow(player);
            else PENDING.remove(id);
        }
    }

    private static void giveNow(ServerPlayerEntity player) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending != null) player.getInventory().offerOrDrop(pending.prize());
    }

    public static boolean isSpinning(UUID player) {
        return PENDING.containsKey(player);
    }
}
