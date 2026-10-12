package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Server -> client: start looping a boss song (sound event id), or stop the boss music when {@code sound} is empty. */
public record BossMusicPayload(String sound) implements CustomPayload {
    public static final Id<BossMusicPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "boss_music"));
    public static final PacketCodec<RegistryByteBuf, BossMusicPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, BossMusicPayload::sound, BossMusicPayload::new);

    public static final BossMusicPayload STOP = new BossMusicPayload("");

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
