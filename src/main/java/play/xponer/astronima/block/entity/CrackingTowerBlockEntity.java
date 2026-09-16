package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.organic.Cracking;

/**
 * Thermal cracking of tholin or sludge into ethylene, straight into the sealed room this tower
 * sits in — the same "one-shot batch, vented on completion" shape
 * {@code ElectrolysisCellBlockEntity} already established for a gas product, reused rather than
 * reinvented. See {@code design/petrochemicals.md} §2 for why the split is an engineering yield
 * rather than an invented stoichiometric equation, and why there is no solid byproduct slot at
 * all — the whole charge leaves as gas.
 */
public class CrackingTowerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED_ONLY = 0;

    /** Work for one charge — shorter than the electrolysis cell's: this is a feedstock step,
     * not the investment machine at the end of a chain. */
    public static final int BATCH_WORK = 240;

    public CrackingTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRACKING_TOWER.get(), pos, state, 1);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.CRACKING_TOWER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        ItemStack feed = getItem(SLOT_FEED_ONLY);
        return feed.is(ModItems.THOLIN_CLUMP.get()) || feed.is(ModItems.SLUDGE.get());
    }

    /** No solid product slot exists — the whole charge leaves as gas, so nothing ever blocks. */
    @Override
    public boolean hasRoomForProduct() {
        return true;
    }

    /** Only actually runs with somewhere sealed to vent into — the electrolysis cell's own rule,
     * for the same reason (a tower venting into nothing loses the whole charge for nothing). */
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

    /** The sealed room the cracked gas actually vents into, or null if there is none. */
    private RoomState receivingRoom() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
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
        ItemStack feed = getItem(SLOT_FEED_ONLY);
        if (!feed.is(ModItems.THOLIN_CLUMP.get()) && !feed.is(ModItems.SLUDGE.get())) {
            return;
        }
        RoomState room = receivingRoom();
        if (room == null) {
            return; // guarded by workState, but never crack a charge into nowhere
        }

        Cracking.Yield yield = Cracking.crack(Cracking.CHARGE_GRAMS);
        if (yield.ethyleneMol() > 0) {
            room.addGasAt(Gas.ETHYLENE, yield.ethyleneMol(), room.temperatureK());
        }
        if (yield.methaneMol() > 0) {
            room.addGasAt(Gas.METHANE, yield.methaneMol(), room.temperatureK());
        }
        feed.shrink(1);
    }
}
