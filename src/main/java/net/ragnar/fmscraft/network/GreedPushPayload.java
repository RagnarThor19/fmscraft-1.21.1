package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: a nudge added to the player's own velocity (RAGGIMEL's Twister dragging everyone in). Unlike a
 * velocity update it doesn't replace how they're moving, so they can still run against it.
 */
public record GreedPushPayload(float x, float y, float z) implements CustomPayload {
    public static final Id<GreedPushPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "greed_push"));
    public static final PacketCodec<RegistryByteBuf, GreedPushPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, GreedPushPayload::x,
            PacketCodecs.FLOAT, GreedPushPayload::y,
            PacketCodecs.FLOAT, GreedPushPayload::z,
            GreedPushPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
