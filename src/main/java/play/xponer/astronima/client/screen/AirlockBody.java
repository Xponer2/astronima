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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.airlock.AirlockStatus;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.Schematic;
import play.xponer.astronima.item.WrenchItem;
import play.xponer.astronima.menu.AirlockUiHolder;
import play.xponer.astronima.network.AirlockCommandPayload;
import play.xponer.astronima.network.AirlockTerminalPayload;
import play.xponer.astronima.sim.airlock.AirlockCycle;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The airlock panel's own picture and click regions, ported from the old {@code AirlockScreen}
 * verbatim — the same {@code MachineBody} pattern: everything this drew by hand still draws the
 * same way, only {@code leftPos}/{@code topPos} became {@link #getContentX()}/
 * {@link #getContentY()} and {@code menu} became {@link #holder}. No item slots at all — an
 * airlock holds none — so unlike {@code ProcessingUi} there is no chrome around this beyond the
 * click overlays {@link AirlockUi} lays on top of it.
 */
public final class AirlockBody extends UIElement {

    static final int WIDTH = 200;
    private static final int TEXT_WIDTH = WIDTH - 24;
    static final int HEIGHT = 162;

    private static final int LINE_Y = 58;
    private static final int CHAMBER_X = 12;
    private static final int INNER_X = 62;
    private static final int OUTER_X = 84;
    private static final int PUMP_X = 118;
    private static final int TANK_X = 160;

    private static final int DOOR_W = 14;
    private static final int DOOR_H = 26;
    private static final int VESSEL_W = 22;
    private static final int VESSEL_H = 30;
    private static final int PUMP_SIZE = 18;

    private static final int TERMINAL_PAD = 3;

    static final int SCAN_X = 140;
    static final int SCAN_Y = 16;
    static final int SCAN_W = 48;
    static final int SCAN_H = 13;

    static final int BUTTON_X = 12;
    static final int BUTTON_Y = 136;
    static final int BUTTON_W = 176;
    static final int BUTTON_H = 18;

    /** Where each terminal is on the panel — shared with {@link AirlockUi}, which sizes the
     *  click overlays from this same table. */
    static final Map<Role, int[]> TERMINAL_BOXES = new EnumMap<>(Role.class);

    static {
        TERMINAL_BOXES.put(Role.INNER_DOOR, new int[] {INNER_X, LINE_Y - 12, DOOR_W, DOOR_H});
        TERMINAL_BOXES.put(Role.OUTER_DOOR, new int[] {OUTER_X, LINE_Y - 12, DOOR_W, DOOR_H});
        TERMINAL_BOXES.put(Role.PUMP, new int[] {PUMP_X, LINE_Y - 8, PUMP_SIZE, PUMP_SIZE});
        TERMINAL_BOXES.put(Role.TANK, new int[] {TANK_X, LINE_Y - 14, VESSEL_W, VESSEL_H});
    }

    private final AirlockUiHolder holder;
    private int animationTick;
    private int lastMouseX, lastMouseY;

    public AirlockBody(AirlockUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
        addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.TICK,
                e -> animationTick++);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos,
                           int mouseX, int mouseY) {
        AirlockStatus status = holder.status();
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);

        drawStatusLine(graphics, status, leftPos, topPos);
        drawScanButton(graphics, leftPos, topPos, mouseX, mouseY);
        drawProcessLine(graphics, status, leftPos, topPos, mouseX, mouseY);
        drawGauges(graphics, status, leftPos, topPos);
        drawButton(graphics, status, leftPos, topPos, mouseX, mouseY);
    }

    /**
     * The one-line summary and, under it, the fault — <em>named on the device the player
     * chose</em>, which is the whole point of commissioning.
     */
    private void drawStatusLine(GuiGraphicsExtractor graphics, AirlockStatus status,
                                int leftPos, int topPos) {
        int y = topPos + 22;
        String text;
        int colour;
        if (status.chamberFault() != play.xponer.astronima.airlock.AirlockResolver.Fault.NONE) {
            text = words(status.chamberFault().name());
            colour = MachineFrame.BAD;
        } else if (status.blank()) {
            text = "NOT COMMISSIONED";
            colour = MachineFrame.WARN;
        } else if (!status.commissioned()) {
            Role role = status.firstFault();
            text = role.label() + " — " + status.problem(role).words();
            colour = MachineFrame.BAD;
        } else if (status.cycleFault() != AirlockCycle.Fault.NONE) {
            text = words(status.cycleFault().name());
            colour = MachineFrame.WARN;
        } else {
            text = words(status.phase().name());
            colour = MachineFrame.GOOD;
        }
        MachineFrame.label(graphics, font(), "STATUS", leftPos + 12, y, TEXT_WIDTH);
        MachineFrame.value(graphics, font(), text.toUpperCase(Locale.ROOT), leftPos + 56, y, TEXT_WIDTH, colour);

        if (!status.commissioned()) {
            if (holdsWrench()) {
                MachineFrame.value(graphics, font(), "Click a terminal, then wrench its device",
                        leftPos + 12, y + 11, TEXT_WIDTH, MachineFrame.TEXT_DIM);
            } else {
                MachineFrame.value(graphics, font(), "Hold a wrench to commission",
                        leftPos + 12, y + 11, TEXT_WIDTH, MachineFrame.WARN);
            }
        }
    }

    /** SCAN proposes bindings for whatever is still empty. Proposals may be wrong. */
    private void drawScanButton(GuiGraphicsExtractor graphics, int leftPos, int topPos,
                                int mouseX, int mouseY) {
        int x = leftPos + SCAN_X;
        int y = topPos + SCAN_Y;
        MachineFrame.knob(graphics, x, y, SCAN_W, SCAN_H);
        Schematic.caption(graphics, font(), "SCAN", x + SCAN_W / 2, y + 3,
                inside(mouseX, mouseY, leftPos, topPos, SCAN_X, SCAN_Y, SCAN_W, SCAN_H)
                        ? MachineFrame.GOOD : MachineFrame.TEXT);
    }

    /**
     * The line itself: each terminal green when its device checks out, red when the fault
     * is there, and a dashed socket when nobody has filled it.
     */
    private void drawProcessLine(GuiGraphicsExtractor graphics, AirlockStatus status,
                                 int leftPos, int topPos, int mouseX, int mouseY) {
        int y = topPos + LINE_Y;
        boolean pumping = status.phase() == AirlockCycle.Phase.PUMPING;
        boolean filling = status.phase() == AirlockCycle.Phase.REPRESSURIZING;

        drawTerminalPlinths(graphics, leftPos, topPos);

        boolean chamberOk = status.chamberFault()
                == play.xponer.astronima.airlock.AirlockResolver.Fault.NONE;
        int chamberState = chamberOk ? Schematic.LIVE : Schematic.BROKEN;
        Schematic.vessel(graphics, leftPos + CHAMBER_X, y - 14, VESSEL_W, VESSEL_H,
                fraction(status.chamberKPa(), status.habitatKPa()), chamberState);
        Schematic.caption(graphics, font(), "CHAMBER", leftPos + CHAMBER_X + VESSEL_W / 2,
                y + 20, MachineFrame.TEXT_DIM);
        Schematic.caption(graphics, font(),
                status.chamberVolume() > 0 ? status.chamberVolume() + " m3" : "--",
                leftPos + CHAMBER_X + VESSEL_W / 2, y + 30, MachineFrame.TEXT);

        Schematic.line(graphics, leftPos + CHAMBER_X + VESSEL_W, y,
                INNER_X - CHAMBER_X - VESSEL_W - 2, Schematic.IDLE, false, animationTick);
        drawDoorTerminal(graphics, status, Role.INNER_DOOR, INNER_X, leftPos, topPos, status.innerShut());
        drawDoorTerminal(graphics, status, Role.OUTER_DOOR, OUTER_X, leftPos, topPos, status.outerShut());

        AirlockStatus.Terminal pump = status.terminal(Role.PUMP);
        Schematic.line(graphics, leftPos + OUTER_X + DOOR_W + 2, y,
                PUMP_X - OUTER_X - DOOR_W - 4, terminalColour(pump), pumping, animationTick);
        if (pump.bound()) {
            Schematic.pump(graphics, leftPos + PUMP_X, y - 8, PUMP_SIZE,
                    pumping && pump.ok() ? Schematic.LIVE : terminalColour(pump));
        } else {
            Schematic.emptyTerminal(graphics, leftPos + PUMP_X, y - 8, PUMP_SIZE, PUMP_SIZE);
        }
        drawTerminalCaption(graphics, status, Role.PUMP, "PUMP", PUMP_X + PUMP_SIZE / 2, leftPos, y);

        AirlockStatus.Terminal tank = status.terminal(Role.TANK);
        Schematic.line(graphics, leftPos + PUMP_X + PUMP_SIZE + 4, y,
                TANK_X - PUMP_X - PUMP_SIZE - 6, terminalColour(tank), pumping || filling,
                animationTick);
        if (tank.bound()) {
            Schematic.vessel(graphics, leftPos + TANK_X, y - 14, VESSEL_W, VESSEL_H,
                    (float) Math.clamp(status.tankKPa() / 3000.0, 0.0, 1.0),
                    terminalColour(tank));
        } else {
            Schematic.emptyTerminal(graphics, leftPos + TANK_X, y - 14, VESSEL_W, VESSEL_H);
        }
        drawTerminalCaption(graphics, status, Role.TANK, "TANK", TANK_X + VESSEL_W / 2, leftPos, y);

        drawTerminalHover(graphics, leftPos, topPos, mouseX, mouseY);
    }

    private void drawTerminalPlinths(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        for (int[] box : TERMINAL_BOXES.values()) {
            MachineFrame.knob(graphics, leftPos + box[0] - TERMINAL_PAD,
                    topPos + box[1] - TERMINAL_PAD,
                    box[2] + 2 * TERMINAL_PAD, box[3] + 2 * TERMINAL_PAD);
        }
    }

    private void drawTerminalHover(GuiGraphicsExtractor graphics, int leftPos, int topPos,
                                   int mouseX, int mouseY) {
        for (int[] box : TERMINAL_BOXES.values()) {
            if (overTerminal(mouseX, mouseY, leftPos, topPos, box)) {
                MachineFrame.outline(graphics, leftPos + box[0] - TERMINAL_PAD,
                        topPos + box[1] - TERMINAL_PAD,
                        box[2] + 2 * TERMINAL_PAD, box[3] + 2 * TERMINAL_PAD, MachineFrame.GOOD);
            }
        }
    }

    private void drawDoorTerminal(GuiGraphicsExtractor graphics, AirlockStatus status,
                                  Role role, int x, int leftPos, int topPos, boolean shut) {
        int y = topPos + LINE_Y;
        AirlockStatus.Terminal terminal = status.terminal(role);
        if (terminal.bound()) {
            Schematic.door(graphics, leftPos + x, y - 12, DOOR_W, DOOR_H, shut,
                    terminalColour(terminal));
        } else {
            Schematic.emptyTerminal(graphics, leftPos + x, y - 12, DOOR_W, DOOR_H);
        }
        drawTerminalCaption(graphics, status, role, role == Role.INNER_DOOR ? "INNER" : "OUTER",
                x + DOOR_W / 2, leftPos, y);
    }

    /**
     * Under every terminal: what it is, and where the device on it sits relative to the
     * panel. The offset is the only thing that tells two bulkhead doors apart on a diagram,
     * so an empty terminal says {@code — bind —} rather than nothing.
     */
    private void drawTerminalCaption(GuiGraphicsExtractor graphics, AirlockStatus status,
                                     Role role, String caption, int centreX, int leftPos, int y) {
        AirlockStatus.Terminal terminal = status.terminal(role);
        Schematic.caption(graphics, font(), caption, leftPos + centreX, y + 20,
                MachineFrame.TEXT_DIM);
        String under = terminal.bound()
                ? AirlockStatus.offsetLabel(terminal.offset()) : "- bind -";
        Schematic.caption(graphics, font(), under, leftPos + centreX, y + 30,
                terminal.ok() ? MachineFrame.TEXT : MachineFrame.BAD);
    }

    private static int terminalColour(AirlockStatus.Terminal terminal) {
        if (!terminal.bound()) {
            return Schematic.IDLE;
        }
        return terminal.ok() ? Schematic.LIVE : Schematic.BROKEN;
    }

    private void drawGauges(GuiGraphicsExtractor graphics, AirlockStatus status, int leftPos, int topPos) {
        int y = topPos + 108;
        MachineFrame.label(graphics, font(), "CHAMBER", leftPos + 12, y, TEXT_WIDTH);
        MachineFrame.bar(graphics, leftPos + 62, y - 1, 60, 7,
                fraction(status.chamberKPa(), status.habitatKPa()), MachineFrame.ACCENT);
        MachineFrame.value(graphics, font(), Math.round(status.chamberKPa()) + " kPa",
                leftPos + 128, y, TEXT_WIDTH, MachineFrame.TEXT);

        MachineFrame.label(graphics, font(), "STORE", leftPos + 12, y + 12, TEXT_WIDTH);
        float stored = (float) Math.clamp(status.tankKPa() / 3000.0, 0.0, 1.0);
        MachineFrame.bar(graphics, leftPos + 62, y + 11, 60, 7, stored,
                MachineFrame.colourFor(stored));
        MachineFrame.value(graphics, font(), Math.round(status.tankKPa()) + " kPa",
                leftPos + 128, y + 12, TEXT_WIDTH, MachineFrame.TEXT);
    }

    /** One button, because the cycle decides what pressing it means from where it is. */
    private void drawButton(GuiGraphicsExtractor graphics, AirlockStatus status,
                            int leftPos, int topPos, int mouseX, int mouseY) {
        int x = leftPos + BUTTON_X;
        int y = topPos + BUTTON_Y;
        MachineFrame.knob(graphics, x, y, BUTTON_W, BUTTON_H);
        boolean hovered = inside(mouseX, mouseY, leftPos, topPos, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H);
        String label = !status.commissioned() ? "NOT READY" : switch (status.phase()) {
            case SEALED -> "CYCLE OUT";
            case VACUUM -> "CYCLE IN";
            default -> "RUNNING…";
        };
        int colour = !status.commissioned() ? MachineFrame.BAD
                : hovered ? MachineFrame.GOOD : MachineFrame.TEXT;
        Schematic.caption(graphics, font(), label, x + BUTTON_W / 2, y + 5, colour);
    }

    private static float fraction(double value, double full) {
        return full > 0 ? (float) Math.clamp(value / full, 0.0, 1.0) : 0f;
    }

    /** {@code NOT_SEALED → not sealed} — the enum, said out loud. */
    private static String words(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ---- hit testing, shared by drawing and clicking --------------------------

    private boolean inside(double mouseX, double mouseY, int leftPos, int topPos,
                           int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX <= leftPos + x + width
                && mouseY >= topPos + y && mouseY <= topPos + y + height;
    }

    /**
     * Whether the pointer is over a terminal's button — the plinth, not just the symbol.
     *
     * <p>The one hitbox that drawing the plinth, highlighting the hover, showing the tooltip
     * and handling the click all share, so the thing the player sees, points at and presses is
     * provably the same region (PLAN rule 20). Widening past the symbol to the whole plinth is
     * why a click just outside a 14-px-wide door still lands.
     */
    boolean overTerminal(double mouseX, double mouseY, int leftPos, int topPos, int[] box) {
        return inside(mouseX, mouseY, leftPos, topPos, box[0] - TERMINAL_PAD, box[1] - TERMINAL_PAD,
                box[2] + 2 * TERMINAL_PAD, box[3] + 2 * TERMINAL_PAD);
    }

    // ---- clicking, called by AirlockUi's overlay elements ----------------------

    void onCycleClicked() {
        ClientPacketDistributor.sendToServer(new AirlockCommandPayload(holder.pos()));
    }

    void onScanClicked() {
        send(Role.INNER_DOOR, AirlockTerminalPayload.SCAN);
    }

    /** Left-click a terminal to arm it, right-click to clear it — see {@link AirlockUi}'s own
     *  overlay wiring for which button reaches this. */
    void onTerminalClicked(Role role, boolean rightClick) {
        send(role, rightClick ? AirlockTerminalPayload.CLEAR : AirlockTerminalPayload.ARM);
    }

    private void send(Role role, int action) {
        ClientPacketDistributor.sendToServer(
                new AirlockTerminalPayload(holder.pos(), role.ordinal(), action));
    }

    // ---- tooltips ---------------------------------------------------------------

    java.util.List<net.minecraft.network.chat.Component> scanTooltip() {
        return java.util.List.of(
                net.minecraft.network.chat.Component.literal("SCAN"),
                net.minecraft.network.chat.Component.literal(
                                "Propose devices for the empty terminals — check them.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** The lines a hovered terminal shows: what is on it, and what a click will do. */
    java.util.List<net.minecraft.network.chat.Component> terminalTooltip(Role role) {
        AirlockStatus status = holder.status();
        AirlockStatus.Terminal terminal = status.terminal(role);
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        if (!terminal.bound()) {
            lines.add(net.minecraft.network.chat.Component.literal(role.label() + " — empty")
                    .withStyle(net.minecraft.ChatFormatting.WHITE));
            lines.add(net.minecraft.network.chat.Component.literal("Click to arm the wrench, then click the "
                    + role.words() + " in the world.").withStyle(net.minecraft.ChatFormatting.GRAY));
            if (!holdsWrench()) {
                lines.add(net.minecraft.network.chat.Component.literal("Hold a wrench first.")
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        } else if (terminal.ok()) {
            lines.add(net.minecraft.network.chat.Component.literal(role.label() + " — "
                            + AirlockStatus.offsetLabel(terminal.offset()))
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            lines.add(net.minecraft.network.chat.Component.literal("Right-click to clear.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.add(net.minecraft.network.chat.Component.literal(role.label() + " — "
                            + terminal.problem().words())
                    .withStyle(net.minecraft.ChatFormatting.RED));
            lines.add(net.minecraft.network.chat.Component.literal("Right-click to clear, then re-bind.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * Whether the player is holding a wrench in either hand.
     *
     * <p>The exact condition {@link AirlockTerminalPayload} arms on — a wrench in the
     * inventory but not in a hand cannot arm a terminal — so the panel promises only what the
     * click can deliver, rather than sending the player to a terminal that will refuse them.
     */
    private boolean holdsWrench() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
                return true;
            }
        }
        return false;
    }

    @LDLRegisterClient(name = "airlock_body", registry = "ldlib2:ui_element_renderer")
    public static final class AirlockBodyRenderer
            extends DelegatingUIElementRenderer<AirlockBody, AirlockBodyRenderer> {
        @Override
        public Class<AirlockBody> type() {
            return AirlockBody.class;
        }

        @Override
        public void drawBackgroundAdditional(AirlockBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.lastMouseX = guiContext.mouseX;
            element.lastMouseY = guiContext.mouseY;
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY(), guiContext.mouseX, guiContext.mouseY);
        }
    }
}
