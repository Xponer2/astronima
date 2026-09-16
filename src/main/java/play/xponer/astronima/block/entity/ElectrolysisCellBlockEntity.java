package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;
import play.xponer.astronima.sim.ore.MoltenElectrolysis;
import play.xponer.astronima.sim.ore.OreBody;

/**
 * Molten-oxide electrolysis: splits whatever oxide its installed electrode targets into
 * metal at the cathode and free oxygen at the anode, straight into the sealed room this
 * cell is set into.
 *
 * <p><strong>What it reaches is set by the electrode, never by the power drawn.</strong>
 * An earlier draft of this design gated species behind watts available and broke
 * {@code machines.md} §7's own contract before anything was built — see
 * {@code design/electrolysis.md}'s status note. Power here does exactly what it does on
 * every sibling machine: buys the crank rate, nothing else. {@link #target()} reads only
 * {@link #SLOT_ELECTRODE}; nothing here ever reads how much power arrived.
 *
 * <p><strong>One target, not a mixture</strong> (a named simplification,
 * {@code design/electrolysis.md} §3/§7): a real cell at a fixed voltage would reduce every
 * oxide below its threshold at once. This one converts only the installed electrode's own
 * share of a fixed assumed feedstock ({@link OreBody#chondrite}, the same composition the
 * retort already cites for its own hydration figure) — a whole batch computed at once in
 * {@link #finishBatch()}, the same one-shot shape {@code SolarRetortBlockEntity} already
 * uses, rather than the slow, per-tick accumulation the carbonyl refiner and fluidized bed
 * need for their own hazards. Electrolysis has no gradual hazard to model: nothing here
 * leaks or blows out between ticks, so there is nothing gradual state would buy.
 */
public class ElectrolysisCellBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_ELECTRODE = 1;
    public static final int SLOT_OUTPUT = 2;

    /** Work for one charge. Long, because this is an investment machine you leave running. */
    public static final int BATCH_WORK = 400;

    /**
     * Mass of one charge, in grams. The same {@link SolarRetortBlockEntity#CHARGE_GRAMS} a
     * bucket of tailings already stands for elsewhere in this tier — one assumed composition,
     * used consistently rather than invented fresh per machine.
     */
    public static final double CHARGE_GRAMS = 4000.0;

    /** Moles of metal one output item stands for — one, so recovery shows as a plain count,
     * the same convention {@code FluidizedBedBlockEntity#MOL_PER_ITEM} already uses. */
    public static final double MOL_PER_ITEM = 1.0;

    public ElectrolysisCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTROLYSIS_CELL.get(), pos, state, 3);
    }

    /**
     * No dial, no drift, no wrench: the only thing an operator ever sets is which electrode
     * is installed, and that is a swap, not a calibration.
     */
    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.ELECTROLYSIS;
    }

    /**
     * Which metal the cell is currently tuned for, read straight off whatever electrode is
     * installed. No electrode at all is a valid, deliberate default: iron, the cheapest and
     * first oxide, needs no crafting prerequisite at all.
     */
    public ElectrolysisSpecies target() {
        ItemStack electrode = getItem(SLOT_ELECTRODE);
        if (electrode.is(ModItems.SILICON_ELECTRODE.get())) {
            return ElectrolysisSpecies.SILICON;
        }
        if (electrode.is(ModItems.ALUMINUM_ELECTRODE.get())) {
            return ElectrolysisSpecies.ALUMINUM;
        }
        return ElectrolysisSpecies.IRON;
    }

    /** The metal item this cell's current target actually produces. */
    private static Item itemFor(ElectrolysisSpecies species) {
        return switch (species) {
            case IRON -> net.minecraft.world.item.Items.IRON_INGOT;
            case SILICON -> ModItems.SILICON.get();
            case ALUMINUM -> ModItems.ALUMINUM.get();
        };
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.TAILINGS.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(itemFor(target())));
    }

    /** Only actually runs with somewhere sealed to send the oxygen — same rule the retort
     * already enforces, for the same reason (oxygen.md §7). */
    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        return receivingRoom() == null ? WorkState.BACKPRESSURE : general;
    }

    /** The sealed room next door the oxygen actually goes into, or null if there is none. */
    private RoomState receivingRoom() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(worldPosition);
        if (reading == null || !reading.sealed() || reading.openToSpace()) {
            return null;
        }
        return reading.state();
    }

    /**
     * Only the electrode slot may be pulled from the sides at all, and only for its own
     * item — otherwise a hopper below would treat "which electrode is installed" as free
     * product the moment this had more than the crusher's two slots, stripping the cell's
     * own equipment out from under a running batch.
     */
    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return side == net.minecraft.core.Direction.DOWN
                ? new int[] {SLOT_OUTPUT} : new int[] {SLOT_INPUT};
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack,
                                          net.minecraft.core.Direction side) {
        return slot == SLOT_OUTPUT;
    }

    /**
     * Splits the whole assumed charge (§ class doc) at once: the target's own share of it
     * becomes metal and oxygen, everything else is residue this machine does not track as
     * an item — a named cut, {@code design/electrolysis.md} §7.
     */
    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (!feed.is(ModItems.TAILINGS.get())) {
            return;
        }
        RoomState room = receivingRoom();
        if (room == null) {
            return; // guarded by workState, but never bake a charge into nowhere
        }

        ElectrolysisSpecies species = target();
        OreBody body = OreBody.chondrite(CHARGE_GRAMS);
        MoltenElectrolysis.Charge charge = MoltenElectrolysis.Charge.of(body, species);
        MoltenElectrolysis.Step result = MoltenElectrolysis.step(charge, 1.0);

        int metalItems = (int) Math.floor(result.metalMol() / MOL_PER_ITEM);
        if (metalItems > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(itemFor(species), metalItems));
        }
        if (result.o2Mol() > 0) {
            room.addGasAt(Gas.OXYGEN, result.o2Mol(), room.temperatureK());
        }
        feed.shrink(1);
    }
}
