package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.item.CrushedIlmeniteItem;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.metal.CentrifugalBed;
import play.xponer.astronima.sim.metal.FluidizedBedControl;
import play.xponer.astronima.sim.metal.IlmeniteReduction;
import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * The centrifugal fluidized-bed reactor, run in a box.
 *
 * <p>You cannot fluidize a bed in free fall, so this one makes its own gravity: spin the drum and
 * the ilmenite is flung against its perforated wall, and a fixed inward hydrogen flow fluidizes it
 * against the artificial gravity the drum now trims for itself (see {@link #setting()}). Spin too
 * fast and it packs to the wall and stalls; spin too slow and the flow carries the whole bed out
 * the exhaust, feed and fresh iron alike — the machine now finds the middle of that window on its
 * own, for whatever grind it was fed. See {@code design/fluidized-bed.md}, and
 * {@link CentrifugalBed} / {@link IlmeniteReduction} for the physics and chemistry it drives.
 *
 * <p><strong>The hydrogen is consumed</strong> — the deliberate contrast with the carbonyl
 * refiner one bench over, whose carbon monoxide is a carrier that comes back. Here the H₂ takes
 * the oxygen and leaves as water, so the player must keep supplying it.
 */
public class FluidizedBedBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_IRON = 1;
    public static final int SLOT_TITANIA = 2;

    /** Work for one charge. Long, because this is a process you leave running and trim. */
    public static final int BATCH_WORK = 400;

    /** Ilmenite in one crushed-ilmenite item, in moles. Sourced from the MC-free control. */
    public static final double CHARGE_ILMENITE_MOL = FluidizedBedControl.CHARGE_ILMENITE_MOL;

    /** Moles of iron (or titania) one output item stands for — one, so recovery shows as a count. */
    public static final double MOL_PER_ITEM = 1.0;


    /** The solids in the drum for the batch in progress. */
    private IlmeniteReduction.Charge charge = IlmeniteReduction.Charge.empty();

    public FluidizedBedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUIDIZED_BED.get(), pos, state, 3);
    }

    /**
     * No dial and nothing to service: the drum matches its own spin to whatever it was fed.
     *
     * <p>Trimming the spin against the grind was never a preference — there is exactly one band
     * that boils a given grind, found the same way a player found it: bracket the two failures
     * and sit between them. {@link #setting()} does that itself now
     * ({@link FluidizedBedControl#matchedDialFor}), so there is no operator setting left for
     * bearing drag to age away from.
     */
    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.FLUIDBED;
    }

    /**
     * How fast the drum is turning, 0..1 — computed fresh every call from the feed actually in
     * the hopper, never set by a player.
     *
     * <p>{@link FluidizedBedControl#matchedDialFor} brackets the boiling band for the current
     * {@link #feedMicrons()} — the same {@link CentrifugalBed#regimeAt} a player used to walk by
     * hand between {@link CentrifugalBed.Regime#BLOWING_OUT} and {@link CentrifugalBed.Regime#PACKED}
     * — and returns its midpoint, so the drum sits centred in the window rather than pinned to
     * either edge of it.
     */
    public double setting() {
        return FluidizedBedControl.matchedDialFor(feedMicrons());
    }

    /** Drum speed it is actually turning at, rpm. */
    public double rpm() {
        return rpmFor(setting());
    }

    /** Geometric map from the 0..1 dial to a drum speed, so the good band sits mid-dial. */
    public static double rpmFor(double setting) {
        return FluidizedBedControl.rpmFor(setting);
    }

    /** Grain size of the feed, µm — what slides the fluidization window. */
    public double feedMicrons() {
        ItemStack feed = getItem(SLOT_INPUT);
        return feed.is(ModItems.CRUSHED_ILMENITE.get())
                ? CrushedIlmeniteItem.micronsOf(feed)
                : play.xponer.astronima.sim.ore.Comminution.GRAIN_SIZE_MICRONS;
    }

    /** Which regime the bed is in at the current grind and spin. */
    public CentrifugalBed.Regime regime() {
        return CentrifugalBed.regimeAt(feedMicrons(), rpm());
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.CRUSHED_ILMENITE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed()
                || (hasRoom(SLOT_IRON, new ItemStack(ModItems.IRON_POWDER.get()))
                        && hasRoom(SLOT_TITANIA, new ItemStack(ModItems.TITANIA.get())));
    }

    /**
     * The bed only advances when it can actually reduce: fed, room for product, hydrogen present,
     * and <em>not</em> packed to the wall.
     *
     * <p>Blowing out is deliberately still a run — the batch advances while the bed leaves,
     * because that loss is the lesson (see {@link WorkState#BLOWING_OUT}). Only a packed bed and a
     * dry room hold the bar.
     */
    @Override
    public boolean canRun() {
        return super.canRun() && roomHydrogen() > 0 && regime() != CentrifugalBed.Regime.PACKED;
    }

    /**
     * What the bed is doing, in the order the player can act on it: the slot stalls first, then
     * the two spin failures and the missing reagent, which are the ones this machine adds.
     */
    @Override
    public WorkState workState() {
        return FluidizedBedControl.workStateFor(super.workState(), roomHydrogen() > 0, regime());
    }

    private double roomHydrogen() {
        RoomState room = room();
        return room == null ? 0 : room.gases().get(Gas.HYDROGEN);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    /**
     * One reduction step, and the entrainment that comes with it.
     *
     * <p>Called from the machine's own tick, so it runs at whatever rate the game runs the
     * machine — the same cadence the work counter advances at, for the reason the refiner taught.
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
        CentrifugalBed.Regime regime = regime();
        if (regime == CentrifugalBed.Regime.PACKED) {
            return; // packed to the wall: nothing reduces, and the charge is kept
        }
        double availableH2 = room.gases().get(Gas.HYDROGEN);
        if (availableH2 <= 0) {
            return; // no reagent: hold rather than blow the feed out for nothing
        }
        if (charge.solidMassGrams() <= 0) {
            charge = IlmeniteReduction.Charge.ofIlmenite(CHARGE_ILMENITE_MOL); // load a fresh drum
        }
        double contact = CentrifugalBed.contactQuality(feedMicrons(), rpm());
        double entrainment = CentrifugalBed.entrainmentSeverity(feedMicrons(), rpm());
        IlmeniteReduction.Step step = IlmeniteReduction.step(charge, availableH2, contact,
                entrainment, FluidizedBedControl.STEP_RATE);

        if (step.h2ConsumedMol() > 0) {
            // Consumed, not handed back — the whole contrast with the refiner (rule 8).
            room.removeGas(Gas.HYDROGEN, step.h2ConsumedMol());
        }
        if (step.waterProducedMol() > 0) {
            room.addGasAt(Gas.WATER_VAPOR, step.waterProducedMol(), room.temperatureK());
        }
        charge = step.charge();
        setChanged();
    }

    /**
     * Finishes a charge: iron powder and titania in the amounts that survived the spin.
     *
     * <p>A matched run recovers nearly the whole charge; a run spun too slow recovers a fraction,
     * because the bed left out the exhaust before it finished reducing. That difference is the
     * reactor's point, so the output is floored from the charge, not fixed.
     */
    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (!feed.is(ModItems.CRUSHED_ILMENITE.get())) {
            return;
        }
        int iron = (int) Math.floor(charge.ironMol() / MOL_PER_ITEM);
        int titania = (int) Math.floor(charge.titaniaMol() / MOL_PER_ITEM);
        if (iron > 0) {
            pushOutput(SLOT_IRON, new ItemStack(ModItems.IRON_POWDER.get(), iron));
        }
        if (titania > 0) {
            pushOutput(SLOT_TITANIA, new ItemStack(ModItems.TITANIA.get(), titania));
        }
        feed.shrink(1);
        charge = IlmeniteReduction.Charge.empty();
    }

    /** Iron reduced in the batch so far, in moles, for the panel readout. */
    public double ironRecovered() {
        return charge.ironMol();
    }

    /** Power draw while it is heating and spinning, W — heat law inherited from the base. */
    public static final double DRUM_WATTS = HeatBalance.WORKED_MACHINE_W;

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("charge_ilmenite", charge.ilmeniteMol());
        output.putDouble("charge_iron", charge.ironMol());
        output.putDouble("charge_titania", charge.titaniaMol());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // No migration for the old "spin"/dial state any more: the drum's speed is derived
        // fresh from feedMicrons() every call now, not stored, so there is nothing left for an
        // old save's dial position to feed into.
        charge = new IlmeniteReduction.Charge(
                input.getDoubleOr("charge_ilmenite", 0.0),
                input.getDoubleOr("charge_iron", 0.0),
                input.getDoubleOr("charge_titania", 0.0));
    }
}
