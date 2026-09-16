package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
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
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.MicroscopeLayout;
import play.xponer.astronima.item.PetriDishItem;
import play.xponer.astronima.menu.MicroscopeUiHolder;
import play.xponer.astronima.network.FocusPayload;
import play.xponer.astronima.sim.lab.Culture;
import play.xponer.astronima.sim.lab.Focus;
import play.xponer.astronima.sim.lab.Organism;

import java.util.Locale;

/**
 * The microscope's own picture and controls, ported from the old {@code MicroscopeScreen}
 * verbatim — the {@code MachineBody} pattern again. The fine-focus knob is instance state on
 * this element, exactly as it was a field on the old screen: the server never tracks it, so
 * there is nothing to read from a holder for it at all.
 *
 * <p>One deliberate, named change from the old screen (rule 8): {@code mouseScrolled} used to
 * answer a scroll wheel <em>anywhere in the game window</em>, because that is the shape
 * {@code Screen}'s own scroll hook has — its bounds are the whole window, not the panel. Here
 * the wheel is wired to this element specifically, so it turns the focus while the pointer is
 * over the microscope's own picture, not from anywhere on screen. A real microscope does not
 * answer a hand nowhere near it either.
 */
public final class MicroscopeBody extends UIElement {

    static final int WIDTH = MicroscopeLayout.WIDTH;
    static final int HEIGHT = MicroscopeLayout.HEIGHT;

    private static final int TEXT_WIDTH = 150;

    static final int FIELD_X = MicroscopeLayout.FIELD_X;
    static final int FIELD_Y = MicroscopeLayout.FIELD_Y;
    static final int FIELD = MicroscopeLayout.FIELD;
    static final int KNOB_X = MicroscopeLayout.KNOB_X;
    static final int KNOB_Y = MicroscopeLayout.KNOB_Y;
    static final int KNOB_H = MicroscopeLayout.KNOB_H;

    private static final int BEZEL = 0xFF2A323A;
    private static final int STAIN_POSITIVE = 0xFF7B4FA8;
    private static final int STAIN_NEGATIVE = 0xFFD05A7A;
    private static final int DIM = 0xFF7E8894;
    private static final int HEAD = 0xFFF2F6FA;
    private static final int ACCENT = 0xFF6FB0EE;

    private final MicroscopeUiHolder holder;
    private double knob = 0.12;

    public MicroscopeBody(MicroscopeUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
        addEventListener(UIEvents.MOUSE_WHEEL, this::onWheel);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), holder.getDisplayName().getString().toUpperCase(Locale.ROOT),
                leftPos + 8, topPos + 7, WIDTH - 16, MachineFrame.TEXT);
        MachineFrame.divider(graphics, leftPos + 7, topPos + 104, WIDTH - 14);
        MachineFrame.feedSlot(graphics, leftPos + 8, topPos + 92);
        drawField(graphics, leftPos, topPos);
        drawKnob(graphics, leftPos, topPos);
        drawReading(graphics, leftPos, topPos);
    }

    /** The field of view: round, lit, and only honest at one knob position. */
    private void drawField(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int cx = leftPos + FIELD_X + FIELD / 2;
        int cy = topPos + FIELD_Y + FIELD / 2;
        int radius = FIELD / 2;
        disc(graphics, cx, cy, radius + 1, BEZEL);

        Culture culture = PetriDishItem.cultureOf(holder.dish());
        Organism organism = culture.growing();
        double sharp = Focus.sharpness(knob);
        int light = (int) (0x30 + 0xA0 * Focus.contrast(knob));
        disc(graphics, cx, cy, radius, (light << 24) | 0x00E9EEF2);

        if (organism == null || !culture.hasColonies()) {
            graphics.centeredText(font(), Component.translatable(
                            holder.dish().isEmpty() ? "astronima.microscope.empty"
                                    : "astronima.microscope.nothing"), cx, cy - 4, DIM);
            return;
        }
        drawCells(graphics, cx, cy, radius, organism, sharp);
    }

    /**
     * The organisms, arranged the way they actually arrange.
     *
     * <p>Clusters bunch, chains run in a line, pairs sit in twos. That arrangement <em>is</em> half
     * the identification, so it has to be drawn honestly — and the scatter from being out of focus
     * is what makes a chain look like a clump.
     */
    private void drawCells(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                           Organism organism, double sharp) {
        int cell = switch (organism.gram()) {
            case POSITIVE -> STAIN_POSITIVE;
            case NEGATIVE -> STAIN_NEGATIVE;
        };
        Culture culture = PetriDishItem.cultureOf(holder.dish());
        if (culture.gram() == null) {
            cell = 0xFF9AA4AE;                 // unstained: barely there, which is the point
        }
        double wander = Focus.scatter(knob);
        int size = organism.shape() == Organism.Shape.BACILLUS ? 5 : 3;
        int seed = organism.name().hashCode();

        int groups = 7;
        for (int g = 0; g < groups; g++) {
            double angle = (seed * 17L + g * 211L) % 360 * Math.PI / 180.0;
            double away = 0.15 + ((seed / 3 + g * 71) % 100) / 160.0;
            int gx = cx + (int) (Math.cos(angle) * radius * away);
            int gy = cy + (int) (Math.sin(angle) * radius * away);
            int howMany = switch (organism.arrangement()) {
                case SINGLE -> 1;
                case PAIRS -> 2;
                case CHAINS -> 5;
                case CLUSTERS -> 6;
            };
            for (int i = 0; i < howMany; i++) {
                int dx;
                int dy;
                switch (organism.arrangement()) {
                    case CHAINS -> {
                        dx = (int) (Math.cos(angle) * i * (size + 1));
                        dy = (int) (Math.sin(angle) * i * (size + 1));
                    }
                    case CLUSTERS -> {
                        dx = ((seed + i * 37) % 5 - 2) * (size - 1);
                        dy = ((seed + i * 53) % 5 - 2) * (size - 1);
                    }
                    case PAIRS -> {
                        dx = i * (size + 1);
                        dy = 0;
                    }
                    default -> {
                        dx = 0;
                        dy = 0;
                    }
                }
                double drift = wander * (((seed + g * 13 + i * 29) % 100) / 50.0 - 1);
                int x = gx + dx + (int) (drift * size);
                int y = gy + dy + (int) (drift * size * 0.7);
                int wide = size + (int) Math.round(wander * 1.5);
                int alpha = (int) (0x50 + 0xAF * sharp);
                graphics.fill(x, y, x + wide, y + wide, (alpha << 24) | (cell & 0xFFFFFF));
            }
        }
    }

    /** The fine focus, as a vertical travel with the plane unmarked — finding it is the job. */
    private void drawKnob(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int x = leftPos + KNOB_X;
        int y = topPos + KNOB_Y;
        graphics.fill(x, y, x + 8, y + KNOB_H, 0x40000000);
        for (int tick = 0; tick <= 8; tick++) {
            int at = y + KNOB_H * tick / 8;
            graphics.fill(x - 2, at, x, at + 1, BEZEL);
        }
        int at = y + (int) (KNOB_H * knob);
        boolean sharp = Focus.isReadable(knob);
        graphics.fill(x - 3, at - 2, x + 11, at + 3, sharp ? ACCENT : 0xFF6E7883);
        MachineFrame.value(graphics, font(), Component.literal(String.format(Locale.ROOT, "%.0f%%",
                Focus.sharpness(knob) * 100)), x - 4, y + KNOB_H + 4, TEXT_WIDTH, sharp ? ACCENT : DIM);
    }

    private void drawReading(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int x = leftPos + 8;
        int y = topPos + 20;
        Culture culture = PetriDishItem.cultureOf(holder.dish());
        if (culture.shape() != null) {
            MachineFrame.value(graphics, font(), Component.literal("recorded:"), x, y, TEXT_WIDTH, DIM);
            MachineFrame.value(graphics, font(), Component.literal(
                    culture.shape().name().toLowerCase(Locale.ROOT)), x, y + 10, TEXT_WIDTH, HEAD);
            MachineFrame.value(graphics, font(), Component.literal(
                    culture.arrangement().name().toLowerCase(Locale.ROOT)), x, y + 20, TEXT_WIDTH, HEAD);
            return;
        }
        MachineFrame.value(graphics, font(), Component.translatable(
                Focus.isReadable(knob) ? "astronima.microscope.record"
                        : "astronima.microscope.turn"), x, y, TEXT_WIDTH, Focus.isReadable(knob) ? ACCENT : DIM);
    }

    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                             int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    private void onWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        knob = Math.clamp(knob - event.deltaY * 0.012, 0, 1);
    }

    // ---- knob drag, called by MicroscopeUi's overlay ----------------------------

    void onKnobMouseDown(double mouseY) {
        setKnob(mouseY);
    }

    void onKnobDrag(double mouseY) {
        setKnob(mouseY);
    }

    private void setKnob(double mouseY) {
        int topPos = (int) getContentY();
        knob = Math.clamp((mouseY - topPos - KNOB_Y) / KNOB_H, 0, 1);
    }

    /** The field's own click: commits the reading, only while it is actually in focus. The
     *  server decides whether it was sharp enough to write down; the knob position just rides
     *  along on the packet. */
    void onFieldClicked() {
        if (Focus.isReadable(knob)) {
            ClientPacketDistributor.sendToServer(new FocusPayload(holder.pos(), (float) knob));
        }
    }

    @LDLRegisterClient(name = "microscope_body", registry = "ldlib2:ui_element_renderer")
    public static final class MicroscopeBodyRenderer
            extends DelegatingUIElementRenderer<MicroscopeBody, MicroscopeBodyRenderer> {
        @Override
        public Class<MicroscopeBody> type() {
            return MicroscopeBody.class;
        }

        @Override
        public void drawBackgroundAdditional(MicroscopeBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
