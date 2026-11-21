package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.network.DeathMarkPayload;
import net.ragnar.fmscraft.world.DeathMarks;

/**
 * The faint mark at the top of the screen that shows which way your things are since you died (DeathMarks): a small
 * hollow diamond, the pale teal of a recovery compass, solid. It slides along the top of the screen as you turn,
 * sitting over them when you face them; when they're behind you it waits at the edge as a little arrow on the side to
 * turn to. It fades away as you walk up to them, and hides with the rest of the HUD (F1).
 */
public final class DeathMarkHud {
    /** Teal of the recovery compass, and how faint it is (out of 255). */
    private static final int COLOR = 0x8FE3DC, ALPHA = 255;
    /** It's gone by the time you're this close, and starts to fade from twice as far. */
    private static final double GONE = DeathMarks.REACHED + 2;
    /** Turned this far away (in degrees), it's at the edge of its band. */
    private static final float SPAN = 90f;
    private static final int TOP = 3;

    // A 5x5 hollow diamond, and the arrows for "turn left" / "turn right"
    private static final String[] DIAMOND = {"..#..", ".#.#.", "#...#", ".#.#.", "..#.."};
    private static final String[] LEFT = {"..#", ".#.", "#..", ".#.", "..#"};
    private static final String[] RIGHT = {"#..", ".#.", "..#", ".#.", "#.."};

    private static GlobalPos mark;

    private DeathMarkHud() {
    }

    public static void receive(DeathMarkPayload payload) {
        mark = payload.mark().orElse(null);
    }

    public static void clear() {
        mark = null;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (mark == null || client.player == null || client.world == null || client.options.hudHidden) return;
        if (!client.player.isAlive() || mark.dimension() != client.world.getRegistryKey()) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d at = camera.getPos(), target = Vec3d.ofBottomCenter(mark.pos());
        double dx = target.x - at.x, dz = target.z - at.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float fade = (float) MathHelper.clamp((distance - GONE) / GONE, 0, 1);
        int alpha = Math.round(ALPHA * fade);
        if (alpha < 4) return;

        float turn = DeathMarks.turnTo(dx, dz, camera.getYaw());
        int half = ctx.getScaledWindowWidth() * 2 / 5;
        int centre = ctx.getScaledWindowWidth() / 2;
        int argb = alpha << 24 | COLOR;
        if (Math.abs(turn) <= SPAN) {
            draw(ctx, DIAMOND, centre + Math.round(turn / SPAN * half) - 2, argb);
        } else if (turn < 0) {
            draw(ctx, LEFT, centre - half - 1, argb);
        } else {
            draw(ctx, RIGHT, centre + half - 1, argb);
        }
    }

    private static void draw(DrawContext ctx, String[] shape, int left, int argb) {
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                if (shape[row].charAt(col) == '#') ctx.fill(left + col, TOP + row, left + col + 1, TOP + row + 1, argb);
            }
        }
    }
}
