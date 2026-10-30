package net.ragnar.fmscraft.fight;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * A cutscene that plays in the arena while a sin's portal is open, instead of just waiting. Bosses that have one
 * return it from {@link net.ragnar.fmscraft.boss.ModBossEntities#introFor}. All times count from the portal opening,
 * so whoever comes through late joins the intro where everyone else already is.
 */
public interface FightIntro {
    /** {@code player} just arrived in the arena, {@code elapsed} ticks after the portal opened. */
    void onEnter(ServerPlayerEntity player, FightSession session, int elapsed);

    /** Every tick while the portal is open, with everyone waiting in the arena. */
    void tick(MinecraftServer server, FightSession session, List<ServerPlayerEntity> players, int elapsed);

    /** The portal closed and the boss is up: hand control back. */
    void onFightStart(List<ServerPlayerEntity> players);
}
