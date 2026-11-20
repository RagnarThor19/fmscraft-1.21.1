package net.ragnar.fmscraft.client.credits;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The memories video shown after the True Ending credits, streamed from {@code video/memories.fmv} (built by
 * {@code tools/video/credits_video.py}: a header, the hole's block grid, then JPEG frames).
 *
 * <p>A background thread reads and decodes the frames in order a few ahead of time; the render thread uploads the
 * one due into a texture. Nothing is kept beyond a small queue, so the two minutes never sit in memory at once.
 */
public final class CreditsVideo implements AutoCloseable {
    public static final Identifier FILE = Identifier.of(FmsCraft.MOD_ID, "video/memories.fmv");
    public static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "dynamic/credits_video");
    private static final int AHEAD = 12;

    public final int width;
    public final int height;
    public final float fps;
    public final int frameCount;
    /** The hole's grid: {@code cells[row * cols + col]} is 0 for void, else its breaking order 1..255. */
    public final int cols;
    public final int rows;
    public final byte[] cells;

    private final DataInputStream in;
    private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<>(AHEAD);
    private final Thread decoder;
    private volatile boolean closed;
    @Nullable
    private VideoTexture texture;
    private int shown = -1;

    private record Frame(int index, ByteBuffer pixels) {
        void free() {
            STBImage.stbi_image_free(pixels);
        }
    }

    private CreditsVideo(DataInputStream in) throws IOException {
        this.in = in;
        byte[] magic = in.readNBytes(4);
        if (!"FMSV".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII)) || in.readInt() != 1) {
            throw new IOException("not a credits video");
        }
        width = in.readInt();
        height = in.readInt();
        fps = in.readInt() / 1000f;
        frameCount = in.readInt();
        cols = in.readInt();
        rows = in.readInt();
        cells = in.readNBytes(cols * rows);
        decoder = new Thread(this::decode, "FMS credits video");
        decoder.setDaemon(true);
    }

    /** Opens the video and starts decoding its first frames, or null if it's missing or broken. */
    @Nullable
    public static CreditsVideo open(ResourceManager resources) {
        Optional<Resource> resource = resources.getResource(FILE);
        if (resource.isEmpty()) return null;
        try {
            CreditsVideo video = new CreditsVideo(new DataInputStream(new BufferedInputStream(resource.get().getInputStream(), 1 << 16)));
            video.decoder.start();
            return video;
        } catch (IOException e) {
            FmsCraft.LOGGER.warn("Couldn't open the credits video", e);
            return null;
        }
    }

    public float duration() {
        return frameCount / fps;
    }

    public int cell(int col, int row) {
        return cells[row * cols + col] & 0xFF;
    }

    private void decode() {
        try {
            for (int i = 0; i < frameCount && !closed; i++) {
                int length = in.readInt();
                byte[] jpeg = in.readNBytes(length);
                ByteBuffer data = MemoryUtil.memAlloc(length);
                ByteBuffer pixels;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    data.put(jpeg).flip();
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
                    pixels = STBImage.stbi_load_from_memory(data, w, h, channels, 4);
                    if (pixels != null && (w.get(0) != width || h.get(0) != height)) {
                        STBImage.stbi_image_free(pixels);
                        pixels = null;
                    }
                } finally {
                    MemoryUtil.memFree(data);
                }
                if (pixels == null) continue;
                Frame frame = new Frame(i, pixels);
                while (!closed && !queue.offer(frame, 50, TimeUnit.MILLISECONDS)) {
                    // the screen is behind (or paused): wait for room
                }
                if (closed) frame.free();
            }
        } catch (IOException | InterruptedException e) {
            if (!closed) FmsCraft.LOGGER.warn("The credits video stopped early", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            drain();
        }
    }

    /**
     * Shows the frame due at {@code index} (render thread): skips any it's late for, keeps the last one if the next
     * isn't decoded yet.
     */
    public void show(int index) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        if (texture == null) {
            texture = new VideoTexture(width, height);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, texture);
        }
        Frame best = null;
        Frame next;
        while ((next = queue.peek()) != null && next.index() <= index) {
            queue.poll();
            if (best != null) best.free();
            best = next;
        }
        if (best != null) {
            texture.upload(best.pixels(), width, height);
            shown = best.index();
            best.free();
        }
    }

    /** Whether a frame has been shown yet (until then the texture is empty). */
    public boolean ready() {
        return shown >= 0;
    }

    private void drain() {
        Frame frame;
        while ((frame = queue.poll()) != null) frame.free();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        decoder.interrupt();
        drain();
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE);
            texture = null;
        }
    }

    /** A plain RGBA texture the frames are copied straight into. */
    private static final class VideoTexture extends AbstractTexture {
        VideoTexture(int width, int height) {
            TextureUtil.prepareImage(getGlId(), width, height);
            setFilter(true, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        void upload(ByteBuffer pixels, int width, int height) {
            bindTexture();
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    MemoryUtil.memAddress(pixels));
        }

        @Override
        public void load(ResourceManager manager) {
        }
    }
}
