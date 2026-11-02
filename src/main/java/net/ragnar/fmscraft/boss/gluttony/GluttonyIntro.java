package net.ragnar.fmscraft.boss.gluttony;

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
 * LalliLarge's intro: the same wheel as the others (timed by {@link GreedTimeline}), zooming in on Gluttony. Apple Pie
 * starts {@link LalliTimeline#SONG_LEAD} ticks (3.5 s) before the portal closes: the last of the wheel. When it closes,
 * the wheel fades as the first strum hits and everyone is high over the Earth in space, frozen, in a ring, looking
 * down at it; then they float down to the pole (his entrance, in {@link LalliEntity}).
 */
public final class GluttonyIntro implements FightIntro {
    public static final GluttonyIntro INSTANCE = new GluttonyIntro();

    private GluttonyIntro() {
    }

    /** Tick (since the portal opened) Apple Pie starts. */
    public static int musicStart(int joinTicks) {
        return Math.max(0, joinTicks - LalliTimeline.SONG_LEAD);
    }

    @Override
    public void onEnter(ServerPlayerEntity player, FightSession session, int elapsed) {
        Cinematics.freeze(player);
        Cinematics.screen(player, BossScreenPayload.intro(Sin.GLUTTONY), elapsed, session.joinTicks);
    }

    @Override
    public void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed) {
        if (session.song() == null && elapsed >= musicStart(session.joinTicks)) {
            BossFights.playSong(server, session, LalliEntity.PHASE_ONE, 0);
        }
        if (elapsed == GreedTimeline.zoomStart(session.joinTicks)) {
            // The wheel turns towards Gluttony: a cold wind, far off
            for (ServerPlayerEntity player : players) {
                player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.15f, 0.5f);
                player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.MASTER, 1.0f, 0.5f);
            }
        }
    }

    @Override
    public void onFightStart(List<ServerPlayerEntity> players) {
        int n = players.size();
        double radius = LalliTimeline.ringRadius(n);
        Vec3d center = GluttonyArena.POLE.add(0, LalliTimeline.SKY_HEIGHT, 0);
        for (int i = 0; i < n; i++) {
            ServerPlayerEntity player = players.get(i);
            double[] slot = LalliTimeline.ringSlot(i, n);
            double x = center.x + slot[0] * radius, z = center.z + slot[1] * radius;
            // Facing the middle of the ring, looking down at the Earth far below
            float yaw = (float) (MathHelper.atan2(center.z - z, center.x - x) * (180 / Math.PI)) - 90f;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 900, 0, false, false, false));
            player.teleport(player.getServerWorld(), x, center.y, z, yaw, 55f);
            player.fallDistance = 0;
            Cinematics.freeze(player);
            Cinematics.screen(player, BossScreenPayload.CLEAR, 0, LalliTimeline.REVEAL_TICKS);
            player.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.25f, 1.6f);
            player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 1.0f, 0.5f);
        }
    }
}
