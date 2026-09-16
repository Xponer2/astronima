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
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudConfig;
import play.xponer.astronima.client.hud.HudPaint;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.Instruments;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.suit.HelmetAtmosphere;
import play.xponer.astronima.sim.suit.SuitTelemetry;

/**
 * The suit's own status display: what you are carrying and how long it will last.
 *
 * <p>Deliberately gated on the status display subsystem being repaired. Before that,
 * this panel is <em>absent</em> rather than showing zeroes — an unrepaired suit does
 * not lie to you, it tells you nothing, which is the honest version of flying blind
 * (design/suit.md §2.3). Fixing the display is therefore a real upgrade: it is the
 * moment the suit stops being a mystery.
 *
 * <p>Two bars, because there are exactly two things that run out and they run out on
 * different clocks. A dash instead of a bar means nothing is fitted — a distinct
 * message from an empty one, and one worth noticing before you seal up.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class SuitHud {
    /** Room a line of text has here: the suit strip. */
    private static final int TEXT_WIDTH = 150;

    private static final int MARGIN = 4;
    private static final int ROW_HEIGHT = 11;
    private static final int BAR_WIDTH = 46;
    private static final int BAR_HEIGHT = 5;
    private static final String LABEL_WIDTH_SAMPLE = "CART";

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Astronima.MODID, "suit"), SuitHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int packed = minecraft.player.getData(ModAttachments.SUIT_TELEMETRY);
        if (!SuitTelemetry.isWorn(packed) || !SuitTelemetry.displayWorks(packed)) {
            return;
        }
        float helmetCo2 = minecraft.player.getData(ModAttachments.HELMET_CO2);
        boolean sealed = SuitTelemetry.isSealed(packed);

        Font font = minecraft.font;
        int labelWidth = font.width(LABEL_WIDTH_SAMPLE) + 4;
        int width = MARGIN * 2 + labelWidth + BAR_WIDTH + 4 + font.width("100%");
        int height = MARGIN * 2 + ROW_HEIGHT * 2 + (sealed ? ROW_HEIGHT : 0);

        float scale = (float) (double) HudConfig.SUIT_SCALE.get();
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        int x = HudConfig.SUIT_ANCHOR.get().resolveX(HudConfig.SUIT_OFFSET_X.get(), screenWidth, width);
        int y = HudConfig.SUIT_ANCHOR.get().resolveY(HudConfig.SUIT_OFFSET_Y.get(), screenHeight, height);

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            // The identity stripe doubles as the seal lamp: lit while the visor is closed.
            // Holds the space it just drew on, so the placement module knows this part
            // of the screen is spoken for. Every frame, so a gauge that stops drawing
            // stops holding.
            play.xponer.astronima.client.hud.HudPanels.reserve("suithud", x, y, width, height, System.currentTimeMillis() / 1000.0);
            Instruments.panel(graphics, x, y, width, height,
                    sealed ? HudScale.COLOR_INFO : HudScale.COLOR_FRAME);

            int row = y + MARGIN;
            row = supplyRow(graphics, font, x + MARGIN, row, labelWidth, "O2",
                    SuitTelemetry.tankPercent(packed));
            row = supplyRow(graphics, font, x + MARGIN, row, labelWidth, "CART",
                    SuitTelemetry.cartridgePercent(packed));

            if (sealed) {
                helmetRow(graphics, font, x + MARGIN, row, labelWidth, helmetCo2);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** One consumable: label, bar, and the number, or a dash when nothing is fitted. */
    private static int supplyRow(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                 int labelWidth, String label, int percent) {
        MachineFrame.overlay(graphics, font, label, x, y + 1, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int barX = x + labelWidth;

        if (!SuitTelemetry.isFitted(percent)) {
            // An empty milled track with a dash: unmistakably "nothing there", which
            // is not the same problem as "nearly out".
            Instruments.track(graphics, barX, y + 2, BAR_WIDTH, BAR_HEIGHT);
            MachineFrame.overlay(graphics, font, Component.translatable("astronima.suit.not_fitted"),
                    barX + BAR_WIDTH + 4, y + 1, TEXT_WIDTH, HudScale.COLOR_TEXT_DIM);
            return y + ROW_HEIGHT;
        }

        int colour = HudScale.supplyColor(percent);
        // A marker at the quarter mark: the point the colour changes, and the point a
        // sensible person turns back.
        Instruments.gauge(graphics, barX, y + 2, BAR_WIDTH, BAR_HEIGHT,
                percent / 100f, colour, HudPaint.markerOffset(25, 100, BAR_WIDTH));
        Instruments.value(graphics, font, percent + "%", barX + BAR_WIDTH + 4, y + 1, colour);
        return y + ROW_HEIGHT;
    }

    /**
     * Helmet carbon dioxide, shown only while sealed because that is the only time it
     * can build up. This is the reading that kills people who are watching the oxygen
     * gauge instead.
     */
    private static void helmetRow(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                  int labelWidth, float ppCo2) {
        MachineFrame.overlay(graphics, font, "CO2", x, y + 1, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int barX = x + labelWidth;
        boolean dangerous = HelmetAtmosphere.isDangerous(ppCo2);
        int colour = dangerous ? HudScale.COLOR_BAD
                : ppCo2 >= HelmetAtmosphere.DANGEROUS_PPCO2_KPA / 2
                ? HudScale.COLOR_WARN : HudScale.COLOR_GOOD;

        Instruments.gauge(graphics, barX, y + 2, BAR_WIDTH, BAR_HEIGHT,
                HudScale.fraction(ppCo2, HelmetAtmosphere.DANGEROUS_PPCO2_KPA), colour, -1);
        Instruments.value(graphics, font, String.format("%.1f", ppCo2),
                barX + BAR_WIDTH + 4, y + 1, colour);
    }

    private SuitHud() {}
}
