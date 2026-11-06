package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: the sky over LEONARD's plaza and what Munch is doing (drawn by client/PrideSky).
 * {@code phase} 1 is the bright day over the clouds, 2 the storm CONEMAN brings. {@code munch} is one of the MUNCH_
 * states; for {@link #MUNCH_CHARGING} and {@link #MUNCH_FIRING}, {@code ticks} is how long that lasts and (x, y, z) is
 * where his beam is going.
 */
public record PrideSkyPayload(int phase, int munch, int ticks, float x, float y, float z) implements CustomPayload {
    /** Under the clouds, out of sight. */
    public static final int MUNCH_HIDDEN = 0;
    /** Risen (rising with the song's first five seconds), nodding to the beat and watching you. */
    public static final int MUNCH_WATCHING = 1;
    /** Head still, turned on the target, eyes burning brighter. */
    public static final int MUNCH_CHARGING = 2;
    /** The beam. */
    public static final int MUNCH_FIRING = 3;
    /** Toppling back into the clouds. */
    public static final int MUNCH_FALLING = 4;

    public static final Id<PrideSkyPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "pride_sky"));
    public static final PacketCodec<RegistryByteBuf, PrideSkyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PrideSkyPayload::phase,
            PacketCodecs.VAR_INT, PrideSkyPayload::munch,
            PacketCodecs.VAR_INT, PrideSkyPayload::ticks,
            PacketCodecs.FLOAT, PrideSkyPayload::x,
            PacketCodecs.FLOAT, PrideSkyPayload::y,
            PacketCodecs.FLOAT, PrideSkyPayload::z,
            PrideSkyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
