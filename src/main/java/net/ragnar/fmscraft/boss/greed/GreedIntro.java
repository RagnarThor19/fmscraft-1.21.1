package net.ragnar.fmscraft.boss.greed;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightIntro;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.network.GreedSkyPayload;

import java.util.List;

/** RAGGIMEL's intro, see {@link GreedTimeline}. */
public final class GreedIntro implements FightIntro {
    public static final GreedIntro INSTANCE = new GreedIntro();

    private GreedIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.INTRO, elapsed, session.joinTicks);
        sky(player, 1);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (session.song() == null && elapsed >= GreedTimeline.musicStart(session.joinTicks)) {
            BossFights.playSong(server, session, RaggimelEntity.PHASE_ONE, GreedTimeline.MUSIC_FADE);
        }
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel starts turning towards Greed
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.MASTER, 1.0f, 0.6f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            Cinematics.unfreeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, GreedTimeline.INTRO_REVEAL);
            player.playSoundToPlayer(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
    }

    static void sky(ServerPlayerEntity player, int phase) {
        if (ServerPlayNetworking.canSend(player, GreedSkyPayload.ID)) ServerPlayNetworking.send(player, new GreedSkyPayload(phase));
    }
}
