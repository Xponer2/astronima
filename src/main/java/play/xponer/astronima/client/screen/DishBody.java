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
import play.xponer.astronima.client.hud.DishLayout;
import play.xponer.astronima.client.hud.Layout;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.sim.lab.Antibiotic;
import play.xponer.astronima.sim.lab.Culture;
import play.xponer.astronima.sim.lab.DiscDiffusion;
import play.xponer.astronima.sim.lab.GramStain;
import play.xponer.astronima.sim.lab.IdentificationKey;
import play.xponer.astronima.sim.lab.Isolates;
import play.xponer.astronima.sim.lab.Organism;
import play.xponer.astronima.sim.lab.PlateMotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The hand bench's own picture and buttons, ported from the old {@code DishScreen} verbatim —
 * {@link BiomonitorBody}'s "screen with no menu" shape. The one thing biomonitor never needed:
 * a held button. Decolorizing is press-and-hold, so this listens for both {@code MOUSE_DOWN} (arm
 * the hold, or fire an ordinary click) and {@code MOUSE_UP} (commit the pour) rather than only the
 * single {@code CLICK} event — the same two-event split the old screen's own
 * {@code mouseClicked}/{@code mouseReleased} pair was.
 */
public final class DishBody extends UIElement {

    static final int WIDTH = DishLayout.WIDTH;
    static final int HEIGHT = DishLayout.HEIGHT;
    private static final int TEXT_WIDTH = 150;

    private static final int AGAR = 0xFFB89A5E;
    private static final int AGAR_DARK = 0xFF8C7444;
    private static final int GLASS = 0xFFBCCED6;
    private static final int COLONY = 0xFFEDE7D6;
    private static final int TEXT = 0xFFC9D2DA;
    private static final int DIM = 0xFF7E8894;
    private static final int HEAD = 0xFFF2F6FA;
    private static final int ACCENT = 0xFF6FB0EE;
    private static final int WARN = 0xFFD9A24C;
    private static final int BAD = 0xFFD24438;
    private static final int CLEAR = 0xFF11161B;

    private enum View { PLATE, DISCS }

    private final Consumer<Culture> save;
    private Culture culture;
    private View view = View.PLATE;
    private long pouringSince;
    private long viewChangedAt = System.currentTimeMillis();
    private double lastGrown = -1;
    private long grewAt;
    private String rulerReading = "";
    private final List<Button> buttons = new ArrayList<>();

    private record Button(String label, Layout.Box box, Runnable action, boolean held,
                          boolean enabled) { }

    public DishBody(Culture culture, Consumer<Culture> save) {
        this.culture = culture;
        this.save = save;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) {
                return;
            }
            for (Button button : buttons) {
                if (!button.enabled() || !hovering(button.box(), event.x, event.y)) {
                    continue;
                }
                if (button.held()) {
                    pouringSince = System.currentTimeMillis();
                } else {
                    button.action().run();
                }
                return;
            }
        });
        addEventListener(UIEvents.MOUSE_UP, event -> {
            if (pouringSince == 0) {
                return;
            }
            double seconds = (System.currentTimeMillis() - pouringSince) / 1000.0;
            pouringSince = 0;
            Organism organism = culture.growing();
            if (organism != null) {
                apply(culture.stained(GramStain.run(organism, GramStain.properOrder(), seconds)));
            }
        });
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void noticeGrowth() {
        if (culture.grownHours() != lastGrown) {
            lastGrown = culture.grownHours();
            grewAt = System.currentTimeMillis();
        }
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        noticeGrowth();
        MachineFrame.panel(graphics, left, top, WIDTH, HEIGHT);

        var box = DishLayout.plate();
        drawPlate(graphics, left + box.x(), top + box.y(), box.width(), mouseX, mouseY);
        drawReadout(graphics, left + DishLayout.readout().x(), top + DishLayout.readout().y(),
                DishLayout.readout().width());
        drawTools(graphics, left, top, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ the plate

    private void drawPlate(GuiGraphicsExtractor graphics, int x, int y, int size,
                           int mouseX, int mouseY) {
        int radius = size / 2 - 2;
        int cx = x + size / 2;
        int cy = y + size / 2;
        disc(graphics, cx, cy, radius + 2, GLASS);
        disc(graphics, cx, cy, radius, AGAR_DARK);
        disc(graphics, cx, cy, radius - 2, AGAR);

        if (culture.isBlank()) {
            graphics.centeredText(font(), Component.literal("sterile"), cx, cy - 4, DIM);
            return;
        }
        if (view == View.DISCS && culture.hasColonies()) {
            drawDiscs(graphics, cx, cy, radius, mouseX, mouseY);
            return;
        }
        double grown = Math.clamp(culture.grownHours() / Culture.READY_HOURS, 0, 1);
        if (grown <= 0.02) {
            graphics.centeredText(font(), Component.literal("streaked"), cx, cy - 4, DIM);
            return;
        }
        if (culture.isConfluent()) {
            disc(graphics, cx, cy, (int) (radius * 0.86), COLONY);
            graphics.centeredText(font(), Component.literal("confluent"), cx, cy - 4, 0xFF6A6250);
            return;
        }
        int seed = culture.organism().hashCode();
        int most = 46;
        int up = PlateMotion.showing(culture.grownHours(), most);
        double since = (System.currentTimeMillis() - grewAt) / 1000.0;
        for (int i = 0; i < up; i++) {
            double angle = (seed * 31L + i * 137L) % 360 * Math.PI / 180.0;
            double away = 0.25 + ((seed / 7 + i * 53) % 100) / 140.0;
            int quadrant = i * 4 / most;
            int dotX = cx + (int) (Math.cos(angle) * radius * away);
            int dotY = cy + (int) (Math.sin(angle) * radius * away);
            double own = PlateMotion.emergence(since - (up - 1 - i) * 0.05);
            int full = quadrant == 3 ? 3 : 2;
            int dot = (int) Math.round(full * own);
            if (dot <= 0) {
                continue;
            }
            graphics.fill(dotX, dotY, dotX + dot, dotY + dot, COLONY);
        }
        if (!culture.hasColonies()) {
            graphics.centeredText(font(), Component.literal(
                            String.format(Locale.ROOT, "%.0f / %.0f h", culture.grownHours(),
                                    Culture.READY_HOURS)),
                    cx, y + size - 12, DIM);
        }
    }

    private void drawDiscs(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                           int mouseX, int mouseY) {
        Organism organism = culture.growing();
        if (organism == null) {
            return;
        }
        rulerReading = "";
        double perMm = radius / 45.0;
        double swept = PlateMotion.sweep((System.currentTimeMillis() - viewChangedAt) / 1000.0);
        List<Antibiotic> drugs = Antibiotic.all();
        for (int i = 0; i < drugs.size(); i++) {
            double angle = Math.PI * 2 * i / drugs.size() - Math.PI / 2;
            int dx = cx + (int) (Math.cos(angle) * radius * 0.44);
            int dy = cy + (int) (Math.sin(angle) * radius * 0.44);
            double zone = DiscDiffusion.zoneMm(organism, drugs.get(i));
            int ring = (int) Math.round(zone * perMm / 2 * swept);
            Antibiotic.Call call = drugs.get(i).read(zone);
            disc(graphics, dx, dy, ring, CLEAR);
            ringEdge(graphics, dx, dy, ring, callColour(call));
            disc(graphics, dx, dy, (int) (DiscDiffusion.DISC_DIAMETER_MM * perMm / 2), 0xFFE8E4D8);

            boolean over = Math.hypot(mouseX - dx, mouseY - dy) <= Math.max(ring, 6);
            if (over && swept >= 1) {
                graphics.fill(dx - ring, dy, dx + ring + 1, dy + 1, HEAD);
                graphics.fill(dx - ring, dy - 2, dx - ring + 1, dy + 3, HEAD);
                graphics.fill(dx + ring, dy - 2, dx + ring + 1, dy + 3, HEAD);
                rulerReading = String.format(Locale.ROOT, "%s  %.0f mm  %s",
                        drugs.get(i).name(), zone, call);
            }
            MachineFrame.value(graphics, font(), Component.literal(String.format(Locale.ROOT, "%.0f", zone)),
                    dx - 5, dy - 3, TEXT_WIDTH, over ? HEAD : ACCENT);
        }
    }

    private static int callColour(Antibiotic.Call call) {
        return switch (call) {
            case SUSCEPTIBLE -> ACCENT;
            case INTERMEDIATE -> WARN;
            case RESISTANT -> BAD;
        };
    }

    private static void ringEdge(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int colour) {
        if (radius <= 0) {
            return;
        }
        for (int step = 0; step < 60; step++) {
            double angle = Math.PI * 2 * step / 60;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    // ------------------------------------------------------------------ the readout

    private void drawReadout(GuiGraphicsExtractor graphics, int x, int y, int room) {
        MachineFrame.value(graphics, font(), Component.translatable("astronima.dish.title"), x, y, TEXT_WIDTH, HEAD);
        int row = y + 14;
        for (String line : culture.label()) {
            MachineFrame.value(graphics, font(), Component.literal(font().plainSubstrByWidth(line, room)),
                    x, row, TEXT_WIDTH, TEXT);
            row += 10;
        }
        if (culture.isBlank()) {
            return;
        }
        row += 4;
        IdentificationKey key = culture.key();
        if (!key.isSelfConsistent()) {
            MachineFrame.value(graphics, font(), Component.literal("these cannot"), x, row, TEXT_WIDTH, BAD);
            MachineFrame.value(graphics, font(), Component.literal("all be true"), x, row + 10, TEXT_WIDTH, BAD);
            MachineFrame.value(graphics, font(), Component.literal("re-stain it"), x, row + 22, TEXT_WIDTH, WARN);
            return;
        }
        List<Organism> left = key.candidates(Isolates.all());
        if (left.size() == 1) {
            MachineFrame.value(graphics, font(), Component.literal("IDENTIFIED"), x, row, TEXT_WIDTH, ACCENT);
            MachineFrame.value(graphics, font(), Component.literal(left.get(0).name()), x, row + 11, TEXT_WIDTH, HEAD);
            return;
        }
        MachineFrame.value(graphics, font(), Component.literal(left.size() + " candidates"), x, row, TEXT_WIDTH, WARN);
        row += 11;
        for (String next : key.outstanding()) {
            MachineFrame.value(graphics, font(), Component.literal("- " + next), x, row, TEXT_WIDTH, DIM);
            row += 10;
        }
    }

    // ------------------------------------------------------------------ the tools

    private void drawTools(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        buttons.clear();
        Layout.Box strip = DishLayout.tools();
        int x = left + strip.x();
        int y = top + strip.y();
        graphics.fill(x, y - 4, x + strip.width(), y - 3, 0xFF2A323A);

        boolean pickable = culture.hasColonies() && culture.growing() != null;
        add(left, top, "stain", x, y, 54, true, pickable && culture.gram() == null, () -> { });
        add(left, top, "scope", x + 58, y, 44, false, pickable && culture.shape() == null, () -> {
            Organism organism = culture.growing();
            apply(culture.seen(organism.shape(), organism.arrangement()));
        });
        add(left, top, "peroxide", x + 106, y, 56, false, pickable && culture.catalase() == null,
                () -> apply(culture.tested(true, culture.growing().catalase())));
        add(left, top, "oxidase", x + 166, y, 52, false, pickable && culture.oxidase() == null,
                () -> apply(culture.tested(false, culture.growing().oxidase())));
        add(left, top, view == View.PLATE ? "discs" : "plate", x + 222, y, 46, false, pickable,
                () -> {
                    view = view == View.PLATE ? View.DISCS : View.PLATE;
                    viewChangedAt = System.currentTimeMillis();
                });

        for (Button button : buttons) {
            boolean over = button.enabled() && hovering(button.box(), left, top, mouseX, mouseY);
            int face = !button.enabled() ? 0x18FFFFFF : over ? 0x40FFFFFF : 0x28FFFFFF;
            graphics.fill(left + button.box().x(), top + button.box().y(),
                    left + button.box().right(), top + button.box().bottom(), face);
            graphics.centeredText(font(), Component.literal(button.label()),
                    left + button.box().x() + button.box().width() / 2,
                    top + button.box().y() + 5,
                    button.enabled() ? (over ? HEAD : TEXT) : DIM);
        }
        drawPourBar(graphics, x, y + 20, strip.width());
        if (!rulerReading.isEmpty()) {
            MachineFrame.value(graphics, font(), Component.literal(rulerReading), x, y + 20, TEXT_WIDTH, HEAD);
        }
    }

    private void drawPourBar(GuiGraphicsExtractor graphics, int x, int y, int room) {
        if (pouringSince == 0) {
            MachineFrame.value(graphics, font(), Component.translatable("astronima.dish.hold"), x, y + 2, TEXT_WIDTH, DIM);
            return;
        }
        double seconds = (System.currentTimeMillis() - pouringSince) / 1000.0;
        double full = GramStain.SAFE_DECOLORIZE_SECONDS * 2.5;
        int filled = (int) (room * Math.clamp(seconds / full, 0, 1));
        boolean tooLong = seconds > GramStain.SAFE_DECOLORIZE_SECONDS;
        boolean tooShort = seconds < GramStain.MINIMUM_DECOLORIZE_SECONDS;
        graphics.fill(x, y, x + room, y + 9, 0x40000000);
        int colour = tooLong ? BAD : tooShort ? WARN : ACCENT;
        if (tooLong) {
            int alpha = 0x60 + (int) (0x9F * PlateMotion.pulse(seconds));
            colour = (alpha << 24) | (colour & 0xFFFFFF);
        }
        graphics.fill(x, y, x + filled, y + 9, colour);

        int from = (int) (room * GramStain.MINIMUM_DECOLORIZE_SECONDS / full);
        int to = (int) (room * GramStain.SAFE_DECOLORIZE_SECONDS / full);
        graphics.fill(x + from, y - 2, x + from + 1, y + 11, HEAD);
        graphics.fill(x + to, y - 2, x + to + 1, y + 11, HEAD);
        MachineFrame.value(graphics, font(), Component.literal(String.format(Locale.ROOT, "%.1f s", seconds)),
                x + room - 26, y + 1, TEXT_WIDTH, HEAD);
    }

    private void add(int left, int top, String label, int x, int y, int wide, boolean held,
                     boolean enabled, Runnable action) {
        buttons.add(new Button(label, new Layout.Box(label, x - left, y - top, wide, 15), action,
                held, enabled));
    }

    private boolean hovering(Layout.Box box, double mouseX, double mouseY) {
        return hovering(box, 0, 0, mouseX, mouseY);
    }

    private boolean hovering(Layout.Box box, int left, int top, double mouseX, double mouseY) {
        return mouseX >= left + box.x() && mouseX < left + box.right()
                && mouseY >= top + box.y() && mouseY < top + box.bottom();
    }

    private void apply(Culture next) {
        culture = next;
        noticeGrowth();
        save.accept(next);
    }

    @LDLRegisterClient(name = "dish_body", registry = "ldlib2:ui_element_renderer")
    public static final class DishBodyRenderer
            extends DelegatingUIElementRenderer<DishBody, DishBodyRenderer> {
        @Override
        public Class<DishBody> type() {
            return DishBody.class;
        }

        @Override
        public void drawBackgroundAdditional(DishBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY(), guiContext.mouseX, guiContext.mouseY);
        }
    }
}
