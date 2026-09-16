package play.xponer.astronima.block.entity;

import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.wire.WirePower;
import play.xponer.astronima.wire.WireSignal;
import play.xponer.astronima.sim.circuit.Delivery;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.sim.power.PowerBalance;
import play.xponer.astronima.sim.thermal.HeatBalance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * Shared body of a machine: it holds items, it runs over time, and it can be fed.
 *
 * <p>Written because the first version of these machines held nothing and took no
 * time — hold ore, click, get product. That is a crafting recipe with extra steps, and
 * it is the difference between a machine you operate and a verb you perform on a
 * block. A machine has to have <em>state you can inspect</em> and <em>inputs you can
 * control</em>; everything else is decoration on top of those two things.
 *
 * <p>Ticking rather than clicking also buys automation for free: a hopper can feed a
 * thing that consumes from a slot on its own schedule, and cannot feed a thing that
 * only reacts to a right-click. That is why every serious tech mod's machines look
 * like this, and it is not a graphics question.
 */
public abstract class ProcessingBlockEntity extends ReadableBlockEntity
        implements net.minecraft.world.WorldlyContainer {
    /** Work added per tick when a player is turning the handle. */
    public static final int CRANK_RATE = 4;

    /** Work added per tick when only a hopper is feeding it — slower, but unattended. */
    public static final int IDLE_RATE = 1;

    /** Ticks of no cranking after which the machine falls back to the idle rate. */
    private static final int CRANK_MEMORY_TICKS = 30;

    protected final NonNullList<ItemStack> items;
    private int work;
    private int crankedUntil;
    /** What {@link #drawPower()} last actually got, 0..1 — synced so the panel can show it
     *  (design/machine-io.md, "why is there no energy gauge"): a machine drawing a partial
     *  supply and one simply not wired at all both used to read as the same silent "creeping"
     *  state, with no way to tell "wire it up" from "you already did, badly" apart. */
    private double lastPoweredFraction;

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                    int slots) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ subclass API

    /** Work needed to finish the batch currently in the input slot. */
    /** Which machine this is, so one list decides what its feed slot takes. */
    public abstract play.xponer.astronima.menu.ProcessingMenu.Kind kind();

    /**
     * The feed slot is index 0 on every machine; everything after it is product.
     *
     * <p>Stated once here rather than per machine because the automation rules below all
     * hang off it, and a machine that numbered its slots differently would silently let a
     * hopper drain its input.
     */
    public static final int SLOT_FEED = 0;

    // ------------------------------------------------------------------ automation
    //
    // Reported from play: "hoppers put the item in the feed slot and take it back out of
    // the same slot instead of the product one". A plain Container has no sides, so a
    // hopper may touch any slot — it fed the machine and then immediately stole the feed
    // back, and no amount of correct processing logic could survive that. Sided access
    // fixes it, in the grammar the vanilla furnace already taught: in from the top and
    // sides, out from the bottom.

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            int[] products = new int[getContainerSize() - 1];
            for (int i = 0; i < products.length; i++) {
                products[i] = i + 1;
            }
            return products;
        }
        return new int[] {SLOT_FEED};
    }

    /** Only the feed slot takes anything, and only what this machine can actually use. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == SLOT_FEED && canPlaceItem(slot, stack);
    }

    /** Only product comes out. A hopper must never be able to drain the feed. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != SLOT_FEED;
    }

    /** The same rule the feed slot in the screen uses, so the two cannot drift apart. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FEED
                && play.xponer.astronima.menu.ProcessingMenu.feedAccepts(kind(), stack);
    }

    public abstract int workRequired();

    /**
     * Whether this machine can make any progress at all without real electrical power — true for
     * everything by default (machines.md §7: "faster, never better," every machine can be
     * hand-cranked or left idling unattended on a hopper). False is a real, narrow exception: the
     * one process in this game with no manual-labour equivalent. A person turning a handle can
     * crush rock or work metal; nobody can hand-crank electron transfer across a membrane, and
     * every real route to splitting water needs actual electricity (design/oxygen.md §1) — there
     * is no honest idle rate for this specific reaction the way there is for every other machine.
     */
    protected boolean canRunWithoutPower() {
        return true;
    }

    // ------------------------------------------------------------------ calibration
    //
    // Lifted here rather than written into each machine, because five copies of one mechanic is
    // five places to fix it and four of them will be missed (rule 20). A machine joins in by
    // naming its drift; everything else - the wear, the wrench, the settling, the save - happens
    // once. See design/calibration.md.

    /**
     * What the operator last set, 0..1, by hand, on the machine.
     *
     * <p>NaN until somebody sets or loads one, so that {@link #defaultSetting()} can be a method a
     * subclass overrides. Reading it through the accessor rather than calling an overridable method
     * from the constructor, which is the version of this that works and the version that doesn't.
     */
    private double calibratedTo = Double.NaN;

    /**
     * Where this machine sits when it comes out of the crate.
     *
     * <p>Machines disagree: a separator wants a strong field, a forge a gentle blow. A default in
     * the middle would be a machine that arrives mis-set for no reason the player can see.
     */
    protected double defaultSetting() {
        return 0.5;
    }

    /** Work done since then, which is what moves it. */
    private double workSinceCalibration;

    /** Seconds of settling left after a turn of the wrench. The machine is stopped meanwhile. */
    private double calibrating;

    /**
     * Which way this machine's setting slides as it is used.
     *
     * <p>{@link Calibration.Drift#NONE} by default: a machine has to opt in, and the solar retort
     * deliberately does not — its right answer already moves on its own, because the sun moves it.
     */
    public Calibration.Drift drift() {
        return Calibration.Drift.NONE;
    }

    /** Whether this machine is set with a wrench rather than with a dial in its panel. */
    public boolean isSetByWrench() {
        return drift().isSetByWrench();
    }

    /** What the operator set, for a panel to show beside where it has got to. */
    public double calibratedTo() {
        return Double.isNaN(calibratedTo) ? defaultSetting() : calibratedTo;
    }

    /**
     * Where the setting actually is.
     *
     * <p>Everything downstream reads this rather than what was set, because the rock does not know
     * what anybody intended.
     */
    public double calibratedSetting() {
        return Calibration.after(calibratedTo(), drift(), workSinceCalibration);
    }

    public double calibrationError() {
        return Calibration.error(calibratedTo(), calibratedSetting());
    }

    /** Work charged toward drift since the setting was last put right - what
     *  {@link #calibratedSetting()} itself already reads, exposed so a codex calculator can be
     *  handed the exact same number a real machine used, rather than a second copy of it. */
    public double workSinceCalibration() {
        return workSinceCalibration;
    }

    public boolean isWorthRecalibrating() {
        return Calibration.isWorthResetting(calibratedTo(), calibratedSetting());
    }

    /** How far through a settling it is, 0..1, or zero when nobody has touched it. */
    public double calibrationProgress() {
        return calibrating <= 0 ? 0 : 1 - calibrating / Calibration.SECONDS_TO_CALIBRATE;
    }

    public boolean isBeingCalibrated() {
        return calibrating > 0;
    }

    /** One notch of the adjuster. Ten of them cross the range: a job rather than a flick. */
    public static final double NOTCH = 0.1;

    /**
     * A turn of the wrench.
     *
     * <p>On a wrench-set machine this moves the setting one notch <em>and</em> re-shims it true,
     * because on a real machine those are one job. An early version separated them and left the
     * player unable to choose a setting at all — the dial was gone and the wrench only put things
     * back where they had been.
     *
     * <p>On a machine that kept its dial the wrench only re-zeroes the instrument: the setpoint is
     * the operator's instruction and a tool has no business moving it.
     *
     * @param direction +1 one way, -1 the other; ignored where a dial chooses the value
     */
    public void turnTheAdjuster(int direction) {
        if (isSetByWrench()) {
            double next = Math.clamp(calibratedTo() + NOTCH * Math.signum(direction), 0.0, 1.0);
            if (Math.abs(next - calibratedTo()) > 1e-4) {
                abandonBatch(); // a batch worked at two settings is not one product
            }
            calibratedTo = next;
        }
        workSinceCalibration = 0;
        calibrating = Calibration.SECONDS_TO_CALIBRATE;
        setChanged();
    }

    /**
     * Sets it outright, from a dial.
     *
     * <p>Re-zeroes the wear as well, because a value the operator has just typed in is by
     * definition where they want it — the drift starts again from there.
     */
    protected void setCalibratedTo(double value) {
        double next = Math.clamp(value, 0.0, 1.0);
        if (Math.abs(next - calibratedTo()) > 1e-4) {
            calibratedTo = next;
            workSinceCalibration = 0;
            onSettingChanged();
        }
    }

    /**
     * Sets it as a wrench would, for the debug command.
     *
     * <p>Public where {@link #setCalibratedTo} is not, because a dial belongs to the machine that
     * has one and this is the operator standing at the thing with a tool.
     */
    public void calibrateTo(double value) {
        setCalibratedTo(value);
        setChanged();
    }

    /**
     * What a machine does when its dial moves.
     *
     * <p>Abandoning the batch by default: a load worked at two settings is not one product, and
     * the crusher's yield would otherwise be a matter of flipping to fine on the last tick. The
     * machines whose <em>operation</em> is moving the dial override this to do nothing.
     */
    protected void onSettingChanged() {
        abandonBatch();
    }

    /**
     * Work done, for machines whose setting drifts as they are used.
     *
     * <p>Hung on the tick that already counts work rather than on a clock, because that is the
     * distinction that matters: a machine standing idle keeps its setting, and one that has been
     * grinding all afternoon does not.
     */
    protected void wear(int amount) {
        if (drift().drifts()) {
            workSinceCalibration += Calibration.workCharged(
                    workSinceCalibration, amount, drift(), isServoHolding());
        }
    }

    /** Whether a wired, powered servo is holding this machine's calibration steady. */
    private boolean servoHolding;

    public boolean isServoHolding() {
        return servoHolding;
    }

    /**
     * Whether a servo is landed on this machine, wired and live, with power arriving.
     *
     * <p>Both halves are required and the second is the tier speaking: a machine turned by
     * somebody's arm draws nothing, so its servo is dead. The servo is what electricity buys.
     *
     * @param poweredFraction what share of a full draw actually arrived this tick
     */
    private boolean servoLive(double poweredFraction) {
        return poweredFraction > 0
                && level instanceof ServerLevel serverLevel
                && WireSignal.anyInputLive(serverLevel, worldPosition,
                        play.xponer.astronima.wire.Terminated.SERVO);
    }

    /**
     * A key written by an older version of this machine and no longer written by this one.
     *
     * <p>Its own named door rather than a raw read, so that rule 5's guard — every key saved is
     * loaded and every key loaded is saved — can tell a deliberate migration from the silent data
     * loss it exists to catch. A raw {@code input.getDoubleOr} here would have to be excused by a
     * comment, and a guard satisfied by prose is not a guard (rule 33).
     *
     * @return the old value, or NaN when this save never had one
     */
    protected static double legacyValue(ValueInput input, String key) {
        return input.getDoubleOr(key, Double.NaN);
    }

    /**
     * Counts the settling down.
     *
     * <p>Called before the run check, not after: while it is settling the machine cannot run, and
     * counting this down on the far side of that check would leave a machine stopped forever by
     * its own calibration.
     */
    private void settle() {
        if (calibrating > 0) {
            calibrating = Math.max(0, calibrating - 1 / 20.0);
            setChanged();
        }
    }

    /** True when the feed slot holds something this machine can process right now. */
    public abstract boolean hasFeed();

    /**
     * True when the batch this machine would finish has somewhere to go.
     *
     * <p>Asked separately from {@link #hasFeed()} rather than folded into one "can it run"
     * because the two stalls need opposite actions from the player, and a machine that only
     * reports "stopped" makes them guess which. Implementations must answer safely with an
     * empty feed — there is no batch to place, so there is nothing blocking it.
     */
    public abstract boolean hasRoomForProduct();

    /** True when the input is something this machine can process and outputs have room. */
    public boolean canRun() {
        return hasFeed() && hasRoomForProduct();
    }

    /**
     * What the machine is doing and why, for the panel.
     *
     * <p>Overridable because a machine can be stopped for a reason of its own — the forge
     * holding a cracked billet is fed and has room, and calling that "no feed" would send
     * the player to fetch more metal for a piece that is already ruined.
     */
    public WorkState workState() {
        return WorkState.of(hasFeed(), hasRoomForProduct(), crankedUntil > 0);
    }

    /** What share of a full electrical draw this machine last actually got, 0..1 — meaningless
     *  while hand-cranked, since {@link #drawPower()} never runs in that tick at all. */
    public double poweredFraction() {
        return lastPoweredFraction;
    }

    /** Consumes the input and produces outputs. Called once when work completes. */
    protected abstract void finishBatch();

    // --------------------------------------------------------------------- operation

    /**
     * A machine's own thing to do every tick, regardless of whether it is working.
     *
     * <p>Called before the calibrating/starved/blocked early return in {@link #serverTick},
     * because some per-machine state — the solar retort's vessel temperature, which is only
     * sunlight and focus, not feed — keeps being true whether or not a batch is running. A
     * hook here rather than a check bolted onto the shared tick keeps that reasoning local to
     * the one machine it actually applies to.
     */
    protected void onServerTick() {}

    /** Called when a player turns the handle; keeps the fast rate alive briefly. */
    public void crank() {
        crankedUntil = CRANK_MEMORY_TICKS;
        setChanged();
    }

    public void serverTick() {
        onServerTick();
        if (crankedUntil > 0) {
            crankedUntil--;
        }
        settle();
        if (isBeingCalibrated() || !canRun()) {
            // Losing the feed does not throw away progress: a machine you have to
            // babysit is a chore, and half-crushed rock does not un-crush itself.
            return;
        }
        // Power buys the rate a person on the handle buys, and no more (machines.md §7:
        // faster, never better). A partial supply buys a partial rate rather than nothing,
        // so six panels is a slower machine and not a cliff. canRunWithoutPower() narrows this
        // for the one machine with no real hand-crank equivalent - see its own doc.
        double poweredFraction = drawPower();
        lastPoweredFraction = poweredFraction;
        servoHolding = servoLive(poweredFraction);
        boolean canIdle = canRunWithoutPower();
        int rate = crankedUntil > 0 && canIdle ? CRANK_RATE
                : Math.max(canIdle ? IDLE_RATE : 0, (int) Math.round(CRANK_RATE * poweredFraction));
        work += rate;
        wear(rate);
        shedWorkHeat(poweredFraction);
        if (work >= workRequired()) {
            work = 0;
            finishBatch();
        }
        setChanged();
    }

    /**
     * The heat a machine under the handle puts into the room around it.
     *
     * <p>Every joule the operator puts in comes back out: crushing rock, working metal and
     * turning a drum all end as friction and deformation, and none of it leaves the room.
     * So it is charged <strong>only while the machine is actually being cranked</strong> —
     * an idling machine is not doing work on anything and must not warm a habitat for free,
     * which would turn the thermal counter into a block you place rather than a thing you do.
     *
     * <p>Contributed in joules per tick from the machine's own tick, which is what keeps it
     * honest across cadences — see {@code Room.pendingHeatJ}.
     */
    private void shedWorkHeat(double poweredFraction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Cranked or plugged in, the heat is the same: everything the operator or the cable
        // puts in ends as friction and broken rock, and none of it leaves the room. Charging
        // for both at once would double-count a machine somebody is cranking while it draws.
        double watts = crankedUntil > 0
                ? HeatBalance.machineWatts(1)
                : PowerBalance.wasteHeatWatts(HeatBalance.WORKED_MACHINE_W * poweredFraction);
        if (watts > 0) {
            Atmosphere.get(serverLevel).addHeatJoules(worldPosition, watts * 0.05);
        }
    }

    /**
     * Takes a tick's worth of energy from a cell it is touching, and says what share of its
     * rated draw it got.
     *
     * <p>Nothing is drawn while somebody is cranking: a machine already turning under a hand
     * does not need the cell, and letting it take both would charge the room twice for one
     * batch of work.
     */
    private double drawPower() {
        if (crankedUntil > 0 || !(level instanceof ServerLevel)) {
            return 0;
        }
        // Held off by logic, when a control line has actually been wired to it.
        //
        // The unwired case runs, and that asymmetry is the whole design of the enable: a machine
        // that stopped the moment the feature shipped would have broken every base in the world
        // for a feature nobody had opted into. Wire a line to the blue stud and you have taken
        // charge of the machine; leave it bare and nothing changed.
        // Scoped to the enable stud. Unscoped, a wire landed on the servo would have read as
        // somebody having taken charge of the machine, and a machine with a dead enable line and a
        // live servo would have switched itself on (design/calibration.md §6.4).
        if (WireSignal.hasSignalWiring((ServerLevel) level, worldPosition,
                        play.xponer.astronima.wire.Terminated.ENABLE)
                && !WireSignal.anyInputLive((ServerLevel) level, worldPosition,
                        play.xponer.astronima.wire.Terminated.ENABLE)) {
            return 0;
        }

        double wantedJ = PowerBalance.joules(HeatBalance.WORKED_MACHINE_W, 0.05);

        // A routed trace first: what a machine at the end of one actually gets is decided by the
        // metal and the distance the player chose, and the shortfall warms the run rather than
        // vanishing.
        WirePower.Run traced = WirePower.runFrom((ServerLevel) level, worldPosition);
        if (traced != null && !traced.isEmpty()) {
            return drawnThrough((ServerLevel) level, traced, wantedJ);
        }

        CableNetworks.Resolved run =
                CableNetworks.resolveFrom((ServerLevel) level, worldPosition);
        if (run == null || run.isEmpty()) {
            return 0;
        }
        double wanted = PowerBalance.joules(HeatBalance.WORKED_MACHINE_W, 0.05);
        // Ask for what a full draw would need *before* the run takes its share, so a machine at
        // the end of a long cable is starved rather than quietly having the loss forgiven. The
        // share is found by asking the law what a full delivery would cost.
        double efficiency = PowerRun.efficiency(run, wanted, 0.05);
        double asked = efficiency > 0 ? wanted / efficiency : wanted;
        double taken = 0;
        for (PowerCellBlockEntity cell : run.cells()) {
            taken += cell.draw(asked - taken);
            if (taken >= asked) {
                break;
            }
        }
        double got = PowerRun.sendAlong((ServerLevel) level, run, taken, 0.05);
        return wanted <= 0 ? 0 : Math.clamp(got / wanted, 0.0, 1.0);
    }

    /**
     * How much of a full draw arrives through a routed trace, 0..1.
     *
     * <p><strong>The machine gets what the bus can push, and a full battery does not change
     * that.</strong> The old model worked out what the run would waste and asked the battery for
     * that much extra, so a hundred metres of signal wire to a crusher cost nothing but charge and
     * the machine ran at full speed — {@code design/electrical.md} §5.1's undervolting was written
     * down and never happened. {@link play.xponer.astronima.sim.circuit.Sag} is the divider that
     * makes it true: half the voltage at the machine is a <em>quarter</em> of the work.
     *
     * <p>And an undervolted machine is a <strong>cheaper</strong> load, not a dearer one. A badly
     * wired base does not drain its batteries faster; it just gets nothing done, which is a far
     * better lesson about what a bad run costs.
     */
    private double drawnThrough(ServerLevel level, WirePower.Run run, double wantedJ) {
        double resistance = run.resistanceOhms();
        // What the bus can actually push through this much line into a load of this size - not
        // what the machine would like. A wire cannot be persuaded to carry more by wanting it
        // more: the source has a fixed voltage, the line and the load divide it, and the machine
        // gets whatever share its own resistance won.
        double asked = play.xponer.astronima.sim.circuit.Sag.drawWatts(resistance,
                HeatBalance.WORKED_MACHINE_W, Delivery.BUS_VOLTS) * 0.05;

        double taken = 0;
        for (BlockPos terminal : run.terminals()) {
            if (taken >= asked) {
                break;
            }
            if (level.getBlockEntity(terminal) instanceof PowerCellBlockEntity cell) {
                taken += cell.draw(asked - taken);
            }
        }
        if (taken <= 0) {
            return 0;
        }
        double arrived = WirePower.send(level, run, taken, 0.05);
        return wantedJ <= 0 ? 0 : Math.clamp(arrived / wantedJ, 0.0, 1.0);
    }

    public float progress() {
        int required = workRequired();
        return required <= 0 ? 0 : Math.clamp(work / (float) required, 0f, 1f);
    }

    public int work() {
        return work;
    }

    public void setWork(int value) {
        work = Math.max(0, value);
    }

    /** Resets progress when a setting changes mid-batch — the grind has to be uniform. */
    protected void abandonBatch() {
        work = 0;
        setChanged();
    }

    /** True when a hopper or a player has it running, for the block's lit state. */
    public boolean isRunning() {
        return canRun();
    }

    /** Puts a stack into an output slot if it fits, returning what did not fit. */
    protected ItemStack pushOutput(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) {
            items.set(slot, stack);
            return ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }
        int room = existing.getMaxStackSize() - existing.getCount();
        int moved = Math.min(room, stack.getCount());
        existing.grow(moved);
        stack.shrink(moved);
        return stack;
    }

    protected boolean hasRoom(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack existing = items.get(slot);
        return existing.isEmpty()
                || (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize());
    }

    // -------------------------------------------------------------------- Container

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("calibrated_to", calibratedTo());
        output.putDouble("work_since_calibration", workSinceCalibration);
        output.putDouble("calibrating", calibrating);
        net.minecraft.world.ContainerHelper.saveAllItems(output, items);
        output.putInt("work", work);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // A machine from a save made before wear existed reads as freshly calibrated rather than
        // as one that has been running unattended since the world began (rule 5).
        calibratedTo = Math.clamp(input.getDoubleOr("calibrated_to", defaultSetting()), 0.0, 1.0);
        workSinceCalibration = Math.max(0, input.getDoubleOr("work_since_calibration", 0));
        calibrating = Math.max(0, input.getDoubleOr("calibrating", 0));
        items.clear();
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        work = input.getIntOr("work", 0);
    }

    /**
     * Drops everything held, for anything that needs to spill a machine on purpose.
     *
     * <p>Not what makes breaking a machine safe — that is vanilla's, and it took a mutation
     * to establish it: {@code BlockEntity.preRemoveSideEffects} already drops the contents of
     * any {@code Container}, before the block's own removal hook and before the block entity
     * is detached. Five blocks here carried an override that called this and never ran; they
     * are gone. The guarantee lives in
     * {@code scenario_breaking_a_full_machine_drops_everything}.
     */
    public void dropContents(Level level, BlockPos pos) {
        net.minecraft.world.Containers.dropContents(level, pos, this);
    }
}
