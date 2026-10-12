package net.ragnar.fmscraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Server -> client: spin the tome wheel. The outcome is already decided: the wheel must stop {@code offset}
 * of the way into {@code slice} after {@code spinTicks}, revealing {@code prize}.
 */
public record TomeWheelPayload(int sin, int slice, float offset, int spinTicks, ItemStack prize) implements CustomPayload {
    public static final Id<TomeWheelPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "tome_wheel"));
    public static final PacketCodec<RegistryByteBuf, TomeWheelPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TomeWheelPayload::sin,
            PacketCodecs.VAR_INT, TomeWheelPayload::slice,
            PacketCodecs.FLOAT, TomeWheelPayload::offset,
            PacketCodecs.VAR_INT, TomeWheelPayload::spinTicks,
            ItemStack.PACKET_CODEC, TomeWheelPayload::prize,
            TomeWheelPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
