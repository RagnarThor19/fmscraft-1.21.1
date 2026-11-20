package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Client -> server: the credits are over (or closed), so the protection given for them can go. */
public record CreditsDonePayload() implements CustomPayload {
    public static final CreditsDonePayload INSTANCE = new CreditsDonePayload();
    public static final Id<CreditsDonePayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "credits_done"));
    public static final PacketCodec<RegistryByteBuf, CreditsDonePayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
