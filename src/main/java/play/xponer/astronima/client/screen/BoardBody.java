package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.client.hud.GateSymbol;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.Schematic;
import play.xponer.astronima.item.CircuitClipboard;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.network.CircuitPlatePayload;
import play.xponer.astronima.sim.logic.BoardEditor;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.Gate;
import play.xponer.astronima.sim.logic.PartType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The circuit board's own picture and input, ported from the old {@code CircuitEditorScreen}
 * verbatim — {@link BiomonitorBody}'s "screen with no menu" shape, since a circuit plate opens
 * with no server-side container at all. Draws — it does not decide; every decision lives in
 * {@link BoardEditor}, which is Minecraft-free and tested.
 *
 * <h2>One class instead of a subclass, for the macro plate</h2>
 * The old {@code MacroPlateEditorScreen extends CircuitEditorScreen}, overriding
 * {@code maxInputs}/{@code maxOutputs} (5 instead of 4) and adding a side panel of carried chips
 * via {@code drawExtra}. {@code UIElement} composition does not have an equivalent override
 * point to hang a subclass off, so both became one class with a {@code sidePanel} flag and the
 * pin counts as constructor parameters — the same shape {@code MachineBody} already uses for
 * sixteen machine kinds in one class, not sixteen subclasses.
 *
 * <h2>What is not here: the rename field</h2>
 * The one real vanilla widget this screen had — an {@code EditBox} for the plate's name — is not
 * ported to a custom element. {@link CircuitEditorUi}/{@link MacroPlateEditorUi}'s own
 * {@code ModularUIScreen} subclass adds it from {@code init()} exactly as the old {@code Screen}
 * did, the same move {@link ProcessorEditorUi} makes for its own {@code MultiLineEditBox}.
 */
public final class BoardBody extends UIElement {

    static final int WIDTH = 336;
    private static final int TEXT_WIDTH = WIDTH - 24;
    static final int HEIGHT = 222;

    private static final int CELL_W = 42;
    private static final int CELL_H = 32;
    private static final int BOX_W = 32;
    private static final int BOX_H = 22;
    private static final int PIN_INSET = 6;
    private static final int PIN_PITCH = 8;
    private static final int PIN_REACH = 5;

    private static final int BOARD = 0xFF10281F;
    private static final int BOARD_DEEP = 0xFF0B1D17;
    private static final int BOARD_EDGE = 0xFF061210;
    private static final int BOARD_SHEEN = 0xFF16362A;
    private static final int VIA = 0xFF17332A;
    private static final int TRACE = 0xFFD08A3C;
    private static final int TRACE_CORE = 0xFFF6C070;
    private static final int TRACE_SHADOW = 0xFF57371A;
    private static final int WIRE_PENDING = 0xFF63D98A;
    private static final int PIN_BARE = 0xFF4C5E58;
    private static final int PIN_WIRED = 0xFFCBD6D2;
    private static final int PIN_LEGAL = 0xFF63D98A;
    private static final int PIN_RIM = 0xFF08120F;

    private static final int CLIP_BTN = 12;
    private static final int PALETTE_W = 42;
    private static final int PALETTE_H = 30;

    /** The macro side panel's own geometry — meaningless unless {@link #sidePanel}. */
    private static final int SIDE_GAP = 8;
    private static final int SIDE_WIDTH = 108;
    private static final int SIDE_ROW_HEIGHT = 18;
    private static final int SIDE_ROW_INSET = 6;

    final List<Hit> hits = new ArrayList<>();
    final List<Tip> tips = new ArrayList<>();
    private final List<Port> ports = new ArrayList<>();

    private final Optional<CircuitPlatePayload.At> mounted;
    private final BoardEditor editor;
    private final int maxInputs;
    private final int maxOutputs;
    private final boolean sidePanel;
    /** The screen's own generic title ("Circuit Plate" / "Macro Circuit Plate") — distinct from
     *  {@link #name}, the plate's own editable name, drawn separately in the header's own right
     *  side by the vanilla {@code EditBox} {@link CircuitEditorUi}/{@link MacroPlateEditorUi} add. */
    private final Component title;

    private int left;
    private int top;
    private int boardX;
    private int boardY;

    private String name;
    private String lastSentName;

    public BoardBody(Circuit circuit, Optional<CircuitPlatePayload.At> mounted, String name,
                     Component title, int maxInputs, int maxOutputs, boolean sidePanel) {
        this.editor = new BoardEditor(circuit);
        this.mounted = mounted;
        this.name = name;
        this.lastSentName = name;
        this.title = title;
        this.maxInputs = maxInputs;
        this.maxOutputs = maxOutputs;
        this.sidePanel = sidePanel;

        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(sidePanel ? WIDTH + SIDE_GAP + SIDE_WIDTH : WIDTH);
        getLayout().height(HEIGHT);

        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.MOUSE_UP, event -> onMouseUp(event.x, event.y));
        addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** The plate's own name, live — read by {@link CircuitEditorUi}'s screen subclass so its
     *  {@code EditBox} can seed and update it without this element needing to know about it.
     *  Not called {@code name()}: {@code UIElement} already declares one, for a different
     *  purpose entirely (its own registry/debug name), and overriding it here would have
     *  silently repurposed that instead of adding a new accessor. */
    String plateName() {
        return name;
    }

    void setPlateName(String value) {
        name = value;
        send();
    }

    record Hit(int x, int y, int width, int height, Runnable action, String tip) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    record Tip(int x, int y, int width, int height, String text) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record Port(int x, int y, BoardEditor.Pin pin) {
        boolean near(double mouseX, double mouseY) {
            return Math.abs(mouseX - x) <= PIN_REACH && Math.abs(mouseY - y) <= PIN_REACH;
        }
    }

    private Circuit circuit() {
        return editor.circuit();
    }

    // ---- drawing -----------------------------------------------------------------

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.clear();
        tips.clear();
        ports.clear();

        left = (int) getContentX();
        top = (int) getContentY();
        boardX = left + 38;
        boardY = top + 26;

        MachineFrame.panel(graphics, left, top, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), title.getString().toUpperCase(Locale.ROOT),
                left + 10, top + 9, TEXT_WIDTH, MachineFrame.TEXT);
        drawClipboardButtons(graphics, mouseX, mouseY);

        drawBoard(graphics);

        collectPorts();
        drawEdgePins(graphics);
        drawTargetCell(graphics, mouseX, mouseY);
        drawWires(graphics);
        drawGates(graphics, mouseX, mouseY);
        drawPins(graphics, mouseX, mouseY);
        drawPending(graphics, mouseX, mouseY);
        drawPalette(graphics, mouseX, mouseY);
        drawStatus(graphics);
        if (sidePanel) {
            drawChipPanel(graphics, mouseX, mouseY);
        }
    }

    private void drawBoard(GuiGraphicsExtractor graphics) {
        int x = boardX - 3;
        int y = boardY - 3;
        int w = Circuit.BOARD_COLUMNS * CELL_W + 6;
        int h = Circuit.BOARD_ROWS * CELL_H + 6;

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, BOARD_EDGE);
        graphics.fill(x, y, x + w, y + h, BOARD_DEEP);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOARD);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, BOARD_SHEEN);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, BOARD_SHEEN);

        for (int column = 0; column <= Circuit.BOARD_COLUMNS; column++) {
            for (int row = 0; row <= Circuit.BOARD_ROWS; row++) {
                int vx = boardX + column * CELL_W;
                int vy = boardY + row * CELL_H;
                graphics.fill(vx - 1, vy - 1, vx + 1, vy + 1, VIA);
            }
        }
    }

    private void tooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(font(),
                List.of(Component.literal(text).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
    }

    private void collectPorts() {
        for (int i = 0; i < maxInputs; i++) {
            ports.add(new Port(left + 22, railY(i, maxInputs), new BoardEditor.Pin(true, -1, i)));
        }
        for (int i = 0; i < maxOutputs; i++) {
            ports.add(new Port(boardX + Circuit.BOARD_COLUMNS * CELL_W + 14, railY(i, maxOutputs),
                    new BoardEditor.Pin(false, -1, i)));
        }
        for (int i = 0; i < circuit().nodes().size(); i++) {
            Circuit.Node node = circuit().nodes().get(i);
            int bx = boxX(node);
            int by = boxY(node);
            int h = nodeBoxHeight(node);
            for (int p = 0; p < node.inputCount(); p++) {
                ports.add(new Port(bx - 2, pinY(by, h, p, node.inputCount()),
                        new BoardEditor.Pin(false, i, p)));
            }
            for (int p = 0; p < node.outputCount(); p++) {
                ports.add(new Port(bx + BOX_W + 2, pinY(by, h, p, node.outputCount()),
                        new BoardEditor.Pin(true, i, p)));
            }
        }
    }

    private int railY(int index, int total) {
        return boardY + CELL_H / 2 + index * ((Circuit.BOARD_ROWS * CELL_H - CELL_H)
                / Math.max(1, total - 1));
    }

    private int boxX(Circuit.Node node) {
        return boardX + node.x() * CELL_W + (CELL_W - BOX_W) / 2;
    }

    private int nodeBoxHeight(Circuit.Node node) {
        if (!node.isSubcircuit()) {
            return BOX_H;
        }
        int pins = Math.max(node.inputCount(), node.outputCount());
        return Math.max(BOX_H, 2 * PIN_INSET + (pins - 1) * PIN_PITCH);
    }

    private int pinY(int by, int h, int index, int count) {
        return count <= 1 ? by + h / 2
                : by + PIN_INSET + index * (h - 2 * PIN_INSET) / (count - 1);
    }

    private int boxY(Circuit.Node node) {
        int h = nodeBoxHeight(node);
        return boardY + node.y() * CELL_H + (CELL_H - h) / 2;
    }

    private void drawEdgePins(GuiGraphicsExtractor graphics) {
        int railTop = railY(0, maxOutputs) - 10;
        int railBottom = railY(maxOutputs - 1, maxOutputs) + 10;
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? left + 16 : boardX + Circuit.BOARD_COLUMNS * CELL_W + 8;
            graphics.fill(x, railTop, x + 12, railBottom, BOARD_DEEP);
            graphics.fill(x + 1, railTop + 1, x + 11, railBottom - 1, 0xFF2A2118);
        }
        MachineFrame.label(graphics, font(), "IN", left + 14, top + 14, TEXT_WIDTH);
        MachineFrame.label(graphics, font(), "OUT",
                boardX + Circuit.BOARD_COLUMNS * CELL_W + 6, top + 14, TEXT_WIDTH);
        for (int i = 0; i < maxInputs; i++) {
            Schematic.caption(graphics, font(), String.valueOf((char) ('A' + i)),
                    left + 10, railY(i, maxInputs) - 4, 0xFFD9C9A8);
        }
        char startChar = maxOutputs == 5 ? 'V' : 'W';
        for (int i = 0; i < maxOutputs; i++) {
            Schematic.caption(graphics, font(), String.valueOf((char) (startChar + i)),
                    boardX + Circuit.BOARD_COLUMNS * CELL_W + 28, railY(i, maxOutputs) - 4, 0xFFD9C9A8);
        }
    }

    private void drawWires(GuiGraphicsExtractor graphics) {
        for (int i = 0; i < circuit().nodes().size(); i++) {
            Circuit.Node node = circuit().nodes().get(i);
            for (int p = 0; p < node.inputCount(); p++) {
                drawWireTo(graphics, node.input(p), portOf(new BoardEditor.Pin(false, i, p)));
            }
        }
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            drawWireTo(graphics, circuit().outputs().get(i),
                    portOf(new BoardEditor.Pin(false, -1, i)));
        }
    }

    private void drawWireTo(GuiGraphicsExtractor graphics, Circuit.Source source,
                            @Nullable Port destination) {
        if (destination == null || source.isOff()) {
            return;
        }
        Port from = portOf(source.isNode()
                ? new BoardEditor.Pin(true, source.node(), source.output())
                : new BoardEditor.Pin(true, -1, source.pin()));
        if (from == null) {
            return;
        }
        run(graphics, from.x(), from.y(), destination.x(), destination.y(), TRACE);
    }

    private void run(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int colour) {
        int mid = (x1 + x2) / 2;
        int shadow = colour == WIRE_PENDING ? 0xFF1E5233 : TRACE_SHADOW;
        int core = colour == WIRE_PENDING ? 0xFFA8F0BF : TRACE_CORE;

        horizontal(graphics, x1, mid, y1, shadow, 2);
        vertical(graphics, mid, y1, y2, shadow, 2);
        horizontal(graphics, mid, x2, y2, shadow, 2);

        horizontal(graphics, x1, mid, y1, colour, 1);
        vertical(graphics, mid, y1, y2, colour, 1);
        horizontal(graphics, mid, x2, y2, colour, 1);

        graphics.fill(Math.min(x1, mid), y1 - 1, Math.max(x1, mid), y1, core);
        graphics.fill(Math.min(mid, x2), y2 - 1, Math.max(mid, x2), y2, core);
        if (y1 != y2) {
            graphics.fill(mid - 2, y1 - 2, mid + 2, y1 + 2, colour);
            graphics.fill(mid - 1, y1 - 1, mid + 1, y1 + 1, core);
            graphics.fill(mid - 2, y2 - 2, mid + 2, y2 + 2, colour);
            graphics.fill(mid - 1, y2 - 1, mid + 1, y2 + 1, core);
        }
    }

    private void horizontal(GuiGraphicsExtractor graphics, int a, int b, int y, int colour, int half) {
        graphics.fill(Math.min(a, b), y - half, Math.max(a, b) + 1, y + half, colour);
    }

    private void vertical(GuiGraphicsExtractor graphics, int x, int a, int b, int colour, int half) {
        graphics.fill(x - half, Math.min(a, b), x + half, Math.max(a, b) + 1, colour);
    }

    private void drawGates(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < circuit().nodes().size(); i++) {
            Circuit.Node node = circuit().nodes().get(i);
            int bx = boxX(node);
            int by = boxY(node);
            int h = nodeBoxHeight(node);
            boolean hovered = mouseX >= bx && mouseX < bx + BOX_W
                    && mouseY >= by && mouseY < by + h;

            graphics.fill(bx + 2, by + 2, bx + BOX_W + 2, by + h + 2, 0x50000000);
            drawNodeSymbol(graphics, node, bx, by, hovered, h);
            if (hovered) {
                MachineFrame.outline(graphics, bx - 2, by - 2, BOX_W + 4, h + 4, PIN_LEGAL);
            }
            Schematic.caption(graphics, font(), "#" + (i + 1), bx + BOX_W / 2, by - 9,
                    hovered ? PIN_LEGAL : 0xFF6E8279);

            tips.add(new Tip(bx, by, BOX_W, h, nodeTooltip(node) + " #" + (i + 1)));
        }
    }

    private void drawNodeSymbol(GuiGraphicsExtractor graphics, Circuit.Node node, int x, int y,
                                boolean hovered, int h) {
        if (!node.isSubcircuit()) {
            GateSymbol.draw(graphics, node.gate(), x, y, BOX_W, BOX_H, hovered);
            return;
        }
        int colour = hovered ? PIN_LEGAL : 0xFF9FB3AA;
        graphics.fill(x, y, x + BOX_W, y + h, BOARD_DEEP);
        MachineFrame.outline(graphics, x, y, BOX_W, h, colour);
        int pins = Math.max(node.inputCount(), node.outputCount());
        for (int p = 0; p < pins; p++) {
            int ty = pinY(y, h, p, pins) - 1;
            graphics.fill(x - 1, ty, x + 1, ty + 2, colour);
            graphics.fill(x + BOX_W - 1, ty, x + BOX_W + 1, ty + 2, colour);
        }
        String label = node.name().isEmpty() ? "IC" : MachineFrame.fit(font(), node.name(), BOX_W - 4);
        Schematic.caption(graphics, font(), label, x + BOX_W / 2, y + h / 2 - 3, colour);
    }

    private String nodeTooltip(Circuit.Node node) {
        boolean canReplace = editor.armed() != null || editor.armedSubcircuit() != null;
        String action = canReplace
                ? " Shift-click to swap in the armed part, keeping its wires; drag to move,"
                        + " right-click to remove."
                : " Drag to move, right-click to remove.";
        if (node.isSubcircuit()) {
            String label = node.name().isEmpty() ? "Nested plate" : node.name();
            return label + " - " + node.subcircuit().nodes().size() + " gates inside." + action;
        }
        return node.gate().label() + " - " + node.gate().description() + "." + action;
    }

    private void drawPins(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Port port : ports) {
            boolean legal = editor.isLegalTarget(port.pin());
            boolean wired = port.pin().drives() || editor.feeding(port.pin()) != null;
            boolean hovered = port.near(mouseX, mouseY);
            int size = legal || hovered ? 4 : 3;
            int colour = legal ? PIN_LEGAL : wired ? PIN_WIRED : PIN_BARE;

            graphics.fill(port.x() - size - 1, port.y() - size - 1,
                    port.x() + size + 1, port.y() + size + 1, PIN_RIM);
            graphics.fill(port.x() - size, port.y() - size, port.x() + size, port.y() + size, colour);
            if (!port.pin().drives() && !wired) {
                graphics.fill(port.x() - size + 2, port.y() - size + 2,
                        port.x() + size - 2, port.y() + size - 2, BOARD_DEEP);
            } else {
                graphics.fill(port.x() - size, port.y() - size, port.x(), port.y() - size + 1,
                        0xFFFFFFFF);
                graphics.fill(port.x() - size, port.y() - size, port.x() - size + 1, port.y(),
                        0xFFFFFFFF);
            }
            if (legal) {
                MachineFrame.outline(graphics, port.x() - size - 3, port.y() - size - 3,
                        (size + 3) * 2, (size + 3) * 2, PIN_LEGAL);
            }
        }
    }

    private void drawTargetCell(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!editor.isCarrying() && editor.armed() == null && editor.armedSubcircuit() == null) {
            return;
        }
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        int x = boardX + cell[0] * CELL_W;
        int y = boardY + cell[1] * CELL_H;
        boolean armedForReplace = !editor.isCarrying()
                && (editor.armed() != null || editor.armedSubcircuit() != null) && shiftHeld();
        boolean free = !circuit().occupied(cell[0], cell[1])
                || (editor.isCarrying() && circuit().nodeAt(cell[0], cell[1]) >= 0
                        && hoveringOwnSquare(cell))
                || (armedForReplace && circuit().nodeAt(cell[0], cell[1]) >= 0);
        MachineFrame.outline(graphics, x + 1, y + 1, CELL_W - 2, CELL_H - 2,
                free ? MachineFrame.GOOD : MachineFrame.WARN);
    }

    private boolean hoveringOwnSquare(int[] cell) {
        return editor.carriedFrom() != null && editor.carriedFrom()[0] == cell[0]
                && editor.carriedFrom()[1] == cell[1];
    }

    private static boolean shiftHeld() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }

    private void drawPending(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] carriedFrom = editor.carriedFrom();
        if (carriedFrom != null) {
            int index = circuit().nodeAt(carriedFrom[0], carriedFrom[1]);
            if (index >= 0) {
                Circuit.Node node = circuit().nodes().get(index);
                int h = nodeBoxHeight(node);
                int x = mouseX - BOX_W / 2;
                int y = mouseY - h / 2;
                graphics.fill(x + 4, y + 5, x + BOX_W + 4, y + h + 5, 0x40000000);
                drawNodeSymbol(graphics, node, x, y, true, h);
                MachineFrame.outline(graphics, x - 2, y - 2, BOX_W + 4, h + 4, PIN_LEGAL);
            }
        }
        Port from = editor.pulling() == null ? null : portOf(editor.pulling());
        if (from != null) {
            run(graphics, from.x(), from.y(), mouseX, mouseY, WIRE_PENDING);
        }
    }

    private void drawClipboardButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = top + 7;
        drawClipButton(graphics, mouseX, mouseY, left + WIDTH - 40, y, "C",
                "Copy this whole board to the clipboard.", this::copyToClipboard);
        drawClipButton(graphics, mouseX, mouseY, left + WIDTH - 26, y, "V",
                "Paste a board from the clipboard, replacing this one.", this::pasteFromClipboard);
    }

    private void drawClipButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y,
                                String glyph, String tip, Runnable action) {
        boolean hovered = mouseX >= x && mouseX < x + CLIP_BTN && mouseY >= y && mouseY < y + CLIP_BTN;
        graphics.fill(x, y, x + CLIP_BTN, y + CLIP_BTN, hovered ? 0xFF1D4432 : BOARD_DEEP);
        MachineFrame.outline(graphics, x, y, CLIP_BTN, CLIP_BTN, hovered ? PIN_LEGAL : BOARD_EDGE);
        Schematic.caption(graphics, font(), glyph, x + CLIP_BTN / 2, y + 2,
                hovered ? PIN_LEGAL : 0xFF9FB3AA);
        hits.add(new Hit(x, y, CLIP_BTN, CLIP_BTN, action, tip));
    }

    private void copyToClipboard() {
        Minecraft.getInstance().keyboardHandler.setClipboard(CircuitClipboard.encode(circuit()));
    }

    private void pasteFromClipboard() {
        String held = Minecraft.getInstance().keyboardHandler.getClipboard();
        CircuitClipboard.decode(held).ifPresentOrElse(editor::paste, editor::pasteFailed);
        send();
    }

    private void drawPalette(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = top + HEIGHT - 44;
        MachineFrame.divider(graphics, left + 8, y - 6, WIDTH - 16);
        int x = left + 10;
        for (Gate gate : Gate.values()) {
            boolean chosen = editor.armed() == gate;
            boolean hovered = mouseX >= x && mouseX < x + PALETTE_W
                    && mouseY >= y && mouseY < y + PALETTE_H;

            graphics.fill(x, y, x + PALETTE_W, y + PALETTE_H, chosen ? 0xFF1D4432 : BOARD_DEEP);
            MachineFrame.outline(graphics, x, y, PALETTE_W, PALETTE_H,
                    chosen ? PIN_LEGAL : hovered ? 0xFF9FB3AA : BOARD_EDGE);
            GateSymbol.draw(graphics, gate, x + 4, y + 3, PALETTE_W - 8, PALETTE_H - 12,
                    chosen || hovered);
            Schematic.caption(graphics, font(), gate.label(), x + PALETTE_W / 2,
                    y + PALETTE_H - 9, chosen ? PIN_LEGAL : 0xFF9FB3AA);

            final Gate picked = gate;
            hits.add(new Hit(x, y, PALETTE_W, PALETTE_H, () -> editor.arm(picked),
                    gate.label() + " - " + gate.description()
                            + ". Pick it, then click an empty square."));
            x += PALETTE_W + 4;
        }
        int full = circuit().nodes().size();
        MachineFrame.value(graphics, font(), full + "/" + Circuit.MAX_NODES,
                left + WIDTH - 28, y + PALETTE_H / 2 - 4, TEXT_WIDTH,
                full >= Circuit.MAX_NODES ? MachineFrame.WARN : MachineFrame.TEXT_DIM);
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        int y = top + HEIGHT - 13;
        boolean quiet = editor.status().isEmpty();
        graphics.fill(left + 6, y - 3, left + WIDTH - 6, y + 9, BOARD_DEEP);
        graphics.fill(left + 7, y - 2, left + WIDTH - 7, y + 8, quiet ? BOARD : 0xFF3A1614);
        MachineFrame.value(graphics, font(), quiet ? summary() : editor.status(), left + 11, y,
                TEXT_WIDTH, quiet ? 0xFF9FD8B4 : 0xFFFFB4A8);
    }

    private String summary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            Circuit.Source source = circuit().outputs().get(i);
            if (source.isOff()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("   ");
            }
            out.append((char) ('W' + i)).append(" = ")
                    .append(LogicPartItem.describe(circuit(), source));
        }
        return out.length() == 0 ? "pick a gate below, click a square, then drag pin to pin"
                : out.toString();
    }

    // ---- the macro plate's own side panel of carried chips ------------------------

    private void drawChipPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int sideX = left + WIDTH + SIDE_GAP;
        int sideY = top;

        MachineFrame.panel(graphics, sideX, sideY, SIDE_WIDTH, HEIGHT);
        MachineFrame.label(graphics, font(), "CHIPS", sideX + SIDE_ROW_INSET, sideY + 9, SIDE_WIDTH - 12);
        MachineFrame.divider(graphics, sideX + 6, sideY + 20, SIDE_WIDTH - 12);

        List<ItemStack> chips = carriedChips();
        int rowY = sideY + 26;
        int maxRows = (HEIGHT - 30) / SIDE_ROW_HEIGHT;
        if (chips.isEmpty()) {
            Schematic.caption(graphics, font(), "none carried", sideX + SIDE_WIDTH / 2, rowY + 4,
                    0xFF6E8279);
            return;
        }
        for (int i = 0; i < chips.size() && i < maxRows; i++) {
            drawChipRow(graphics, chips.get(i), sideX + SIDE_ROW_INSET, rowY,
                    SIDE_WIDTH - SIDE_ROW_INSET * 2, mouseX, mouseY);
            rowY += SIDE_ROW_HEIGHT;
        }
        if (chips.size() > maxRows) {
            Schematic.caption(graphics, font(), "+" + (chips.size() - maxRows) + " more",
                    sideX + SIDE_WIDTH / 2, rowY + 4, 0xFF6E8279);
        }
    }

    private void drawChipRow(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int w,
                             int mouseX, int mouseY) {
        Circuit chip = LogicPartItem.circuitOf(stack);
        boolean armed = chip.equals(editor.armedSubcircuit());
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SIDE_ROW_HEIGHT - 2;

        graphics.fill(x, y, x + w, y + SIDE_ROW_HEIGHT - 2, armed ? 0xFF1D4432 : 0xFF13241D);
        MachineFrame.outline(graphics, x, y, w, SIDE_ROW_HEIGHT - 2,
                armed ? MachineFrame.GOOD : hovered ? 0xFF9FB3AA : 0xFF243A31);

        String chipName = stack.getHoverName().getString();
        MachineFrame.value(graphics, font(), MachineFrame.fit(font(), chipName, w - 8), x + 4, y + 5,
                w - 8, armed ? 0xFFB9F0CE : chip.isBlank() ? 0xFF6E8279 : 0xFFD9C9A8);

        String tooltip = chip.isBlank() ? chipName + " - blank, nothing to nest"
                : chipName + " - " + chip.nodes().size() + " gates. Click to "
                        + (armed ? "put it back" : "arm it, then click a square") + ".";
        tips.add(new Tip(x, y, w, SIDE_ROW_HEIGHT - 2, tooltip));
        if (!chip.isBlank()) {
            int pins = ((LogicPartItem) stack.getItem()).type().inputs().size();
            String customName = LogicPartItem.customNameOf(stack);
            hits.add(new Hit(x, y, w, SIDE_ROW_HEIGHT - 2,
                    () -> editor.armSubcircuit(chip, pins, customName), ""));
        }
    }

    /** Every {@code PLATE}/{@code MACRO_PLATE} item the local player is carrying, right now. */
    private static List<ItemStack> carriedChips() {
        List<ItemStack> found = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return found;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof LogicPartItem part
                    && (part.type() == PartType.PLATE || part.type() == PartType.MACRO_PLATE)) {
                found.add(stack);
            }
        }
        return found;
    }

    // ---- clicking ----------------------------------------------------------------

    private void onMouseDown(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        int mouseX = (int) event.x;
        int mouseY = (int) event.y;
        int[] cell = cellAt(mouseX, mouseY);
        if (event.button == 1) {
            if (cell != null) {
                editor.removeAt(cell[0], cell[1]);
            }
            send();
            return;
        }
        if (event.button != 0) {
            return;
        }

        Port port = portNear(mouseX, mouseY);
        if (port != null) {
            editor.pressPin(port.pin());
            send();
            return;
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action.run();
                return;
            }
        }
        if (cell != null && editor.pressCell(cell[0], cell[1], event.isShiftDown())) {
            send();
        }
    }

    private void onMouseUp(double mouseX, double mouseY) {
        if (editor.isCarrying()) {
            int[] cell = cellAt(mouseX, mouseY);
            editor.releaseOnCell(cell == null ? -1 : cell[0], cell == null ? -1 : cell[1]);
            send();
            return;
        }
        Port port = portNear(mouseX, mouseY);
        editor.releaseOnPin(port == null ? null : port.pin());
        send();
    }

    private void onHoverTooltips(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        for (Hit hit : hits) {
            if (hit.contains(event.x, event.y) && !hit.tip.isEmpty()) {
                event.hoverTooltips = HoverTooltips.create(
                        Component.literal(hit.tip).withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        for (Tip tip : tips) {
            if (tip.contains(event.x, event.y)) {
                event.hoverTooltips = HoverTooltips.create(
                        Component.literal(tip.text).withStyle(ChatFormatting.GRAY));
                return;
            }
        }
    }

    private int @Nullable [] cellAt(double mouseX, double mouseY) {
        if (mouseX < boardX || mouseY < boardY) {
            return null;
        }
        int x = (int) ((mouseX - boardX) / CELL_W);
        int y = (int) ((mouseY - boardY) / CELL_H);
        return x < Circuit.BOARD_COLUMNS && y < Circuit.BOARD_ROWS ? new int[] {x, y} : null;
    }

    private @Nullable Port portNear(double mouseX, double mouseY) {
        Port best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Port port : ports) {
            if (!port.near(mouseX, mouseY)) {
                continue;
            }
            double distance = Math.abs(mouseX - port.x()) + Math.abs(mouseY - port.y());
            if (distance < bestDistance) {
                best = port;
                bestDistance = distance;
            }
        }
        return best;
    }

    private @Nullable Port portOf(BoardEditor.Pin pin) {
        for (Port port : ports) {
            if (port.pin().equals(pin)) {
                return port;
            }
        }
        return null;
    }

    /**
     * Tells the server, but only when something actually changed.
     *
     * <p>The screen never writes the stack itself: the server decides whether the sender is
     * holding a plate at all, or is in reach of the one on the wall. A refused edit sends nothing,
     * so a refusal costs no packet and cannot arrive as a no-op write.
     */
    private void send() {
        boolean circuitChanged = editor.takeChanged();
        boolean nameChanged = !name.equals(lastSentName);
        if (circuitChanged || nameChanged) {
            lastSentName = name;
            ClientPacketDistributor.sendToServer(new CircuitPlatePayload(editor.circuit(), mounted, name));
        }
    }

    @LDLRegisterClient(name = "board_body", registry = "ldlib2:ui_element_renderer")
    public static final class BoardBodyRenderer
            extends DelegatingUIElementRenderer<BoardBody, BoardBodyRenderer> {
        @Override
        public Class<BoardBody> type() {
            return BoardBody.class;
        }

        @Override
        public void drawBackgroundAdditional(BoardBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, guiContext.mouseX, guiContext.mouseY);
        }
    }
}
