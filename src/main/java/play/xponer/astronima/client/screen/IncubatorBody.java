package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.client.hud.IncubatorLayout;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.item.PetriDishItem;
import play.xponer.astronima.menu.IncubatorUiHolder;
import play.xponer.astronima.network.MachineSettingPayload;
import play.xponer.astronima.sim.lab.Culture;
import play.xponer.astronima.sim.lab.PlateMotion;

import java.util.Locale;

/**
 * The incubator's own picture and dial, ported from the old {@code IncubatorScreen} verbatim —
 * the {@code MachineBody} pattern again. One feed slot, drawn by {@link IncubatorUi} the same
 * way {@code ProcessingUi}'s slots are; this element covers everything else: the door window,
 * the thermostat, and the drag.
 */
public final class IncubatorBody extends UIElement {

    static final int WIDTH = IncubatorLayout.WIDTH;
    static final int HEIGHT = IncubatorLayout.HEIGHT;

    private static final int TEXT_WIDTH = 150;

    private static final int GLASS = 0xFF1B2228;
    private static final int AGAR = 0xFFB89A5E;
    private static final int AGAR_DARK = 0xFF8C7444;
    private static final int COLONY = 0xFFEDE7D6;
    private static final int DIM = 0xFF7E8894;
    private static final int HEAD = 0xFFF2F6FA;
    private static final int ACCENT = 0xFF6FB0EE;
    private static final int WARM = 0xFFD9A24C;
    private static final int BAD = 0xFFD24438;

    private static final int WINDOW_X = IncubatorLayout.WINDOW_X;
    private static final int WINDOW_Y = IncubatorLayout.WINDOW_Y;
    private static final int WINDOW = IncubatorLayout.WINDOW;
    static final int DIAL_X = IncubatorLayout.DIAL_X;
    static final int DIAL_Y = IncubatorLayout.DIAL_Y;
    static final int DIAL_W = IncubatorLayout.DIAL_W;

    private final IncubatorUiHolder holder;
    private boolean dragging;

    public IncubatorBody(IncubatorUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        // Before slots, never after: the panel is opaque, and drawn afterwards it paints
        // straight over every slot and every item in them. Reported as "the inventory just
        // does not open" - it was open, and underneath. ItemSlot children of IncubatorUi are
        // separate elements added after this one, so document order already gives the same
        // ordering the old screen got by calling this first.
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), holder.getDisplayName().getString().toUpperCase(Locale.ROOT),
                leftPos + 8, topPos + 7, WIDTH - 16, MachineFrame.TEXT);
        MachineFrame.divider(graphics, leftPos + 7, topPos + IncubatorLayout.DIVIDER_Y, WIDTH - 14);
        MachineFrame.feedSlot(graphics, leftPos + IncubatorLayout.SLOT_X,
                topPos + IncubatorLayout.SLOT_Y);
        drawWindow(graphics, leftPos, topPos);
        drawDial(graphics, leftPos, topPos);
    }

    /** The door: a dark window with the plate behind it, warm-lit when it is at temperature. */
    private void drawWindow(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int x = leftPos + WINDOW_X;
        int y = topPos + WINDOW_Y;
        double kelvin = holder.setPointK();
        boolean growing = kelvin >= Culture.COLD_K && kelvin < Culture.LETHAL_K;
        graphics.fill(x - 1, y - 1, x + WINDOW + 1, y + WINDOW + 1,
                growing ? WARM : 0xFF2A323A);
        graphics.fill(x, y, x + WINDOW, y + WINDOW, GLASS);

        Culture culture = PetriDishItem.cultureOf(holder.dish());
        int cx = x + WINDOW / 2;
        int cy = y + WINDOW / 2;
        if (holder.dish().isEmpty()) {
            graphics.centeredText(font(), Component.translatable("astronima.incubator.empty"),
                    cx, cy - 4, DIM);
            return;
        }
        int radius = WINDOW / 2 - 6;
        disc(graphics, cx, cy, radius, AGAR_DARK);
        disc(graphics, cx, cy, radius - 1, AGAR);
        if (culture.isBlank()) {
            graphics.centeredText(font(), Component.literal("sterile"), cx, cy - 4, 0xFF6A6250);
            return;
        }
        if (culture.isConfluent()) {
            disc(graphics, cx, cy, (int) (radius * 0.86), COLONY);
            return;
        }
        int most = 34;
        int up = PlateMotion.showing(culture.grownHours(), most);
        int seed = culture.organism().hashCode();
        for (int i = 0; i < up; i++) {
            double angle = (seed * 31L + i * 137L) % 360 * Math.PI / 180.0;
            double away = 0.25 + ((seed / 7 + i * 53) % 100) / 140.0;
            int dotX = cx + (int) (Math.cos(angle) * radius * away);
            int dotY = cy + (int) (Math.sin(angle) * radius * away);
            int dot = i * 4 / most == 3 ? 2 : 1;
            graphics.fill(dotX, dotY, dotX + dot + 1, dotY + dot + 1, COLONY);
        }
        graphics.centeredText(font(), Component.literal(String.format(Locale.ROOT, "%.0f / %.0f h",
                        culture.grownHours(), Culture.READY_HOURS)),
                cx, y + WINDOW - 10, culture.hasColonies() ? ACCENT : DIM);
    }

    /**
     * The thermostat, with the band that works marked on the track.
     *
     * <p>Both ends of the travel are mistakes you are allowed to make, and the marks are what turn
     * them from traps into decisions.
     */
    private void drawDial(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int x = leftPos + DIAL_X;
        int y = topPos + DIAL_Y;
        double kelvin = holder.setPointK();
        double span = play.xponer.astronima.block.entity.IncubatorBlockEntity.HOTTEST_K
                - play.xponer.astronima.block.entity.IncubatorBlockEntity.COLDEST_K;
        double at = (kelvin - play.xponer.astronima.block.entity.IncubatorBlockEntity.COLDEST_K)
                / span;

        graphics.fill(x, y, x + DIAL_W, y + 6, 0x40000000);
        int from = x + (int) (DIAL_W * (Culture.COLD_K
                - play.xponer.astronima.block.entity.IncubatorBlockEntity.COLDEST_K) / span);
        int to = x + (int) (DIAL_W * (Culture.LETHAL_K
                - play.xponer.astronima.block.entity.IncubatorBlockEntity.COLDEST_K) / span);
        graphics.fill(from, y, to, y + 6, 0x3038B060);
        int ideal = x + (int) (DIAL_W * (Culture.IDEAL_K
                - play.xponer.astronima.block.entity.IncubatorBlockEntity.COLDEST_K) / span);
        graphics.fill(ideal, y - 2, ideal + 1, y + 8, HEAD);
        graphics.fill(to, y - 2, to + 1, y + 8, BAD);

        int knob = x + (int) (DIAL_W * at);
        boolean cooking = kelvin >= Culture.LETHAL_K;
        int colour = cooking ? BAD : kelvin < Culture.COLD_K ? DIM : ACCENT;
        if (cooking) {
            int alpha = 0x80 + (int) (0x7F
                    * PlateMotion.pulse(System.currentTimeMillis() / 1000.0));
            colour = (alpha << 24) | (colour & 0xFFFFFF);
        }
        graphics.fill(knob - 2, y - 3, knob + 3, y + 9, colour);

        MachineFrame.value(graphics, font(), Component.literal(String.format(Locale.ROOT, "%.0f C",
                kelvin - 273.15)), x, y + 12, TEXT_WIDTH, cooking ? BAD : MachineFrame.TEXT);
        String note = cooking ? Component.translatable("astronima.incubator.cooking").getString()
                : kelvin < Culture.COLD_K
                        ? Component.translatable("astronima.incubator.cold").getString()
                        : Component.translatable("astronima.incubator.growing").getString();
        MachineFrame.value(graphics, font(), Component.literal(note), x + 34, y + 12, TEXT_WIDTH, DIM);
    }

    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                             int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    // ---- dial drag, called by AirlockUi-style overlay in IncubatorUi ------------

    void onDialMouseDown(double mouseX) {
        dragging = true;
        setFromMouse(mouseX);
    }

    void onDialDrag(double mouseX) {
        if (dragging) {
            setFromMouse(mouseX);
        }
    }

    void onDialReleased() {
        dragging = false;
    }

    /** Drag the knob: the server owns the number, so this asks rather than sets. */
    private void setFromMouse(double mouseX) {
        int leftPos = (int) getContentX();
        double part = Math.clamp((mouseX - leftPos - DIAL_X) / DIAL_W, 0, 1);
        ClientPacketDistributor.sendToServer(
                new MachineSettingPayload(holder.pos(), (float) part));
    }

    @LDLRegisterClient(name = "incubator_body", registry = "ldlib2:ui_element_renderer")
    public static final class IncubatorBodyRenderer
            extends DelegatingUIElementRenderer<IncubatorBody, IncubatorBodyRenderer> {
        @Override
        public Class<IncubatorBody> type() {
            return IncubatorBody.class;
        }

        @Override
        public void drawBackgroundAdditional(IncubatorBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
