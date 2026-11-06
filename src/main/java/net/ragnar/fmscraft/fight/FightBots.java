package net.ragnar.fmscraft.fight;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bots of {@code /fmscraft fight <sin> AI|NoAI <count> <canDie>}: stand-in friends for testing a fight alone.
 * <p>
 * They join next to the portal, step through it one after another, and from then on the fight treats them like
 * anyone else (see {@link FightBot}). Once the fight is over and they're back home, they log off. After a win they
 * follow the last real player out through the exit portal.
 */
public final class FightBots {
    /** Seven friends: you and six others. */
    public static final int MAX = 6;
    /** Ticks between bots stepping through the portal. */
    public static final int ENTER_GAP = 10;
    /** A bot logs off this long after its fight is over (time to see everyone back at the portal), or after it dies. */
    public static final int LEAVE_AFTER = 10 * 20;
    public static final int LEAVE_AFTER_DEATH = 3 * 20;

    private static final Map<UUID, FightBot> BOTS = new LinkedHashMap<>();

    private FightBots() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FightBots::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BOTS.clear());
    }

    /** The sin whose fight bots are still in, or null if there's none. */
    public static Sin busyWith() {
        for (FightBot bot : BOTS.values()) {
            if (bot.fightOverAt < 0 && bot.diedAt < 0) return bot.sin;
        }
        return null;
    }

    public static List<FightBot> bots() {
        return new ArrayList<>(BOTS.values());
    }

    /**
     * Joins {@code count} bots at {@code near} (beside the just-opened portal of {@code sin}) who walk in over the
     * next few seconds. Bots left over from an earlier test log off first.
     */
    public static List<FightBot> start(ServerWorld world, Vec3d near, float yaw, Sin sin, int count, boolean ai, boolean canDie) {
        MinecraftServer server = world.getServer();
        for (FightBot old : bots()) leave(old);

        List<FightBot> joined = new ArrayList<>();
        long now = server.getTicks();
        for (int i = 0; i < count; i++) {
            String name = "FmsBot" + (i + 1);
            ServerPlayerEntity stale = server.getPlayerManager().getPlayer(name);
            if (stale instanceof FightBot staleBot) leave(staleBot);
            else if (stale != null) continue; // someone real has that name

            double angle = Math.PI * 2 * i / Math.max(1, count);
            Vec3d at = near.add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            FightBot bot = FightBot.join(world, at, yaw, name, sin, ai, canDie);
            bot.enterAt = now + ENTER_GAP * (i + 1L);
            BOTS.put(bot.getUuid(), bot);
            joined.add(bot);
        }
        return joined;
    }

    private static void tick(MinecraftServer server) {
        if (BOTS.isEmpty()) return;
        long now = server.getTicks();
        for (FightBot bot : bots()) {
            if (server.getPlayerManager().getPlayer(bot.getUuid()) != bot) {
                BOTS.remove(bot.getUuid()); // kicked, or gone some other way
                continue;
            }
            FightSession session = BossFights.session(bot.sin);

            if (bot.isDead() || bot.isRemoved()) {
                if (bot.diedAt < 0) bot.diedAt = now;
                if (now - bot.diedAt >= LEAVE_AFTER_DEATH) leave(bot);
                continue;
            }

            if (bot.enterAt >= 0) {
                if (session != null && session.stage() == FightSession.Stage.JOINING) {
                    if (now >= bot.enterAt) {
                        BossFights.enter(bot, bot.sin);
                        bot.enterAt = -1;
                    }
                    continue;
                }
                bot.enterAt = -1; // the portal closed (or the fight was ended) before it got in
            }

            boolean inFight = session != null && session.participants.contains(bot.getUuid());
            if (!inFight) {
                if (bot.fightOverAt < 0) bot.fightOverAt = now;
                if (now - bot.fightOverAt >= LEAVE_AFTER) leave(bot);
                continue;
            }

            if (session.stage() == FightSession.Stage.WON && !realPlayerStillIn(server, session)) {
                // Everyone real has gone home: follow them out, as if through the exit portal
                BossFights.onPortalTouched(bot, bot.sin);
            }
        }
    }

    private static boolean realPlayerStillIn(MinecraftServer server, FightSession session) {
        for (ServerPlayerEntity player : BossFights.inArena(server, session)) {
            if (!(player instanceof FightBot)) return true;
        }
        return false;
    }

    /** Logs the bot off like a player closing the game ("left the game"). */
    public static void leave(FightBot bot) {
        BOTS.remove(bot.getUuid());
        MinecraftServer server = bot.getServer();
        if (server != null && server.getPlayerManager().getPlayer(bot.getUuid()) == bot) {
            bot.networkHandler.onDisconnected(new DisconnectionInfo(Text.literal("Test over")));
        }
    }
}
