package net.ragnar.fmscraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.ragnar.fmscraft.client.credits.CreditsMusic;
import net.ragnar.fmscraft.client.credits.CreditsVideo;
import net.ragnar.fmscraft.client.credits.MemoryHole;
import net.ragnar.fmscraft.network.CreditsDonePayload;
import net.ragnar.fmscraft.network.CreditsPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The True Ending credits, rolled like the vanilla ones after the dragon: the logo (with its FMS EDITION), the end
 * portal's starfield behind, the vignette, the credits music, white and yellow text scrolling up. Hold space to speed
 * it up; Escape leaves. They list the seven sins and their bosses, who laid each soul to rest, fun facts about the
 * players ({@link net.ragnar.fmscraft.ending.CreditsFacts}), and end on "Thank you for playing." held in the middle.
 *
 * <p>Then the void cracks open and the memories video plays through the hole, the music carrying on under it; when it
 * ends, everything fades to black with the music ({@link MemoryHole}). The music is played by the screen itself
 * ({@link CreditsMusic}) so it can fade.
 */
public class FmsCreditsScreen extends Screen {
    private static final Identifier VIGNETTE_TEXTURE = Identifier.ofVanilla("textures/misc/credits_vignette.png");
    private static final Text SEPARATOR = Text.literal("============").formatted(Formatting.WHITE);
    private static final String INDENT = "           ";
    private static final float BASE_SPEED = 0.5f;
    /** How long "Thank you for playing." stays in the middle of the screen once it gets there, then fades. */
    private static final int LINGER_TICKS = 20 * 15;
    private static final int FADE_TICKS = 40;
    private static final int FACT_WIDTH = 300;
    /** The boss each sin is fought as (their entity names). */
    private static final String[] BOSSES = {"raggimel", "ornamix", "leonard", "lalli", "gummss", "mugen", "ricky"};

    private final CreditsPayload payload;
    private final List<OrderedText> lines = new ArrayList<>();
    private final IntSet centered = new IntOpenHashSet();
    private final LogoDrawer logo = new LogoDrawer(false);
    private float time;
    private boolean fast;
    private boolean done;
    /** Ticks since the last line stopped in the middle (-1 until it does). */
    private int lingered = -1;
    @Nullable
    private MemoryHole memories;
    /** When the memories began (Util.getMeasuringTimeNano), 0 until then. */
    private long memoriesStart;
    private boolean finished;

    public FmsCreditsScreen(CreditsPayload payload) {
        super(NarratorManager.EMPTY);
        this.payload = payload;
    }

    @Override
    protected void init() {
        if (!lines.isEmpty()) return;
        ClientBossMusic.stop();
        CreditsMusic.start();
        // Opened now so its first frames are decoded long before they're needed
        CreditsVideo video = CreditsVideo.open(client.getResourceManager());
        if (video != null) memories = new MemoryHole(video);
        if (payload.memoriesOnly()) {
            if (memories == null) close();
            else memoriesStart = Util.getMeasuringTimeNano();
            return;
        }
        section("credits.fmscraft.sins");
        for (Sin sin : Sin.values()) {
            add(Text.literal(sin.displayName()).withColor(sin.color()), false);
            add(Text.literal(INDENT).append(Text.translatable("entity.fmscraft." + BOSSES[sin.ordinal()]))
                    .append(Text.literal(", " + sin.bossName()).formatted(Formatting.GRAY)).formatted(Formatting.WHITE), false);
            String placer = sin.ordinal() < payload.soulPlacers().size() ? payload.soulPlacers().get(sin.ordinal()) : "";
            if (!placer.isEmpty()) {
                add(Text.literal(INDENT).append(Text.translatable("credits.fmscraft.laid_to_rest", placer)).formatted(Formatting.GRAY), false);
            }
            blank(2);
        }

        if (!payload.facts().isEmpty()) {
            section("credits.fmscraft.facts");
            for (CreditsPayload.Fact fact : payload.facts()) {
                Object[] args = fact.args().stream().map(a -> Text.literal(a).formatted(Formatting.WHITE)).toArray();
                for (OrderedText line : textRenderer.wrapLines(Text.translatable(fact.key(), args).formatted(Formatting.GRAY), FACT_WIDTH)) {
                    centered.add(lines.size());
                    lines.add(line);
                }
                blank(1);
            }
            blank(1);
        }

        section("credits.fmscraft.made_for");
        for (Sin sin : Sin.values()) add(Text.literal(sin.bossName()).formatted(Formatting.WHITE), true);
        blank(6);
        add(Text.translatable("credits.fmscraft.thanks").formatted(Formatting.YELLOW), true);
    }

    private void section(String key) {
        add(SEPARATOR, true);
        add(Text.translatable(key).formatted(Formatting.YELLOW), true);
        add(SEPARATOR, true);
        blank(2);
    }

    private void add(Text text, boolean center) {
        if (center) centered.add(lines.size());
        lines.add(text.asOrderedText());
    }

    private void blank(int count) {
        for (int i = 0; i < count; i++) lines.add(OrderedText.EMPTY);
    }

    @Override
    public void tick() {
        if (finished) {
            close();
            return;
        }
        if (lingered >= 0 && memoriesStart == 0 && ++lingered >= LINGER_TICKS) {
            if (memories != null) memoriesStart = Util.getMeasuringTimeNano();
            else close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && memoriesStart == 0) fast = true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) fast = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (done) return;
        done = true;
        if (ClientPlayNetworking.canSend(CreditsDonePayload.ID)) ClientPlayNetworking.send(CreditsDonePayload.INSTANCE);
        client.setScreen(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (memoriesStart != 0 && memories != null) {
            renderMemories(context);
            return;
        }
        renderVignette(context);
        time = Math.max(0, time + delta * BASE_SPEED * (fast ? 5 : 1));
        int left = width / 2 - 128;
        int top = height + 50;
        float scroll = -time;
        context.getMatrices().push();
        context.getMatrices().translate(0, scroll, 0);
        logo.draw(context, width, 1.0f, top);
        int y = top + 100;
        for (int i = 0; i < lines.size(); i++) {
            // The last line stops in the middle of the screen
            int color = Colors.WHITE;
            if (i == lines.size() - 1) {
                float past = y + scroll - (height / 2f - 6);
                if (past <= 0) {
                    context.getMatrices().translate(0, -past, 0);
                    if (lingered < 0) lingered = 0;
                    float fade = Math.clamp((LINGER_TICKS - lingered - delta) / FADE_TICKS, 0.0f, 1.0f);
                    color = (Math.max(5, (int) (fade * 255)) << 24) | 0xFFFFFF;
                }
            }
            if (y + scroll + 20 > 0 && y + scroll < height || i == lines.size() - 1) {
                if (centered.contains(i)) context.drawCenteredTextWithShadow(textRenderer, lines.get(i), left + 128, y, color);
                else context.drawTextWithShadow(textRenderer, lines.get(i), left, y, color);
            }
            y += 12;
        }
        context.getMatrices().pop();
    }

    /** Seconds into the memories (0 before they start). */
    public float memoriesTime() {
        return memoriesStart == 0 ? 0 : (Util.getMeasuringTimeNano() - memoriesStart) / 1.0e9f;
    }

    /** Development: starts the memories this many seconds in (CreditsPreview). */
    public void seekMemories(float seconds) {
        if (memories != null) memoriesStart = Util.getMeasuringTimeNano() - (long) (seconds * 1.0e9);
    }

    private void renderMemories(DrawContext context) {
        float t = memoriesTime();
        memories.render(context, width, height, t);
        renderVignette(context);
        float fade = memories.fadeOut(t);
        CreditsMusic.setLevel(1 - fade);
        if (fade > 0) {
            MemoryHole.rect(context.getMatrices().peek().getPositionMatrix(), 0, 0, width, height, (Math.round(fade * 255) << 24), false);
        }
        if (memories.finished(t)) finished = true;
    }

    private void renderVignette(DrawContext context) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR);
        context.drawTexture(VIGNETTE_TEXTURE, 0, 0, 0, 0.0f, 0.0f, width, height, width, height);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillWithLayer(RenderLayer.getEndPortal(), 0, 0, width, height, 0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        CreditsMusic.stop();
        if (memories != null) memories.close();
    }
}
