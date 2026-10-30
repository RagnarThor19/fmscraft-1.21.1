package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: start looping a boss song (sound event id), fading it in over {@code fadeTicks} (0 = at once),
 * or stop the boss music when {@code sound} is empty.
 */
public record BossMusicPayload(String sound, int fadeTicks) implements CustomPayload {
    public static final Id<BossMusicPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "boss_music"));
    public static final PacketCodec<RegistryByteBuf, BossMusicPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BossMusicPayload::sound,
            PacketCodecs.VAR_INT, BossMusicPayload::fadeTicks,
            BossMusicPayload::new);

    public static final BossMusicPayload STOP = new BossMusicPayload("");

    public BossMusicPayload(String sound) {
        this(sound, 0);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
