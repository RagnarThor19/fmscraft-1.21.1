package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Server -> client: the player went down into a Greed Vault (thick dark fog) or came back out of it. */
public record VaultFogPayload(boolean inside) implements CustomPayload {
    public static final Id<VaultFogPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "vault_fog"));
    public static final PacketCodec<RegistryByteBuf, VaultFogPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOL, VaultFogPayload::inside, VaultFogPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
