package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.ragnar.fmscraft.boss.greed.GreedTimeline;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightIntro;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.List;

/**
 * ORNAMIX's intro: the same 30 seconds as RAGGIMEL's (the wheel, timed by {@link GreedTimeline}), zooming in on Wrath
 * instead, with N***as in Paris starting for the last 2 seconds. When it lifts he's kneeling in the middle of the pit
 * over his planted sword (his entrance, timed to the song's drop: {@link OrnamixTimeline}).
 */
public final class WrathIntro implements FightIntro {
    public static final WrathIntro INSTANCE = new WrathIntro();

    private WrathIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.WRATH), elapsed, session.joinTicks);
        OrnamixEntity.sky(player, 1);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        // Two seconds before the fight, so its beat drops just as he slams down (OrnamixTimeline)
        if (session.song() == null && elapsed >= Math.max(0, session.joinTicks - OrnamixTimeline.SONG_LEAD)) {
            BossFights.playSong(server, session, OrnamixEntity.PHASE_ONE, OrnamixTimeline.SONG_LEAD_FADE);
        }
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel starts turning towards Wrath
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.MASTER, 1.0f, 0.5f);
                player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.MASTER, 1.0f, 0.6f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            Cinematics.unfreeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, GreedTimeline.INTRO_REVEAL);
            player.playSoundToPlayer(SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }
    }
}
