package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: the sky over RICKY's city (drawn by client/SlothSky). {@code phase} 1 is the sunset the dimension
 * stands still in; 2 is the black sky full of red lights and the grey haze he brings when he goes Super Saiyan.
 */
public record SlothSkyPayload(int phase) implements CustomPayload {
    public static final Id<SlothSkyPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "sloth_sky"));
    public static final PacketCodec<RegistryByteBuf, SlothSkyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SlothSkyPayload::phase,
            SlothSkyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
