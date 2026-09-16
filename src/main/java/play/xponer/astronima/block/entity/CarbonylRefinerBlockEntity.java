package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.metal.Carbonyl;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.power.PowerBalance;
import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * The Mond process, run in a box.
 *
 * <p>Carbon monoxide walks the nickel out of a charge of grains at 50 °C and puts it back down
 * pure at 230 °C, <strong>handing the carbon monoxide back</strong>. Iron does not follow — iron
 * pentacarbonyl needs far higher pressure — so what comes out is nickel and iron, apart.
 *
 * <p><strong>The temperature between the two used to be the whole hazard.</strong> Held warm
 * enough to form and too cool to decompose, the vessel worked perfectly and filled with nickel
 * carbonyl — dangerous at parts per million and slow to announce itself — and a player nursing
 * a dial through the journey by hand could park there and never notice. The vessel now runs its
 * own temperature (see {@link #setpointK()}): it heats straight to the point where both halves
 * of the process run at once, so it is structurally never caught between them.
 *
 * <p>It draws power, which is why it is a v0.55 machine: the retort is a mirror and reaches
 * 1287 K but cannot <em>hold</em> 50 °C, and holding a low temperature takes a heater.
 */
public class CarbonylRefinerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    /**
     * The iron gets a slot of its own.
     *
     * <p>Because the machine is a <em>separator</em>: two piles come out and they must not
     * share a slot. The first draft pushed both into one, the nickel filled it, and the iron
     * was quietly destroyed — which is the same shape of bug as the magnetic separator's two
     * streams, and the reason that machine has two slots too.
     */
    public static final int SLOT_IRON = 2;

    /** Work for one charge. Long, because this is a process you leave running. */
    public static final int BATCH_WORK = 400;

    /** Nickel in one charge of grains, in moles — chondritic metal at 7 %. */
    public static final double CHARGE_NICKEL_MOL = 8.0;

    /** How much of the possible movement one tick performs. */
    private static final double STEP_RATE = 0.02;


    /** Nickel currently riding in the gas, in moles — the thing that leaks. */
    private double carbonylHeld;

    public CarbonylRefinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARBONYL_REFINER.get(), pos, state, 3);
    }

    /**
     * No dial and nothing to service: the machine runs its own temperature journey.
     *
     * <p>Forming and decomposing were never really a <em>decision</em> — the correct journey is
     * always the same one, form completely, then decompose completely — so a player dragging a
     * handle through it was reproducing a fixed procedure by hand. {@link #setpointK()} now
     * chooses the vessel's temperature itself every tick, so there is no operator setting for
     * an element to age away from.
     */
    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.REFINER;
    }

    /**
     * What the vessel is actually holding, in K — computed fresh every call, never set by a
     * player.
     *
     * <p>One target, the whole time there is a charge or held gas to work through:
     * {@link Carbonyl#DECOMPOSING_COMPLETE_K}. Not a compromise — {@link Carbonyl#formingFraction}
     * never falls away above its own threshold
     * ({@link play.xponer.astronima.client.hud.RefinerView#isStranded}'s own doc names this: an
     * earlier attempt to model forming stopping at high heat was wrong, and a test caught it),
     * so running hot the whole batch still lifts nickel into the gas at full rate.
     * {@link Carbonyl#decomposingFraction} is <em>also</em> 1 up here, so whatever rides in the
     * gas comes back down as pure metal the very next tick instead of accumulating. The vessel
     * is never anywhere it forms without also decomposing, so it is never
     * {@link Carbonyl.Stage#HOLDING} — the gap between the two that used to be the entire
     * reason a player had to watch this machine's dial.
     */
    public double setpointK() {
        return hasFeed() || carbonylHeld > 0 ? Carbonyl.DECOMPOSING_COMPLETE_K : COLDEST_K;
    }

    /** How much poison is loose inside it, in moles of nickel carried. */
    public double carbonylHeld() {
        return carbonylHeld;
    }

    /** Coldest and hottest the vessel ever reaches, for the panel's scale. */
    public static final double COLDEST_K = 273.15;
    public static final double HOTTEST_K = Carbonyl.DECOMPOSING_COMPLETE_K + 50;

    /** Where a kelvin reading sits on that scale, 0..1 — for the panel's readout bar. */
    public static double dialFor(double kelvin) {
        return Math.clamp((kelvin - COLDEST_K) / (HOTTEST_K - COLDEST_K), 0.0, 1.0);
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.IRON_NICKEL_GRAINS.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed()
                || (hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.PURE_NICKEL.get()))
                        && hasRoom(SLOT_IRON, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
    }

    /**
     * The one stall a refiner has that no other machine does.
     *
     * <p>Never {@code TOO_COLD} any more: {@link #setpointK()} only ever sits anywhere but
     * {@link #COLDEST_K} while there is feed, and it is never too cold there.
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        return carrierAvailable() <= 0 ? WorkState.BACKPRESSURE : general;
    }

    /** Carbon monoxide free in the room the refiner opens onto. */
    private double carrierAvailable() {
        RoomState room = room();
        return room == null ? 0 : room.gases().get(Gas.CARBON_MONOXIDE);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    /**
     * One step of the process, and the leak that comes with it.
     *
     * <p>Called from the machine's own tick, so it runs at whatever rate the game runs the
     * machine — no clock gate, for the reason the generator and the purge valve each taught.
     */
    @Override
    public void serverTick() {
        super.serverTick();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(worldPosition);
        if (room == null) {
            return;
        }
        double co = room.gases().get(Gas.CARBON_MONOXIDE);
        Carbonyl.Step step = Carbonyl.step(nickelLeftInCharge(), carbonylHeld, co,
                setpointK(), STEP_RATE);

        if (step.nickelCarried() > 0) {
            carried += step.nickelCarried();
            carbonylHeld += step.nickelCarried();
            room.removeGas(Gas.CARBON_MONOXIDE, step.coBound());
        }
        if (step.nickelPutDown() > 0) {
            carbonylHeld -= step.nickelPutDown();
            recovered += step.nickelPutDown();
            // The carrier comes back. This is the line the whole design turns on.
            room.addGasAt(Gas.CARBON_MONOXIDE, step.coReleased(), room.temperatureK());
        }
        leak(room);
        if (step.nickelCarried() > 0 || step.nickelPutDown() > 0) {
            setChanged();
        }
    }

    /** Nickel still in the solid charge, in moles. */
    private double nickelLeftInCharge() {
        return hasFeed() ? Math.max(CHARGE_NICKEL_MOL - carried, 0) : 0;
    }

    private double carried;
    private double recovered;

    /**
     * A share of whatever is riding in the gas escapes, every tick.
     *
     * <p><strong>Not a fault state — a property of the machine.</strong> Vessels leak, and a
     * refiner run correctly holds carbonyl only briefly, so the escape is negligible. One
     * parked in the gap holds it indefinitely and the trickle becomes the room's problem. That
     * is what makes the temperature a decision rather than a formality: the danger is
     * proportional to how long you leave the poison inside.
     */
    private void leak(RoomState room) {
        if (carbonylHeld <= 0) {
            return;
        }
        double escaped = carbonylHeld * LEAK_FRACTION;
        carbonylHeld -= escaped;
        // The nickel goes with it and so does its carrier: four moles of carbon monoxide per
        // mole leave the cycle for good. So a machine parked in the gap does not only poison
        // the room, it eats the very resource that made the process worth having.
        room.addGasAt(Gas.NICKEL_CARBONYL, escaped, room.temperatureK());
    }

    /** Share of the held carbonyl that escapes each tick. */
    public static final double LEAK_FRACTION = 0.002;

    /**
     * Finishes a charge: pure nickel out, and the iron that never followed it.
     *
     * <p>Both, because the process is a separator rather than a purifier — the point is that
     * you now know what is in each pile.
     */
    @Override
    protected void finishBatch() {
        ItemStack charge = getItem(SLOT_INPUT);
        if (!charge.is(ModItems.IRON_NICKEL_GRAINS.get())) {
            return;
        }
        pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.PURE_NICKEL.get()));
        pushOutput(SLOT_IRON, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        charge.shrink(1);
        carried = 0;
        recovered = 0;
    }

    /** Power draw while it is holding a temperature, W — it is a heater with a job. */
    public static final double HEATER_WATTS = HeatBalance.WORKED_MACHINE_W;

    /** Nickel recovered from the charge so far, for the panel. */
    public double recovered() {
        return recovered;
    }

    /** The alloy this machine makes possible, for the JEI page's sake. */
    public static double seamNickel() {
        return ColdWorking.SEAM_NICKEL;
    }

    /** Joules a tick of heating costs, so the panel and the draw agree. */
    public static double tickJoules() {
        return PowerBalance.joules(HEATER_WATTS, 0.05);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("carbonyl_held", carbonylHeld);
        output.putDouble("carried", carried);
        output.putDouble("recovered", recovered);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // No migration for the old "setpoint_k"/dial state any more: the vessel's temperature
        // is derived fresh from nickelLeftInCharge()/carbonylHeld() every call now, not stored,
        // so there is nothing left for an old save's dial position to feed into.
        carbonylHeld = input.getDoubleOr("carbonyl_held", 0.0);
        carried = input.getDoubleOr("carried", 0.0);
        recovered = input.getDoubleOr("recovered", 0.0);
    }
}
