package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import play.xponer.astronima.client.hud.MachineFrame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudConfig;
import play.xponer.astronima.client.hud.HudPaint;
import play.xponer.astronima.client.hud.Instruments;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.network.AtmosphereStatusPayload;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.tox.ContaminationVerdict;
import play.xponer.astronima.sim.thermal.ThermalVerdict;
import play.xponer.astronima.sim.burn.Flammability;

import java.util.Locale;

/**
 * The analyzer's instrument panel: a compact gauge cluster rather than a wall of
 * chat text. Bars carry the reading, colour carries the judgement, and numbers are
 * present but secondary — the same way a real cockpit instrument works.
 *
 * <p>Pure display: every value arrives in {@link AtmosphereStatusPayload}; the client
 * simulates nothing. Colour and scale decisions live in {@link HudScale}, which is
 * unit-tested against the physiological thresholds so a green bar always means safe.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class AnalyzerHud {
    /** Room a line of text has here: the overlay column, which floats and has no panel to be cut by. */
    private static final int TEXT_WIDTH = 180;

    /** Snapshots stop rendering if the server goes quiet (three sync periods). */
    private static final long STALE_AFTER_MS = 1600;

    private static final int ROW_HEIGHT = 11;
    private static final int BAR_WIDTH = 58;
    private static final int BAR_HEIGHT = 5;
    private static final int MARGIN = 5;
    /** Widest readout we ever print, used to size the panel so nothing overflows. */
    private static final String WIDEST_READOUT = "-000.0 kPa";

    /** All row labels, so the label column can be measured rather than guessed. */
    private static final String[] LABELS = { "O2", "CO2", "PRES", "TEMP", "HUM", "NOISE", "TANK" };

    private static int panelX;
    private static int panelY;

    private static volatile @Nullable AtmosphereStatusPayload latest;
    private static volatile long receivedAtMs;

    public static void accept(AtmosphereStatusPayload payload) {
        latest = payload;
        receivedAtMs = System.currentTimeMillis();
    }

    /**
     * The air where the player is standing, or null when nothing has arrived lately.
     *
     * <p>Shared because a <strong>second</strong> instrument now needs it: a wire's current rating
     * depends on what is around it to carry the heat away, and the goggles are in the player's
     * hand, in this room. A client cannot sample the atmosphere at an arbitrary cell — it is only
     * ever told about its own — so "here" is both what is available and what a real meter reads.
     */
    public static @Nullable AtmosphereStatusPayload here() {
        return System.currentTimeMillis() - receivedAtMs > STALE_AFTER_MS ? null : latest;
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Astronima.MODID, "analyzer_hud"),
                AnalyzerHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        AtmosphereStatusPayload status = latest;
        if (status == null || minecraft.player == null || minecraft.options.hideGui
                || System.currentTimeMillis() - receivedAtMs > STALE_AFTER_MS
                || !holdingAnalyzer(minecraft)) {
            return;
        }

        Font font = minecraft.font;
        int rows = status.hasAtmosphere() ? 6 : 1;
        boolean showTank = status.onTank() || status.tankFraction() >= 0;
        int height = 14 + rows * ROW_HEIGHT + (status.hasAtmosphere() ? 8 : 0) + (showTank ? ROW_HEIGHT : 0);

        // Measure, never assume: label and readout columns are sized from the font, so
        // "NOISE" and "-100.0 kPa" cannot run over the bar or off the panel.
        int labelWidth = 0;
        for (String label : LABELS) {
            labelWidth = Math.max(labelWidth, font.width(label));
        }
        int barX = MARGIN + labelWidth + 4;
        int readoutX = barX + BAR_WIDTH + 4;
        int width = readoutX + font.width(WIDEST_READOUT) + MARGIN;
        width = Math.max(width, MARGIN + font.width(title(status).getString()) + MARGIN);

        float scale = (float) (double) HudConfig.PANEL_SCALE.get();
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        panelX = HudConfig.PANEL_ANCHOR.get().resolveX(HudConfig.PANEL_OFFSET_X.get(),
                screenWidth, width);
        panelY = HudConfig.PANEL_ANCHOR.get().resolveY(HudConfig.PANEL_OFFSET_Y.get(),
                screenHeight, height);

        // Holds the space it is about to draw on, in the coordinates the placement module works
        // in. This panel is drawn under a scale transform, so its own x, y, width and height are
        // in scaled pixels and have to be multiplied back out - a reservation in the wrong
        // coordinate space is worse than none, because it is confidently wrong.
        play.xponer.astronima.client.hud.HudPanels.reserve("analyzer",
                Math.round(panelX * scale), Math.round(panelY * scale),
                Math.round(width * scale), Math.round(height * scale),
                System.currentTimeMillis() / 1000.0);

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            beginFrame();
            drawPanel(graphics, font, status, width, height, barX, readoutX, showTank);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, AtmosphereStatusPayload status,
                                  int width, int height, int barX, int readoutX, boolean showTank) {
        panel(graphics, panelX, panelY, width, height);
        int y = panelY + 4;
        MachineFrame.overlay(graphics, font, title(status), panelX + MARGIN, y, TEXT_WIDTH, titleColor(status));
        y += ROW_HEIGHT + 2;

        if (status.hasAtmosphere()) {
            y = gauge(graphics, font, y, barX, readoutX, "O2", status.partialPressure(Gas.OXYGEN),
                    HudScale.O2_FULL_SCALE_KPA, HudScale.oxygenColor(status.partialPressure(Gas.OXYGEN)), "kPa");
            y = gauge(graphics, font, y, barX, readoutX, "CO2", status.partialPressure(Gas.CARBON_DIOXIDE),
                    HudScale.CO2_FULL_SCALE_KPA,
                    HudScale.carbonDioxideColor(status.partialPressure(Gas.CARBON_DIOXIDE)), "kPa");
            y = gauge(graphics, font, y, barX, readoutX, "PRES", status.pressureKPa(),
                    HudScale.PRESSURE_FULL_SCALE_KPA, HudScale.pressureColor(status.pressureKPa()), "kPa");

            double celsius = status.temperatureK() - 273.15;
            // The temperature, then which way it is going. The second is the one that is
            // actionable: 7 C and steady is a cold store room, 7 C and falling is a habitat
            // on its way to sixty below with you inside it, and the number alone cannot
            // tell those apart. See sim/thermal/ThermalVerdict.
            ThermalVerdict.Reading heat = ThermalVerdict.read(status.temperatureK(),
                    status.heatLossWatts(), status.heatSupplyWatts(),
                    status.skyFraction(), status.insulatedFraction());
            y = bar(graphics, font, y, barX, readoutX, "TEMP", HudScale.temperatureFraction(celsius),
                    HudScale.temperatureColor(celsius),
                    format(celsius) + " C " + arrow(heat.trend()));
            y = bar(graphics, font, y, barX, readoutX, "HUM", status.relativeHumidity(),
                    HudScale.humidityColor(status.relativeHumidity()),
                    Math.round(status.relativeHumidity() * 100) + " %");
            y = bar(graphics, font, y, barX, readoutX, "NOISE", HudScale.fraction(status.noiseDb(), 100),
                    HudScale.noiseColor(status.noiseDb()), Math.round(status.noiseDb()) + " dB");

            // The sentence, and only when the room is not simply holding: a line that is
            // always on screen is a line that stops being read, and "holding" is the state
            // the player is aiming at rather than one they need told about.
            if (!heat.isGood() || status.temperatureK() < ThermalVerdict.COLD_K) {
                MachineFrame.overlay(graphics, font, heat.advice(), panelX + MARGIN, y, TEXT_WIDTH,
                        heat.isGood() ? HudScale.COLOR_LABEL : HudScale.COLOR_WARN);
                y += ROW_HEIGHT;
            }

            // What is wrong with this air, and what to do — the same ranking the server uses,
            // drawn only when there is something to say. Before this the panel listed eleven
            // partial pressures and left the player to work out which row was the one killing
            // them, which is a table rather than an instrument.
            ContaminationVerdict.Reading air = ContaminationVerdict.read(
                    status::partialPressure, status.relativeHumidity() >= MOLD_RH);
            if (!air.isClean()) {
                MachineFrame.overlay(graphics, font, air.advice(), panelX + MARGIN, y, TEXT_WIDTH,
                        air.needsPurge() ? HudScale.COLOR_BAD : HudScale.COLOR_WARN);
                y += ROW_HEIGHT;
            }

            compositionStrip(graphics, y, width, status);
            y += 8;
        }

        if (showTank) {
            int color = status.onTank() ? HudScale.COLOR_INFO : HudScale.COLOR_LABEL;
            bar(graphics, font, y, barX, readoutX, "TANK",
                    Math.max(0, status.tankFraction()), color,
                    Math.round(Math.max(0, status.tankFraction()) * 100) + " %");
            y += ROW_HEIGHT;
        }

        // Drawn last and below the panel so it can never overlap a gauge row.
        if (status.hasAtmosphere()
                && Flammability.IgnitionStatus.values()[status.ignition()]
                        == Flammability.IgnitionStatus.EXPLOSIVE) {
            warningStrip(graphics, font, y, "EXPLOSIVE MIXTURE");
        }
    }

    // ------------------------------------------------------------------ drawing

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Instruments.panel(graphics, x, y, width, height, HudScale.COLOR_INFO);
    }

    private static int gauge(GuiGraphicsExtractor graphics, Font font, int y, int barX, int readoutX,
                             String label, double value, double fullScale, int color, String unit) {
        return bar(graphics, font, y, barX, readoutX, label, HudScale.fraction(value, fullScale), color,
                format(value) + " " + unit, HudPaint.markerOffset(
                        HudScale.dangerThreshold(label), fullScale, BAR_WIDTH));
    }

    private static int bar(GuiGraphicsExtractor graphics, Font font, int y, int barX, int readoutX,
                           String label, float fraction, int color, String readout) {
        return bar(graphics, font, y, barX, readoutX, label, fraction, color, readout, -1);
    }

    /**
     * One instrument row: label, milled track with a segmented bar, then the number.
     *
     * <p>The displayed fraction is smoothed rather than taken raw. A needle that jumps
     * between two values every server tick is genuinely harder to read than one that
     * glides, and it is the single biggest difference between a panel that looks like
     * an instrument and one that looks like a debug overlay.
     */
    private static int bar(GuiGraphicsExtractor graphics, Font font, int y, int barX, int readoutX,
                           String label, float fraction, int color, String readout, int markerAt) {
        Instruments.label(graphics, font, label, panelX + MARGIN, y);
        Instruments.gauge(graphics, panelX + barX, y + 1, BAR_WIDTH, BAR_HEIGHT,
                smoothed(label, Math.clamp(fraction, 0f, 1f)), color, markerAt);
        Instruments.value(graphics, font, readout, panelX + readoutX, y, HudScale.COLOR_VALUE);
        return y + ROW_HEIGHT;
    }

    /** Per-row smoothing state, keyed by the row's label. */
    private static final java.util.Map<String, Float> SMOOTHED = new java.util.HashMap<>();
    private static long lastFrameMs = System.currentTimeMillis();
    private static float frameDelta = 1 / 60f;

    private static void beginFrame() {
        long now = System.currentTimeMillis();
        frameDelta = Math.min(0.2f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
    }

    private static float smoothed(String key, float target) {
        float next = HudPaint.approach(SMOOTHED.getOrDefault(key, target), target,
                frameDelta, 0.18f);
        SMOOTHED.put(key, next);
        return next;
    }

    /** Proportional bar of the whole mixture, one segment per species. */
    private static void compositionStrip(GuiGraphicsExtractor graphics, int y, int width,
                                         AtmosphereStatusPayload status) {
        float total = 0;
        for (Gas gas : Gas.values()) {
            total += Math.max(0, status.partialPressure(gas));
        }
        int stripX = panelX + MARGIN;
        int stripWidth = width - 2 * MARGIN;
        graphics.fill(stripX, y, stripX + stripWidth, y + 4, HudScale.COLOR_TRACK);
        if (total <= 0) {
            return;
        }
        int cursor = stripX;
        for (Gas gas : Gas.values()) {
            float share = Math.max(0, status.partialPressure(gas)) / total;
            int segment = Math.round(stripWidth * share);
            if (segment <= 0) {
                continue;
            }
            int end = Math.min(stripX + stripWidth, cursor + segment);
            graphics.fill(cursor, y, end, y + 4, HudScale.gasColor(gas));
            cursor = end;
        }
    }

    private static void warningStrip(GuiGraphicsExtractor graphics, Font font, int y, String text) {
        int width = font.width(text) + 8;
        graphics.fill(panelX, y + 2, panelX + width, y + 14, 0xE0401414);
        graphics.fill(panelX, y + 2, panelX + width, y + 3, HudScale.COLOR_BAD);
        MachineFrame.overlay(graphics, font, text, panelX + 4, y + 5, TEXT_WIDTH, HudScale.COLOR_BAD);
    }

    private static Component title(AtmosphereStatusPayload status) {
        return switch (status.environment()) {
            case AtmosphereStatusPayload.ENV_VACUUM -> Component.translatable("astronima.hud.vacuum");
            case AtmosphereStatusPayload.ENV_OPEN_AIR -> Component.translatable("astronima.hud.open_air");
            case AtmosphereStatusPayload.ENV_UNSEALABLE ->
                    Component.translatable("astronima.hud.unsealable", status.volumeBlocks());
            default -> Component.translatable("astronima.hud.sealed", status.volumeBlocks());
        };
    }

    private static int titleColor(AtmosphereStatusPayload status) {
        return switch (status.environment()) {
            case AtmosphereStatusPayload.ENV_VACUUM -> HudScale.COLOR_BAD;
            case AtmosphereStatusPayload.ENV_OPEN_AIR -> HudScale.COLOR_GOOD;
            case AtmosphereStatusPayload.ENV_UNSEALABLE -> HudScale.COLOR_WARN;
            default -> HudScale.COLOR_VALUE;
        };
    }

    /** Which way the room is going, as one glyph beside the number. */
    private static String arrow(ThermalVerdict.Trend trend) {
        return switch (trend) {
            case FALLING -> "v";
            case RISING -> "^";
            case STEADY -> "=";
        };
    }

    /**
     * Relative humidity at which mould takes hold.
     *
     * <p>Mirrors {@code Humidity.moldFavourable}, which needs a whole room and the client has
     * only a number. Kept beside the one line that uses it rather than smuggled into the
     * payload: a second float on the wire to save a comparison would be the more expensive
     * mistake.
     */
    private static final float MOLD_RH = 0.70f;

    private static String format(double value) {
        return String.format(Locale.ROOT, Math.abs(value) >= 100 ? "%.0f" : "%.1f", value);
    }

    private static boolean holdingAnalyzer(Minecraft minecraft) {
        assert minecraft.player != null;
        return minecraft.player.getMainHandItem().is(ModItems.GAS_ANALYZER.get())
                || minecraft.player.getOffhandItem().is(ModItems.GAS_ANALYZER.get());
    }

    private AnalyzerHud() {}
}
