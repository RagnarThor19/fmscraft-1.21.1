package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.ragnar.fmscraft.FmsCraft;

import java.util.Optional;

/** Server -> client: where the player's things are lying since they died (DeathMarks), or empty once they're back. */
public record DeathMarkPayload(Optional<GlobalPos> mark) implements CustomPayload {
    public static final Id<DeathMarkPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "death_mark"));
    public static final PacketCodec<RegistryByteBuf, DeathMarkPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.optional(GlobalPos.PACKET_CODEC), DeathMarkPayload::mark, DeathMarkPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
