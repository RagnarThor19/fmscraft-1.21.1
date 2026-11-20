package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

import java.util.List;

/**
 * Server -> client: roll the True Ending credits. {@code soulPlacers} has who placed each sin's soul, in sin order
 * ("" if nobody did, when they're shown by command); {@code facts} are the fun facts about the players
 * (see {@link net.ragnar.fmscraft.ending.CreditsFacts}). {@code memoriesOnly} skips straight to the memories video at
 * the end ({@code /fmscraft credits memories}).
 */
public record CreditsPayload(List<String> soulPlacers, List<Fact> facts, boolean memoriesOnly) implements CustomPayload {
    public static final Id<CreditsPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "credits"));

    /** A fun fact: its translation key and arguments (the player's name first, then the number if there is one). */
    public record Fact(String key, List<String> args) {
        public static final PacketCodec<RegistryByteBuf, Fact> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Fact::key,
                PacketCodecs.STRING.collect(PacketCodecs.toList()), Fact::args,
                Fact::new);
    }

    public static final PacketCodec<RegistryByteBuf, CreditsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.collect(PacketCodecs.toList()), CreditsPayload::soulPlacers,
            Fact.CODEC.collect(PacketCodecs.toList()), CreditsPayload::facts,
            PacketCodecs.BOOL, CreditsPayload::memoriesOnly,
            CreditsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
