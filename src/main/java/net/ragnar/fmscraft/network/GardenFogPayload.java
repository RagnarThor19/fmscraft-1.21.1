package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Server -> client: the player went into Lust's Garden (the fog takes on a rose tint) or came back out. */
public record GardenFogPayload(boolean inside) implements CustomPayload {
    public static final Id<GardenFogPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "garden_fog"));
    public static final PacketCodec<RegistryByteBuf, GardenFogPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOL, GardenFogPayload::inside, GardenFogPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
