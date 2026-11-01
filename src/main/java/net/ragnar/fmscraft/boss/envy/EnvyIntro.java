package net.ragnar.fmscraft.boss.envy;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.boss.greed.GreedTimeline;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightIntro;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.List;

/**
 * GUMMSS's intro: the same wheel as RAGGIMEL's and ORNAMIX's (timed by {@link GreedTimeline}), zooming in on Envy.
 * Test &amp; Recognise starts during the zoom ({@link GummssTimeline#musicStart}). When the portal closes, everyone is
 * lifted high over the arena into a ring round GUMMSS, facing him, while the wheel fades; then they fall with him
 * (his entrance, in {@link GummssEntity}), still frozen, and land as the bass drops.
 */
public final class EnvyIntro implements FightIntro {
    public static final EnvyIntro INSTANCE = new EnvyIntro();

    private EnvyIntro() {
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.ENVY), elapsed, session.joinTicks);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (session.song() == null && elapsed >= GummssTimeline.musicStart(session.joinTicks)) {
            BossFights.playSong(server, session, GummssEntity.SONG, GummssTimeline.MUSIC_FADE);
        }
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel turns towards Envy: something takes notice of you
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.MASTER, 0.8f, 0.5f);
                player.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.0f, 0.5f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        int n = players.size();
        double radius = GummssTimeline.ringRadius(n);
        Vec3d center = EnvyArena.BOSS_SPAWN;
        for (int i = 0; i < n; i++) {
            ServerPlayerEntity player = players.get(i);
            double[] slot = GummssTimeline.ringSlot(i, n);
            double x = center.x + slot[0] * radius, z = center.z + slot[1] * radius;
            // Facing him, looking a little down past him at the arena far below
            float yaw = (float) (MathHelper.atan2(center.z - z, center.x - x) * (180 / Math.PI)) - 90f;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,
                    GummssTimeline.HOVER_TICKS + GummssTimeline.FALL_TICKS + 200, 0, false, false, false));
            player.teleport(player.getServerWorld(), x, center.y, z, yaw, 12f);
            player.fallDistance = 0;
            Cinematics.freeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, GummssTimeline.REVEAL_TICKS);
            player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.25f, 1.6f);
            player.playSoundToPlayer(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
    }
}
