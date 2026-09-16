package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.chem.HaliteElectrolysis;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * The Downs process: molten rock salt, electrolyzed into real sodium metal and real chlorine
 * gas — {@code 2 NaCl (l) -> 2 Na (l) + Cl2 (g)}. See {@code design/halogens.md}.
 *
 * <p>Mirrors {@link TroiliteRoasterBlockEntity}'s own shape exactly: one ore item is one fixed
 * charge, converted whole in {@link #finishBatch()} rather than accumulated per tick, because
 * — like troilite roasting and molten-oxide electrolysis — there is no gradual hazard here for
 * per-tick state to buy. Gated on a sealed room to vent into, the same reason
 * {@link ElectrolysisCellBlockEntity} gates on one for its own oxygen: chlorine is real and
 * dangerous, and this machine does not make it disappear just because nowhere sealed exists to
 * receive it.
 */
public class DownsCellBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 400;

    /** One halite ore item's own declared mass — design/halogens.md §3. */
    public static final double HALITE_GRAMS_PER_ITEM = 200.0;

    public DownsCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOWNS_CELL.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.DOWNS_CELL;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModBlocks.HALITE_ORE.get().asItem());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.SODIUM.get()));
    }

    /** Only runs with somewhere sealed to vent the chlorine — the same rule the electrolysis
     * cell already enforces for its own oxygen. */
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

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (!feed.is(ModBlocks.HALITE_ORE.get().asItem())) {
            return;
        }
        RoomState room = receivingRoom();
        if (room == null) {
            return; // guarded by workState, but never electrolyze a charge into nowhere
        }

        HaliteElectrolysis.Charge charge = HaliteElectrolysis.Charge.of(HALITE_GRAMS_PER_ITEM);
        HaliteElectrolysis.Step step = HaliteElectrolysis.step(charge, 1.0);

        int sodiumItems = (int) Math.floor(step.sodiumMol()
                / play.xponer.astronima.item.SodiumItem.MOL_PER_ITEM);
        if (sodiumItems > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.SODIUM.get(), sodiumItems));
        }
        if (step.cl2Mol() > 0) {
            room.addGasAt(Gas.CHLORINE, step.cl2Mol(), room.temperatureK());
        }
        feed.shrink(1);
    }
}
