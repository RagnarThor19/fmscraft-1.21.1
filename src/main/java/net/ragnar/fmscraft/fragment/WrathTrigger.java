package net.ragnar.fmscraft.fragment;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Wrath: kill 3 wardens within 4 minutes (any players). A red boss bar counts down for everyone online,
 * and the 3rd warden drops the fragment.
 */
public final class WrathTrigger {
    private static final WrathTracker TRACKER = new WrathTracker();
    private static final ServerBossBar BAR = new ServerBossBar(Text.empty(), BossBar.Color.RED, BossBar.Style.NOTCHED_6);

    private WrathTrigger() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof WardenEntity) || !(source.getAttacker() instanceof PlayerEntity)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;

            MinecraftServer server = world.getServer();
            WrathTracker.Result result = TRACKER.onKill(server.getTicks());
            if (result == WrathTracker.Result.COMPLETED) {
                BAR.clearPlayers();
                FragmentEffects.drop(world, entity.getPos().add(0, 0.5, 0), Sin.WRATH, 1);
            } else {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.0f, 0.8f);
                }
                updateBar(server);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TRACKER.checkExpired(server.getTicks())) {
                BAR.clearPlayers();
                server.getPlayerManager().broadcast(
                        Text.translatable("message.fmscraft.wrath_failed").formatted(net.minecraft.util.Formatting.DARK_RED), false);
            } else if (TRACKER.isActive() && server.getTicks() % 10 == 0) {
                updateBar(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
    }

    private static void updateBar(MinecraftServer server) {
        long left = TRACKER.ticksLeft(server.getTicks());
        long seconds = Math.max(0, (left + 19) / 20);
        BAR.setName(Text.translatable("bossbar.fmscraft.wrath",
                TRACKER.kills(), WrathTracker.KILLS_NEEDED, String.format("%d:%02d", seconds / 60, seconds % 60)));
        BAR.setPercent(Math.max(0f, (float) left / WrathTracker.WINDOW_TICKS));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BAR.addPlayer(player);
        }
    }

    public static int kills() {
        return TRACKER.kills();
    }

    public static void reset() {
        TRACKER.reset();
        BAR.clearPlayers();
    }
}
