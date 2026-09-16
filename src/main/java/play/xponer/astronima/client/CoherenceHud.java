package play.xponer.astronima.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudConfig;
import play.xponer.astronima.client.hud.HudPaint;
import play.xponer.astronima.client.hud.HudPanels;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.Instruments;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.network.CoherenceStatusPayload;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.magic.Coherence;

/**
 * The coherence survey meter's instrument panel — design/astra-incognita.md §9.3's "needle-and-bar
 * hybrid": six bars, one per term, sorted longest-first so the instrument's own ordering names the
 * next thing to fix, plus a needle for total τ whose overshoot is itself a reading — a site that
 * looks fine on average but swings badly is a bad site, and only the needle says so.
 *
 * <p>Pure display: every value arrives in {@link CoherenceStatusPayload}; the client never
 * re-derives {@link Coherence}. Colour and scale decisions live in {@link HudScale}, unit-tested
 * against the model's own reference points.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class CoherenceHud {
    private static final long STALE_AFTER_MS = 1600;

    private static final int ROW_HEIGHT = 11;
    private static final int BAR_WIDTH = 58;
    private static final int BAR_HEIGHT = 5;
    private static final int MARGIN = 5;
    private static final int TEXT_WIDTH = 180;
    private static final String WIDEST_READOUT = "-000.0 ms";

    private static final Map<Coherence.Term, String> LABELS = Map.of(
            Coherence.Term.THERMAL, "THERM",
            Coherence.Term.VIBRATION, "VIB",
            Coherence.Term.RADIATION, "RAD",
            Coherence.Term.FIELD, "FIELD",
            Coherence.Term.GAS, "GAS",
            Coherence.Term.OBSERVER, "OBS");
    private static final String TOTAL_LABEL = "τ";

    private static int panelX;
    private static int panelY;

    private static volatile @Nullable CoherenceStatusPayload latest;
    private static volatile long receivedAtMs;

    public static void accept(CoherenceStatusPayload payload) {
        latest = payload;
        receivedAtMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Astronima.MODID, "coherence_hud"),
                CoherenceHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        CoherenceStatusPayload status = latest;
        if (status == null || minecraft.player == null || minecraft.options.hideGui
                || System.currentTimeMillis() - receivedAtMs > STALE_AFTER_MS
                || !holdingMeter(minecraft)) {
            return;
        }

        Font font = minecraft.font;
        int rows = LABELS.size() + 1; // six terms plus the total needle row
        int height = 14 + rows * ROW_HEIGHT;

        int labelWidth = font.width(TOTAL_LABEL);
        for (String label : LABELS.values()) {
            labelWidth = Math.max(labelWidth, font.width(label));
        }
        int barX = MARGIN + labelWidth + 4;
        int readoutX = barX + BAR_WIDTH + 4;
        int width = readoutX + font.width(WIDEST_READOUT) + MARGIN;

        float scale = (float) (double) HudConfig.PANEL_SCALE.get();
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        panelX = HudConfig.PANEL_ANCHOR.get().resolveX(HudConfig.PANEL_OFFSET_X.get(), screenWidth, width);
        panelY = HudConfig.PANEL_ANCHOR.get().resolveY(HudConfig.PANEL_OFFSET_Y.get(), screenHeight, height);

        HudPanels.reserve("coherence", Math.round(panelX * scale), Math.round(panelY * scale),
                Math.round(width * scale), Math.round(height * scale), System.currentTimeMillis() / 1000.0);

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            beginFrame();
            drawPanel(graphics, font, status, width, barX, readoutX);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, CoherenceStatusPayload status,
                                  int width, int barX, int readoutX) {
        int height = 14 + (LABELS.size() + 1) * ROW_HEIGHT;
        Instruments.panel(graphics, panelX, panelY, width, height, HudScale.COLOR_INFO);
        int y = panelY + 4;
        MachineFrame.overlay(graphics, font, "COHERENCE", panelX + MARGIN, y, TEXT_WIDTH, HudScale.COLOR_VALUE);
        y += ROW_HEIGHT + 2;

        Map<Coherence.Term, Double> breakdown = new java.util.EnumMap<>(Coherence.Term.class);
        for (Coherence.Term term : Coherence.Term.values()) {
            breakdown.put(term, (double) status.seconds(term));
        }
        List<Coherence.Term> worstFirst = Coherence.worstFirst(breakdown);
        for (Coherence.Term term : worstFirst) {
            double seconds = breakdown.get(term);
            y = bar(graphics, font, y, barX, readoutX, LABELS.get(term),
                    HudScale.coherenceFraction(seconds), HudScale.coherenceColor(seconds),
                    Coherence.formatSeconds(seconds));
        }

        double totalSeconds = Coherence.totalSeconds(breakdown);
        needle(graphics, font, y, barX, readoutX, totalSeconds);
    }

    /** One decomposition row: label, milled track with a segmented bar, then the number. */
    private static int bar(GuiGraphicsExtractor graphics, Font font, int y, int barX, int readoutX,
                           String label, float fraction, int color, String readout) {
        Instruments.label(graphics, font, label, panelX + MARGIN, y);
        Instruments.gauge(graphics, panelX + barX, y + 1, BAR_WIDTH, BAR_HEIGHT,
                smoothed(label, Math.clamp(fraction, 0f, 1f)), color, -1);
        Instruments.value(graphics, font, readout, panelX + readoutX, y, HudScale.COLOR_VALUE);
        return y + ROW_HEIGHT;
    }

    /**
     * The total-τ needle. Unlike a decomposition bar, its own position is not simply smoothed
     * toward the target — it is driven by {@link HudPaint#springStep}, a damped spring that
     * overshoots on a sudden change and settles when the site is actually steady. The swing is
     * the reading: a needle that slams and quivers says the site is unstable before the number
     * printed beside it does (design/presentation.md §2).
     */
    private static void needle(GuiGraphicsExtractor graphics, Font font, int y, int barX, int readoutX,
                               double totalSeconds) {
        Instruments.label(graphics, font, TOTAL_LABEL, panelX + MARGIN, y);
        Instruments.track(graphics, panelX + barX, y + 1, BAR_WIDTH, BAR_HEIGHT);

        float target = HudScale.coherenceFraction(totalSeconds);
        HudPaint.NeedleState state = HudPaint.springStep(needlePosition, needleVelocity, target, frameDelta);
        needlePosition = state.position();
        needleVelocity = state.velocity();

        int needleX = panelX + barX + Math.round(Math.clamp(needlePosition, 0f, 1f) * BAR_WIDTH);
        graphics.fill(needleX, y, needleX + 1, y + 1 + BAR_HEIGHT, HudScale.coherenceColor(totalSeconds));

        Instruments.value(graphics, font, Coherence.formatSeconds(totalSeconds), panelX + readoutX, y,
                HudScale.COLOR_VALUE);
    }

    // ------------------------------------------------------------------ smoothing state

    private static final Map<String, Float> SMOOTHED = new HashMap<>();
    private static float needlePosition;
    private static float needleVelocity;
    private static long lastFrameMs = System.currentTimeMillis();
    private static float frameDelta = 1 / 60f;

    private static void beginFrame() {
        long now = System.currentTimeMillis();
        frameDelta = Math.min(0.2f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
    }

    private static float smoothed(String key, float target) {
        float next = HudPaint.approach(SMOOTHED.getOrDefault(key, target), target, frameDelta, 0.18f);
        SMOOTHED.put(key, next);
        return next;
    }

    private static boolean holdingMeter(Minecraft minecraft) {
        assert minecraft.player != null;
        return minecraft.player.getMainHandItem().is(ModItems.COHERENCE_METER.get())
                || minecraft.player.getOffhandItem().is(ModItems.COHERENCE_METER.get());
    }

    private CoherenceHud() {}
}
