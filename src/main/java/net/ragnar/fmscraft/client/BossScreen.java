package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.greed.GreedTimeline;
import net.ragnar.fmscraft.network.BossScreenPayload;

/**
 * What covers the screen during boss cutscenes ({@link BossScreenPayload}): the Seven Deadly Sins wheel that slowly
 * zooms in on the sin being fought, a fade to black, and flashes on top (lightning white, or firelight orange). Drawn
 * over the whole HUD. While the wheel or the black is up, the mouse doesn't turn the camera (MouseMixin).
 */
public final class BossScreen {
    private static final Identifier WHEEL = Identifier.of(FmsCraft.MOD_ID, "textures/gui/seven_sins.png");
    private static final int WHEEL_W = 1672, WHEEL_H = 941;
    /** The middle of each sin's circle on the wheel, as a fraction of its width and height, and how far the zoom goes. */
    private static final float GREED_X = 0.665f, GREED_Y = 0.272f, WRATH_X = 0.291f, WRATH_Y = 0.567f, ZOOM = 3.4f;
    private static final float ENVY_X = 0.595f, ENVY_Y = 0.805f;
    private static final int FLASH_COLOR = 0xE6F4FF, FIRE_COLOR = 0xFF6A1E;

    private enum Mode { NONE, INTRO, BLACK }

    private static Mode mode = Mode.NONE;
    private static float cover, prevCover, coverStep;
    private static boolean covering;
    private static int introStart, introDuration, introTicks;
    private static float focusX = GREED_X, focusY = GREED_Y;
    private static float flash, prevFlash, flashStep;
    private static int flashColor = FLASH_COLOR;

    private BossScreen() {
    }

    public static void receive(BossScreenPayload payload) {
        String screen = payload.screen();
        if (screen.startsWith(BossScreenPayload.INTRO + ":")) {
            String sin = screen.substring(screen.indexOf(':') + 1);
            focusX = switch (sin) {
                case "wrath" -> WRATH_X;
                case "envy" -> ENVY_X;
                default -> GREED_X;
            };
            focusY = switch (sin) {
                case "wrath" -> WRATH_Y;
                case "envy" -> ENVY_Y;
                default -> GREED_Y;
            };
            screen = BossScreenPayload.INTRO;
        } else if (screen.equals(BossScreenPayload.INTRO)) {
            focusX = GREED_X;
            focusY = GREED_Y;
        }
        switch (screen) {
            case BossScreenPayload.INTRO -> {
                mode = Mode.INTRO;
                introStart = payload.elapsed();
                introDuration = payload.duration();
                introTicks = 0;
                cover = prevCover = 1;
                covering = true;
            }
            case BossScreenPayload.BLACK -> {
                if (mode != Mode.BLACK) cover = prevCover = 0;
                mode = Mode.BLACK;
                covering = true;
                coverStep = 1f / Math.max(1, payload.duration());
            }
            case BossScreenPayload.CLEAR -> {
                covering = false;
                coverStep = 1f / Math.max(1, payload.duration());
                if (payload.duration() <= 0) {
                    mode = Mode.NONE;
                    cover = prevCover = 0;
                }
            }
            case BossScreenPayload.FLASH, BossScreenPayload.FLASH_FIRE -> {
                flashColor = screen.equals(BossScreenPayload.FLASH_FIRE) ? FIRE_COLOR : FLASH_COLOR;
                flash = prevFlash = Math.max(flash, payload.elapsed() / 100f);
                flashStep = flash / Math.max(1, payload.duration());
            }
            default -> {
            }
        }
    }

    /** True while the screen is (being) covered: the camera stays put. */
    public static boolean locksCamera() {
        return mode != Mode.NONE && covering;
    }

    public static void tick() {
        prevCover = cover;
        prevFlash = flash;
        if (mode != Mode.NONE) {
            introTicks++;
            cover = MathHelper.clamp(cover + (covering ? coverStep : -coverStep), 0f, 1f);
            if (!covering && cover <= 0) mode = Mode.NONE;
        }
        if (flash > 0) flash = Math.max(0, flash - flashStep);
    }

    public static void clear() {
        mode = Mode.NONE;
        cover = prevCover = flash = prevFlash = 0;
        covering = false;
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        float delta = counter.getTickDelta(true);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (mode != Mode.NONE) {
            float alpha = MathHelper.lerp(delta, prevCover, cover);
            if (alpha > 0) {
                ctx.fill(0, 0, w, h, ((int) (alpha * 255) << 24));
                if (mode == Mode.INTRO) drawWheel(ctx, w, h, alpha, introStart + introTicks + delta);
            }
        }
        float f = MathHelper.lerp(delta, prevFlash, flash);
        if (f > 0.01f) ctx.fill(0, 0, w, h, ((int) (Math.min(1f, f) * 255) << 24) | flashColor);
    }

    /** The wheel, covering the screen, zoomed in on the sin as far as the intro has got. */
    private static void drawWheel(DrawContext ctx, int w, int h, float alpha, float elapsed) {
        float zoom = GreedTimeline.zoom(elapsed, introDuration);
        float scale = Math.max(w / (float) WHEEL_W, h / (float) WHEEL_H) * (1 + (ZOOM - 1) * zoom);
        float fx = MathHelper.lerp(zoom, 0.5f, focusX) * WHEEL_W, fy = MathHelper.lerp(zoom, 0.5f, focusY) * WHEEL_H;
        // Keep the focus in the middle of the screen, but never show past the picture's edges
        float tx = MathHelper.clamp(w / 2f - fx * scale, w - WHEEL_W * scale, 0);
        float ty = MathHelper.clamp(h / 2f - fy * scale, h - WHEEL_H * scale, 0);
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(tx, ty, 0);
        matrices.scale(scale, scale, 1);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(WHEEL, 0, 0, 0f, 0f, WHEEL_W, WHEEL_H, WHEEL_W, WHEEL_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
