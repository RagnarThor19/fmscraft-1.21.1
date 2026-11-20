package net.ragnar.fmscraft;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The memories video at the end of the credits (tools/video/credits_video.py) is in the shape CreditsVideo reads. */
class CreditsVideoFileTest {
    private static final Path FILE = Path.of("src/main/resources/assets/fmscraft/video/memories.fmv");

    @Test
    void headerHoleAndFramesReadBack() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(FILE)))) {
            assertEquals("FMSV", new String(in.readNBytes(4), StandardCharsets.US_ASCII));
            assertEquals(1, in.readInt(), "version");
            int width = in.readInt(), height = in.readInt();
            float fps = in.readInt() / 1000f;
            int frames = in.readInt(), cols = in.readInt(), rows = in.readInt();
            assertTrue(width > 0 && height > 0 && fps > 0 && frames > 0);
            assertTrue(frames / fps > 60, "the memories run over a minute");

            byte[] cells = in.readNBytes(cols * rows);
            int open = 0;
            boolean firstBreaks = false, lastBreaks = false;
            for (byte cell : cells) {
                int order = cell & 0xFF;
                if (order != 0) open++;
                firstBreaks |= order == 1;
                lastBreaks |= order == 255;
            }
            assertTrue(open > cols * rows / 3, "a real hole, not a few blocks");
            assertTrue(open < cols * rows, "with void round it");
            assertTrue(firstBreaks && lastBreaks, "the blocks break in order from 1 to 255");

            for (int i = 0; i < frames; i++) {
                int length = in.readInt();
                byte[] jpeg = in.readNBytes(length);
                assertEquals(length, jpeg.length, "frame " + i + " is whole");
                assertTrue((jpeg[0] & 0xFF) == 0xFF && (jpeg[1] & 0xFF) == 0xD8, "frame " + i + " is a JPEG");
            }
            assertEquals(-1, in.read(), "nothing after the last frame");
        }
    }
}
