package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: the sky over MUGEN's compound (drawn by client/LustSky). {@code phase} 1 is the violet night under
 * the full moon, 2 the blood-red eclipse; {@code blackout} is how many ticks the world stays black round you (his
 * Blackout), 0 to lift it at once, -1 to leave it as it is.
 */
public record LustSkyPayload(int phase, int blackout) implements CustomPayload {
    public static final Id<LustSkyPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "lust_sky"));
    public static final PacketCodec<RegistryByteBuf, LustSkyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, LustSkyPayload::phase,
            PacketCodecs.INTEGER, LustSkyPayload::blackout,
            LustSkyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
