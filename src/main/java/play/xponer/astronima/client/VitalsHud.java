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
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.Instruments;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.VitalSigns;
import play.xponer.astronima.sim.tox.GasToxicity;

import java.util.Map;

/**
 * The medical monitor: what is wrong with you, and what to do about it.
 *
 * <p>Hypoxia, carbon dioxide, carbon monoxide, decompression sickness and mould all
 * hand out the same nausea and weakness, so watching the symptoms tells a player
 * nothing about the cause. This names the condition, colours it by severity, and
 * prints the remedy — the same instrument-before-hazard rule the atmosphere gauges
 * follow, turned on the player's own body.
 *
 * <p>Always on, and silent while healthy: it appears only when something is actually
 * happening.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class VitalsHud {
    /** Room a line of text has here: the vitals strip. */
    private static final int TEXT_WIDTH = 150;

    private static final int ROW_HEIGHT = 10;
    private static final int MARGIN = 4;
    private static final int DOT = 3;
    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 5;
    private static final float CO_SHOW_ABOVE = 0.02f;
    private static final float N2_SHOW_ABOVE = 40f;
    private static final float SEA_LEVEL_N2_KPA = 79f;
    /** Hard cap on panel width, so a bad day cannot cover the screen. */
    private static final int MAX_WIDTH = 118;

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Astronima.MODID, "vitals"), VitalsHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int packed = minecraft.player.getData(ModAttachments.VITALS);
        float coDose = minecraft.player.getData(ModAttachments.CO_DOSE);
        float tissueN2 = minecraft.player.getData(ModAttachments.TISSUE_N2);
        Map<Ailment, Ailment.Severity> active = VitalSigns.unpack(packed);
        boolean showCo = coDose >= CO_SHOW_ABOVE;
        boolean showN2 = tissueN2 >= N2_SHOW_ABOVE;
        if (active.isEmpty() && !showCo && !showN2) {
            return; // nothing to report
        }

        Font font = minecraft.font;
        int gauges = (showCo ? 1 : 0) + (showN2 ? 1 : 0);
        int width = MARGIN * 2 + Math.max(gaugeBlockWidth(font), widestRow(font, active));
        int height = MARGIN * 2 + active.size() * ROW_HEIGHT + gauges * (ROW_HEIGHT + 2);

        float scale = (float) (double) HudConfig.VITALS_SCALE.get();
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        int x = HudConfig.VITALS_ANCHOR.get().resolveX(HudConfig.VITALS_OFFSET_X.get(), screenWidth, width);
        int y = HudConfig.VITALS_ANCHOR.get().resolveY(HudConfig.VITALS_OFFSET_Y.get(), screenHeight, height);

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            // Holds the space it just drew on, so the placement module knows this part
            // of the screen is spoken for. Every frame, so a gauge that stops drawing
            // stops holding.
            play.xponer.astronima.client.hud.HudPanels.reserve("vitalshud", x, y, width, height, System.currentTimeMillis() / 1000.0);
            Instruments.panel(graphics, x, y, width, height, borderFor(packed));
            int row = y + MARGIN;

            for (Map.Entry<Ailment, Ailment.Severity> entry : active.entrySet()) {
                int colour = severityColour(entry.getValue());
                // A filled dot reads as a warning lamp on a panel.
                graphics.fill(x + MARGIN, row + 2, x + MARGIN + DOT, row + 2 + DOT, colour);
                // The name only. The remedy lives on the biomonitor, which is a screen
                // you open deliberately; repeating it here made a corner overlay wide
                // enough to cover the inventory, and it was widest exactly when things
                // were going worst.
                MachineFrame.overlay(graphics, font, Component.literal(entry.getKey().displayName()),
                        x + MARGIN + DOT + 3, row, TEXT_WIDTH, colour);
                row += ROW_HEIGHT;
            }

            if (showCo) {
                row = gauge(graphics, font, x + MARGIN, row, "CO",
                        coDose, coColour(coDose)) ;
            }
            if (showN2) {
                gauge(graphics, font, x + MARGIN, row, "N2",
                        tissueN2 / SEA_LEVEL_N2_KPA, tissueN2 > 60 ? HudScale.COLOR_WARN : HudScale.COLOR_INFO);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int gauge(GuiGraphicsExtractor graphics, Font font, int x, int y,
                             String label, float fraction, int colour) {
        Instruments.label(graphics, font, label, x, y);
        int barX = x + font.width("CO") + 4;
        Instruments.gauge(graphics, barX, y + 1, BAR_WIDTH, BAR_HEIGHT,
                Math.clamp(fraction, 0f, 1f), colour, -1);
        return y + ROW_HEIGHT + 2;
    }

    /**
     * Widest row the panel will draw, capped.
     *
     * <p>The cap is the point: the panel used to size itself to its content, so the
     * more conditions a player had the wider it grew, and a dying player's overlay
     * covered the screen. A readout that gets harder to see through as things get
     * worse is the wrong way round.
     */
    private static int widestRow(Font font, Map<Ailment, Ailment.Severity> active) {
        int widest = 0;
        for (Ailment ailment : active.keySet()) {
            widest = Math.max(widest, DOT + 3 + font.width(ailment.displayName()));
        }
        return Math.min(widest, MAX_WIDTH);
    }

    private static int gaugeBlockWidth(Font font) {
        return font.width("CO") + 4 + BAR_WIDTH;
    }

    private static int severityColour(Ailment.Severity severity) {
        return switch (severity) {
            case NONE, MILD -> HudScale.COLOR_WARN;
            case SEVERE -> 0xFFDD8833;
            case CRITICAL -> HudScale.COLOR_BAD;
        };
    }

    private static int coColour(float dose) {
        return switch (GasToxicity.CoStatus.classify(dose)) {
            case NONE -> HudScale.COLOR_LABEL;
            case MILD -> HudScale.COLOR_WARN;
            case SEVERE -> 0xFFDD8833;
            case CRITICAL -> HudScale.COLOR_BAD;
        };
    }

    private static int borderFor(int packed) {
        return severityColour(VitalSigns.worst(packed));
    }

    private VitalsHud() {}
}
