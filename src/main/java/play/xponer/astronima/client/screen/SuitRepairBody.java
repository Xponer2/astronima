package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.client.ModKeybinds;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.network.SuitRepairDonePayload;
import play.xponer.astronima.sim.suit.SuitSubsystem;
import play.xponer.astronima.sim.suit.repair.CartridgeSeating;
import play.xponer.astronima.sim.suit.repair.GloveLeakHunt;
import play.xponer.astronima.sim.suit.repair.InsulationRoute;
import play.xponer.astronima.sim.suit.repair.LatchSequence;
import play.xponer.astronima.sim.suit.repair.RegulatorCalibration;
import play.xponer.astronima.sim.suit.repair.RepairTask;
import play.xponer.astronima.sim.suit.repair.RepairTasks;
import play.xponer.astronima.sim.suit.repair.SealPressureTest;
import play.xponer.astronima.sim.suit.repair.SolderJoints;

/**
 * The repair bench's own picture and input, ported from the old {@code SuitRepairScreen}
 * verbatim — {@link BiomonitorBody}'s "screen with no menu" shape. Six subsystems, six different
 * instruments; the screen renders whichever the task is and forwards input to it, deciding
 * nothing itself (every rule lives in {@code sim/suit/repair} and is unit-tested).
 *
 * <p><strong>One thing changed, not just moved.</strong> The old screen's {@code keyReleased}
 * override fired {@link SealPressureTest#release} the instant the repair-hold key came up.
 * LDLib2's key events route by UI focus, and this element holds none of the vanilla text-input
 * kind of focus a key event would need — rather than gamble on focus routing for one edge case,
 * the release is detected the same way the hold already was: by polling the raw key state every
 * frame ({@link #pollHeldInput}) and noticing the down-to-up edge, which needs nothing from the
 * framework at all.
 */
public final class SuitRepairBody extends UIElement {

    static final int WIDTH = 236;
    private static final int TEXT_WIDTH = WIDTH - 24;
    static final int HEIGHT = 150;

    private static final int ROUTE_MID_OFFSET = 46;
    private static final int ROUTE_SPAN = 40;

    private final SuitSubsystem subsystem;
    private final RepairTask task;

    private long lastFrameMs = System.currentTimeMillis();
    private boolean finished;
    private boolean repairKeyWasDown;
    private int lastMouseX, lastMouseY;

    public SuitRepairBody(SuitSubsystem subsystem) {
        this.subsystem = subsystem;
        this.task = RepairTasks.forSubsystem(subsystem);
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);

        addEventListener(UIEvents.MOUSE_DOWN, event -> onClick((int) event.x, (int) event.y));
        addEventListener(UIEvents.MOUSE_UP, event -> onRelease());
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    // --------------------------------------------------------------------- lifecycle

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        double dtSeconds = Math.min(0.1, (now - lastFrameMs) / 1000.0);
        lastFrameMs = now;

        steer(left, top, mouseX, mouseY);
        if (!finished) {
            pollHeldInput(dtSeconds);
            task.tick(dtSeconds);
            if (task.isComplete()) {
                complete();
            }
        }

        drawFrame(graphics, left, top);
        int workTop = top + 30;
        switch (task) {
            case SealPressureTest seal -> drawSeal(graphics, seal, left, workTop);
            case LatchSequence latches -> drawLatches(graphics, latches, left, workTop, mouseX, mouseY);
            case RegulatorCalibration dial -> drawRegulator(graphics, dial, left, workTop);
            case CartridgeSeating push -> drawSeating(graphics, push, left, workTop);
            case InsulationRoute route -> drawRoute(graphics, route, left, workTop);
            case SolderJoints board -> drawBoard(graphics, board, left, workTop);
            case GloveLeakHunt glove -> drawGlove(graphics, glove, left, workTop);
            default -> {}
        }
        drawFooter(graphics, left, top);
    }

    private void drawGlove(GuiGraphicsExtractor graphics, GloveLeakHunt glove, int left, int y) {
        int x = left + 18;
        int w = WIDTH - 36;

        graphics.fill(x, y + 22, x + w, y + 26, 0xFF2A3138);
        int at = x + (int) (glove.position() * w);
        graphics.fill(at - 2, y + 16, at + 3, y + 32, HudScale.COLOR_VALUE);

        int loudness = (int) (glove.bubbling() * 46);
        for (int i = 0; i < loudness / 3; i++) {
            int bx = at - 6 + (i * 5) % 13;
            graphics.fill(bx, y + 14 - i * 2, bx + 2, y + 16 - i * 2, HudScale.COLOR_INFO);
        }
        graphics.fill(x, y + 44, x + w, y + 50, 0xFF1A1F24);
        graphics.fill(x, y + 44, x + (int) (glove.bubbling() * w), y + 50, HudScale.COLOR_INFO);

        MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.glove.pressure"),
                x, y + 58, TEXT_WIDTH, HudScale.COLOR_LABEL);
        graphics.fill(x + 60, y + 58, x + 60 + 80, y + 64, 0xFF1A1F24);
        graphics.fill(x + 60, y + 58, x + 60 + (int) (glove.pressure() * 80), y + 64,
                glove.pressure() < 0.25 ? HudScale.COLOR_WARN : HudScale.COLOR_VALUE);
        if (glove.missedPatches() > 0) {
            MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.glove.patched",
                            glove.missedPatches()),
                    x, y + 72, TEXT_WIDTH, HudScale.COLOR_WARN);
        }
    }

    private void drawFrame(GuiGraphicsExtractor graphics, int left, int top) {
        MachineFrame.panel(graphics, left, top, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(),
                Component.translatable("astronima.suit.repairing",
                        Component.literal(subsystem.displayName())),
                left + 8, top + 8, TEXT_WIDTH, HudScale.COLOR_VALUE);
        MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.hint." + task.hintKey()),
                left + 8, top + 19, TEXT_WIDTH, HudScale.COLOR_LABEL);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int left, int top) {
        int barY = top + HEIGHT - 10;
        int barW = WIDTH - 16;
        graphics.fill(left + 8, barY, left + 8 + barW, barY + 4, HudScale.COLOR_TRACK);
        int filled = Math.round(barW * Math.clamp(task.progress(), 0f, 1f));
        if (filled > 0) {
            graphics.fill(left + 8, barY, left + 8 + filled, barY + 4, HudScale.COLOR_GOOD);
        }
    }

    private void complete() {
        finished = true;
        ClientPacketDistributor.sendToServer(
                new SuitRepairDonePayload(subsystem.ordinal(), task.quality()));
        Minecraft.getInstance().setScreen(null);
    }

    // ------------------------------------------------------ helmet seal: apply, watch

    private void drawSeal(GuiGraphicsExtractor graphics, SealPressureTest seal, int left, int y) {
        int x = left + 12;
        int w = WIDTH - 24;

        graphics.fill(x, y, x + w, y + 10, HudScale.COLOR_TRACK);
        int bandStart = x + (int) (w * SealPressureTest.COVERAGE_MIN / 1.6);
        int bandEnd = x + (int) (w * SealPressureTest.COVERAGE_MAX / 1.6);
        graphics.fill(bandStart, y, bandEnd, y + 10, 0x552FBF6B);
        int coverage = x + (int) (w * Math.min(1.6, seal.coverage()) / 1.6);
        graphics.fill(coverage - 1, y - 2, coverage + 1, y + 12, HudScale.COLOR_VALUE);
        MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.seal.coverage"),
                x, y + 14, TEXT_WIDTH, HudScale.COLOR_LABEL);

        int traceTop = y + 30;
        int traceH = 44;
        graphics.fill(x, traceTop, x + w, traceTop + traceH, 0xFF141A20);
        int pressureY = traceTop + traceH
                - (int) (traceH * seal.pressureKPa() / SealPressureTest.TEST_PRESSURE_KPA);
        int drawn = (int) (w * seal.testProgress());
        int colour = seal.pressureKPa() > SealPressureTest.FAIL_PRESSURE_KPA
                ? HudScale.COLOR_GOOD : HudScale.COLOR_BAD;
        if (seal.phase() != SealPressureTest.Phase.APPLYING) {
            graphics.fill(x, pressureY, x + Math.max(1, drawn), pressureY + 2, colour);
        }
        int failY = traceTop + traceH
                - (int) (traceH * SealPressureTest.FAIL_PRESSURE_KPA / SealPressureTest.TEST_PRESSURE_KPA);
        graphics.fill(x, failY, x + w, failY + 1, 0x66E2564A);
        MachineFrame.value(graphics, font(), String.format("%.1f kPa", seal.pressureKPa()),
                x, traceTop + traceH + 3, TEXT_WIDTH, colour);
    }

    // ------------------------------------------------------- tank mount: order, torque

    private void drawLatches(GuiGraphicsExtractor graphics, LatchSequence latches, int left, int y,
                             int mouseX, int mouseY) {
        int centreX = left + WIDTH / 2;
        int centreY = y + 44;
        int radius = 40;

        for (int i = 0; i < LatchSequence.LATCH_COUNT; i++) {
            int lx = centreX + (int) (radius * Math.cos(latchAngle(i))) - 6;
            int ly = centreY + (int) (radius * Math.sin(latchAngle(i))) - 6;
            int colour = switch (latches.stateOf(i)) {
                case TIGHT -> HudScale.COLOR_GOOD;
                case STRIPPED -> HudScale.COLOR_BAD;
                case UNTOUCHED -> i == latches.expectedLatch()
                        ? HudScale.COLOR_INFO : HudScale.COLOR_TRACK;
            };
            graphics.fill(lx, ly, lx + 12, ly + 12, colour);
        }

        int gaugeX = left + 12;
        int gaugeY = y + 92;
        int gaugeW = WIDTH - 24;
        graphics.fill(gaugeX, gaugeY, gaugeX + gaugeW, gaugeY + 8, HudScale.COLOR_TRACK);
        int specStart = gaugeX + (int) (gaugeW * LatchSequence.SPEC_MIN / 1.6);
        int specEnd = gaugeX + (int) (gaugeW * LatchSequence.SPEC_MAX / 1.6);
        graphics.fill(specStart, gaugeY, specEnd, gaugeY + 8, 0x552FBF6B);
        if (latches.holding() >= 0) {
            int mark = gaugeX + (int) (gaugeW * Math.min(1.6, latches.torque()) / 1.6);
            graphics.fill(mark - 1, gaugeY - 2, mark + 1, gaugeY + 10, HudScale.COLOR_VALUE);
        }
    }

    private static double latchAngle(int index) {
        return index * Math.PI * 2 / LatchSequence.LATCH_COUNT - Math.PI / 2;
    }

    private int latchAt(int left, int mouseX, int mouseY, int y) {
        int centreX = left + WIDTH / 2;
        int centreY = y + 44;
        for (int i = 0; i < LatchSequence.LATCH_COUNT; i++) {
            int lx = centreX + (int) (40 * Math.cos(latchAngle(i)));
            int ly = centreY + (int) (40 * Math.sin(latchAngle(i)));
            if (Math.hypot(mouseX - lx, mouseY - ly) <= 10) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------- regulator: continuous nulling

    private void drawRegulator(GuiGraphicsExtractor graphics, RegulatorCalibration dial, int left, int y) {
        int x = left + 12;
        int w = WIDTH - 24;

        int centre = x + w / 2;
        graphics.fill(x, y + 18, x + w, y + 20, HudScale.COLOR_TRACK);
        int tolerance = (int) (w * RegulatorCalibration.TOLERANCE * 2);
        graphics.fill(centre - tolerance, y + 14, centre + tolerance, y + 24, 0x332FBF6B);
        int needle = centre + (int) (w * dial.error());
        needle = Math.clamp(needle, x, x + w);
        graphics.fill(needle - 1, y + 8, needle + 1, y + 30, dial.isNulled()
                ? HudScale.COLOR_GOOD : HudScale.COLOR_WARN);

        int dialY = y + 48;
        graphics.fill(x, dialY, x + w, dialY + 6, HudScale.COLOR_TRACK);
        int knob = x + (int) (w * dial.dial());
        graphics.fill(knob - 3, dialY - 3, knob + 3, dialY + 9, HudScale.COLOR_VALUE);

        int dwellY = y + 70;
        MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.regulator.dwell"),
                x, dwellY, TEXT_WIDTH, HudScale.COLOR_LABEL);
        graphics.fill(x, dwellY + 12, x + w, dwellY + 18, HudScale.COLOR_TRACK);
        int held = (int) (w * dial.dwellSeconds() / RegulatorCalibration.DWELL_REQUIRED_SECONDS);
        if (held > 0) {
            graphics.fill(x, dwellY + 12, x + Math.min(w, held), dwellY + 18, HudScale.COLOR_INFO);
        }
    }

    // --------------------------------------------- scrubber bay: one committed push

    private void drawSeating(GuiGraphicsExtractor graphics, CartridgeSeating push, int left, int y) {
        int x = left + 12;
        int w = WIDTH - 24;
        int barY = y + 30;

        graphics.fill(x, barY, x + w, barY + 22, HudScale.COLOR_TRACK);
        int seatStart = x + (int) (w * CartridgeSeating.SEAT_DEPTH);
        int seatEnd = x + (int) (w * CartridgeSeating.CRUSH_DEPTH);
        graphics.fill(seatStart, barY, seatEnd, barY + 22, 0x552FBF6B);
        graphics.fill(seatEnd, barY, x + w, barY + 22, 0x33E2564A);

        int face = x + (int) (w * push.depth());
        graphics.fill(x, barY + 4, Math.max(x + 2, face), barY + 18, HudScale.COLOR_VALUE);

        int resistY = barY + 32;
        MachineFrame.value(graphics, font(), Component.translatable("astronima.repair.seating.resistance"),
                x, resistY, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int resistance = (int) (w * (1.0 - push.pushSpeed() / 0.5));
        graphics.fill(x, resistY + 12, x + w, resistY + 18, HudScale.COLOR_TRACK);
        if (resistance > 0) {
            graphics.fill(x, resistY + 12, x + Math.min(w, resistance), resistY + 18,
                    HudScale.COLOR_WARN);
        }
    }

    // ------------------------------------------------- thermal layer: steady tracing

    private void drawRoute(GuiGraphicsExtractor graphics, InsulationRoute route, int left, int y) {
        int x = left + 12;
        int w = WIDTH - 24;
        int mid = y + ROUTE_MID_OFFSET;
        int span = ROUTE_SPAN;

        for (int px = 0; px < w; px++) {
            double at = px / (double) w;
            int centre = mid + (int) (span * InsulationRoute.channelCentreAt(at));
            int half = Math.max(1, (int) (span * InsulationRoute.CHANNEL_HALF_WIDTH));
            boolean done = at <= route.position();
            graphics.fill(x + px, centre - half, x + px + 1, centre + half,
                    done ? 0x552FBF6B : 0xFF1A2129);
        }

        for (int i = 1; i < InsulationRoute.ANCHORS; i++) {
            int ax = x + (int) (w * i / (double) InsulationRoute.ANCHORS);
            graphics.fill(ax, mid - span - 4, ax + 1, mid + span + 4, 0x66FFFFFF);
        }

        int threadX = x + (int) (w * route.position());
        int threadY = mid + (int) (span * route.lateral());
        graphics.fill(threadX - 2, threadY - 2, threadX + 3, threadY + 3,
                route.isInChannel() ? HudScale.COLOR_GOOD : HudScale.COLOR_WARN);

        double pressure = route.slipPressure();
        if (pressure > 0) {
            int warnW = (int) (w * pressure);
            int colour = pressure > 0.65 ? HudScale.COLOR_BAD : HudScale.COLOR_WARN;
            graphics.fill(x, mid + span + 8, x + warnW, mid + span + 11, colour);
        }
    }

    // ------------------------------------------------------ status display: accuracy

    private void drawBoard(GuiGraphicsExtractor graphics, SolderJoints board, int left, int y) {
        int x = left + 18;
        int w = WIDTH - 36;
        int h = 88;
        graphics.fill(x, y, x + w, y + h, 0xFF16241C);

        for (int i = 0; i < board.padCount(); i++) {
            int px = x + (int) (w * board.padX(i));
            int py = y + (int) (h * board.padY(i));
            int colour = board.isBridged(i) ? HudScale.COLOR_BAD
                    : board.isSoldered(i) ? HudScale.COLOR_GOOD
                    : HudScale.COLOR_LABEL;
            int size = board.isBridged(i) ? 5 : 3;
            graphics.fill(px - size, py - size, px + size, py + size, colour);
        }
    }

    // ------------------------------------------------------------------------ input

    /** Continuous pointer input, for the two tasks that are steered rather than clicked. */
    private void steer(int left, int top, int mouseX, int mouseY) {
        if (finished) {
            return;
        }
        int x = left + 12;
        int w = WIDTH - 24;
        switch (task) {
            case RegulatorCalibration dial -> dial.setDial((mouseX - x) / (double) w);
            case InsulationRoute route ->
                    route.steer((mouseY - (top + 30 + ROUTE_MID_OFFSET)) / (double) ROUTE_SPAN);
            default -> {}
        }
    }

    private void onClick(int mouseX, int mouseY) {
        if (finished) {
            return;
        }
        int left = (int) getContentX();
        int top = (int) getContentY();
        switch (task) {
            case SealPressureTest seal -> {
                if (seal.phase() == SealPressureTest.Phase.TESTING) {
                    seal.restart();
                }
            }
            case LatchSequence latches -> {
                int latch = latchAt(left, mouseX, mouseY, top + 30);
                if (latch >= 0) {
                    if (latches.stateOf(latch) == LatchSequence.LatchState.STRIPPED) {
                        latches.rework(latch);
                    } else {
                        latches.beginTorque(latch);
                    }
                }
            }
            case CartridgeSeating push -> push.beginPush();
            case GloveLeakHunt glove -> glove.patch();
            case SolderJoints board -> {
                int x = left + 18;
                int w = WIDTH - 36;
                board.touch((mouseX - x) / (double) w, (mouseY - (top + 30)) / 88.0);
            }
            default -> {}
        }
    }

    private void onRelease() {
        if (finished) {
            return;
        }
        switch (task) {
            case LatchSequence latches -> latches.release();
            case CartridgeSeating push -> push.release();
            default -> {}
        }
    }

    /**
     * Continuous held input, sampled per frame — and, on the down-to-up edge, the release the
     * old screen's {@code keyReleased} override used to fire directly (see the class doc).
     */
    private void pollHeldInput(double dtSeconds) {
        if (!(task instanceof SealPressureTest seal)) {
            return;
        }
        boolean down = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),
                ModKeybinds.REPAIR_HOLD.getKey().getValue());
        if (down) {
            seal.applyHold(dtSeconds);
        } else if (repairKeyWasDown) {
            seal.release();
        }
        repairKeyWasDown = down;
    }

    @LDLRegisterClient(name = "suit_repair_body", registry = "ldlib2:ui_element_renderer")
    public static final class SuitRepairBodyRenderer
            extends DelegatingUIElementRenderer<SuitRepairBody, SuitRepairBodyRenderer> {
        @Override
        public Class<SuitRepairBody> type() {
            return SuitRepairBody.class;
        }

        @Override
        public void drawBackgroundAdditional(SuitRepairBody element, IGUIContext context) {
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
