package net.ragnar.fmscraft.boss.sloth;

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
 * RICKY's intro: the same wheel as the others (timed by {@link GreedTimeline}), zooming in on Sloth, with only the wind
 * over the flooded city. When the portal closes the wheel fades on the clearing at sunset, everyone standing in an arc
 * east of its middle looking west into the sun; overseas starts, and there he is, asleep in the grass by the water (his
 * arrival, in {@link RickyEntity}).
 */
public final class SlothIntro implements FightIntro {
    public static final SlothIntro INSTANCE = new SlothIntro();

    private SlothIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.SLOTH), elapsed, session.joinTicks);
        RickyEntity.sky(player, 1);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel turns towards Sloth: wind over the water, a bell far off in the drowned streets
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.12f, 0.6f);
                player.playSoundToPlayer(SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 0.4f, 0.5f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        int n = players.size();
        double radius = RickyTimeline.watchRadius(n);
        Vec3d center = SlothArena.CENTER;
        for (int i = 0; i < n; i++) {
            ServerPlayerEntity player = players.get(i);
            double[] slot = RickyTimeline.watchSlot(i, n);
            double r = radius + (n > 7 && i % 2 == 1 ? 2.5 : 0);
            double x = center.x + slot[0] * r, z = center.z + slot[1] * r;
            float yaw = (float) (MathHelper.atan2(center.z - z, center.x - x) * (180 / Math.PI)) - 90f;
            player.teleport(player.getServerWorld(), x, SlothArena.feetAt(player.getServerWorld(), x, z), z, yaw, -2f);
            player.fallDistance = 0;
            // Held until the wheel has gone (RickyEntity lets go)
            Cinematics.freeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, RickyTimeline.REVEAL_TICKS);
            RickyEntity.sky(player, 1);
            player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.2f, 1.6f);
        }
    }
}
