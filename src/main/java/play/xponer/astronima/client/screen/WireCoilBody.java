package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.Schematic;
import play.xponer.astronima.item.WireCoil;
import play.xponer.astronima.network.WireCoilSettingPayload;
import play.xponer.astronima.sim.circuit.Ampacity;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.circuit.Conductor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.wire.WireRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The wire coil panel's own picture and click regions, ported from the old {@code WireCoilScreen}
 * verbatim — {@code design/ui-ldlib2-machines.md}'s pattern: everything this drew by hand still
 * draws the same way, only {@code leftPos}/{@code topPos} became {@link #getContentX()}/
 * {@link #getContentY()}. No menu, no slots — the same "screen with no server round trip beyond
 * one fire-and-forget packet" shape {@link BiomonitorBody} already established, so {@link #font()}
 * is free to call {@code Minecraft.getInstance()} directly.
 *
 * <p>The old screen's {@code rows} list (hit region, action, tooltip, rebuilt fresh every frame)
 * is kept exactly as it was rather than exploded into one {@code AirlockUi}-style overlay per
 * region — there are dozens of rows here (every conductor, every colour swatch, every gauge, both
 * modes) and they all resolve the same way a click landed on the old screen: whichever row's box
 * contains the point. One listener on this element re-runs that same loop; nothing about the hit
 * test itself changed.
 */
public final class WireCoilBody extends UIElement {

    static final int WIDTH = 248;
    private static final int TEXT_WIDTH = WIDTH - 24;
    static final int HEIGHT = 250;

    private static final int ROW_HEIGHT = 13;
    private static final int LIST_Y = 34;
    private static final int SWATCH = 12;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCHES_PER_ROW = 8;
    private static final double AMBIENT_K = 200.0;

    private final List<Row> rows = new ArrayList<>();
    private WireCoil coil;

    public WireCoilBody(WireCoil coil) {
        this.coil = coil;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) {
                return;
            }
            for (Row row : rows) {
                if (row.contains(event.x, event.y)) {
                    row.action.run();
                    return;
                }
            }
        });
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            for (Row row : rows) {
                if (row.contains(event.x, event.y) && !row.tip.isEmpty()) {
                    event.hoverTooltips = HoverTooltips.create(row.tip.toArray());
                    return;
                }
            }
        });
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** One clickable region and what it does, so drawing and hit-testing cannot disagree. */
    private record Row(int x, int y, int width, int height, Runnable action, List<Component> tip) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top) {
        rows.clear();
        MachineFrame.panel(graphics, left, top, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), "WIRE COIL", left + 10, top + 10, TEXT_WIDTH, MachineFrame.TEXT);
        MachineFrame.label(graphics, font(), "ohm/blk   amps vac / air", left + 128, top + 10, TEXT_WIDTH);

        drawConductors(graphics, left, top);
        drawColours(graphics, left, top);
        drawGauges(graphics, left, top);
        drawModes(graphics, left, top);
    }

    private void drawConductors(GuiGraphicsExtractor graphics, int left, int top) {
        int y = top + LIST_Y;
        for (ConductorMaterial material : ConductorMaterial.values()) {
            Conductor metre = new Conductor(material, Conductor.mm2(Conductor.STANDARD_MM2), 1.0);
            boolean chosen = material == coil.material();

            if (chosen) {
                MachineFrame.knob(graphics, left + 8, y - 3, WIDTH - 16, ROW_HEIGHT - 2);
            }
            boolean available = play.xponer.astronima.wire.WireStock.isAvailable(material);
            MachineFrame.value(graphics, font(), material.name().toLowerCase(Locale.ROOT),
                    left + 14, y, TEXT_WIDTH, !available ? MachineFrame.TEXT_DIM
                            : chosen ? MachineFrame.GOOD : MachineFrame.TEXT);
            MachineFrame.value(graphics, font(),
                    String.format(Locale.ROOT, "%.4f", metre.resistance(
                            ConductorMaterial.REFERENCE_K)),
                    left + 128, y, TEXT_WIDTH, MachineFrame.TEXT);
            MachineFrame.value(graphics, font(), String.format(Locale.ROOT, "%.0f / %.0f",
                            Ampacity.limitAmps(metre, AMBIENT_K, 0.0),
                            Ampacity.limitAmps(metre, AMBIENT_K, 1.0)),
                    left + 186, y, TEXT_WIDTH, MachineFrame.TEXT);

            ConductorMaterial picked = material;
            rows.add(new Row(left + 8, y - 3, WIDTH - 16, ROW_HEIGHT - 2,
                    available ? () -> apply(new WireCoil(picked, coil.colour(), coil.mode(), coil.gauge()))
                            : () -> { },
                    tipFor(material, metre, available)));
            y += ROW_HEIGHT;
        }
    }

    private List<Component> tipFor(ConductorMaterial material, Conductor metre, boolean available) {
        if (!available) {
            return List.of(
                    Component.literal(material.name().toLowerCase(Locale.ROOT))
                            .withStyle(ChatFormatting.GRAY),
                    Component.literal("No way to draw this yet - its tier has not arrived")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal(String.format(Locale.ROOT, "would be %.4f ohm per block",
                            metre.resistance(ConductorMaterial.REFERENCE_K)))
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(
                Component.literal(material.name().toLowerCase(Locale.ROOT) + "  "
                        + material.formula()).withStyle(ChatFormatting.WHITE),
                Component.literal(String.format(Locale.ROOT, "resistivity %.2e ohm.m",
                        material.resistivity20C())).withStyle(ChatFormatting.GRAY),
                Component.literal(String.format(Locale.ROOT, "melts at %.0f K",
                        material.meltingPointK())).withStyle(ChatFormatting.GRAY),
                Component.literal("Vacuum carries less: no air to take the heat away")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    private void drawColours(GuiGraphicsExtractor graphics, int left, int top) {
        int y = top + LIST_Y + ConductorMaterial.values().length * ROW_HEIGHT + 8;
        MachineFrame.label(graphics, font(), "INSULATION", left + 14, y, TEXT_WIDTH);
        DyeColor[] colours = DyeColor.values();
        for (int i = 0; i < colours.length; i++) {
            DyeColor colour = colours[i];
            int x = left + 14 + (i % SWATCHES_PER_ROW) * (SWATCH + SWATCH_GAP);
            int swatchY = y + 11 + (i / SWATCHES_PER_ROW) * (SWATCH + SWATCH_GAP);
            int rgb = 0xFF000000 | colour.getTextureDiffuseColor() & 0xFFFFFF;
            graphics.fill(x, swatchY, x + SWATCH, swatchY + SWATCH, rgb);
            if (colour == coil.colour()) {
                MachineFrame.outline(graphics, x - 1, swatchY - 1, SWATCH + 2, SWATCH + 2, 0xFFFFFFFF);
            }
            DyeColor picked = colour;
            rows.add(new Row(x, swatchY, SWATCH, SWATCH,
                    () -> apply(coil.withColour(picked)),
                    List.of(Component.literal(picked.getSerializedName()),
                            Component.literal("Two colours never join - this is how runs cross")
                                    .withStyle(ChatFormatting.GRAY))));
        }
    }

    private void drawGauges(GuiGraphicsExtractor graphics, int left, int top) {
        int y = top + HEIGHT - 48;
        MachineFrame.label(graphics, font(), "GAUGE", left + 14, y - 10, TEXT_WIDTH);
        int buttonWidth = (WIDTH - 34) / WireGauge.values().length;
        int x = left + 14;
        for (WireGauge gauge : WireGauge.values()) {
            boolean chosen = coil.gauge() == gauge;
            MachineFrame.knob(graphics, x, y, buttonWidth, 16);
            if (chosen) {
                MachineFrame.outline(graphics, x, y, buttonWidth, 16, MachineFrame.GOOD);
            }
            Schematic.caption(graphics, font(),
                    String.format(Locale.ROOT, "%.1f", gauge.squareMillimetres()),
                    x + buttonWidth / 2, y + 4, chosen ? MachineFrame.GOOD : MachineFrame.TEXT_DIM);
            rows.add(new Row(x, y, buttonWidth, 16,
                    () -> apply(new WireCoil(coil.material(), coil.colour(), coil.mode(), gauge)),
                    gaugeTip(gauge)));
            x += buttonWidth;
        }
    }

    private List<Component> gaugeTip(WireGauge gauge) {
        Conductor metre = gauge.metre(coil.material());
        double perBlock = metre.resistance(ConductorMaterial.REFERENCE_K);
        double inVacuum = Ampacity.limitAmps(metre, AMBIENT_K, 0.0);
        double inAir = Ampacity.limitAmps(metre, AMBIENT_K, 1.0);
        return List.of(
                Component.literal(gauge.id() + "  " + String.format(Locale.ROOT, "%.1f mm2",
                        gauge.squareMillimetres())).withStyle(ChatFormatting.WHITE),
                Component.literal(String.format(Locale.ROOT, "%.4f ohm per block", perBlock))
                        .withStyle(ChatFormatting.GRAY),
                Component.literal(String.format(Locale.ROOT, "%.1f A outside, %.1f A in air",
                        inVacuum, inAir)).withStyle(ChatFormatting.GRAY),
                Component.literal(String.format(Locale.ROOT, "%.1f m per item of stock",
                        gauge.pixelsPerItem() / 16.0)).withStyle(ChatFormatting.GRAY));
    }

    private void drawModes(GuiGraphicsExtractor graphics, int left, int top) {
        int y = top + HEIGHT - 26;
        int buttonWidth = (WIDTH - 34) / 2;
        drawMode(graphics, left + 14, y, buttonWidth, WireRouter.Mode.PATHFIND, "ROUTE AROUND",
                "Finds a way past whatever is in the way.");
        drawMode(graphics, left + 20 + buttonWidth, y, buttonWidth, WireRouter.Mode.STRAIGHT,
                "STRAIGHT ONLY", "Lays straight legs and refuses rather than detour, so you decide.");
    }

    private void drawMode(GuiGraphicsExtractor graphics, int x, int y, int buttonWidth,
                          WireRouter.Mode mode, String label, String what) {
        MachineFrame.knob(graphics, x, y, buttonWidth, 16);
        boolean chosen = coil.mode() == mode;
        if (chosen) {
            MachineFrame.outline(graphics, x, y, buttonWidth, 16, MachineFrame.GOOD);
        }
        Schematic.caption(graphics, font(), label, x + buttonWidth / 2, y + 4,
                chosen ? MachineFrame.GOOD : MachineFrame.TEXT_DIM);
        rows.add(new Row(x, y, buttonWidth, 16, () -> apply(coil.withMode(mode)),
                List.of(Component.literal(label), Component.literal(what)
                        .withStyle(ChatFormatting.GRAY))));
    }

    /**
     * The panel never edits the stack itself.
     *
     * <p>It asks, and the server decides — so a change survives only if the player really is
     * holding a coil. The local copy updates too, because a control that waited a round trip to
     * show what you just picked feels broken even when it is correct.
     */
    private void apply(WireCoil next) {
        coil = next;
        ClientPacketDistributor.sendToServer(WireCoilSettingPayload.of(next));
    }

    @LDLRegisterClient(name = "wire_coil_body", registry = "ldlib2:ui_element_renderer")
    public static final class WireCoilBodyRenderer
            extends DelegatingUIElementRenderer<WireCoilBody, WireCoilBodyRenderer> {
        @Override
        public Class<WireCoilBody> type() {
            return WireCoilBody.class;
        }

        @Override
        public void drawBackgroundAdditional(WireCoilBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
