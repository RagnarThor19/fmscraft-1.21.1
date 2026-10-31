package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Server -> client: the sky over ORNAMIX's colosseum. 1 = phase 1's bright red, 2 = Ragemode's black red. */
public record WrathSkyPayload(int phase) implements CustomPayload {
    public static final Id<WrathSkyPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "wrath_sky"));
    public static final PacketCodec<RegistryByteBuf, WrathSkyPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, WrathSkyPayload::phase, WrathSkyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
