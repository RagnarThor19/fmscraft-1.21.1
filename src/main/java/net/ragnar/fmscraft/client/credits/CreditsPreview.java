package net.ragnar.fmscraft.client.credits;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.client.FmsCreditsScreen;
import net.ragnar.fmscraft.network.CreditsPayload;

import java.util.List;

/**
 * Development only: with {@code FMSCRAFT_CREDITS_PREVIEW=<seconds>} set, {@code gradlew runClient} goes straight from
 * the title screen to the memories at the end of the credits (that many seconds in), saves screenshots of its key
 * moments to {@code run/screenshots/credits_*.png}, and quits. Does nothing otherwise.
 */
public final class CreditsPreview {
    /** Seconds after the start of the preview to take each screenshot. */
    private static final float[] SHOTS = {2.6f, 4.3f, 4.75f, 5.0f, 5.4f, 6.2f, 8.0f, 20.0f};

    private static boolean opened;
    private static int taken;

    private CreditsPreview() {
    }

    public static void register() {
        String seek = System.getenv("FMSCRAFT_CREDITS_PREVIEW");
        if (seek == null) return;
        float from = Float.parseFloat(seek);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!opened && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                opened = true;
                FmsCreditsScreen screen = new FmsCreditsScreen(new CreditsPayload(List.of(), List.of(), true));
                client.setScreen(screen);
                screen.seekMemories(from);
                return;
            }
            if (!opened) return;
            if (!(client.currentScreen instanceof FmsCreditsScreen screen)) {
                client.scheduleStop();
                return;
            }
            float t = screen.memoriesTime() - from;
            if (taken < SHOTS.length && t >= SHOTS[taken]) {
                String name = "credits_" + (int) from + "_" + taken + ".png";
                ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> { });
                FmsCraft.LOGGER.info("Credits preview: {} at {} s", name, from + t);
                taken++;
                if (taken == SHOTS.length) client.scheduleStop();
            }
        });
    }
}
