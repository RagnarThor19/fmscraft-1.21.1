package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.network.MugenControlPayload;
import net.ragnar.fmscraft.sin.Sin;

/**
 * MUGEN's Controller, on the player he has on his strings ({@link MugenControlPayload}): for five seconds their
 * movement keys don't do what they should. Every second and a bit it scrambles a new way: everything backwards,
 * forward and sideways swapped, turned round the other way, or backwards with the jump key upside down (jumping when
 * you don't press it). Violet throbs at the edges of the screen the whole time. Applied by KeyboardInputMixin.
 * <p>
 * It never outlives the fight: leaving the arena lets go at once.
 */
public final class MugenControl {
    /** How long each way of scrambling lasts. */
    public static final int STEP = 25;
    private static int ticks;
    private static long seed;

    private MugenControl() {
    }

    public static void receive(MugenControlPayload payload) {
        ticks = Math.max(0, payload.ticks());
        seed = payload.seed();
    }

    public static void tick(MinecraftClient client) {
        if (ticks <= 0) return;
        if (client.world == null || !client.world.getRegistryKey().equals(Arenas.key(Sin.LUST))) {
            clear();
            return;
        }
        if (!client.isPaused()) ticks--;
    }

    public static void clear() {
        ticks = 0;
    }

    public static boolean active() {
        return ticks > 0;
    }

    /** Which way it's scrambling right now, 0-3. */
    static int mode() {
        return (int) Math.floorMod(seed + ticks / STEP, 4L);
    }

    /** Scrambles what the keys just set (KeyboardInputMixin, at the end of KeyboardInput.tick). */
    public static void scramble(Input input) {
        if (ticks <= 0) return;
        float f = input.movementForward, s = input.movementSideways;
        switch (mode()) {
            case 0 -> {
                input.movementForward = -f;
                input.movementSideways = -s;
            }
            case 1 -> {
                input.movementForward = s;
                input.movementSideways = f;
            }
            case 2 -> {
                input.movementForward = -s;
                input.movementSideways = -f;
            }
            default -> {
                input.movementForward = -f;
                input.jumping = !input.jumping;
            }
        }
        input.pressingForward = input.movementForward > 0;
        input.pressingBack = input.movementForward < 0;
        input.pressingLeft = input.movementSideways > 0;
        input.pressingRight = input.movementSideways < 0;
    }

    /** Violet throbbing in at the edges of the screen. */
    public static void render(DrawContext ctx, RenderTickCounter counter) {
        if (ticks <= 0) return;
        float fade = Math.min(1f, ticks / 10f);
        float beat = MathHelper.sin((ticks - counter.getTickDelta(true)) * 0.45f) * 0.5f + 0.5f;
        int a = (int) (fade * (70 + 70 * beat));
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int edge = Math.max(24, Math.min(w, h) / 5), color = 0x5A1E8C;
        ctx.fillGradient(0, 0, w, edge, (a << 24) | color, color);
        ctx.fillGradient(0, h - edge, w, h, color, (a << 24) | color);
        for (int i = 0; i < 8; i++) {
            int strip = edge / 8, alpha = a * (8 - i) / 8;
            ctx.fill(i * strip, 0, (i + 1) * strip, h, (alpha << 24) | color);
            ctx.fill(w - (i + 1) * strip, 0, w - i * strip, h, (alpha << 24) | color);
        }
    }
}
