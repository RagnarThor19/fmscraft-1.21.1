package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.network.TomeWheelPayload;
import net.ragnar.fmscraft.reward.WheelMath;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Matrix4f;

/**
 * Draws the tome wheel on the HUD as pixel art. Every frame the wheel is re-rasterised onto a coarse pixel grid
 * (each wheel pixel is 2x2 GUI pixels), so it stays blocky while it turns, like a sprite in a retro game.
 * Purely visual: the result comes from the server and the wheel glides into it.
 */
public final class TomeWheelHud {
    private static final int POP_IN = 8;
    private static final int HOLD = 60;
    private static final int FADE = 10;
    /** Wheel radius in wheel pixels, and how many GUI pixels one wheel pixel is. */
    private static final int R = 22;
    private static final int P = 2;
    private static final int GRID = R + 4;

    private static final int OUTLINE = 0x1E1A16;
    private static final int RIM = 0x8C7340;
    private static final int RIM_LIGHT = 0xC2A85A;
    private static final int RIM_DARK = 0x5E4A28;
    private static final int RIM_INNER = 0x3A2F22;
    private static final int RIVET = 0xE2D4A0;
    private static final int SPOKE = 0x2A241E;
    private static final int PAGE = 0xE8DDC0;
    private static final int GOLD = 0xD8B65A;
    private static final int GOLD_LIGHT = 0xF0E0A0;

    private static final String[] BOOK = {
            ".KKKKK",
            "KSCCCK",
            "KSCGCK",
            "KSCCCK",
            "KSCCCK",
            "KSPPPK",
            "KKKKK.",
    };
    private static final String[] POINTER = {
            "KKKKKKK",
            "KGHGGGK",
            "KGGGGGK",
            ".KGGGK.",
            "..KGK..",
            "...K...",
    };

    private static TomeWheelPayload spin;
    private static float target;
    private static int age;
    private static int lastSlice;
    private static int knock;
    private static boolean revealed;

    private TomeWheelHud() {
    }

    public static void start(TomeWheelPayload payload) {
        spin = payload;
        target = WheelMath.finalRotation(payload.slice(), payload.offset());
        age = 0;
        knock = 0;
        lastSlice = WheelMath.sliceAt(0);
        revealed = false;
        sound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
    }

    public static void clear() {
        spin = null;
    }

    private static float rotation(float time) {
        return target * WheelMath.ease(time / spin.spinTicks());
    }

    public static void tick() {
        if (spin == null) return;
        age++;
        if (knock > 0) knock--;

        int slice = WheelMath.sliceAt(rotation(age));
        if (age <= spin.spinTicks() && slice != lastSlice) {
            // A click and a little kick of the pointer for every slice that passes; pitch rises as it slows
            float progress = (float) age / spin.spinTicks();
            sound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.8f + progress * 0.9f, 0.35f);
            knock = 2;
        }
        lastSlice = slice;

        if (!revealed && age >= spin.spinTicks()) {
            revealed = true;
            switch (WheelMath.SLICES[spin.slice()]) {
                case BEGINNER -> sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.6f);
                case ADVANCED -> {
                    sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);
                    sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
                }
                case MASTER -> sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            }
        }
        if (age > spin.spinTicks() + HOLD + FADE) spin = null;
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (spin == null || client.options.hudHidden) return;

        float now = age + counter.getTickDelta(false);
        float rot = rotation(Math.min(now, spin.spinTicks()));
        float fadeAge = now - spin.spinTicks() - HOLD;
        float alpha = fadeAge > 0 ? 1f - Math.min(1f, fadeAge / FADE) : 1f;
        float grow = WheelMath.ease(Math.min(1f, now / POP_IN));
        float radius = R * (0.3f + 0.7f * grow);
        boolean blink = revealed && (age / 4) % 2 == 0;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2 - 12;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        VertexConsumer vc = ctx.getVertexConsumers().getBuffer(RenderLayer.getGui());

        // The wheel, one row at a time, merging runs of equal pixels into a single quad
        for (int j = -GRID; j <= GRID; j++) {
            int runStart = -GRID;
            int runColor = pixel(-GRID, j, rot, radius, alpha, blink);
            for (int i = -GRID + 1; i <= GRID + 1; i++) {
                int color = i <= GRID ? pixel(i, j, rot, radius, alpha, blink) : 0;
                if (color != runColor) {
                    if (runColor != 0) quad(vc, m, cx + runStart * P - 1, cy + j * P - 1, cx + i * P - 1, cy + (j + 1) * P - 1, runColor);
                    runStart = i;
                    runColor = color;
                }
            }
        }

        if (grow > 0.98f) {
            for (int s = 0; s < WheelMath.SLICES.length; s++) {
                double mid = Math.toRadians((s + 0.5) * WheelMath.SLICE_DEGREES + rot);
                int bx = (int) Math.round(Math.sin(mid) * R * 0.66);
                int by = (int) Math.round(-Math.cos(mid) * R * 0.66);
                WheelTier tier = WheelMath.SLICES[s];
                int emblem = tier == WheelTier.MASTER ? GOLD_LIGHT : GOLD;
                sprite(vc, m, cx, cy, bx - 3, by - 3, BOOK, alpha, ch -> switch (ch) {
                    case 'K' -> OUTLINE;
                    case 'S' -> darken(tier.sliceColor, 0.35f);
                    case 'C' -> lighten(tier.sliceColor, 0.35f);
                    case 'G' -> emblem;
                    default -> PAGE;
                });
            }
            sprite(vc, m, cx, cy, -3, -R - 5 - (knock > 0 ? 1 : 0), POINTER, alpha, ch -> switch (ch) {
                case 'K' -> OUTLINE;
                case 'H' -> GOLD_LIGHT;
                default -> GOLD;
            });
        }
        ctx.draw();

        if (alpha > 0.1f) {
            TextRenderer font = client.textRenderer;
            int textAlpha = (int) (alpha * 255) << 24;
            WheelTier won = WheelMath.SLICES[spin.slice()];
            Text title = Text.translatable("wheel.fmscraft.title", Sin.values()[spin.sin()].displayName());
            ctx.drawCenteredTextWithShadow(font, title, cx, cy - (R + 6) * P - 12, 0xD8C89A | textAlpha);
            if (revealed) {
                ctx.drawCenteredTextWithShadow(font, Text.translatable(won.translationKey()).formatted(won.textColor, Formatting.BOLD),
                        cx, cy + (R + 3) * P + 4, 0xFFFFFF | textAlpha);
                ctx.drawCenteredTextWithShadow(font, spin.prize().getName(), cx, cy + (R + 3) * P + 16, 0xFFFFFF | textAlpha);
            }
        }
    }

    /** Color of wheel pixel (i, j) relative to the center, or 0 for nothing. */
    private static int pixel(int i, int j, float rot, float radius, float alpha, boolean blink) {
        double r = Math.sqrt(i * i + j * j);
        if (r > radius + 2) {
            double shadow = Math.sqrt((i - 1) * (i - 1) + (j - 1) * (j - 1));
            return shadow <= radius + 2 ? argb(0x000000, 0.35f * alpha) : 0;
        }
        if (r > radius + 1) return argb(OUTLINE, alpha);

        double theta = Math.toDegrees(Math.atan2(i, -j));
        double local = (((theta - rot) % 360) + 360) % 360;
        double inSlice = local % WheelMath.SLICE_DEGREES;
        double edgePx = Math.toRadians(Math.min(inSlice, WheelMath.SLICE_DEGREES - inSlice)) * r;
        // Fixed light from the top-left, posterised into three bands like hand-shaded pixel art
        int light = (-i - j) > R * 0.45 ? 1 : (-i - j) < -R * 0.45 ? -1 : 0;

        if (r > radius - 1) {
            if (edgePx < 0.9) return argb(RIVET, alpha);
            return argb(light > 0 ? RIM_LIGHT : light < 0 ? RIM_DARK : RIM, alpha);
        }
        if (r > radius - 2) return argb(RIM_INNER, alpha);
        if (r <= 2.3) {
            int hub = Sin.values()[spin.sin()].color();
            return argb(i == -1 && j == -1 ? lighten(hub, 0.4f) : hub, alpha);
        }
        if (r <= 4) return argb(OUTLINE, alpha);
        if (edgePx < 0.55) return argb(SPOKE, alpha);

        int slice = Math.min(WheelMath.SLICES.length - 1, (int) (local / WheelMath.SLICE_DEGREES));
        int color = WheelMath.SLICES[slice].sliceColor;
        if (revealed && slice == spin.slice() && blink) color = lighten(color, 0.45f);
        // Darker inner ring with a dithered edge
        if (r < radius * 0.42 || (r < radius * 0.48 && ((i + j) & 1) == 0)) color = darken(color, 0.22f);
        if (light > 0) color = lighten(color, 0.14f);
        else if (light < 0) color = darken(color, 0.18f);
        return argb(color, alpha);
    }

    private static void sprite(VertexConsumer vc, Matrix4f m, int cx, int cy, int gx, int gy, String[] rows, float alpha,
                               java.util.function.IntUnaryOperator palette) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char ch = rows[y].charAt(x);
                if (ch == '.') continue;
                int px = cx + (gx + x) * P - 1, py = cy + (gy + y) * P - 1;
                quad(vc, m, px, py, px + P, py + P, argb(palette.applyAsInt(ch), alpha));
            }
        }
    }

    /** Same vertex order as DrawContext.fill. */
    private static void quad(VertexConsumer vc, Matrix4f m, int x1, int y1, int x2, int y2, int color) {
        vc.vertex(m, x1, y1, 0).color(color);
        vc.vertex(m, x1, y2, 0).color(color);
        vc.vertex(m, x2, y2, 0).color(color);
        vc.vertex(m, x2, y1, 0).color(color);
    }

    private static int argb(int rgb, float alpha) {
        return ((int) (MathHelper.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((int) (r + (255 - r) * t) << 16) | ((int) (g + (255 - g) * t) << 8) | (int) (b + (255 - b) * t);
    }

    private static int darken(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((int) (r * (1 - t)) << 16) | ((int) (g * (1 - t)) << 8) | (int) (b * (1 - t));
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }
}
