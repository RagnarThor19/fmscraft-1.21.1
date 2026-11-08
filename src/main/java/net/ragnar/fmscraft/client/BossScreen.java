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
    private static final float ENVY_X = 0.595f, ENVY_Y = 0.805f, GLUTTONY_X = 0.400f, GLUTTONY_Y = 0.797f;
    private static final float LUST_X = 0.709f, LUST_Y = 0.569f, PRIDE_X = 0.500f, PRIDE_Y = 0.166f;
    private static final float SLOTH_X = 0.338f, SLOTH_Y = 0.274f;
    private static final int WHITE_COLOR = 0xF2F8FF, DEEP_TOP = 0x1F5F9E, DEEP_BOTTOM = 0x061535;
    private static final int FLASH_COLOR = 0xE6F4FF, FIRE_COLOR = 0xFF6A1E;

    private enum Mode { NONE, INTRO, BLACK, WHITE, DEEP }

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
                case "gluttony" -> GLUTTONY_X;
                case "lust" -> LUST_X;
                case "pride" -> PRIDE_X;
                case "sloth" -> SLOTH_X;
                default -> GREED_X;
            };
            focusY = switch (sin) {
                case "wrath" -> WRATH_Y;
                case "envy" -> ENVY_Y;
                case "gluttony" -> GLUTTONY_Y;
                case "lust" -> LUST_Y;
                case "pride" -> PRIDE_Y;
                case "sloth" -> SLOTH_Y;
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
            case BossScreenPayload.BLACK, BossScreenPayload.WHITE, BossScreenPayload.DEEP -> {
                Mode to = screen.equals(BossScreenPayload.WHITE) ? Mode.WHITE : screen.equals(BossScreenPayload.DEEP) ? Mode.DEEP : Mode.BLACK;
                if (mode != to) cover = prevCover = 0;
                mode = to;
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
                if (mode == Mode.DEEP) drawDeep(ctx, w, h, alpha, introTicks + delta);
                else ctx.fill(0, 0, w, h, ((int) (alpha * 255) << 24) | (mode == Mode.WHITE ? WHITE_COLOR : 0));
                if (mode == Mode.INTRO) drawWheel(ctx, w, h, alpha, introStart + introTicks + delta);
            }
        }
        float f = MathHelper.lerp(delta, prevFlash, flash);
        if (f > 0.01f) ctx.fill(0, 0, w, h, ((int) (Math.min(1f, f) * 255) << 24) | flashColor);
    }

    /**
     * Under the water (RAGGIMEL's tide): deep blue, lighter towards the surface, bands of light rippling across it and
     * bubbles rising.
     */
    private static void drawDeep(DrawContext ctx, int w, int h, float alpha, float time) {
        int a = (int) (alpha * 255) << 24;
        ctx.fillGradient(0, 0, w, h, a | DEEP_TOP, a | DEEP_BOTTOM);
        int bands = 7;
        for (int i = 0; i < bands; i++) {
            float y = (i + 0.5f) / bands * h * 0.6f + MathHelper.sin(time * 0.07f + i * 1.7f) * 6;
            int bandAlpha = (int) (alpha * (40 + 25 * MathHelper.sin(time * 0.11f + i))) << 24;
            int x0 = (int) (MathHelper.sin(time * 0.05f + i) * w * 0.1f - w * 0.1f);
            ctx.fill(x0, (int) y, x0 + (int) (w * 1.2f), (int) y + 2 + i % 2, bandAlpha | 0x8FD4FF);
        }
        net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(4242L);
        for (int i = 0; i < 40; i++) {
            float speed = 0.6f + random.nextFloat() * 1.4f, size = 1 + random.nextInt(3);
            float x = random.nextFloat() * w + MathHelper.sin(time * 0.1f + i) * 4;
            float y = h - ((random.nextFloat() * h + time * speed * 3) % (h + 10));
            ctx.fill((int) x, (int) y, (int) (x + size), (int) (y + size), ((int) (alpha * 140) << 24) | 0xCFEFFF);
        }
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
