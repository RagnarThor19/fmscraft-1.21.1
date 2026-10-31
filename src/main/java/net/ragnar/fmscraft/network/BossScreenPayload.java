package net.ragnar.fmscraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

/**
 * Server -> client: what covers the screen during a boss fight's cutscenes (drawn by client/BossScreen).
 * <ul>
 *   <li>{@link #INTRO}: the Seven Deadly Sins wheel, {@code elapsed} ticks into a {@code duration}-tick intro, zooming
 *       in on Greed; {@link #intro} names another sin to zoom in on.</li>
 *   <li>{@link #BLACK}: fade to black over {@code duration} ticks.</li>
 *   <li>{@link #CLEAR}: fade whatever covers the screen away over {@code duration} ticks.</li>
 *   <li>{@link #FLASH}: a lightning-white flash on top of everything, fading over {@code duration} ticks;
 *       {@code elapsed} is its strength in percent. {@link #FLASH_FIRE} is the same in the orange of firelight.</li>
 * </ul>
 * While the intro or black screen is up, the client also stops the mouse turning the camera.
 */
public record BossScreenPayload(String screen, int elapsed, int duration) implements CustomPayload {
    public static final String INTRO = "intro", BLACK = "black", CLEAR = "clear", FLASH = "flash", FLASH_FIRE = "flash_fire";

    public static final Id<BossScreenPayload> ID = new Id<>(Identifier.of(FmsCraft.MOD_ID, "boss_screen"));
    public static final PacketCodec<RegistryByteBuf, BossScreenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BossScreenPayload::screen,
            PacketCodecs.VAR_INT, BossScreenPayload::elapsed,
            PacketCodecs.VAR_INT, BossScreenPayload::duration,
            BossScreenPayload::new);

    /** The intro screen, zooming in on {@code sin} on the wheel. */
    public static String intro(Sin sin) {
        return INTRO + ":" + sin.id();
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
