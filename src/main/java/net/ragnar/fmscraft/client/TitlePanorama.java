package net.ragnar.fmscraft.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * The title screen's spinning background: a random one of the panorama sets the mod ships, picked again every time the
 * title screen opens and never the same set twice in a row (the last one is remembered across launches in
 * {@code config/fmscraft_title_panorama.txt}). Sets live in {@code textures/gui/title/background/panorama<n>/},
 * {@code panorama_0.png} to {@code panorama_5.png}, n counting up from 1. Sets with any face missing are skipped; with
 * none, the vanilla panorama stays.
 * <p>
 * {@code /panorama [size]} takes the six pictures for a set from where you stand, using vanilla's own panorama capture,
 * into {@code .minecraft/panoramas/<time>/screenshots}.
 */
public final class TitlePanorama {
    private static final int MAX_SETS = 64;
    private static final Random RANDOM = new Random();
    private static List<Identifier> sets;
    private static Identifier current;
    /** The set picked while the game loads is preloaded with it, so the first title screen keeps it. */
    private static boolean keepLoadPick;

    private TitlePanorama() {
    }

    private static List<Identifier> sets() {
        if (sets != null) return sets;
        sets = new ArrayList<>();
        var mod = FabricLoader.getInstance().getModContainer(FmsCraft.MOD_ID).orElse(null);
        if (mod == null) return sets;
        for (int n = 1; n <= MAX_SETS; n++) {
            String dir = "textures/gui/title/background/panorama" + n;
            boolean complete = true;
            for (int face = 0; face < 6 && complete; face++) {
                complete = mod.findPath("assets/" + FmsCraft.MOD_ID + "/" + dir + "/panorama_" + face + ".png")
                        .filter(Files::exists).isPresent();
            }
            if (complete) sets.add(Identifier.of(FmsCraft.MOD_ID, dir + "/panorama"));
        }
        return sets;
    }

    private static Path lastFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("fmscraft_title_panorama.txt");
    }

    /** A random set other than the one shown last, or null when the mod ships none. */
    private static Identifier next() {
        List<Identifier> all = sets();
        if (all.isEmpty()) return null;
        if (current == null) {
            try {
                current = Identifier.tryParse(Files.readString(lastFile()).trim());
            } catch (Exception ignored) {
                // First launch, or the file was removed
            }
        }
        List<Identifier> choices = new ArrayList<>(all);
        if (choices.size() > 1) choices.remove(current);
        current = choices.get(RANDOM.nextInt(choices.size()));
        try {
            Files.writeString(lastFile(), current.toString());
        } catch (Exception e) {
            FmsCraft.LOGGER.warn("Couldn't remember the title panorama", e);
        }
        FmsCraft.LOGGER.info("Title panorama: {} (of {} sets)", current, all.size());
        return current;
    }

    /** The path prefix the cube map adds _0.png ... _5.png to. Called once, when {@code Screen} is loaded. */
    public static Identifier pick(Identifier vanilla) {
        Identifier chosen = next();
        keepLoadPick = chosen != null;
        return chosen == null ? vanilla : chosen;
    }

    /** Switches the shared panorama to another random set, in place. Called each time a title screen is made. */
    public static void shuffle(Identifier[] faces) {
        if (keepLoadPick) {
            keepLoadPick = false;
            return;
        }
        Identifier chosen = next();
        if (chosen == null) return;
        for (int i = 0; i < faces.length; i++) faces[i] = chosen.withPath(chosen.getPath() + "_" + i + ".png");
    }

    public static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("panorama")
                .executes(ctx -> take(1024))
                .then(argument("size", IntegerArgumentType.integer(256, 4096))
                        .executes(ctx -> take(IntegerArgumentType.getInteger(ctx, "size"))))));
    }

    private static int take(int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Run between frames, after the chat screen has closed
        client.send(() -> {
            if (client.player == null) return;
            File dir = new File(client.runDirectory, "panoramas/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")));
            dir.mkdirs();
            Text result = client.takePanorama(dir, size, size);
            client.player.sendMessage(result, false);
            client.player.sendMessage(Text.literal("Saved to " + new File(dir, "screenshots").getAbsolutePath()), false);
        });
        return 1;
    }
}
