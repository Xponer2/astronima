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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.Schematic;
import play.xponer.astronima.menu.StorageTerminalUiHolder;
import play.xponer.astronima.network.TerminalScrollPayload;
import play.xponer.astronima.network.TerminalSortPayload;
import play.xponer.astronima.sim.storage.TerminalView;

import java.util.Locale;

/**
 * The terminal's own panel and slot wells — see {@code design/data-cells.md} §14. The flat,
 * textureless grey the user reported was a plain vanilla {@code AbstractContainerScreen}, which
 * never draws any of this mod's own chrome at all. Three sort buttons, 54 display wells, a real
 * scrollbar (§18 — the 54 slots are a viewport, not a cap, onto a list that can run longer), and
 * the player's own inventory — no separate input well anymore (§27: real placement onto a display
 * row already deposits, so a dedicated feed slot was pure redundancy). {@link StorageTerminalUi}
 * adds the real {@code ItemSlot}s and click overlays on top of what this draws.
 */
public final class StorageTerminalBody extends UIElement {

    private static final int COLUMNS = StorageTerminalBlockEntity.COLUMNS;
    private static final int VISIBLE_ROWS = StorageTerminalBlockEntity.VISIBLE_ROWS;

    static final int BUTTON_Y = 20;
    static final int BUTTON_W = 50;
    static final int BUTTON_H = 13;
    static final int[] BUTTON_X = {8, 64, 120};
    private static final String[] BUTTON_LABEL = {"NAME", "COUNT", "TYPE"};

    static final int GRID_TOP = 40;
    private static final int GRID_HEIGHT = VISIBLE_ROWS * 18;

    /** The scrollbar sits just right of the grid, the same {@value #TRACK_W}px-wide track the
     *  whole panel was widened to fit — see this class's own doc for why 54 real slots still
     *  need one at all. */
    static final int TRACK_X = 8 + COLUMNS * 18 + 6;
    static final int TRACK_W = 10;
    static final int TRACK_Y = GRID_TOP;
    static final int TRACK_H = GRID_HEIGHT;
    private static final int THUMB_MIN_H = 10;

    static final int WIDTH = TRACK_X + TRACK_W + 6;

    private static final int DISPLAY_DIVIDER_Y = GRID_TOP + GRID_HEIGHT + 2;
    static final int INVENTORY_LABEL_Y = DISPLAY_DIVIDER_Y + 2;
    static final int INVENTORY_Y = INVENTORY_LABEL_Y + 12;
    static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;
    static final int HEIGHT = HOTBAR_Y + 18 + 6;

    private final StorageTerminalUiHolder holder;

    public StorageTerminalBody(StorageTerminalUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
        addEventListener(UIEvents.MOUSE_WHEEL, this::onWheel);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos, int mouseX, int mouseY) {
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), holder.getDisplayName().getString().toUpperCase(Locale.ROOT),
                leftPos + 8, topPos + 7, WIDTH - 16, MachineFrame.TEXT);
        drawSortButtons(graphics, leftPos, topPos, mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + GRID_TOP + row * 18);
            }
        }
        drawScrollbar(graphics, leftPos, topPos);
        MachineFrame.divider(graphics, leftPos + 7, topPos + DISPLAY_DIVIDER_Y, WIDTH - 14);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + HOTBAR_Y);
        }
    }

    private void drawSortButtons(GuiGraphicsExtractor graphics, int leftPos, int topPos,
                                 int mouseX, int mouseY) {
        for (int i = 0; i < BUTTON_X.length; i++) {
            int x = leftPos + BUTTON_X[i];
            int y = topPos + BUTTON_Y;
            MachineFrame.knob(graphics, x, y, BUTTON_W, BUTTON_H);
            boolean hovered = mouseX >= x && mouseX <= x + BUTTON_W
                    && mouseY >= y && mouseY <= y + BUTTON_H;
            Schematic.caption(graphics, font(), BUTTON_LABEL[i], x + BUTTON_W / 2, y + 3,
                    hovered ? MachineFrame.GOOD : MachineFrame.TEXT);
        }
    }

    /** The track is always drawn; the thumb's own height and position are the one place the
     *  synced {@code scrollRow}/{@code totalRows} (§18) actually reach the picture — real
     *  numbers, not a decorative bar that never moves. */
    private void drawScrollbar(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        int x = leftPos + TRACK_X;
        int y = topPos + TRACK_Y;
        MachineFrame.well(graphics, x, y, TRACK_W, TRACK_H);
        int totalRows = Math.max(holder.totalRows(), VISIBLE_ROWS);
        int scrollRow = holder.scrollRow();
        int maxScrollRow = Math.max(0, totalRows - VISIBLE_ROWS);
        float thumbFrac = (float) VISIBLE_ROWS / totalRows;
        int thumbH = Math.max(THUMB_MIN_H, Math.round((TRACK_H - 2) * thumbFrac));
        int track = TRACK_H - 2 - thumbH;
        int thumbY = y + 1 + (maxScrollRow > 0 ? Math.round(track * (scrollRow / (float) maxScrollRow)) : 0);
        graphics.fill(x + 1, thumbY, x + TRACK_W - 1, thumbY + thumbH, MachineFrame.ACCENT);
    }

    private void onWheel(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        int delta = event.deltaY > 0 ? -1 : 1;
        sendScroll(holder.scrollRow() + delta);
    }

    // ---- clicking/dragging, called by StorageTerminalUi's overlay elements -----

    /** Sort is not something the display rows themselves know — the sort buttons ask the real
     *  block entity to re-sort its own live view, the same round trip {@code refreshView} already
     *  runs every tick. */
    void onSortClicked(TerminalView.Sort sort) {
        ClientPacketDistributor.sendToServer(new TerminalSortPayload(holder.pos(), sort.ordinal()));
    }

    /** A click or drag at {@code mouseY} inside the track jumps straight to the row that position
     *  represents — the same "click anywhere on the bar, land there" scrollbar feel vanilla's own
     *  creative inventory search bar has, rather than only nudging by a fixed step. */
    void onTrackInteracted(double mouseY) {
        int topPos = (int) getContentY();
        int totalRows = Math.max(holder.totalRows(), VISIBLE_ROWS);
        int maxScrollRow = Math.max(0, totalRows - VISIBLE_ROWS);
        if (maxScrollRow <= 0) {
            return;
        }
        double fraction = (mouseY - topPos - TRACK_Y) / TRACK_H;
        int target = Math.round((float) (fraction * maxScrollRow));
        sendScroll(target);
    }

    private void sendScroll(int row) {
        ClientPacketDistributor.sendToServer(new TerminalScrollPayload(holder.pos(), row));
    }

    @LDLRegisterClient(name = "storage_terminal_body", registry = "ldlib2:ui_element_renderer")
    public static final class StorageTerminalBodyRenderer
            extends DelegatingUIElementRenderer<StorageTerminalBody, StorageTerminalBodyRenderer> {
        @Override
        public Class<StorageTerminalBody> type() {
            return StorageTerminalBody.class;
        }

        @Override
        public void drawBackgroundAdditional(StorageTerminalBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY(), guiContext.mouseX, guiContext.mouseY);
        }
    }
}
