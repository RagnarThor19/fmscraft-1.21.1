package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Server -> client: the sky over RAGGIMEL's island. 1 = phase 1's light blue day, 2 = phase 2's dark blue night. */
public record GreedSkyPayload(int phase) implements CustomPayload {
    public static final Id<GreedSkyPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "greed_sky"));
    public static final PacketCodec<RegistryByteBuf, GreedSkyPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, GreedSkyPayload::phase, GreedSkyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
