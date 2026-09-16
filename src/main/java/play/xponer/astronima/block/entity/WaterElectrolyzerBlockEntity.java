package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * Real electrolysis: {@code 2 H2O -> 2 H2 + O2}, straight into the sealed room this sits in.
 *
 * <p>Feed is a water bottle — the exact item {@code DehumidifierBlockEntity} already produces, so
 * the loop's own byproduct is directly the next machine's feed; nothing new was invented to join
 * them. No catalyst item, deliberately: real electrolyzer electrodes are durable equipment, not a
 * reagent, and the mod already has a real consumed-catalyst machine one bench over
 * (see {@link SabatierReactorBlockEntity}) — giving this one an equivalent part "for symmetry"
 * would be decoration, not chemistry (design/chemistry-loop.md §2).
 *
 * <p>Same one-shot, gas-only-product shape {@code CrackingTowerBlockEntity} already established:
 * one feed slot, no solid output, {@code WorkState.BACKPRESSURE} with nowhere to vent.
 *
 * <p><strong>Requires real power to run at all</strong> ({@link #canRunWithoutPower()} = false) —
 * game-design audit #2, finding C. Every other machine in this mod idles along on
 * {@code ProcessingBlockEntity.IDLE_RATE} with zero power, the deliberate "any machine can be
 * hand-cranked" simplification (machines.md §7). Electrolysis is the one reaction that
 * simplification cannot honestly cover (design/oxygen.md §1: every real route to splitting water
 * needs actual electricity, full stop) — and letting it idle for free turned out to be a real
 * bug, not a harmless shortcut: bolted to a {@code FuelCellBlockEntity} and a
 * {@code DehumidifierBlockEntity}, an electrolyzer that never needed power closed a genuine
 * perpetual-motion loop (water in, free electricity out, water back) that {@code FuelCellBlockEntity}'s
 * own doc comment says was deliberately kept out of the fuel cell for exactly this reason.
 */
public class WaterElectrolyzerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED_ONLY = 0;

    public static final int BATCH_WORK = 260;

    /** Hydrogen and oxygen per batch, in a real 2:1 mole ratio off one water bottle. */
    public static final double H2_PER_BATCH_MOL = 2.0;
    public static final double O2_PER_BATCH_MOL = 1.0;

    public WaterElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_ELECTROLYZER.get(), pos, state, 1);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.WATER_ELECTROLYZER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    protected boolean canRunWithoutPower() {
        return false;
    }

    @Override
    public boolean hasFeed() {
        return isWaterBottle(getItem(SLOT_FEED_ONLY));
    }

    /** A water bottle, specifically — not any potion, since this machine is honest chemistry:
     * it splits water, not whatever else happens to be in a glass bottle. */
    public static boolean isWaterBottle(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    @Override
    public boolean hasRoomForProduct() {
        return true;
    }

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
        if (!isWaterBottle(getItem(SLOT_FEED_ONLY))) {
            return;
        }
        RoomState room = receivingRoom();
        if (room == null) {
            return; // guarded by workState, but never electrolyse a bottle into nowhere
        }
        room.addGasAt(Gas.HYDROGEN, H2_PER_BATCH_MOL, room.temperatureK());
        room.addGasAt(Gas.OXYGEN, O2_PER_BATCH_MOL, room.temperatureK());
        getItem(SLOT_FEED_ONLY).shrink(1);
    }
}
