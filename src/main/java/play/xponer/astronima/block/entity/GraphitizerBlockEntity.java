package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.chem.Graphitization;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * Real high-temperature carbon chemistry, run in a box: {@link Graphitization}'s own two real
 * processes, sharing one vessel that heats itself to whichever setpoint its own feed needs.
 *
 * <p><strong>No dial.</strong> Checked against this codebase's own real history before writing this
 * (design/carbon-fiber.md §2): a player-turned temperature dial was tried on three other machines
 * and removed from all three on direct feedback that dials are useless — the solar retort's focus,
 * the carbonyl refiner's temperature, the fluidized bed's spin. This follows
 * {@link CarbonylRefinerBlockEntity#setpointK()}'s own pattern instead: the vessel reads its own
 * feed and heats straight to the one real temperature that feed needs. The room the player builds
 * around it is the real control surface — oxygen-bearing to stabilize a pitch fiber, purged to
 * carbonize/graphitize carbon powder or a stabilized fiber.
 *
 * <p><strong>Oxygen at the high setpoint is not a stall, it is a different real reaction.</strong>
 * Hot carbon burns in air (C + O2 -&gt; CO2) faster than it reorders, so a Graphitizer run hot in an
 * unpurged room does not hold — it runs the combustion branch instead, consuming the charge as
 * smoke and drawing down the room's own oxygen, the same real spend {@link TroiliteRoasterBlockEntity}
 * already charges for its own reaction.
 */
public class GraphitizerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Work for one charge. Long, because this is a process you leave running, the same standing
     *  the carbonyl refiner and fluidized bed already give a machine with a real per-tick reaction. */
    public static final int BATCH_WORK = 400;

    /** Carbon in one feed item, in moles — sized, with {@link #STEP_RATE_MOL}, so a fully-cranked
     *  batch's own real chemistry finishes converting inside the same window its work bar does. */
    public static final double CHARGE_CARBON_MOL = 10.0;

    /** How much carbon one tick's real step can move at most, mol — independent of hand-cranking
     *  (the same decoupling {@link CarbonylRefinerBlockEntity}'s own leak and the fluidized bed's
     *  own entrainment already use): the chemistry runs at its own real pace, whoever is turning
     *  the handle or not. */
    public static final double STEP_RATE_MOL = 0.1;

    /** Moles of product one output item stands for — one, so a partly-burned charge yields
     *  proportionally fewer items rather than an all-or-nothing batch, the same real "recovers a
     *  fraction" shape {@code FluidizedBedBlockEntity} already gives a spun-too-slow charge. */
    public static final double MOL_PER_ITEM = 1.0;

    /** The charge currently in the vessel: carbon left to process, and product already made. */
    private Graphitization.Charge charge = Graphitization.Charge.of(0);

    /** Whether {@link #charge} has been drawn from the feed item currently sitting in the slot.
     *
     * <p>Not inferred from {@code charge} being empty: real combustion can legitimately burn a
     * whole charge to nothing (carbon and product both zero) well before the work bar's own
     * {@link #workRequired()} ticks have passed, and reloading on "the charge reads empty" would
     * load and burn a second, a third, a whole unlimited series of fresh charges out of the one
     * feed item still sitting in the slot - spending real room oxygen and making real CO2 from
     * nothing. A charge is drawn exactly once per feed item, consumed in {@link #finishBatch()}. */
    private boolean chargeLoaded;

    /** Which real process {@link #charge} was drawn for — lets a mid-batch feed swap be detected
     *  and the stale charge discarded rather than silently finishing under the wrong chemistry. */
    private boolean chargeIsLowSetpoint;

    public GraphitizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAPHITIZER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.GRAPHITIZER;
    }

    /** Whether the loaded feed wants the low, oxidative-stabilization setpoint rather than the
     *  high carbonization/graphitization one. */
    private boolean lowSetpoint() {
        return getItem(SLOT_INPUT).is(ModItems.PITCH_FIBER.get());
    }

    /** What the vessel is actually holding, in K — computed fresh from the feed, never set by a
     *  player. See {@link Graphitization#LOW_SETPOINT_K} / {@link Graphitization#HIGH_SETPOINT_K}. */
    public double setpointK() {
        if (!hasFeed()) {
            return 0;
        }
        return lowSetpoint() ? Graphitization.LOW_SETPOINT_K : Graphitization.HIGH_SETPOINT_K;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        ItemStack feed = getItem(SLOT_INPUT);
        return feed.is(ModItems.CARBON_POWDER.get()) || feed.is(ModItems.PITCH_FIBER.get())
                || feed.is(ModItems.STABILIZED_FIBER.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(outputItem()));
    }

    private net.minecraft.world.item.Item outputItem() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (feed.is(ModItems.CARBON_POWDER.get())) {
            return ModItems.GRAPHITE_POWDER.get();
        }
        if (feed.is(ModItems.STABILIZED_FIBER.get())) {
            return ModItems.CARBON_FIBER.get();
        }
        return ModItems.STABILIZED_FIBER.get(); // pitch_fiber's own product
    }

    /**
     * The vessel's own two stalls: no oxygen to stabilize with, held; oxygen where there should
     * be none, which is not held at all — the batch keeps burning (see {@link #canRun}).
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        double roomO2 = roomOxygenMol();
        if (lowSetpoint()) {
            return roomO2 > 0 ? general : WorkState.NEEDS_OXIDIZER;
        }
        return roomO2 > 0 ? WorkState.COMBUSTING : general;
    }

    /**
     * Combusting is deliberately still a run: the batch advances while the charge burns, because
     * that loss is the lesson (see {@link WorkState#COMBUSTING}). Only a starved stabilization
     * holds the bar.
     */
    @Override
    public boolean canRun() {
        if (!super.canRun()) {
            return false;
        }
        WorkState state = workState();
        return state.isWorking() || state == WorkState.COMBUSTING;
    }

    private double roomOxygenMol() {
        RoomState room = room();
        return room == null ? 0 : room.gases().get(Gas.OXYGEN);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    /**
     * One real step of whichever process the feed calls for, and the combustion that comes with
     * it at the high setpoint if the room still has oxygen in it.
     *
     * <p>Called from the machine's own tick, so it runs at whatever rate the game runs the
     * machine, independent of the work bar's own crank/idle rate — the same cadence the carbonyl
     * refiner's leak and the fluidized bed's entrainment already use.
     */
    @Override
    public void serverTick() {
        super.serverTick();
        if (!(level instanceof ServerLevel serverLevel) || !hasFeed()) {
            return;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(worldPosition);
        if (room == null) {
            return;
        }
        boolean wantsLow = lowSetpoint();
        if (!chargeLoaded) {
            charge = Graphitization.Charge.of(CHARGE_CARBON_MOL);
            chargeLoaded = true;
            chargeIsLowSetpoint = wantsLow;
        } else if (chargeIsLowSetpoint != wantsLow) {
            // The feed was swapped for a different real process mid-batch - a batch worked at
            // two setpoints is not one product, the same reasoning abandonBatch() already
            // applies to a calibration changed mid-run.
            charge = Graphitization.Charge.of(CHARGE_CARBON_MOL);
            chargeIsLowSetpoint = wantsLow;
            abandonBatch();
        }
        double roomO2 = room.gases().get(Gas.OXYGEN);
        if (lowSetpoint()) {
            charge = Graphitization.stepLowSetpoint(charge, roomO2, STEP_RATE_MOL);
        } else {
            Graphitization.Step step = Graphitization.stepHighSetpoint(charge, roomO2, STEP_RATE_MOL);
            if (step.o2ConsumedMol() > 0) {
                room.removeGas(Gas.OXYGEN, step.o2ConsumedMol());
            }
            if (step.co2ProducedMol() > 0) {
                room.addGasAt(Gas.CARBON_DIOXIDE, step.co2ProducedMol(), room.temperatureK());
            }
            charge = step.charge();
        }
        setChanged();
    }

    /**
     * Finishes a charge: whatever the real chemistry actually made it to, harvested as items —
     * nothing at all if the whole charge burned away.
     */
    @Override
    protected void finishBatch() {
        if (!hasFeed()) {
            return;
        }
        int made = (int) Math.floor(charge.productMol() / MOL_PER_ITEM);
        if (made > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(outputItem(), made));
        }
        getItem(SLOT_INPUT).shrink(1);
        charge = Graphitization.Charge.of(0);
        chargeLoaded = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("charge_carbon_mol", charge.carbonMol());
        output.putDouble("charge_product_mol", charge.productMol());
        output.putBoolean("charge_loaded", chargeLoaded);
        output.putBoolean("charge_is_low_setpoint", chargeIsLowSetpoint);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        charge = new Graphitization.Charge(
                input.getDoubleOr("charge_carbon_mol", 0.0),
                input.getDoubleOr("charge_product_mol", 0.0));
        chargeLoaded = input.getBooleanOr("charge_loaded", false);
        chargeIsLowSetpoint = input.getBooleanOr("charge_is_low_setpoint", false);
    }
}
