package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> the one player MUGEN has on his strings (his Controller): for {@code ticks} their movement keys don't do
 * what they should, scrambled a new way every so often by {@code seed} (client/MugenControl). 0 lets go at once.
 */
public record MugenControlPayload(int ticks, long seed) implements CustomPayload {
    public static final Id<MugenControlPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "mugen_control"));
    public static final PacketCodec<RegistryByteBuf, MugenControlPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, MugenControlPayload::ticks,
            PacketCodecs.VAR_LONG, MugenControlPayload::seed,
            MugenControlPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
