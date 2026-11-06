package net.ragnar.fmscraft.boss.pride;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.boss.greed.GreedTimeline;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightIntro;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.network.PrideSkyPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.List;

/**
 * LEONARD's intro: the same wheel as the others (timed by {@link GreedTimeline}), zooming in on Pride, in silence. When
 * the portal closes the wheel fades on the plaza in the sky, everyone standing in an arc west of its middle looking east
 * over the clouds; then ten seconds of nothing at all before the song (his arrival, in {@link LeonardEntity}).
 */
public final class PrideIntro implements FightIntro {
    public static final PrideIntro INSTANCE = new PrideIntro();

    private PrideIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.PRIDE), elapsed, session.joinTicks);
        LeonardEntity.sky(player, 1, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel turns towards Pride: a rush of high wind
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.15f, 0.7f);
                player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 0.5f, 1.6f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        int n = players.size();
        double radius = LeonardTimeline.watchRadius(n);
        Vec3d center = PrideArena.CENTER;
        for (int i = 0; i < n; i++) {
            ServerPlayerEntity player = players.get(i);
            double[] slot = LeonardTimeline.watchSlot(i, n);
            double r = radius + (n > 7 && i % 2 == 1 ? 2.5 : 0);
            double x = center.x + slot[0] * r, z = center.z + slot[1] * r;
            float yaw = (float) (MathHelper.atan2(center.z - z, center.x - x) * (180 / Math.PI)) - 90f;
            player.teleport(player.getServerWorld(), x, center.y, z, yaw, -4f);
            player.fallDistance = 0;
            // Held until the wheel has gone (LeonardEntity lets go)
            Cinematics.freeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, LeonardTimeline.REVEAL_TICKS);
            LeonardEntity.sky(player, 1, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
            player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.2f, 1.6f);
        }
    }
}
