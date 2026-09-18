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
import play.xponer.astronima.client.hud.CrusherView;
import play.xponer.astronima.client.hud.ElectrolysisCellView;
import play.xponer.astronima.client.hud.FluidBedView;
import play.xponer.astronima.client.hud.ForgeView;
import play.xponer.astronima.client.hud.Layout;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.MachinePanel;
import play.xponer.astronima.client.hud.PanelLayout;
import play.xponer.astronima.client.hud.RefinerView;
import play.xponer.astronima.client.hud.RetortView;
import play.xponer.astronima.client.hud.SeparatorView;
import play.xponer.astronima.client.hud.Slider;
import play.xponer.astronima.client.hud.SlsPrinterView;
import play.xponer.astronima.client.hud.WinnowerView;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.menu.ProcessingMenu.Kind;
import play.xponer.astronima.menu.ProcessingUiHolder;
import play.xponer.astronima.network.MachineSettingPayload;
import play.xponer.astronima.network.SlsControlPayload;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.metal.CentrifugalBed;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.metal.FluidizedBedControl;
import play.xponer.astronima.sim.metal.LaserSintering;
import play.xponer.astronima.sim.ore.ChlorateDecomposition;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.Dehydroxylation;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;
import play.xponer.astronima.sim.ore.MoltenElectrolysis;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.ore.RetortAdvice;
import play.xponer.astronima.sim.ore.RetortProcess;
import play.xponer.astronima.sim.ore.SeparatorAdvice;
import play.xponer.astronima.sim.ore.SolarConcentrator;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;

/**
 * The machine panel's own picture, ported from the old {@code ProcessingScreen} verbatim —
 * {@code design/ui-ldlib2-machines.md} §4. Every {@code draw*} method here is the exact method
 * that file had, with {@code leftPos}/{@code topPos} (this element's own {@link #getContentX()}/
 * {@link #getContentY()} now) and {@code menu} ({@link #holder} now) the only things that moved.
 *
 * <p>Purely visual — the item slots, the player's own inventory, and the two draggable regions
 * (the dial, and the SLS plane) are separate LDLib2 elements {@link ProcessingUi} lays on top of
 * this one, each a plain hit-tested {@link UIElement} that calls back into the methods here
 * ({@link #onDialMouseDown}, {@link #onDialDrag}, {@link #onDialReleased},
 * {@link #onPlaneMouseDown}) rather than this element trying to hand-roll hit-testing LDLib2
 * already does correctly for any ordinary child.
 */
public final class MachineBody extends UIElement {

    private static final int DIAL_X = 8;
    private static final int READOUT_WIDTH = ProcessingMenu.PANEL_WIDTH - DIAL_X - 8;
    private static final int DIAL_W = 160;
    private static final int DIAL_H = 6;

    private final ProcessingUiHolder holder;
    /** Fetched lazily, never in the constructor or a field initializer: this element is
     *  built from {@link com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder#createUI},
     *  which runs on the server too (opening the menu), and {@code Minecraft.getInstance()}
     *  is null there. Only {@link #drawMachine} and what it calls ever touch this, and that
     *  only ever runs from the renderer — client-side, never the server. */
    private static Font font() {
        return Minecraft.getInstance().font;
    }
    private final int panelHeight;

    private boolean draggingDial;
    /** Where the knob is drawn while dragging, before the value is committed. NaN when not dragging. */
    private double dragPreviewSetting = Double.NaN;

    /** The most recent mouse position this element was rendered at — absolute screen space, the
     *  same frame {@link #getContentX()} lives in. An input event fires on its own call stack, not
     *  from inside a render pass, so the click/drag handlers below read this rather than an event
     *  coordinate whose space would need re-deriving. At most one frame stale, which a drag cannot
     *  feel. */
    private int lastMouseX, lastMouseY;

    public MachineBody(ProcessingUiHolder holder) {
        this.holder = holder;
        this.panelHeight = MachinePanel.height(ProcessingMenu.panelKind(holder.kind()));
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(MachinePanel.WIDTH);
        getLayout().height(panelHeight);
    }

    public ProcessingUiHolder holder() {
        return holder;
    }

    /** Where {@link #controlBox} sits, in this element's own coordinates — for
     *  {@link ProcessingUi} to size the interactive dial region without duplicating the switch. */
    public Layout.Box controlBoxLocal() {
        return controlBox();
    }

    // --------------------------------------------------------------------- rendering

    private void drawMachine(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        MachineFrame.panel(graphics, x, y, MachinePanel.WIDTH, panelHeight);

        // The name, lost silently in the LDLib2 port: ModularUIContainerScreen's own
        // extractLabels is a stub, unlike vanilla AbstractContainerScreen's automatic title
        // render the old screen relied on — every machine panel read exactly the same until
        // this, whatever it actually was (reported live as looking unchanged / "ии слоп").
        MachineFrame.value(graphics, font(), holder.getDisplayName().getString().toUpperCase(
                java.util.Locale.ROOT), x + 8, y + 7, MachinePanel.WIDTH - 16, MachineFrame.TEXT);

        int row = y + ProcessingMenu.SLOT_ROW_Y;
        // Feed and product wells differ on sight (machine-io.md M3): the feed is the
        // ordinary dark socket with a down-arrow watermark, the products are brighter
        // with an up-arrow — which is which reads before any item is in either.
        MachineFrame.feedSlot(graphics, x + ProcessingMenu.SLOT_IN_X, row);
        // Only machines that split their feed have two outputs. The forge shares the crusher's
        // shape, and testing for CRUSHER rather than "not a two-product machine" drew it a third
        // slot well with no slot behind it.
        if (oneSlotMachine()) {
            // No product well at all: the whole product is gas, vented into the room, and there
            // is no item slot behind it — drawing a well with nothing behind it is exactly the
            // "slot that looks real but is not" trap the crusher/forge comment above already
            // names, so these machines get none rather than a decorative one.
        } else if (!twoProductMachine()) {
            MachineFrame.productSlot(graphics, x + ProcessingMenu.SLOT_OUT_X, row);
        } else {
            MachineFrame.productSlot(graphics, x + ProcessingMenu.SLOT_OUT_X, row - 10);
            MachineFrame.productSlot(graphics, x + ProcessingMenu.SLOT_OUT_X, row + 18);
        }

        // Process bar between input and output: where the batch actually is.
        WorkState state = holder.workState();
        MachineFrame.bar(graphics, x + 48, row + 5, 62, 7,
                holder.progress(), state.isWorking() ? MachineFrame.ACCENT : MachineFrame.WARN);
        drawWorkState(graphics, state, x + 48, row + 15);

        drawControl(graphics, x, y, mouseX, mouseY);
        drawReadouts(graphics, x, y);
        MachineFrame.divider(graphics, x + 7, y + ProcessingMenu.INVENTORY_LABEL_Y - 6,
                MachinePanel.WIDTH - 14);
    }

    /**
     * Why the bar is where it is, right underneath the bar.
     *
     * <p>Reported from play: <em>"if you take the item out the progress is kept but
     * stops"</em>. Keeping it is deliberate — half-crushed rock does not un-crush itself —
     * but a bar frozen at 40 % with nothing to explain it is indistinguishable from a
     * machine that has broken, and the kindness only reads as one if the player can tell
     * the two apart. So the bar goes amber when it is holding, and says what it is holding
     * for; the remedy sits under it, in the same grammar the biomonitor uses for an ailment.
     */
    /** Room the power chip takes on the work-state line, whenever it is drawn at all. */
    private static final int POWER_CHIP_WIDTH = 34;

    private void drawWorkState(GuiGraphicsExtractor graphics, WorkState state, int x, int y) {
        int chipWidth = state.isWorking() ? POWER_CHIP_WIDTH : 0;
        MachineFrame.value(graphics, font(), state.label(), x, y, READOUT_WIDTH - chipWidth,
                state.isWorking() ? MachineFrame.GOOD : MachineFrame.WARN);
        if (state.isHolding()) {
            MachineFrame.label(graphics, font(), state.remedy(), x, y + 10, READOUT_WIDTH);
        }
        if (state.isWorking()) {
            drawPowerChip(graphics, state, x + READOUT_WIDTH - chipWidth, y);
        }
    }

    /**
     * What is actually driving the batch: the handle, or the wire.
     *
     * <p>Reported live: <em>"машины в которых всего 2 слота что они берут взамен, энергию?
     * почему тогда шкалы энергии нету?"</em> — every machine here can run hand-cranked or wired,
     * and until this existed both "nobody has wired it up" and "wired, but starved by a bad run"
     * read as the exact same silent {@code CREEPING} state, with no way to tell them apart.
     */
    private void drawPowerChip(GuiGraphicsExtractor graphics, WorkState state, int x, int y) {
        if (state == WorkState.CRANKING) {
            MachineFrame.value(graphics, font(), "HAND", x, y, POWER_CHIP_WIDTH, MachineFrame.GOOD);
            return;
        }
        float fraction = (float) holder.poweredFraction();
        MachineFrame.value(graphics, font(), Math.round(fraction * 100) + "%", x, y,
                POWER_CHIP_WIDTH, MachineFrame.colourFor(fraction));
    }

    /**
     * The control, drawn where the machine says its control is.
     *
     * <p>Every machine used to put its dial on the same strip fourteen pixels above the readouts,
     * whatever the machine was — which was fairly described as lazy. Each picture now declares a
     * {@code control()} box on the thing the dial actually drives: the crusher's under its jaws,
     * the bed's directly under the spin window it has to land in, the refiner's under the
     * temperature scale it <em>is</em>. The layout scan refuses a control that lands on anything
     * else, so moving one is a change somebody can make without opening the game.
     */
    private void drawControl(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        if (holder.isSetByWrench()) {
            drawCalibration(graphics, x, y);
            return;
        }
        Layout.Box box = controlBox();
        int bx = x + DIAL_X + box.x();
        // The declared box owns the knob's overhang; the bar itself sits four pixels down inside
        // it. Drawing the bar at the box top is how a handle ends up on top of the thing above it
        // with the layout scan perfectly happy.
        int by = y + ProcessingMenu.READOUT_Y + box.y() + 4;
        int width = box.width();

        // No floating caption any more. It used to sit above a strip of chrome and had to, because
        // a bar in the same place on every machine says nothing about itself. A control that lives
        // on the jaws it moves does not need a label reading "jaw gap" - and where the picture is
        // dense, a caption is one more thing overlapping something. The name is on hover instead,
        // which is the convention the rest of this panel already uses (PLAN rule 23).
        if (overDial(mouseX, mouseY)) {
            MachineFrame.outline(graphics, bx - 2, by - 3, width + 4, DIAL_H + 6,
                    MachineFrame.ACCENT);
            graphics.setComponentTooltipForNextFrame(font(),
                    java.util.List.of(net.minecraft.network.chat.Component.literal(controlName())),
                    mouseX, mouseY);
        }
        if (holder.kind() == Kind.WINNOWER) {
            // No dial, and no fake one either: drawing a handle a player cannot move is
            // worse than drawing nothing, because they will try. The bar shows how well
            // this feed can be classified, which is the number the grind decided.
            MachineFrame.bar(graphics, bx, by, width, DIAL_H,
                    (float) holder.setting(), MachineFrame.ACCENT);
            return;
        }
        if (holder.kind() == Kind.RETORT) {
            // No knob: the mirror aims itself (SolarRetortBlockEntity#focus()), chasing the
            // safe point inside the window every tick as the sun moves. The window is still
            // worth drawing - it is how a player reads why the retort has stalled TOO_COLD
            // rather than working, which the mark alone would not explain.
            float sun = Math.max(holder.sunlight(), 0.01f);
            RetortProcess process = holder.retortProcess();
            MachineFrame.window(graphics, bx, by, width, DIAL_H,
                    (float) SolarConcentrator.focusFor(sun, process.onsetK()),
                    (float) SolarConcentrator.focusFor(sun, process.completeK()),
                    (float) SolarConcentrator.focusFor(sun, process.spoilK()),
                    (float) holder.setting(), MachineFrame.ACCENT);
            return;
        }
        if (holder.kind() == Kind.REFINER) {
            // No control here either: the vertical temperature scale drawn in the refiner's
            // own picture (drawRefinerPicture) already marks both thresholds and the live
            // reading, and the vessel now chooses its own temperature
            // (CarbonylRefinerBlockEntity#setpointK()) - a second bar and knob repeating the
            // same number here would be decoration, the same reasoning WINNOWER's bar exists
            // to avoid faking.
            return;
        }
        if (holder.kind() == Kind.FLUIDBED) {
            // Same reasoning as the refiner: the bed's own picture (drawFluidBedPicture)
            // already draws the spin bar with the boiling band painted on it and the live
            // marker on top, and the drum now matches its own spin to the feed
            // (FluidizedBedBlockEntity#setting()) - nothing left here for a second control to
            // add.
            return;
        }
        if (holder.kind() == Kind.ELECTROLYSIS) {
            // No control at all: what this cell reaches is set by which electrode is
            // installed (ElectrolysisCellBlockEntity#target()), read off the electrode slot
            // itself, not off anything drawn or dragged in this strip.
            return;
        }
        if (holder.kind() == Kind.SLS) {
            // Nothing here either: the plane in drawSlsPrinterPicture is both the readout and
            // the control, dragged directly rather than read off a bar underneath it — this
            // strip has nothing left to add.
            return;
        }
        if (holder.kind() == Kind.CRACKING_TOWER || holder.kind() == Kind.POLYMERIZER
                || holder.kind() == Kind.WATER_ELECTROLYZER || holder.kind() == Kind.SABATIER_REACTOR
                || holder.kind() == Kind.BOSCH_REACTOR || holder.kind() == Kind.TROILITE_ROASTER
                || holder.kind() == Kind.SULFURIC_ACID_PLANT || holder.kind() == Kind.HEAVY_WATER_CELL
                || holder.kind() == Kind.TITANIUM_CELL || holder.kind() == Kind.INDUCTION_FURNACE
                || holder.kind() == Kind.FREEZE_DRYER || holder.kind() == Kind.DOWNS_CELL
                || holder.kind() == Kind.ZONE_REFINER || holder.kind() == Kind.HF_DIGESTER
                || holder.kind() == Kind.ETCH_STATION) {
            // None of these has a control: each reacts whatever the feed slot (and, for most of
            // them, the room) supplies it — nothing here for a player to set. The heavy water
            // cell's own cascade math, the titanium cell's own reduction, the furnace's own melt,
            // the freeze dryer's own vacuum/LN2 gate, the Downs cell's own electrolysis and the
            // etch station's own real fixed 1:6 stoichiometry have no dial either: one batch,
            // always the same reaction.
            return;
        }
        MachineFrame.bar(graphics, bx, by, width, DIAL_H,
                (float) holder.setting(), MachineFrame.ACCENT);

        // A raised handle, bevelled like the panel so it reads as a physical control. Placed by
        // the same arithmetic that reads the mouse, so grabbing it does not make it jump - and at
        // the value the operator asked for, because that is the one the mouse sets. Where the
        // machine has actually got to is the mark below.
        //
        // While actively dragging, drawn from the local preview rather than holder.calibratedTo():
        // the server value only updates once, on release (see onDialReleased), so a live-tracking
        // knob needs a value that does not wait on a round trip.
        double knobSetting = draggingDial && !Double.isNaN(dragPreviewSetting)
                ? dragPreviewSetting : holder.calibratedTo();
        MachineFrame.knob(graphics, Slider.knobX(knobSetting, bx, width), by - 3,
                Slider.KNOB_WIDTH, DIAL_H + 6);
        drawDriftMark(graphics, bx, by, width);
    }

    /**
     * On a machine that kept its dial: where the setting really is, under where it was asked for.
     *
     * <p>The refiner's element ages and the bed's bearings drag, so the vessel runs colder and the
     * drum slower than the handle says. Without this the drift would be a hidden number in exactly
     * the window built to show the player what the machine is doing — they would watch a dial
     * sitting on the right answer and a machine failing to give it (rule 25).
     */
    private void drawDriftMark(GuiGraphicsExtractor graphics, int x, int y, int width) {
        if (!holder.drift().drifts()) {
            return;
        }
        // A wrench stops these machines for three seconds too, and a machine that stops without
        // saying why is the thing this panel keeps being rewritten to stop being. The dial is
        // still the player's, so the line goes under it rather than replacing it.
        double turning = holder.calibrating();
        if (turning > 0) {
            MachineFrame.value(graphics, font(), "re-zeroing - " + Math.round(turning * 100) + "%",
                    x, y + DIAL_H + 8, width + 40, MachineFrame.ACCENT);
            return;
        }
        boolean worth = holder.calibrationError()
                >= play.xponer.astronima.sim.machine.Calibration.WORTH_RESETTING;
        int actual = Slider.knobX(holder.setting(), x, width) + Slider.KNOB_WIDTH / 2;
        graphics.fill(actual, y + DIAL_H + 3, actual + 1, y + DIAL_H + 7,
                worth ? MachineFrame.BAD : MachineFrame.TEXT_DIM);
        if (worth) {
            MachineFrame.value(graphics, font(), holder.drift().fault() + " - wrench to re-zero",
                    x, y + DIAL_H + 8, width + 40, MachineFrame.BAD);
        }
    }

    /**
     * A wrench-set machine's calibration, where its dial used to be.
     *
     * <p>A status lamp, not a bar: this strip has never answered the mouse (three machines'
     * worth of a slider anyone could leave on one value forever was not a decision — see
     * {@code design/calibration.md}), but a bar with tick marks on it still <em>reads</em> as a
     * slider whether or not it is one, and a player who tries to drag a status is not wrong to
     * try. A lamp only ever reads as a status — the same instrument vocabulary
     * {@code design/presentation.md} §2 already uses for one.
     *
     * <p>What has gone wrong is the machine's own word for it, carried on {@code DATA_DRIFT}: a
     * crusher says its jaws are worn open and a forge says its ram spring has gone soft. One
     * sentence for all of them would have been the "generic machine readout" this panel keeps
     * being rewritten to stop being (rule 28).
     */
    private void drawCalibration(GuiGraphicsExtractor graphics, int panelX, int panelY) {
        Layout.Box box = controlBox();
        int x = panelX + DIAL_X + box.x();
        int y = panelY + ProcessingMenu.READOUT_Y + box.y() + 4;
        int width = box.width();

        double error = holder.calibrationError();
        boolean worth = error >= play.xponer.astronima.sim.machine.Calibration.WORTH_RESETTING;
        double turning = holder.calibrating();
        int lampColour = turning > 0 ? MachineFrame.ACCENT : worth ? MachineFrame.BAD : MachineFrame.GOOD;

        MachineFrame.bar(graphics, x, y, DIAL_H, DIAL_H, 1f, lampColour);

        String says = turning > 0
                ? "calibrating - " + Math.round(turning * 100) + "%"
                : worth ? holder.drift().fault() + " - reset with a wrench" : "set true";
        int textX = x + DIAL_H + 4;
        MachineFrame.value(graphics, font(), says, textX, y, width + 40 - DIAL_H - 4,
                turning > 0 ? MachineFrame.ACCENT
                        : worth ? MachineFrame.BAD : MachineFrame.TEXT_DIM);
    }

    /**
     * Where this machine keeps its control.
     *
     * <p>Read from the picture rather than from a constant, and hit-tested from the same call, so
     * the rectangle a player can grab is the rectangle they can see. The two used to be separate
     * literals, which is how a control ends up drawn in one place and draggable in another.
     */
    private Layout.Box controlBox() {
        return switch (holder.kind()) {
            // The crusher, separator and forge have no dial any more: they are set on the machine
            // with a wrench, and that strip is a readout of how far each has drifted since. A
            // slider you could leave on one value forever was not a decision, and three of these
            // machines had one (design/calibration.md).
            case CRUSHER -> CrusherView.control();
            case SEPARATOR -> SeparatorView.control();
            case FORGE -> ForgeView.control();
            case RETORT -> RetortView.control();
            case REFINER -> RefinerView.control();
            case FLUIDBED -> FluidBedView.control();
            // The winnowing table has no control at all; its bar reports what the grind decided.
            case WINNOWER -> WinnowerView.control();
            // Neither has a control: what the cell reaches is set by which electrode is
            // installed, not by anything drawn here.
            case ELECTROLYSIS -> ElectrolysisCellView.control();
            // No bar either: the plane drawn in drawSlsPrinterPicture is the real control,
            // dragged directly.
            case SLS -> SlsPrinterView.control();
            // Neither has a control at all — the tower cracks whatever is fed it, and the
            // polymerizer strings whatever ethylene the room has. Never actually drawn or
            // hit-tested: drawControl returns before using this box, and overDial excludes both
            // kinds outright — a case is still required because the switch is exhaustive.
            case CRACKING_TOWER, POLYMERIZER, WATER_ELECTROLYZER, SABATIER_REACTOR, BOSCH_REACTOR,
                    TROILITE_ROASTER, SULFURIC_ACID_PLANT, HEAVY_WATER_CELL, TITANIUM_CELL,
                    INDUCTION_FURNACE, IRON_SMELTER, FREEZE_DRYER, DOWNS_CELL, ZONE_REFINER,
                    HF_DIGESTER, ETCH_STATION, GRAPHITIZER, ALGAE_BIOREACTOR, ANAEROBIC_DIGESTER ->
                    new Layout.Box("none", 0, 0, 0, 0);
        };
    }

    /**
     * The live consequence of the control, beside the control.
     *
     * <p>For the crusher this is the entire tier expressed in three numbers: how fine
     * the grind is, how much of the mineral that frees, and what it costs to get
     * there. Watching liberation and cost climb together as the jaws close is how a
     * player learns the trade without being told it.
     */
    private void drawReadouts(GuiGraphicsExtractor graphics, int panelX, int panelY) {
        int x = panelX + DIAL_X;
        PanelLayout layout = new PanelLayout(panelY + ProcessingMenu.READOUT_Y);

        if (holder.kind() == Kind.CRUSHER) {
            drawCrusherReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.FORGE) {
            drawForgeReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.RETORT) {
            drawRetortReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.WINNOWER) {
            drawWinnowerReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.REFINER) {
            drawRefinerReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.FLUIDBED) {
            drawFluidBedReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.ELECTROLYSIS) {
            drawElectrolysisCellReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.SLS) {
            drawSlsPrinterReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.HEAVY_WATER_CELL) {
            drawHeavyWaterReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.IRON_SMELTER) {
            drawIronSmelterReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.SABATIER_REACTOR) {
            drawSabatierReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.BOSCH_REACTOR) {
            drawBoschReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.TROILITE_ROASTER) {
            drawTroiliteRoasterReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.SULFURIC_ACID_PLANT) {
            drawSulfuricAcidReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.POLYMERIZER) {
            drawPolymerizerReadouts(graphics, x, layout);
        } else if (holder.kind() == Kind.CRACKING_TOWER
                || holder.kind() == Kind.WATER_ELECTROLYZER || holder.kind() == Kind.TITANIUM_CELL
                || holder.kind() == Kind.INDUCTION_FURNACE || holder.kind() == Kind.FREEZE_DRYER
                || holder.kind() == Kind.DOWNS_CELL || holder.kind() == Kind.ZONE_REFINER
                || holder.kind() == Kind.HF_DIGESTER) {
            // Nothing extra: the shared bar and work-state line drawn above already say
            // everything these two report — a feed slot and a room reagent, not a second-order
            // number worth a picture of its own the way WINNOWER's bar or REFINER's column are.
        } else if (holder.kind() == Kind.ETCH_STATION) {
            drawEtchStationReadouts(graphics, x, layout);
        } else {
            drawSeparatorReadouts(graphics, x, layout);
        }
    }

    /**
     * The room reagents a Sabatier batch needs, real and otherwise invisible — the one thing this
     * machine hid before (design/machines.md's own Update section — "Cranking" that never finishes
     * used to give no way to tell "the room ran out" from "no power" apart).
     */
    private void drawSabatierReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "CO2", holder.reagentA());
        drawReagentBar(graphics, x, layout.row(), "H2", holder.reagentB());
    }

    /** As {@link #drawSabatierReadouts}: the same two reagents, the Bosch reaction's own ratio. */
    private void drawBoschReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "CO2", holder.reagentA());
        drawReagentBar(graphics, x, layout.row(), "H2", holder.reagentB());
    }

    /** The room oxygen the currently-loaded ore's own real sulfide content actually needs —
     *  dynamic, unlike every sibling reagent reading (design/machines.md's own Update section). */
    private void drawTroiliteRoasterReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "O2", holder.reagentA());
    }

    /** The only machine in the mod reading three room gases at once — all three, real. */
    private void drawSulfuricAcidReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "SO2", holder.reagentA());
        drawReagentBar(graphics, x, layout.row(), "O2", holder.reagentB());
        drawReagentBar(graphics, x, layout.row(), "H2O", holder.reagentC());
    }

    /** The room ethylene a batch needs — the polymerizer's own real, otherwise invisible input. */
    private void drawPolymerizerReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "C2H4", holder.reagentA());
    }

    /**
     * The one real reading a stalled etch station cannot show any other way: is the adjacent
     * cleanroom controller certifying this room clean enough to run at all (design/halogens.md
     * §41/§43)? Without this a station with both feeds loaded and no cleanroom nearby would look
     * identical to one waiting on real power, or one waiting on the room to finish certifying.
     */
    private void drawEtchStationReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        drawReagentBar(graphics, x, layout.row(), "CLEAN", holder.reagentA());
    }

    /** One reagent-stock bar: label, bar, percentage — the exact shape
     *  {@code drawIronSmelterReadouts}'s own flux-ratio row already uses, shared across every
     *  room-reagent machine above rather than five copies of the same three draw calls (rule 20). */
    private void drawReagentBar(GuiGraphicsExtractor graphics, int x, int y, String label,
                                double fraction) {
        MachineFrame.label(graphics, font(), label, x, y, READOUT_WIDTH);
        MachineFrame.bar(graphics, x + 44, y, 60, 7, (float) fraction,
                MachineFrame.colourFor((float) fraction));
        MachineFrame.value(graphics, font(), Math.round(fraction * 100) + "%",
                x + 110, y, READOUT_WIDTH, MachineFrame.TEXT);
    }

    /**
     * The cell's whole readout: what is actually in the feed slot right now, and how far that
     * still is from reactor-grade. No dial to draw — the cascade math has no player-set parameter,
     * just "run it again."
     */
    private void drawHeavyWaterReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        double fraction = holder.d2oFraction();
        MachineFrame.value(graphics, font(),
                String.format(java.util.Locale.ROOT, "D2O: %.4f%%", fraction * 100),
                x, layout.row(), READOUT_WIDTH,
                fraction >= 0.995 ? MachineFrame.GOOD : MachineFrame.TEXT);
        String advice = fraction >= 0.995
                ? "reactor-grade — bottle it"
                : "feed the output back in for another stage";
        MachineFrame.label(graphics, font(), advice, x, layout.row(), READOUT_WIDTH);
    }

    /** How clean the last batch actually was — the honest reading design/iron-smelter.md
     *  promises, since the smelter itself has no dial of its own to draw. */
    private void drawIronSmelterReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        double ratio = holder.fluxRatio();
        int y = layout.row();
        MachineFrame.label(graphics, font(), "FLUX RATIO", x, y, READOUT_WIDTH);
        MachineFrame.bar(graphics, x + 44, y, 60, 7, (float) ratio, MachineFrame.colourFor((float) ratio));
        MachineFrame.value(graphics, font(), Math.round(ratio * 100) + "%",
                x + 110, y, READOUT_WIDTH, MachineFrame.TEXT);
        String advice = ratio >= 0.995
                ? "fully fluxed — clean iron, real slag"
                : ratio <= 0.0
                        ? "no flux — crude iron, no slag"
                        : "under-fluxed — add magnesium oxide for a cleaner batch";
        MachineFrame.label(graphics, font(), advice, x, layout.row(), READOUT_WIDTH);
    }

    /**
     * The refiner draws itself: a column, gas going up it, and metal coming back out at the top.
     *
     * <p><strong>It had been wearing the separator's readout.</strong> Both fell through the same
     * else branch, so a carbonyl refiner reported a magnetic field strength and a recovery grade —
     * three numbers that mean nothing about it — and had done since it shipped. Nothing caught that
     * because nothing checks whether a machine's readout is <em>about</em> that machine.
     *
     * <p>The Mond process is two temperatures. Carbon monoxide over warm nickel makes a gas; carry
     * that gas somewhere hotter and it hands the nickel back, pure. Iron does the same at a
     * different temperature, and that gap is the whole separation — it is not a melt, it is one
     * metal volunteering to leave as a vapour while the other stays put.
     */
    private void drawRefinerReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        int temperature = holder.temperatureK();
        int top = layout.row();
        drawRefinerPicture(graphics, x, top, temperature);
        layout.gap(RefinerView.HEIGHT - PanelLayout.ROW_HEIGHT);

        boolean stranded = RefinerView.isStranded(temperature);
        String advice = stranded
                ? "carbonyl building up - too cool to give it back"
                : RefinerView.depositing(temperature) > 0.05
                        ? "lifting nickel and laying it back down, pure"
                        : "cold - nothing is moving";
        MachineFrame.value(graphics, font(), advice, x, layout.row(), READOUT_WIDTH,
                stranded ? MachineFrame.WARN : MachineFrame.GOOD);
    }

    private void drawRefinerPicture(GuiGraphicsExtractor graphics, int x, int y, int temperature) {
        var charge = RefinerView.charge();
        var column = RefinerView.column();
        var deposit = RefinerView.deposit();
        var scale = RefinerView.scale();
        double lifting = RefinerView.lifting(temperature);
        double laying = RefinerView.depositing(temperature);

        // The charge: iron-nickel grains, with the nickel leaving as it volatilises.
        graphics.fill(x + charge.x(), y + charge.y(), x + charge.x() + charge.width(),
                y + charge.y() + charge.height(), 0xFF20262C);
        for (int i = 0; i < 16; i++) {
            int px = x + charge.x() + 3 + (i * 5) % (charge.width() - 6);
            int py = y + charge.y() + 4 + (i * 7) % (charge.height() - 8);
            boolean gone = (i % 8) / 8.0 < lifting;
            graphics.fill(px, py, px + 3, py + 3, gone ? 0xFF3E464E : 0xFFBFC6CE);
        }
        MachineFrame.label(graphics, font(), "CO in", x + charge.x(),
                y + charge.y() + charge.height() - 9, READOUT_WIDTH);

        // The column, with as much gas in it as is actually being formed.
        graphics.fill(x + column.x(), y + column.y(), x + column.x() + column.width(),
                y + column.y() + column.height(), 0xFF171C21);
        int puffs = (int) Math.round(lifting * 7);
        for (int i = 0; i < puffs; i++) {
            int px = x + column.x() + 4 + (i * 9) % (column.width() - 8);
            int py = y + column.y() + column.height() - 4 - i * 4;
            graphics.fill(px, py, px + 3, py + 2, 0xFF7FA8C8);
        }

        // The hot end, where it comes back down as metal.
        MachineFrame.label(graphics, font(), "PURE NICKEL", x + deposit.x(), y + deposit.y(), READOUT_WIDTH);
        int grains = (int) Math.round(laying * 14);
        for (int i = 0; i < grains; i++) {
            int px = x + deposit.x() + 2 + (i * 7) % (deposit.width() - 6);
            int py = y + deposit.y() + 12 + (i * 5) % (deposit.height() - 14);
            graphics.fill(px, py, px + 3, py + 3, 0xFFE4E8EC);
        }

        // Both thresholds on one scale, so being stuck between them is visible rather than
        // deducible. That is the mistake this machine actually allows: warm enough to have stopped
        // forming, not hot enough to be decomposing, and nothing comes out at all.
        graphics.fill(x + scale.x(), y + scale.y(), x + scale.x() + 10,
                y + scale.y() + scale.height(), 0x50000000);
        int forming = y + scale.y() + (int) ((1 - RefinerView.onScale(
                play.xponer.astronima.sim.metal.Carbonyl.FORMING_COMPLETE_K))
                * scale.height());
        int breaking = y + scale.y() + (int) ((1 - RefinerView.onScale(
                play.xponer.astronima.sim.metal.Carbonyl.DECOMPOSING_K)) * scale.height());
        graphics.fill(x + scale.x() - 2, forming, x + scale.x() + 12, forming + 1,
                MachineFrame.ACCENT);
        graphics.fill(x + scale.x() - 2, breaking, x + scale.x() + 12, breaking + 1,
                MachineFrame.GOOD);
        int at = y + scale.y() + (int) ((1 - RefinerView.onScale(temperature))
                * scale.height());
        graphics.fill(x + scale.x() + 1, at - 1, x + scale.x() + 9, at + 2,
                RefinerView.isStranded(temperature) ? MachineFrame.WARN : MachineFrame.TEXT);
        MachineFrame.value(graphics, font(), temperature + " K", x + scale.x() + 14, at - 3, READOUT_WIDTH,
                MachineFrame.TEXT);
        MachineFrame.label(graphics, font(), "lifts", x + scale.x() + 14, forming - 3, READOUT_WIDTH);
        MachineFrame.label(graphics, font(), "lays", x + scale.x() + 14, breaking - 3, READOUT_WIDTH);
    }

    /**
     * Machines that split their feed into two products, and so lay out three item slots.
     *
     * <p>Reported live: <em>"веяльній стол есть визуально два слота а по факту их 3"</em> — the
     * winnowing table (and, the same bug, the carbonyl refiner) really do have three real
     * container slots ({@code WinnowingTableBlockEntity}/{@code CarbonylRefinerBlockEntity} both
     * size themselves 3, and {@code ProcessingMenu.threeSlots} already lists both), but this
     * method — the one {@code drawMachine} asks before painting the product wells — only named
     * SEPARATOR and FLUIDBED. The third slot was real, clickable and extractable the whole time;
     * it simply had no well painted behind it, so it read as not existing at all.
     */
    boolean twoProductMachine() {
        return holder.kind() == Kind.SEPARATOR || holder.kind() == Kind.FLUIDBED
                || holder.kind() == Kind.WINNOWER || holder.kind() == Kind.REFINER;
    }

    /** Machines whose whole product is gas: one slot only, no product well at all. */
    boolean oneSlotMachine() {
        return holder.kind() == Kind.CRACKING_TOWER || holder.kind() == Kind.WATER_ELECTROLYZER
                || holder.kind() == Kind.SABATIER_REACTOR;
    }

    private void drawFluidBedReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        int top = layout.row();
        drawFluidBedPicture(graphics, x, top);
        layout.gap(FluidBedView.HEIGHT - PanelLayout.ROW_HEIGHT);
    }

    private void drawFluidBedPicture(GuiGraphicsExtractor graphics, int x, int y) {
        var drum = FluidBedView.drum();
        var state = FluidBedView.state();
        var window = FluidBedView.window();
        var figures = FluidBedView.figures();

        double microns = holder.feedMicrons();
        double rpm = FluidizedBedControl.rpmFor(holder.setting());
        CentrifugalBed.Regime regime = CentrifugalBed.regimeAt(microns, rpm);
        double[] band = FluidBedView.window(microns);

        drawDrum(graphics, x + drum.x(), y + drum.y(), drum.width(), microns, rpm, regime);

        String says = switch (regime) {
            case PACKED -> "PACKED - gas channels past a dead bed";
            case BOILING -> "BOILING - reducing";
            case BLOWING_OUT -> "BLOWING OUT - the charge is leaving";
        };
        MachineFrame.value(graphics, font(), says, x + state.x(), y + state.y(), READOUT_WIDTH,
                regime == CentrifugalBed.Regime.BOILING ? MachineFrame.GOOD : MachineFrame.BAD);

        // The spin bar. Slow is left, fast is right, and the good band is painted on it where this
        // grind actually puts it.
        int barY = y + window.y() + 4;
        int barW = window.width();
        graphics.fill(x + window.x(), barY, x + window.x() + barW, barY + 7, 0xFF20262C);
        boolean hasWindow = band[0] < band[1];
        if (hasWindow) {
            int from = x + window.x() + (int) (band[0] * barW);
            int to = x + window.x() + (int) (band[1] * barW);
            graphics.fill(from, barY, Math.max(to, from + 1), barY + 7, MachineFrame.GOOD);
        } else {
            MachineFrame.value(graphics, font(), "too coarse to fluidize - regrind",
                    x + window.x(), barY - 1, READOUT_WIDTH, MachineFrame.BAD);
        }
        int at = x + window.x() + (int) (holder.setting() * barW);
        graphics.fill(at - 1, barY - 3, at + 2, barY + 10,
                regime == CentrifugalBed.Regime.BOILING ? MachineFrame.TEXT : MachineFrame.BAD);
        MachineFrame.label(graphics, font(), "slow", x + window.x(), barY + 9, READOUT_WIDTH);
        MachineFrame.label(graphics, font(), "fast", x + window.x() + barW - 14, barY + 9, READOUT_WIDTH);

        double g = CentrifugalBed.artificialGravity(rpm) / 9.81;
        MachineFrame.value(graphics, font(),
                Math.round(rpm) + " rpm  " + String.format("%.1f g", g),
                x + figures.x(), y + figures.y(), READOUT_WIDTH, MachineFrame.TEXT);
        MachineFrame.label(graphics, font(), "feed " + Math.round(microns) + " um",
                x + figures.x() + 74, y + figures.y(), READOUT_WIDTH);
    }

    /**
     * The cell draws itself: a molten pool, an electrode dipped into it, and the metal it
     * plates out. See {@link ElectrolysisCellView} for why there is no dial here — what this
     * machine reaches is set entirely by which electrode is installed, never by anything
     * drawn or dragged on this panel.
     */
    private void drawElectrolysisCellReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        int top = layout.row();
        drawElectrolysisCellPicture(graphics, x, top);
        layout.gap(ElectrolysisCellView.HEIGHT - PanelLayout.ROW_HEIGHT);
    }

    private void drawElectrolysisCellPicture(GuiGraphicsExtractor graphics, int x, int y) {
        var pool = ElectrolysisCellView.pool();
        var electrode = ElectrolysisCellView.electrode();
        var cathode = ElectrolysisCellView.cathode();

        ElectrolysisSpecies target = holder.electrolysisTarget();
        float progress = holder.progress();

        // The molten pool: a warm gradient, always glowing the same whatever is installed —
        // it is always the same rock melted, only what is dipped into it changes.
        int poolX = x + pool.x();
        int poolY = y + pool.y();
        graphics.fill(poolX, poolY, poolX + pool.width(), poolY + pool.height(), 0xFF3A1408);
        graphics.fill(poolX + 2, poolY + 2, poolX + pool.width() - 2, poolY + pool.height() - 2,
                0xFF7A2C0E);
        graphics.fill(poolX + 4, poolY + pool.height() - 10, poolX + pool.width() - 4,
                poolY + pool.height() - 4, 0xFFC85A1E);

        // The electrode, dipped in from the top — its own colour is the whole readout of
        // what is currently installed.
        int rodColour = switch (target) {
            case IRON -> 0xFF8A929C;
            case SILICON -> 0xFF4A6FA5;
            case ALUMINUM -> 0xFFD8DCE0;
        };
        int rodX = x + electrode.x() + electrode.width() / 2 - 1;
        graphics.fill(rodX, y + electrode.y(), rodX + 3, poolY + 8, rodColour);

        // Bubbles: oxygen rising off the target's own share of the melt, scaled by how far
        // through the batch the cell actually is — a cell just started shows a calm pool,
        // not a fully agitated one it has not earned yet.
        int bubbles = ElectrolysisCellView.bubbleCount(progress);
        long ticks = System.currentTimeMillis() / 50L;
        for (int i = 0; i < bubbles; i++) {
            double rise = ElectrolysisCellView.bubbleRise(i, ticks);
            int bx = poolX + 6 + (i * 11) % Math.max(pool.width() - 12, 1);
            int by = poolY + pool.height() - 6 - (int) (rise * (pool.height() - 10));
            graphics.fill(bx, by, bx + 2, by + 2, 0xFFFFD9A0);
        }

        // What the target is worth, read straight off the same chemistry the block entity
        // itself runs at batch end — a prediction, not a report, the same shape the retort's
        // own O2/water gauges already use.
        MoltenElectrolysis.Charge charge = MoltenElectrolysis.Charge.of(
                OreBody.chondrite(play.xponer.astronima.block.entity
                        .ElectrolysisCellBlockEntity.CHARGE_GRAMS), target);
        MoltenElectrolysis.Step whole = MoltenElectrolysis.step(charge, 1.0);
        int items = (int) Math.floor(whole.metalMol() / play.xponer.astronima.block.entity
                .ElectrolysisCellBlockEntity.MOL_PER_ITEM);

        String targetName = switch (target) {
            case IRON -> "IRON (default)";
            case SILICON -> "SILICON";
            case ALUMINUM -> "ALUMINUM";
        };
        MachineFrame.value(graphics, font(), "TARGET: " + targetName,
                x + cathode.x(), y + cathode.y(), READOUT_WIDTH, MachineFrame.TEXT);
        MachineFrame.label(graphics, font(), "up to " + items + " per batch",
                x + cathode.x(), y + cathode.y() + 10, READOUT_WIDTH);
        MachineFrame.label(graphics, font(), "+ " + Math.round(whole.o2Mol()) + " mol O2",
                x + cathode.x(), y + cathode.y() + 20, READOUT_WIDTH);
    }

    /** Colour for each corner of the process plane — one look, four distinct outcomes. */
    private static int slsRegimeColour(LaserSintering.Regime regime) {
        return switch (regime) {
            case LACK_OF_FUSION -> MachineFrame.ACCENT;
            case SOUND -> MachineFrame.GOOD;
            case KEYHOLING -> MachineFrame.BAD;
            case BALLING -> 0xFF6B2F6E;
        };
    }

    private static String slsRegimeLabel(LaserSintering.Regime regime) {
        return switch (regime) {
            case LACK_OF_FUSION -> "LACK OF FUSION";
            case SOUND -> "SOUND";
            case KEYHOLING -> "KEYHOLING";
            case BALLING -> "BALLING";
        };
    }

    /**
     * The printer draws the plane itself: the first machine picture in the mod that <em>is</em>
     * its own control ({@code design/sls.md} §2). Every cell samples
     * {@link LaserSintering#regime} at the real (power, speed) that cell stands for, so the
     * picture is the physics rather than an illustration of it — the sound pocket is wherever
     * the model actually says it is, not wherever looked good to paint.
     */
    private void drawSlsPrinterReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        int top = layout.row();
        drawSlsPrinterPicture(graphics, x, top);
        layout.gap(SlsPrinterView.HEIGHT - PanelLayout.ROW_HEIGHT);
    }

    private void drawSlsPrinterPicture(GuiGraphicsExtractor graphics, int x, int y) {
        var plane = SlsPrinterView.plane();
        var readout = SlsPrinterView.readout();
        int planeX = x + plane.x();
        int planeY = y + plane.y();
        double cellW = plane.width() / (double) SlsPrinterView.COLS;
        double cellH = plane.height() / (double) SlsPrinterView.ROWS;

        graphics.fill(planeX - 1, planeY - 1, planeX + plane.width() + 1,
                planeY + plane.height() + 1, 0xFF20242A);
        for (int row = 0; row < SlsPrinterView.ROWS; row++) {
            double speed = SlsPrinterView.speedAt(row);
            int cy = planeY + (int) Math.round(row * cellH);
            int cyEnd = planeY + (int) Math.round((row + 1) * cellH);
            for (int col = 0; col < SlsPrinterView.COLS; col++) {
                double power = SlsPrinterView.powerAt(col);
                int cx = planeX + (int) Math.round(col * cellW);
                int cxEnd = planeX + (int) Math.round((col + 1) * cellW);
                LaserSintering.Regime cell = LaserSintering.regime(power, speed);
                graphics.fill(cx, cy, cxEnd, cyEnd, slsRegimeColour(cell));
            }
        }

        // The marker: where the printer is actually set, drawn over its own cell so it reads
        // against every regime's colour rather than just some of them.
        double powerW = holder.slsPowerW();
        double speedMmS = holder.slsSpeedMmS();
        int markerX = planeX + (int) Math.round(SlsPrinterView.colFor(powerW) * cellW + cellW / 2);
        int markerY = planeY + (int) Math.round(SlsPrinterView.rowFor(speedMmS) * cellH + cellH / 2);
        graphics.fill(markerX - 3, markerY - 1, markerX + 4, markerY + 2, 0xFF10131A);
        graphics.fill(markerX - 1, markerY - 3, markerX + 2, markerY + 4, 0xFF10131A);
        graphics.fill(markerX - 2, markerY, markerX + 3, markerY + 1, 0xFFF2F6FA);
        graphics.fill(markerX, markerY - 2, markerX + 1, markerY + 3, 0xFFF2F6FA);

        LaserSintering.Regime regime = LaserSintering.regime(powerW, speedMmS);
        int rx = x + readout.x();
        int ry = y + readout.y();
        MachineFrame.value(graphics, font(), slsRegimeLabel(regime), rx, ry, readout.width(),
                regime == LaserSintering.Regime.SOUND ? MachineFrame.GOOD : MachineFrame.WARN);
        MachineFrame.label(graphics, font(), Math.round(powerW) + " W", rx, ry + 12, readout.width());
        MachineFrame.label(graphics, font(), Math.round(speedMmS) + " mm/s", rx, ry + 22,
                readout.width());
        MachineFrame.label(graphics, font(),
                Math.round(LaserSintering.energyDensity(powerW, speedMmS)) + " J/mm3",
                rx, ry + 32, readout.width());
        MachineFrame.label(graphics, font(),
                Math.round(LaserSintering.soundness(powerW, speedMmS) * 100) + "% sound",
                rx, ry + 46, readout.width());
    }

    /**
     * The drum, seen down its axis: a ring wall with the solids somewhere inside it.
     *
     * <p>Where the solids are <em>is</em> the diagnosis, so their radius comes from the regime and
     * their jitter from the contact quality - a packed bed is drawn dead still on purpose.
     */
    private void drawDrum(GuiGraphicsExtractor graphics, int left, int top, int size,
                          double microns, double rpm, CentrifugalBed.Regime regime) {
        int centreX = left + size / 2;
        int centreY = top + size / 2;
        int wall = size / 2 - 1;
        double spin = FluidBedView.wallSpin(rpm) * (System.currentTimeMillis() % 100000) / 50.0;

        // The perforated wall, as ticks around a circle so the spin is visible.
        for (int i = 0; i < 24; i++) {
            double a = spin + i * Math.PI / 12;
            int px = centreX + (int) (Math.cos(a) * wall);
            int py = centreY + (int) (Math.sin(a) * wall);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, i % 3 == 0 ? 0xFF8A929C : 0xFF4A525C);
        }

        // The bed. Depth measured inward from the wall, so a thin ring hugs it and an entrained
        // cloud reaches the middle.
        double depth = FluidBedView.bedDepth(microns, rpm);
        double shake = FluidBedView.agitation(microns, rpm);
        long phase = System.currentTimeMillis() / 90;
        for (int i = 0; i < 40; i++) {
            double a = spin * 0.8 + i * Math.PI / 20;
            double into = ((i * 37) % 100) / 100.0 * depth;
            double r = wall - 2 - into * (wall - 3);
            int jx = (int) (((phase + i * 7L) % 3) - 1) * (int) Math.round(shake);
            int jy = (int) (((phase + i * 11L) % 3) - 1) * (int) Math.round(shake);
            int px = centreX + (int) (Math.cos(a) * r) + jx;
            int py = centreY + (int) (Math.sin(a) * r) + jy;
            graphics.fill(px, py, px + 2, py + 2, 0xFFB8A07A);
        }

        // What is escaping up the axis, straight from the entrainment term the machine uses, so
        // the plume cannot disagree with the losses actually being taken.
        int plume = (int) Math.round(FluidBedView.losing(microns, rpm) * 6);
        for (int i = 0; i < plume; i++) {
            int py = centreY - i * 3 - (int) (phase % 3);
            graphics.fill(centreX - 1, py, centreX + 1, py + 2, 0xFF8C2F27);
        }
        if (regime == CentrifugalBed.Regime.BLOWING_OUT) {
            MachineFrame.label(graphics, font(), "out", centreX + 3, centreY - wall + 1, READOUT_WIDTH);
        }
    }

    /**
     * The winnowing table, whose readout has to answer a question the others do not:
     * <em>where is the control?</em>
     *
     * <p>There is not one. What it shows instead is how well the feed it has been given can
     * be classified, and a sentence naming whichever of the two upstream decisions — the
     * grind, or being indoors — is currently the limiting one. Without this it fell through
     * to the separator's branch and reported a magnetic field strength it does not have,
     * which is worse than showing nothing.
     */
    /**
     * The separator draws itself: a drum, a feed falling onto it, and two streams leaving.
     *
     * <p>The trade this machine exists for is a <em>shape</em>. Turn the field up and the drum
     * holds particles further round before letting go — which catches more mineral and carries more
     * of the rock it is stuck to. Two gauges in tension said that; a wide catch arc feeding a dirty
     * stream shows it.
     */
    private void drawSeparatorReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        SeparatorAdvice.FieldVerdict verdict = SeparatorAdvice.field(holder.setting());
        int top = layout.row();
        drawSeparatorPicture(graphics, x, top);
        layout.gap(SeparatorView.HEIGHT - PanelLayout.ROW_HEIGHT);

        MachineFrame.value(graphics, font(), verdict.advice(), x, layout.row(), READOUT_WIDTH,
                adviceColour(verdict == SeparatorAdvice.FieldVerdict.SELECTIVE));
        gaugeRow(graphics, x, layout.gaugeRow(), "CAUGHT", holder.lastRecovery());
        gaugeRow(graphics, x, layout.gaugeRow(), "CLEAN", holder.lastGrade());
    }

    /**
     * The crusher draws itself: two jaws at their gap, and what falls out underneath.
     *
     * <p>Narrowing the jaws frees more mineral <em>and</em> costs more cranking, and those two move
     * together — that is the machine. A row of three numbers contained it; a gap you watch close,
     * with the product below getting finer and the effort bar filling, shows it.
     *
     * <p>The grain line is the part worth having. Mineral sits in rock in grains about 120 µm
     * across, and it comes free exactly when the fragments get smaller than that. Drawing the line
     * the fragments have to cross under makes the whole mechanic self-evident, and it is a number
     * the player would otherwise never meet.
     */
    private void drawCrusherReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        double microns = Comminution.particleSizeMicrons(holder.setting());
        double liberation = Comminution.liberation(microns);
        SeparatorAdvice.GrindVerdict verdict = SeparatorAdvice.grind(liberation, holder.setting());

        int top = layout.row();
        drawCrusherPicture(graphics, x, top, microns, liberation);
        layout.gap(CrusherView.HEIGHT - PanelLayout.ROW_HEIGHT);

        MachineFrame.value(graphics, font(), verdict.advice(), x, layout.row(), READOUT_WIDTH,
                adviceColour(verdict == SeparatorAdvice.GrindVerdict.FREED));
    }

    private void drawCrusherPicture(GuiGraphicsExtractor graphics, int x, int y, double microns,
                                    double liberation) {
        var fixedJaw = CrusherView.fixedJaw();
        var swing = CrusherView.swingJaw();
        var product = CrusherView.product();
        var scale = CrusherView.grainScale();
        var effort = CrusherView.effort();
        double gap = CrusherView.jawGap(holder.setting());

        // The jaws. The swing jaw is the one the dial moves, so it is the one that travels.
        int jawTop = y + fixedJaw.y() + 2;
        int jawBottom = y + fixedJaw.y() + fixedJaw.height();
        int swingX = x + fixedJaw.x() + fixedJaw.width()
                + (int) (gap * (swing.x() - fixedJaw.x() - fixedJaw.width()));
        graphics.fill(x + fixedJaw.x(), jawTop, x + fixedJaw.x() + fixedJaw.width(), jawBottom,
                0xFF5A626C);
        graphics.fill(swingX, jawTop, swingX + swing.width(), jawBottom, 0xFF6E7680);
        // Teeth, so it reads as a crusher rather than as two blocks.
        for (int i = 0; i < 5; i++) {
            int ty = jawTop + 4 + i * 6;
            graphics.fill(x + fixedJaw.x() + fixedJaw.width(), ty,
                    x + fixedJaw.x() + fixedJaw.width() + 2, ty + 3, 0xFF8A929C);
            graphics.fill(swingX - 2, ty + 3, swingX, ty + 6, 0xFF8A929C);
        }
        // Rock in the throat, sized by the gap it has to get through.
        int chunk = Math.max(1, (int) (1 + gap * 4));
        for (int i = 0; i < 6; i++) {
            int px = x + fixedJaw.x() + fixedJaw.width() + 3 + (i % 2) * 4;
            int py = jawTop + 3 + i * 5;
            if (py + chunk < jawBottom) {
                graphics.fill(px, py, px + chunk, py + chunk, 0xFF9AA0A8);
            }
        }

        // What comes out: finer as the gap closes, which is the half people forget is the product.
        MachineFrame.label(graphics, font(), Math.round(microns) + " um",
                x + product.x(), y + product.y(), READOUT_WIDTH);
        int grains = (int) (6 + (1 - gap) * 40);
        for (int i = 0; i < grains; i++) {
            int px = x + product.x() + 2 + (i * 11) % (product.width() - 4);
            int py = y + product.y() + 10 + (i * 7) % (product.height() - 12);
            int size = Math.max(1, chunk - 1);
            boolean freed = (i % 10) / 10.0 < liberation;
            graphics.fill(px, py, px + size, py + size, freed ? 0xFFD8C89A : 0xFF6E7480);
        }

        // The grain line, and where the fragments sit against it. Above it the mineral is still
        // locked in rock; below it, free. That crossing IS liberation.
        int mark = y + scale.y() + (int) ((1 - CrusherView.grainMark()) * (scale.height() - 12)) + 6;
        graphics.fill(x + scale.x(), mark, x + scale.x() + scale.width(), mark + 1,
                MachineFrame.GOOD);
        MachineFrame.label(graphics, font(), "grain 120um", x + scale.x(), mark + 3, READOUT_WIDTH);
        int at = y + scale.y() + (int) ((1 - gap) * (scale.height() - 12)) + 6;
        graphics.fill(x + scale.x() + 6, at - 2, x + scale.x() + 14, at + 3,
                MachineFrame.colourFor((float) liberation));
        MachineFrame.value(graphics, font(), Math.round(liberation * 100) + "% free",
                x + scale.x() + 18, at - 3, READOUT_WIDTH, MachineFrame.colourFor((float) liberation));

        // And what it costs, against the hardest this machine can be asked for.
        MachineFrame.label(graphics, font(), "WORK", x + effort.x(), y + effort.y(), READOUT_WIDTH);
        int hardest = play.xponer.astronima.block.entity.OreCrusherBlockEntity.BASE_WORK
                + (int) Math.round(
                Comminution.workPerKilogram(Comminution.FINEST_MICRONS) * 45);
        MachineFrame.bar(graphics, x + effort.x(), y + effort.y() + 11, effort.width() - 2, 7,
                (float) CrusherView.effortFraction(holder.workRequired(), hardest),
                MachineFrame.WARN);
        MachineFrame.value(graphics, font(), holder.workRequired() + "", x + effort.x(),
                y + effort.y() + 22, READOUT_WIDTH, MachineFrame.TEXT);
    }

    private void drawWinnowerPicture(GuiGraphicsExtractor graphics, int x, int y) {
        var feed = WinnowerView.feed();
        var column = WinnowerView.column();
        var light = WinnowerView.light();
        var dense = WinnowerView.dense();

        double sharpness = play.xponer.astronima.sim.ore.Elutriation.sharpness(
                holder.setting(), holder.roomPressureKPa());
        double stream = WinnowerView.streamStrength(holder.roomPressureKPa(),
                play.xponer.astronima.sim.ore.Elutriation.MINIMUM_PRESSURE_KPA);
        double spread = WinnowerView.spread(sharpness);

        // The feed being tipped in.
        for (int i = 0; i < 8; i++) {
            int fx = x + feed.x() + 8 + i * 4;
            graphics.fill(fx, y + feed.y() + 2 + (i % 3), fx + 2, y + feed.y() + 9, 0xFF6E7480);
        }

        // The column, and the stream in it. A thin room draws almost nothing, which is the point:
        // an empty column and a badly set dial are different problems.
        graphics.fill(x + column.x(), y + column.y(), x + column.x() + column.width(),
                y + column.y() + column.height(), 0xFF171C21);
        int arrows = (int) Math.round(1 + stream * 6);
        for (int i = 0; i < arrows; i++) {
            int ax = x + column.x() + 5 + i * 6;
            int alpha = (int) (0x30 + 0xA0 * stream);
            graphics.fill(ax, y + column.y() + 4, ax + 1, y + column.y() + column.height() - 4,
                    (alpha << 24) | 0x0078B0EE);
            graphics.fill(ax - 1, y + column.y() + 4, ax + 2, y + column.y() + 6,
                    (alpha << 24) | 0x00A8CCF4);
        }

        // The two populations, in flight. Dense grains fall in the column; light ones are carried
        // over the lip, and how far over is the sharpness the model actually achieved.
        int floor = y + column.y() + column.height() - 4;
        for (int i = 0; i < 22; i++) {
            boolean heavy = i % 3 == 0;
            double lift = heavy ? 0.12 : 0.85;
            double across = WinnowerView.landing(lift * stream, spread);
            int px = x + column.x() + 4 + (int) (across * (dense.x() - column.x() + 20));
            int py = y + column.y() + 6 + ((i * 13) % (column.height() - 12));
            if (heavy) {
                py = Math.min(floor, py + 10);
            }
            graphics.fill(px, py, px + 2, py + 2, heavy ? 0xFFD8C89A : 0xFF6E7480);
        }

        MachineFrame.label(graphics, font(), "LIGHT", x + light.x(), y + light.y(), READOUT_WIDTH);
        MachineFrame.label(graphics, font(), "DENSE", x + dense.x(), y + dense.y(), READOUT_WIDTH);
        MachineFrame.value(graphics, font(), Math.round(sharpness * 100) + "% cut",
                x + dense.x(), y + dense.y() + 11, READOUT_WIDTH,
                MachineFrame.colourFor((float) sharpness));
    }

    private void drawSeparatorPicture(GuiGraphicsExtractor graphics, int x, int y) {
        var feed = SeparatorView.feed();
        var drum = SeparatorView.drum();
        var caught = SeparatorView.concentrate();
        var tails = SeparatorView.tailings();
        double field = holder.setting();
        double arc = SeparatorView.catchArc(field);
        double dirty = SeparatorView.contamination(field, holder.lastGrade());

        // Feed arriving, as a thin curtain rather than a chute: it is a stream of grains.
        for (int i = 0; i < 9; i++) {
            int fx = x + feed.x() + 6 + i * 3;
            graphics.fill(fx, y + feed.y() + 2 + (i % 3) * 3, fx + 2, y + feed.y() + 12,
                    0xFF6E7480);
        }

        int cx = x + drum.x() + drum.width() / 2;
        int cy = y + drum.y() + drum.height() / 2;
        int radius = drum.width() / 2 - 2;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF39414A);
        }
        // The field, drawn as the arc it actually holds through. Longer arc, stronger magnet.
        for (double a = -Math.PI / 2; a <= -Math.PI / 2 + arc; a += 0.06) {
            int px = cx + (int) (Math.cos(a) * radius);
            int py = cy + (int) (Math.sin(a) * radius);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, MachineFrame.ACCENT);
        }

        // And the particles riding it, released where the arc ends.
        for (int i = 0; i < 6; i++) {
            double a = -Math.PI / 2 + arc * (i + 1) / 7.0;
            int px = cx + (int) (Math.cos(a) * (radius - 3));
            int py = cy + (int) (Math.sin(a) * (radius - 3));
            graphics.fill(px, py, px + 2, py + 2, 0xFFCBD4DE);
        }

        // The two streams. The caught one carries whatever the field dragged along with it, so its
        // dirt is the same number the CLEAN gauge reads - one fact, two views, and they cannot
        // disagree because they come from the same place.
        stream(graphics, x + caught.x(), y + caught.y(), caught.width(), caught.height(),
                "CONCENTRATE", MachineFrame.GOOD, dirty);
        stream(graphics, x + tails.x(), y + tails.y(), tails.width(), tails.height(),
                "TAILINGS", 0xFF6E7480, 1 - dirty * 0.5);
    }

    /** One falling stream, with as much grey rock in it as the number says. */
    private void stream(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        String label, int colour, double dirt) {
        // The box's own width, not the panel-wide READOUT_WIDTH: this label sits beside another
        // stream's, not alone on a full-width line, and letting it claim a whole readout's worth
        // of room let "CONCENTRATE" run straight into "TAILINGS" instead of clipping.
        MachineFrame.label(graphics, font(), label, x, y, width);
        for (int i = 0; i < 14; i++) {
            int px = x + 2 + (i * 7) % Math.max(1, width - 6);
            int py = y + 12 + (i * 5) % Math.max(1, height - 16);
            boolean rock = (i % 7) / 7.0 < dirt;
            graphics.fill(px, py, px + 2, py + 2, rock ? 0xFF6E7480 : colour);
        }
    }

    private void drawRetortPicture(GuiGraphicsExtractor graphics, int x, int y, int temperature,
                                   RetortProcess process, RetortAdvice.Verdict verdict,
                                   int colour) {
        var mirror = RetortView.mirror();
        var beam = RetortView.beam();
        var vessel = RetortView.vessel();
        var scale = RetortView.scale();

        // The dish, as an arc of facets. Concave towards the vessel, because that is the only way
        // a mirror that concentrates anything can face.
        for (int i = 0; i < mirror.height(); i++) {
            double across = (i - mirror.height() / 2.0) / (mirror.height() / 2.0);
            int depth = (int) ((1 - across * across) * (mirror.width() - 8));
            int fx = x + mirror.x() + depth;
            graphics.fill(fx, y + mirror.y() + i, fx + 4, y + mirror.y() + i + 1,
                    i % 3 == 0 ? 0xFFB9C6D2 : 0xFF8C99A6);
        }

        // The beam: brightness is the sun, width is the focus, and they are separate on purpose.
        double bright = RetortView.beamBrightness(holder.sunlight());
        double spread = RetortView.beamSpread(holder.setting());
        int half = Math.max(1, (int) (beam.height() / 2.0 * spread));
        int alpha = (int) (0x18 + 0xC8 * bright);
        for (int step = 0; step < beam.width(); step++) {
            double along = step / (double) beam.width();
            int narrow = (int) Math.round(half * (1 - along * 0.75));
            int cy = y + beam.y() + beam.height() / 2;
            graphics.fill(x + beam.x() + step, cy - narrow, x + beam.x() + step + 1, cy + narrow + 1,
                    (alpha << 24) | 0x00FFE9A8);
        }

        // The vessel, glowing with what is actually in it rather than with the setting.
        double heat = RetortView.mercury(temperature, process.onsetK(), process.spoilK());
        int glow = (int) (0x20 + 0xB0 * heat);
        graphics.fill(x + vessel.x(), y + vessel.y(), x + vessel.x() + vessel.width(),
                y + vessel.y() + vessel.height(), 0xFF20262C);
        graphics.fill(x + vessel.x() + 2, y + vessel.y() + 2,
                x + vessel.x() + vessel.width() - 2, y + vessel.y() + vessel.height() - 2,
                (glow << 24) | (colour & 0xFFFFFF));
        MachineFrame.value(graphics, font(), temperature + " K",
                x + vessel.x() + 2, y + vessel.y() + vessel.height() / 2 - 4, READOUT_WIDTH, MachineFrame.TEXT);

        // The thermometer, read against the window rather than against a maximum: past it, hotter
        // is worse, and a scale that kept filling would be praising the mistake.
        graphics.fill(x + scale.x(), y + scale.y(), x + scale.x() + scale.width(),
                y + scale.y() + scale.height(), 0x50000000);
        int onset = (int) (scale.height()
                * (1 - RetortView.mercury(process.onsetK(), process.onsetK(), process.spoilK())));
        int spoil = (int) (scale.height()
                * (1 - RetortView.mercury(process.spoilK(), process.onsetK(), process.spoilK())));
        graphics.fill(x + scale.x() - 2, y + scale.y() + onset, x + scale.x() + scale.width() + 2,
                y + scale.y() + onset + 1, MachineFrame.GOOD);
        graphics.fill(x + scale.x() - 2, y + scale.y() + spoil, x + scale.x() + scale.width() + 2,
                y + scale.y() + spoil + 1, MachineFrame.BAD);
        int level = (int) (scale.height() * heat);
        graphics.fill(x + scale.x() + 3, y + scale.y() + scale.height() - level,
                x + scale.x() + scale.width() - 3, y + scale.y() + scale.height(), colour);
    }

    /**
     * The winnowing table draws itself: a column of gas with the feed falling through it.
     *
     * <p>Gas up, grains down, and whether a grain reaches the bottom depends on whether the stream
     * can lift it. Two populations crossing in mid-air says more than "63% sized" — and it makes
     * the pressure dependency visible, because in a thin room the column is nearly empty rather
     * than the gauge merely reading low. <em>Bring it inside</em> and <em>turn the dial</em> are
     * different instructions.
     */
    private void drawWinnowerReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        SeparatorAdvice.WinnowVerdict verdict =
                SeparatorAdvice.winnow(holder.setting(), holder.roomPressureKPa());
        int top = layout.row();
        drawWinnowerPicture(graphics, x, top);
        layout.gap(WinnowerView.HEIGHT - PanelLayout.ROW_HEIGHT);

        MachineFrame.value(graphics, font(), verdict.advice(), x, layout.row(), READOUT_WIDTH,
                adviceColour(verdict == SeparatorAdvice.WinnowVerdict.SHARP));

        // The same two numbers in tension the magnet reports, because they are the same
        // two questions: how much of the metal did you catch, and how clean is it.
        gaugeRow(graphics, x, layout.gaugeRow(), "CAUGHT", holder.lastRecovery());
        gaugeRow(graphics, x, layout.gaugeRow(), "CLEAN", holder.lastGrade());
    }

    /**
     * The forge draws itself: a ram, a workpiece going squat under it, and both properties on one
     * plot.
     *
     * <p>Hardness is what you are buying and ductility is what you spend to buy it. Two gauges said
     * that and made the player compare them; <strong>one plot with both lines puts the crossing in
     * front of them</strong>, and the crossing is the lesson — there is a point past which another
     * blow costs more than it gains, and you should see yourself approaching it rather than
     * discover it when the piece cracks.
     */
    private void drawForgeReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        float hardness = holder.lastRecovery();
        float ductility = holder.lastGrade();
        ColdWorking.Verdict verdict = ColdWorking.verdict(
                new ColdWorking.Piece(hardness, ductility, false));

        int top = layout.row();
        drawForgePicture(graphics, x, top, hardness, ductility);
        layout.gap(ForgeView.HEIGHT - PanelLayout.ROW_HEIGHT);

        MachineFrame.value(graphics, font(), verdict.advice(), x, layout.row(), READOUT_WIDTH,
                adviceColour(verdict == ColdWorking.Verdict.READY));
    }

    private void drawForgePicture(GuiGraphicsExtractor graphics, int x, int y, float hardness,
                                  float ductility) {
        var ram = ForgeView.ram();
        var piece = ForgeView.piece();
        var chamber = ForgeView.chamber();
        var plot = ForgeView.plot();

        // The ram, down as far as the blow strength says. Honestly just the dial, and there is no
        // reason to dress that up.
        int drop = (int) (ForgeView.ramTravel(holder.setting()) * 12);
        graphics.fill(x + ram.x(), y + ram.y() + drop, x + ram.x() + ram.width(),
                y + ram.y() + ram.height() - 8 + drop, 0xFF5A626C);
        graphics.fill(x + ram.x() + 4, y + ram.y() + ram.height() - 8 + drop,
                x + ram.x() + ram.width() - 4, y + ram.y() + ram.height() - 4 + drop, 0xFF8A929C);
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + ram.x() + 6 + i * 10, y + ram.y() + 2 + drop,
                    x + ram.x() + 8 + i * 10, y + ram.y() + 8 + drop, 0xFF3E454E);
        }

        // The workpiece, squatter the more it has been worked. From the hardness, not the dial:
        // drawing it from the control would show the next blow before it landed.
        double squash = ForgeView.squash(hardness);
        int tall = (int) (12 - squash * 6);
        int wide = (int) (18 + squash * 14);
        int px = x + piece.x() + (piece.width() - wide) / 2;
        int py = y + piece.y() + piece.height() - tall - 2;
        graphics.fill(px, py, px + wide, py + tall, 0xFFB8A07A);
        graphics.fill(px, py, px + wide, py + 2, 0xFFD8C09A);
        graphics.fill(x + piece.x(), y + piece.y() + piece.height() - 2,
                x + piece.x() + piece.width(), y + piece.y() + piece.height(), 0xFF3E454E);

        MachineFrame.label(graphics, font(), "CHAMBER", x + chamber.x(), y + chamber.y(), READOUT_WIDTH);
        MachineFrame.bar(graphics, x + chamber.x() + 40, y + chamber.y() + 1, 0, 6,
                holder.evacuation(), MachineFrame.ACCENT);

        // Both properties on one plot, so the crossing is a place rather than a comparison.
        graphics.fill(x + plot.x(), y + plot.y(), x + plot.x() + plot.width(),
                y + plot.y() + plot.height(), 0x40000000);
        int base = y + plot.y() + plot.height() - 8;
        int height = plot.height() - 16;
        for (int step = 0; step <= plot.width() - 4; step++) {
            double along = step / (double) (plot.width() - 4);
            // Hardness climbs and saturates; ductility is spent away. Drawn as the curves they
            // are, with the piece's own position marked on them.
            int hard = (int) (height * (1 - Math.pow(1 - along, 1.7)));
            int give = (int) (height * Math.pow(1 - along, 1.3));
            graphics.fill(x + plot.x() + 2 + step, base - hard, x + plot.x() + 3 + step,
                    base - hard + 1, MachineFrame.GOOD);
            graphics.fill(x + plot.x() + 2 + step, base - give, x + plot.x() + 3 + step,
                    base - give + 1, MachineFrame.WARN);
        }
        int at = x + plot.x() + 2 + (int) (ForgeView.workedTo(hardness, ductility)
                * (plot.width() - 4));
        graphics.fill(at, y + plot.y() + 2, at + 1, base + 2,
                ForgeView.worthAnotherBlow(hardness, ductility)
                        ? MachineFrame.TEXT : MachineFrame.BAD);
        MachineFrame.label(graphics, font(), "HARD", x + plot.x() + 2, y + plot.y(), READOUT_WIDTH);
        MachineFrame.label(graphics, font(), "GIVE", x + plot.x() + plot.width() - 26,
                y + plot.y(), READOUT_WIDTH);
        MachineFrame.value(graphics, font(),
                ForgeView.worthAnotherBlow(hardness, ductility) ? "one more" : "stop",
                x + plot.x() + 2, base + 4, READOUT_WIDTH,
                ForgeView.worthAnotherBlow(hardness, ductility)
                        ? MachineFrame.TEXT : MachineFrame.BAD);
    }

    /**
     * The retort, whose control is a window rather than a direction.
     *
     * <p>Everything else in this mod has a dial you push one way for one thing and the
     * other way for another. This one is wrong at <em>both</em> ends — cold gives nothing,
     * hot fuses the charge and seals its water in — so the instrument has to show the
     * target as a place, not as a bigger number. The band is painted into the temperature
     * track and the needle rides it; the sentence underneath says which side you are on.
     */
    private void drawRetortReadouts(GuiGraphicsExtractor graphics, int x, PanelLayout layout) {
        int temperature = holder.temperatureK();
        RetortProcess process = holder.retortProcess();
        RetortAdvice.Verdict verdict = RetortAdvice.read(holder.sunlight(), temperature, process);
        int colour = verdict.isHarmful() ? MachineFrame.BAD
                : verdict.isGood() ? MachineFrame.GOOD : MachineFrame.WARN;

        int top = layout.row();
        drawRetortPicture(graphics, x, top, temperature, process, verdict, colour);
        layout.gap(RetortView.HEIGHT - PanelLayout.ROW_HEIGHT);

        MachineFrame.value(graphics, font(), verdict.advice(process), x, layout.row(), READOUT_WIDTH, colour);

        // Sun on the mirror, because it is the input the player does not otherwise see:
        // the difference between "I set this wrong" and "it is night" is invisible
        // without it, and the two need completely different responses.
        gaugeRow(graphics, x, layout.gaugeRow(), "SUN", holder.sunlight());

        // What this focus would actually get out of the charge in the slot. Predicted
        // rather than reported, so the window can be found before a batch is spent
        // instead of after - which is the difference between an instrument and a receipt.
        if (process == RetortProcess.CHLORATE) {
            ChlorateDecomposition.Bake bake = ChlorateDecomposition.bake(
                    SolarRetortBlockEntity.CHLORATE_CHARGE_GRAMS, temperature);
            gaugeRow(graphics, x, layout.gaugeRow(), "O2",
                    (float) Math.clamp(bake.oxygenMoles() / MAX_EXPECTED_O2_MOL, 0.0, 1.0));
            // A second gauge only while it is doing damage. A permanently-zero chlorine
            // needle would train the player to stop reading it, and this is the one
            // reading in the machine they cannot afford to have learned to ignore.
            if (bake.gassed()) {
                gaugeRow(graphics, x, layout.gaugeRow(), "Cl2", 1.0f);
            }
            return;
        }
        double yield = Dehydroxylation.bake(SolarRetortBlockEntity.CHARGE_GRAMS,
                SolarRetortBlockEntity.hydrationOf(holder.item(0)),
                temperature).waterGrams();
        gaugeRow(graphics, x, layout.gaugeRow(), "YIELD",
                (float) Math.clamp(yield / MAX_EXPECTED_WATER_G, 0.0, 1.0));
    }

    /**
     * Water from a perfectly baked charge of the wettest feed, for scaling the yield bar.
     *
     * <p>Derived rather than typed so that retuning the chemistry cannot leave the gauge
     * silently pinned at full or crawling along the bottom.
     */
    private static final double MAX_EXPECTED_WATER_G =
            SolarRetortBlockEntity.CHARGE_GRAMS * SolarRetortBlockEntity.TAILINGS_HYDRATION
                    * Dehydroxylation.WATER_FRACTION_OF_SERPENTINE;

    /** Oxygen from a fully decomposed chlorate charge, for scaling that gauge the same way. */
    private static final double MAX_EXPECTED_O2_MOL =
            SolarRetortBlockEntity.CHLORATE_CHARGE_GRAMS
                    * ChlorateDecomposition.O2_MOL_PER_GRAM;

    /** One labelled gauge with its number, so a row reads left to right. */
    private void gaugeRow(GuiGraphicsExtractor graphics, int x, int y, String label,
                          float value) {
        MachineFrame.label(graphics, font(), label, x, y, READOUT_WIDTH);
        MachineFrame.bar(graphics, x + 44, y, 60, 7, value, MachineFrame.colourFor(value));
        MachineFrame.value(graphics, font(), Math.round(value * 100) + "%",
                x + 110, y, READOUT_WIDTH, MachineFrame.TEXT);
    }

    /** Green when the setting is in its sweet spot, amber when it is a trade. */
    private static int adviceColour(boolean ideal) {
        return ideal ? MachineFrame.GOOD : MachineFrame.WARN;
    }

    private String controlName() {
        return switch (holder.kind()) {
            case CRUSHER -> "JAW GAP";
            case SEPARATOR -> "FIELD STRENGTH";
            case FORGE -> "BLOW STRENGTH";
            case RETORT -> "MIRROR FOCUS";
            // A temperature, and the only dial in the game whose MIDDLE is the dangerous
            // setting: warm enough to form carbonyl and too cool to break it down again.
            case REFINER -> "VESSEL TEMPERATURE";
            // The winnowing table has no control of its own, deliberately: what it can do
            // is set by how finely the feed was ground. The label names where the decision
            // actually was, so a player is not left hunting for a dial that is not there.
            case WINNOWER -> "SET BY THE GRIND";
            // The one thing the player trims: how fast the drum spins, which is the artificial
            // gravity that lets the fixed flow fluidize the bed instead of blowing it out.
            case FLUIDBED -> "DRUM SPEED";
            // No control of its own either: what this cell reaches is set by which
            // electrode is installed, a swap rather than a handle in this window.
            case ELECTROLYSIS -> "SET BY THE ELECTRODE";
            case SLS -> "LASER POWER x SCAN SPEED";
            // Neither has a control at all — named the same way WINNOWER/ELECTROLYSIS already
            // are, for a machine whose work state is not the player's own setting.
            case CRACKING_TOWER -> "CRACKS WHATEVER IS FED";
            case POLYMERIZER -> "STRUNG FROM THE ROOM'S ETHYLENE";
            case WATER_ELECTROLYZER -> "SPLITS WHATEVER IS FED";
            case SABATIER_REACTOR -> "REACTS THE ROOM'S CO2 AND H2";
            case BOSCH_REACTOR -> "REACTS THE ROOM'S CO2 AND H2";
            case TROILITE_ROASTER -> "BURNS THE ORE'S SULFIDE IN THE ROOM'S OXYGEN";
            case DOWNS_CELL -> "ELECTROLYZES WHATEVER ROCK SALT IS FED";
            case SULFURIC_ACID_PLANT -> "REACTS THE ROOM'S SO2, O2 AND WATER VAPOR";
            case HEAVY_WATER_CELL -> "ONE CASCADE STAGE PER BATCH";
            case TITANIUM_CELL -> "REDUCES WHATEVER TITANIA IS FED";
            case INDUCTION_FURNACE -> "MELTS WITHOUT TOUCHING THE ROOM'S AIR";
            // No control either: how much flux was loaded alongside the ore is a feed decision,
            // not a handle in this window - the panel's own flux-ratio readout says how it went.
            case IRON_SMELTER -> "FLUXED BY WHAT WAS FED WITH IT";
            // No control either: it runs (or does not) on whether it is in vacuum next to a
            // dewar of LN2 — a placement question, not a handle in this window.
            case FREEZE_DRYER -> "NEEDS VACUUM AND LN2, NOT A DIAL";
            // No control either: the real segregation coefficient already gives one un-tunable
            // answer (design/halogens.md §9.3) - nothing here for a player to set.
            case ZONE_REFINER -> "PURIFIES WHATEVER SILICON IS FED";
            // No control either: real fixed 1:1 stoichiometry, both reagents mandatory - nothing
            // here for a player to set.
            case HF_DIGESTER -> "REACTS FLUORITE WITH SULFURIC ACID";
            // No control either: real fixed 1:6 stoichiometry, HF hand-loaded only - nothing
            // here for a player to set.
            case ETCH_STATION -> "ETCHES A WAFER WITH HAND-LOADED HF";
            // No control either: the vessel self-heats to whichever real setpoint its own feed
            // needs (design/carbon-fiber.md §2) - the room's own oxygen is the real decision.
            case GRAPHITIZER -> "SELF-HEATS TO WHAT IS FED";
            // No control either: reacts a real water bottle against whatever real CO2 the room
            // has, always - nothing here for a player to set.
            case ALGAE_BIOREACTOR -> "REACTS THE ROOM'S OWN CO2";
            // No control either: digests whatever real crop waste is fed, at the real fixed
            // 60/40 methane/CO2 split, with no power at all - nothing here for a player to set.
            case ANAEROBIC_DIGESTER -> "DIGESTS WHATEVER WASTE IS FED";
        };
    }

    // --------------------------------------------------------------------- input

    /**
     * Whether the mouse is over this machine's live control, and this machine even has one.
     *
     * <p>Refuses two things: the winnowing table, the retort, the refiner, the fluidized bed,
     * the electrolysis cell, the SLS printer and the seven reagent-driven machines by name,
     * because none of them take a player's setting at all any more; and every wrench-set
     * machine, by asking it. A bar that visibly grabs and then springs back is worse than an
     * inert one: it says there is a setting here and then refuses to keep it.
     *
     * <p>Asked of the machine rather than listed twice: a list of kinds copied into a second
     * place is exactly the copy nobody is forced to touch, and the one that goes stale the day
     * a machine changes its mind (rule 39).
     */
    private boolean overDial(double mouseX, double mouseY) {
        if (holder.kind() == Kind.WINNOWER || holder.kind() == Kind.RETORT
                || holder.kind() == Kind.REFINER || holder.kind() == Kind.FLUIDBED
                || holder.kind() == Kind.ELECTROLYSIS || holder.kind() == Kind.SLS
                || holder.kind() == Kind.CRACKING_TOWER || holder.kind() == Kind.POLYMERIZER
                || holder.kind() == Kind.WATER_ELECTROLYZER || holder.kind() == Kind.SABATIER_REACTOR
                || holder.kind() == Kind.BOSCH_REACTOR || holder.kind() == Kind.TROILITE_ROASTER
                || holder.kind() == Kind.SULFURIC_ACID_PLANT || holder.kind() == Kind.HEAVY_WATER_CELL
                || holder.kind() == Kind.TITANIUM_CELL || holder.kind() == Kind.INDUCTION_FURNACE
                || holder.kind() == Kind.IRON_SMELTER || holder.kind() == Kind.FREEZE_DRYER
                || holder.kind() == Kind.DOWNS_CELL || holder.kind() == Kind.ZONE_REFINER
                || holder.kind() == Kind.HF_DIGESTER || holder.kind() == Kind.ETCH_STATION
                || holder.kind() == Kind.GRAPHITIZER || holder.kind() == Kind.ALGAE_BIOREACTOR
                || holder.kind() == Kind.ANAEROBIC_DIGESTER
                || holder.isSetByWrench()) {
            return false;
        }
        Layout.Box box = controlBox();
        int x = (int) getContentX();
        int y = (int) getContentY();
        return Slider.isOver(mouseX, mouseY, x + DIAL_X + box.x(),
                y + ProcessingMenu.READOUT_Y + box.y() + 4, box.width(), DIAL_H);
    }

    /** Called by the dial-handle overlay {@link ProcessingUi} adds on top of this element. */
    public void onDialMouseDown() {
        if (!overDial(lastMouseX, lastMouseY)) {
            return;
        }
        draggingDial = true;
        updateDragPreview(lastMouseX);
    }

    public void onDialDrag() {
        if (!draggingDial) {
            return;
        }
        updateDragPreview(lastMouseX);
    }

    /**
     * Commits the setting on release, not on every frame of the drag.
     *
     * <p>Sending on every drag update — one machine's real behaviour until this fix — reset the
     * 3-second settle timer dozens of times a second for as long as a hand was on the dial, so
     * it never once counted down and the machine sat idle for the whole gesture: reported,
     * correctly, as "the machine's animation is broken while you touch the dial." The knob still
     * tracks the cursor live (drawn from {@link #dragPreviewSetting} while dragging); only the
     * commit — and the settle it starts — waits for the mouse to come up.
     */
    public void onDialReleased() {
        if (draggingDial && !Double.isNaN(dragPreviewSetting)) {
            sendSetting(dragPreviewSetting);
        }
        draggingDial = false;
        dragPreviewSetting = Double.NaN;
    }

    private void updateDragPreview(double mouseX) {
        Layout.Box box = controlBox();
        int x = (int) getContentX();
        dragPreviewSetting = Slider.valueFor(mouseX, x + DIAL_X + box.x(), box.width());
    }

    /**
     * The plane's own drag — only meaningful for the printer. No calibration or settle timer
     * sits behind this one ({@code SlsPrinterBlockEntity} has neither), so unlike the 1D dial
     * there is nothing to lose by sending on every drag event rather than waiting for release: a
     * marker that only caught up to the cursor once you let go would read as laggy on the one
     * control in the mod meant to feel like drawing directly on the plane.
     */
    public void onPlaneMouseDown() {
        updatePlaneFromMouse(lastMouseX, lastMouseY);
    }

    public void onPlaneDrag() {
        updatePlaneFromMouse(lastMouseX, lastMouseY);
    }

    /** Both axes from one mouse position, sent together — see {@link SlsControlPayload}. */
    private void updatePlaneFromMouse(double mouseX, double mouseY) {
        var plane = SlsPrinterView.plane();
        int x = (int) getContentX() + DIAL_X + plane.x();
        int y = (int) getContentY() + ProcessingMenu.READOUT_Y + plane.y();
        float power01 = (float) Math.clamp((mouseX - x) / plane.width(), 0, 1);
        float speed01 = (float) Math.clamp((mouseY - y) / plane.height(), 0, 1);
        ClientPacketDistributor.sendToServer(
                new SlsControlPayload(holder.pos(), power01, speed01));
    }

    /**
     * Sends the new setting to the server, which owns it.
     *
     * <p>This element never applies a committed change locally — {@link #dragPreviewSetting} is
     * only ever drawn, never treated as the real value. The real value comes back through the
     * menu's synced data on the next tick, so what {@link #drawDriftMark} and the fill compare
     * against is always what the machine actually has.
     */
    private void sendSetting(double value) {
        ClientPacketDistributor.sendToServer(new MachineSettingPayload(holder.pos(), (float) value));
    }

    @LDLRegisterClient(name = "machine_body", registry = "ldlib2:ui_element_renderer")
    public static final class MachineBodyRenderer
            extends DelegatingUIElementRenderer<MachineBody, MachineBodyRenderer> {
        @Override
        public Class<MachineBody> type() {
            return MachineBody.class;
        }

        @Override
        public void drawBackgroundAdditional(MachineBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.lastMouseX = guiContext.mouseX;
            element.lastMouseY = guiContext.mouseY;
            element.drawMachine(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY(), guiContext.mouseX, guiContext.mouseY);
        }
    }
}
