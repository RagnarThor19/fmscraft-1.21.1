package net.ragnar.fmscraft.boss.lust;

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
import net.ragnar.fmscraft.sin.Sin;

import java.util.List;

/**
 * MUGEN's intro: the same wheel as the others (timed by {@link GreedTimeline}), zooming in on Lust, in silence but for
 * a wind through the cherry trees. When the portal closes COMË N GO starts (the boss starts it: its reversed intro is
 * his whole entrance) and the wheel fades on the courtyard at night, everyone standing in an arc to the east of its
 * middle, frozen, watching the shadow spread there (his entrance, in {@link MugenEntity}).
 */
public final class LustIntro implements FightIntro {
    public static final LustIntro INSTANCE = new LustIntro();

    private LustIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.LUST), elapsed, session.joinTicks);
        MugenEntity.sky(player, 1, 0);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel turns towards Lust: wind in the cherry trees, a bell far off
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.BLOCK_CHERRY_LEAVES_BREAK, SoundCategory.MASTER, 0.6f, 0.5f);
                player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.12f, 0.6f);
                player.playSoundToPlayer(SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 0.6f, 0.5f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        int n = players.size();
        double radius = MugenTimeline.watchRadius(n);
        Vec3d center = LustArena.CENTER;
        for (int i = 0; i < n; i++) {
            ServerPlayerEntity player = players.get(i);
            double[] slot = MugenTimeline.watchSlot(i, n);
            // A second row behind the first for a big group, so nobody stands in anybody's way
            double r = radius + (n > 7 && i % 2 == 1 ? 2.5 : 0);
            double x = center.x + slot[0] * r, z = center.z + slot[1] * r;
            float yaw = (float) (MathHelper.atan2(center.z - z, center.x - x) * (180 / Math.PI)) - 90f;
            player.teleport(player.getServerWorld(), x, center.y, z, yaw, 12f);
            player.fallDistance = 0;
            Cinematics.freeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, MugenTimeline.REVEAL_TICKS);
            MugenEntity.sky(player, 1, 0);
            player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.2f, 1.6f);
            player.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.8f, 0.5f);
        }
    }
}
