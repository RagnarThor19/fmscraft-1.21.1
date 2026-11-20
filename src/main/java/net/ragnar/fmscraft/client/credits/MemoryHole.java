package net.ragnar.fmscraft.client.credits;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.FmsCraft;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The end of the True Ending credits: the void cracks like a block being mined, bursts, and through the hole it leaves
 * the memories play. When they end, everything fades to black with the music.
 *
 * <p>The hole is a grid of blocks over the video (its shape and the order the blocks break in come from the video
 * file, designed in {@code tools/video/credits_video.py}). Blocks still standing are simply not drawn, so the credits'
 * own void shows there; broken ones show their piece of the video, shaded where a wall of void still stands next to
 * them so the hole looks dug into the void. What breaks off flies away as chunks of the same void.
 *
 * <p>Everything runs off {@code t}, the seconds since the void was left alone after "Thank you for playing.".
 */
public final class MemoryHole {
    private static final Identifier GLOW = Identifier.of(FmsCraft.MOD_ID, "textures/gui/credits/glow.png");
    private static final Identifier[] CRACKS = new Identifier[10];

    static {
        for (int i = 0; i < CRACKS.length; i++) CRACKS[i] = Identifier.of(FmsCraft.MOD_ID, "textures/gui/credits/crack_" + i + ".png");
    }

    /** How much of the screen's height the hole's grid takes. */
    private static final float HOLE_HEIGHT = 0.58f;
    /** The first cracks... */
    private static final float CRACK_START = 1.4f;
    /** ...the blast (and the video's first frame)... */
    public static final float BURST = 4.6f;
    /** ...and how long the blast takes to reach the rim. */
    private static final float BREAK_SPREAD = 1.15f;
    /** The memory comes in bright and a little too close, and settles. */
    private static final float SETTLE = 2.6f;
    private static final float FADE_OUT = 4.5f;
    private static final float FADE_INTO_END = 2.0f;
    private static final float HOLD_BLACK = 1.5f;
    /** Ken Burns: how much closer the memories drift over their whole length. */
    private static final float DRIFT = 0.06f;
    /** Cracking sounds, seconds after the first crack: closer and closer together. */
    private static final float[] CRACK_SOUNDS = {0.0f, 1.0f, 1.8f, 2.4f, 2.8f, 3.05f};

    /** Crack and glow light: the pale blue-violet of the end portal's stars. */
    private static final float[] LIGHT = {0.72f, 0.86f, 1.0f};
    /** A turn (low 2 bits) and a flip (bit 4) for each block's cracks. */
    private static final int[] CRACK_TURNS = {5, 2, 7, 0, 3, 6, 1, 4, 6, 3, 0, 5, 2, 7, 4, 1, 7, 0, 5, 2, 1, 6, 3};
    private static final int[] SPARK_COLORS = {0xFFFFFF, 0xC9B6FF, 0x9FE6FF, 0x7FD9C8, 0xE6D9FF};

    private final CreditsVideo video;
    private final float[] crackAt;
    private final float[] breakAt;
    private final Random random = new Random(19);
    private final List<Chunk> chunks = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final boolean[] broken;
    private float lastT = -1;
    private int crackSoundsPlayed;
    private boolean burst;
    private float nextCrumble = BURST + 4f;

    // Layout, worked out each frame from the screen size
    private float cell, left, top;

    public MemoryHole(CreditsVideo video) {
        this.video = video;
        int n = video.cols * video.rows;
        crackAt = new float[n];
        breakAt = new float[n];
        broken = new boolean[n];
        for (int i = 0; i < n; i++) {
            int order = video.cells[i] & 0xFF;
            if (order == 0) {
                crackAt[i] = breakAt[i] = Float.NaN;
                continue;
            }
            float o = (order - 1) / 254f;
            breakAt[i] = BURST + BREAK_SPREAD * (float) Math.pow(o, 1.25);
            crackAt[i] = CRACK_START + (BURST - CRACK_START) * 0.8f * o;
        }
    }

    private boolean open(int col, int row) {
        return col >= 0 && row >= 0 && col < video.cols && row < video.rows && video.cell(col, row) != 0;
    }

    private boolean brokenAt(int col, int row, float t) {
        return open(col, row) && t >= breakAt[row * video.cols + col];
    }

    private float videoTime(float t) {
        return Math.max(0, t - BURST);
    }

    /** When the fade to black begins: a little before the memories end, so they fade out still moving. */
    private float fadeStart() {
        return BURST + video.duration() - FADE_INTO_END;
    }

    /** 0-1: how far the screen has faded to black at the end (the music fades with it). */
    public float fadeOut(float t) {
        return MathHelper.clamp((t - fadeStart()) / FADE_OUT, 0f, 1f);
    }

    public boolean finished(float t) {
        return t > fadeStart() + FADE_OUT + HOLD_BLACK;
    }

    public void render(DrawContext context, int width, int height, float t) {
        float dt = lastT < 0 ? 0 : MathHelper.clamp(t - lastT, 0f, 0.1f);
        lastT = t;
        cell = height * HOLE_HEIGHT / video.rows;
        left = (width - cell * video.cols) / 2f;
        top = (height - cell * video.rows) / 2f;
        float cx = width / 2f, cy = height / 2f;

        happen(t, cx, cy);
        step(dt, t);

        context.draw();
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // The light gathering behind the cracks
        if (t > CRACK_START && t < BURST + 0.2f) {
            float k = MathHelper.clamp((t - CRACK_START) / (BURST - CRACK_START), 0f, 1f);
            float flicker = 0.85f + 0.15f * MathHelper.sin(t * 23f) * MathHelper.sin(t * 7.3f);
            float size = cell * video.rows * (0.35f + 0.55f * k);
            glow(matrix, cx, cy, size, 0.32f * k * k * flicker);
        }

        context.getMatrices().push();
        float shake = shake(t);
        if (shake > 0) {
            context.getMatrices().translate(shake * (float) Math.sin(t * 91.0), shake * (float) Math.cos(t * 77.0), 0);
        }
        matrix = context.getMatrices().peek().getPositionMatrix();

        if (t >= BURST) {
            drawVideo(matrix, t);
            drawWalls(matrix, t);
        }
        drawCracks(matrix, t);
        drawChunks(context, matrix);
        drawSparks(matrix);

        context.getMatrices().pop();
        matrix = context.getMatrices().peek().getPositionMatrix();

        // The blast's flash
        if (t >= BURST && t < BURST + 1.2f) {
            float k = (t - BURST) / 1.2f;
            glow(matrix, cx, cy, cell * video.rows * (0.6f + 2.2f * easeOut(k)), 0.9f * (1 - k) * (1 - k));
            if (k < 0.3f) {
                float a = 0.28f * (1 - k / 0.3f);
                rect(matrix, 0, 0, width, height, light(a, 1f, 1f, 1f), true);
            }
        }
    }

    /** The sounds and the bursts of debris, each when its moment comes. */
    private void happen(float t, float cx, float cy) {
        while (crackSoundsPlayed < CRACK_SOUNDS.length && t >= CRACK_START + CRACK_SOUNDS[crackSoundsPlayed]) {
            float k = crackSoundsPlayed / (float) (CRACK_SOUNDS.length - 1);
            sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.5f + 0.15f * k, 0.35f + 0.45f * k);
            sound(SoundEvents.BLOCK_DEEPSLATE_HIT, 0.6f, 0.25f + 0.35f * k);
            crackSoundsPlayed++;
        }
        if (!burst && t >= BURST) {
            burst = true;
            sound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.55f, 0.5f);
            sound(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 0.5f, 1.0f);
            sound(SoundEvents.BLOCK_GLASS_BREAK, 0.6f, 0.45f);
            for (int i = 0; i < 110; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                float r = (float) Math.sqrt(random.nextDouble()) * cell * 1.6f;
                float speed = cell * (3f + random.nextFloat() * 11f);
                sparks.add(new Spark(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                        (float) Math.cos(a) * speed, (float) Math.sin(a) * speed - cell * 2f,
                        0.5f + random.nextFloat() * 1.3f, SPARK_COLORS[random.nextInt(SPARK_COLORS.length)],
                        Math.max(1f, cell * (0.05f + random.nextFloat() * 0.07f))));
            }
        }
        for (int row = 0; row < video.rows; row++) {
            for (int col = 0; col < video.cols; col++) {
                int i = row * video.cols + col;
                if (broken[i] || !open(col, row) || t < breakAt[i]) continue;
                broken[i] = true;
                breakOff(col, row, cx, cy);
            }
        }
        // Now and then a little of the rim crumbles into the hole
        if (t >= nextCrumble && t < fadeStart()) {
            nextCrumble = t + 1.8f + random.nextFloat() * 3.2f;
            crumble();
        }
    }

    private void breakOff(int col, int row, float cx, float cy) {
        float x = left + (col + 0.5f) * cell, y = top + (row + 0.5f) * cell;
        float dx = x - cx, dy = y - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            double a = random.nextDouble() * Math.PI * 2;
            dx = (float) Math.cos(a);
            dy = (float) Math.sin(a);
        } else {
            dx /= len;
            dy /= len;
        }
        float speed = cell * (2.5f + random.nextFloat() * 2.5f);
        chunks.add(new Chunk(x, y, dx * speed, dy * speed - cell * 1.5f, cell, spin(), 0.85f + random.nextFloat() * 0.4f, cell * 12f));
        for (int s = 0; s < 3; s++) {
            float jitter = (random.nextFloat() - 0.5f) * 1.2f;
            float sx = dx * (float) Math.cos(jitter) - dy * (float) Math.sin(jitter);
            float sy = dx * (float) Math.sin(jitter) + dy * (float) Math.cos(jitter);
            float sp = cell * (4f + random.nextFloat() * 5f);
            chunks.add(new Chunk(x + (random.nextFloat() - 0.5f) * cell * 0.6f, y + (random.nextFloat() - 0.5f) * cell * 0.6f,
                    sx * sp, sy * sp - cell * 2f, cell * (0.2f + random.nextFloat() * 0.18f), spin() * 1.5f,
                    0.6f + random.nextFloat() * 0.45f, cell * 14f));
        }
        for (int s = 0; s < 3; s++) {
            float sp = cell * (2f + random.nextFloat() * 6f);
            double a = Math.atan2(dy, dx) + (random.nextDouble() - 0.5) * 1.6;
            sparks.add(new Spark(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0.4f + random.nextFloat() * 0.7f,
                    SPARK_COLORS[random.nextInt(SPARK_COLORS.length)], Math.max(1f, cell * 0.06f)));
        }
    }

    private void crumble() {
        List<int[]> rim = new ArrayList<>();
        for (int row = 0; row < video.rows; row++) {
            for (int col = 0; col < video.cols; col++) {
                if (open(col, row) && !open(col, row - 1)) rim.add(new int[]{col, row});
            }
        }
        if (rim.isEmpty()) return;
        int[] at = rim.get(random.nextInt(rim.size()));
        int pieces = 1 + random.nextInt(3);
        for (int p = 0; p < pieces; p++) {
            float x = left + (at[0] + 0.15f + random.nextFloat() * 0.7f) * cell, y = top + at[1] * cell + cell * 0.05f;
            chunks.add(new Chunk(x, y, (random.nextFloat() - 0.5f) * cell * 0.5f, random.nextFloat() * cell * 0.3f,
                    cell * (0.07f + random.nextFloat() * 0.1f), spin() * 0.6f, 1.4f + random.nextFloat() * 0.6f, cell * 9f));
        }
    }

    private float spin() {
        return (random.nextFloat() - 0.5f) * 10f;
    }

    private void step(float dt, float t) {
        for (Iterator<Chunk> it = chunks.iterator(); it.hasNext(); ) {
            Chunk c = it.next();
            c.age += dt;
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            c.vy += c.gravity * dt;
            c.angle += c.spin * dt;
            if (c.age >= c.life) it.remove();
        }
        float drag = (float) Math.exp(-2.2 * dt);
        for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            s.age += dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vx *= drag;
            s.vy = s.vy * drag + cell * 2.5f * dt;
            if (s.age >= s.life) it.remove();
        }
    }

    private float shake(float t) {
        float k = (t - BURST) / 0.75f;
        if (k < 0 || k > 1) return 0;
        return cell * 0.22f * (1 - k) * (1 - k);
    }

    // ---- Drawing ----

    private void drawVideo(Matrix4f matrix, float t) {
        float vt = videoTime(t);
        int frame = Math.min(video.frameCount - 1, (int) (vt * video.fps));
        video.show(frame);
        if (!video.ready()) return;

        // In a little too close, settling back; then drifting slowly in over the whole video
        float settle = MathHelper.clamp(vt / SETTLE, 0f, 1f);
        float zoom = 1f + 0.1f * (1 - easeOut(settle)) + DRIFT * MathHelper.clamp(vt / video.duration(), 0f, 1f);

        RenderSystem.setShaderTexture(0, CreditsVideo.TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.disableBlend();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int row = 0; row < video.rows; row++) {
            for (int col = 0; col < video.cols; col++) {
                if (!brokenAt(col, row, t)) continue;
                float x0 = left + col * cell, y0 = top + row * cell;
                float u0 = uv(col / (float) video.cols, zoom), u1 = uv((col + 1) / (float) video.cols, zoom);
                float v0 = uv(row / (float) video.rows, zoom), v1 = uv((row + 1) / (float) video.rows, zoom);
                buffer.vertex(matrix, x0, y0, 0).texture(u0, v0).color(-1);
                buffer.vertex(matrix, x0, y0 + cell, 0).texture(u0, v1).color(-1);
                buffer.vertex(matrix, x0 + cell, y0 + cell, 0).texture(u1, v1).color(-1);
                buffer.vertex(matrix, x0 + cell, y0, 0).texture(u1, v0).color(-1);
            }
        }
        draw(buffer);

        // Coming in overexposed, like a memory surfacing
        float bright = 0.62f * (1 - easeOut(settle)) * (1 - easeOut(settle));
        if (bright > 0.005f) {
            additive();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int color = light(bright, 1f, 0.97f, 0.92f);
            for (int row = 0; row < video.rows; row++) {
                for (int col = 0; col < video.cols; col++) {
                    if (brokenAt(col, row, t)) quad(buffer, matrix, left + col * cell, top + row * cell, left + (col + 1) * cell, top + (row + 1) * cell, color, color, color, color);
                }
            }
            draw(buffer);
            normalBlend();
        }
    }

    private static float uv(float f, float zoom) {
        return 0.5f + (f - 0.5f) / zoom;
    }

    /** Shadow cast into the hole by the walls of void round it: deepest under the top edge, as if lit from above. */
    private void drawWalls(Matrix4f matrix, float t) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int dark = argb(0.92f, 0, 0, 0), clear = 0;
        for (int row = 0; row < video.rows; row++) {
            for (int col = 0; col < video.cols; col++) {
                if (!brokenAt(col, row, t)) continue;
                float x0 = left + col * cell, y0 = top + row * cell, x1 = x0 + cell, y1 = y0 + cell;
                if (!brokenAt(col, row - 1, t)) {
                    float d = cell * 0.7f;
                    quad(buffer, matrix, x0, y0, x1, y0 + d, dark, clear, clear, dark);
                    quad(buffer, matrix, x0, y0, x1, y0 + d * 0.28f, dark, clear, clear, dark);
                }
                if (!brokenAt(col, row + 1, t)) {
                    float d = cell * 0.22f;
                    quad(buffer, matrix, x0, y1 - d, x1, y1, clear, dark, dark, clear);
                }
                if (!brokenAt(col - 1, row, t)) {
                    float d = cell * 0.42f;
                    quad(buffer, matrix, x0, y0, x0 + d, y1, dark, dark, clear, clear);
                }
                if (!brokenAt(col + 1, row, t)) {
                    float d = cell * 0.42f;
                    quad(buffer, matrix, x1 - d, y0, x1, y1, clear, clear, dark, dark);
                }
            }
        }
        draw(buffer);
    }

    private void drawCracks(Matrix4f matrix, float t) {
        if (t < CRACK_START) return;
        additive();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        float pulse = 0.8f + 0.2f * MathHelper.sin(t * 9f);
        for (int stage = 0; stage < CRACKS.length; stage++) {
            BufferBuilder buffer = null;
            for (int row = 0; row < video.rows; row++) {
                for (int col = 0; col < video.cols; col++) {
                    int i = row * video.cols + col;
                    if (!open(col, row) || t < crackAt[i] || t >= breakAt[i]) continue;
                    float k = (t - crackAt[i]) / (breakAt[i] - crackAt[i]);
                    if (Math.min(CRACKS.length - 1, (int) (k * CRACKS.length)) != stage) continue;
                    if (buffer == null) buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                    int color = light(MathHelper.clamp((0.22f + 0.5f * k) * pulse, 0f, 1f), LIGHT[0], LIGHT[1], LIGHT[2]);
                    float x0 = left + col * cell, y0 = top + row * cell;
                    // Each block turned and flipped its own way, so the cracks don't tile
                    int turn = CRACK_TURNS[i % CRACK_TURNS.length];
                    float[] corners = {x0, y0, x0, y0 + cell, x0 + cell, y0 + cell, x0 + cell, y0};
                    for (int v = 0; v < 4; v++) {
                        int uv = ((v + (turn & 3)) & 3);
                        float u = uv >= 2 ? 1 : 0, w = uv == 1 || uv == 2 ? 1 : 0;
                        if ((turn & 4) != 0) u = 1 - u;
                        buffer.vertex(matrix, corners[v * 2], corners[v * 2 + 1], 0).texture(u, w).color(color);
                    }
                }
            }
            if (buffer != null) {
                RenderSystem.setShaderTexture(0, CRACKS[stage]);
                draw(buffer);
            }
        }
        normalBlend();
    }

    /** The broken-off pieces: bits of the void itself, tumbling away and shrinking into the distance. */
    private void drawChunks(DrawContext context, Matrix4f matrix) {
        if (chunks.isEmpty()) return;
        VertexConsumer consumer = context.getVertexConsumers().getBuffer(RenderLayer.getEndPortal());
        for (Chunk c : chunks) {
            float k = c.age / c.life;
            float size = c.size * (k < 0.12f ? 1f + 0.6f * k : 1.07f * (float) Math.pow(1 - (k - 0.12f) / 0.88f, 1.4));
            float h = size / 2, cos = MathHelper.cos(c.angle) * h, sin = MathHelper.sin(c.angle) * h;
            // corners: top-left, bottom-left, bottom-right, top-right
            consumer.vertex(matrix, c.x - cos + sin, c.y - sin - cos, 0);
            consumer.vertex(matrix, c.x - cos - sin, c.y - sin + cos, 0);
            consumer.vertex(matrix, c.x + cos - sin, c.y + sin + cos, 0);
            consumer.vertex(matrix, c.x + cos + sin, c.y + sin - cos, 0);
        }
        context.draw();
    }

    private void drawSparks(Matrix4f matrix) {
        if (sparks.isEmpty()) return;
        additive();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Spark s : sparks) {
            float k = s.age / s.life;
            float alpha = (k < 0.1f ? k / 0.1f : 1 - (k - 0.1f) / 0.9f);
            int rgb = s.color;
            int color = light(alpha, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
            float h = s.size / 2;
            quad(buffer, matrix, s.x - h, s.y - h, s.x + h, s.y + h, color, color, color, color);
        }
        draw(buffer);
        normalBlend();
    }

    private void glow(Matrix4f matrix, float x, float y, float size, float alpha) {
        if (alpha <= 0.003f) return;
        additive();
        MinecraftClient.getInstance().getTextureManager().getTexture(GLOW).setFilter(true, false);
        RenderSystem.setShaderTexture(0, GLOW);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int color = light(alpha, LIGHT[0], LIGHT[1], LIGHT[2]);
        float h = size / 2;
        buffer.vertex(matrix, x - h, y - h, 0).texture(0, 0).color(color);
        buffer.vertex(matrix, x - h, y + h, 0).texture(0, 1).color(color);
        buffer.vertex(matrix, x + h, y + h, 0).texture(1, 1).color(color);
        buffer.vertex(matrix, x + h, y - h, 0).texture(1, 0).color(color);
        draw(buffer);
        normalBlend();
    }

    /** A flat rectangle; additive for light, blended for shade. */
    public static void rect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color, boolean light) {
        if (light) additive();
        else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quad(buffer, matrix, x0, y0, x1, y1, color, color, color, color);
        draw(buffer);
        normalBlend();
    }

    /** Corners' colors in the order top-left, bottom-left, bottom-right, top-right. */
    private static void quad(BufferBuilder buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, int tl, int bl, int br, int tr) {
        buffer.vertex(matrix, x0, y0, 0).color(tl);
        buffer.vertex(matrix, x0, y1, 0).color(bl);
        buffer.vertex(matrix, x1, y1, 0).color(br);
        buffer.vertex(matrix, x1, y0, 0).color(tr);
    }

    private static void draw(BufferBuilder buffer) {
        BuiltBuffer built = buffer.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void additive() {
        RenderSystem.enableBlend();
        // Light is premultiplied (its strength in the color, alpha kept whole), as the GUI shaders throw away
        // fragments that are nearly transparent: faint light would end in a hard edge
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
    }

    private static void normalBlend() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Light to add, {@code strength} 0-1, for additive drawing. */
    static int light(float strength, float r, float g, float b) {
        float k = MathHelper.clamp(strength, 0f, 1f);
        return argb(1f, r * k, g * k, b * k);
    }

    static int argb(float a, float r, float g, float b) {
        return (MathHelper.clamp((int) (a * 255), 0, 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static float easeOut(float k) {
        float i = 1 - MathHelper.clamp(k, 0f, 1f);
        return 1 - i * i * i;
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    public void close() {
        video.close();
    }

    private static final class Chunk {
        float x, y, vx, vy, angle, age;
        final float size, spin, life, gravity;

        Chunk(float x, float y, float vx, float vy, float size, float spin, float life, float gravity) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.spin = spin;
            this.life = life;
            this.gravity = gravity;
        }
    }

    private static final class Spark {
        float x, y, vx, vy, age;
        final float life, size;
        final int color;

        Spark(float x, float y, float vx, float vy, float life, int color, float size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
            this.size = size;
        }
    }
}
