package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.chem.AnaerobicDigestion;

/**
 * Real anaerobic digestion, run in a sealed box: real crop waste in, real biogas (into the room)
 * and real fertilizer out (see {@link AnaerobicDigestion}, {@code design/anaerobic-digestion.md}).
 *
 * <p><strong>Runs without power</strong> ({@link #canRunWithoutPower()} = true) — the real,
 * deliberate opposite of {@link AlgaeBioreactorBlockEntity}'s own choice: real digestion is
 * bacterial metabolism running on the waste's own chemical potential, not a reaction with a real
 * continuous external energy requirement the way photosynthesis or electrolysis has.
 *
 * <p>Reads no room gas as an input — unlike the algae bioreactor, which needs the room's own CO2
 * to run, this machine's own sealed vessel is where the real anaerobic chemistry happens; the room
 * around it is only ever written to, never read as a condition to stall on.
 */
public class AnaerobicDigesterBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 200;

    public AnaerobicDigesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANAEROBIC_DIGESTER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.ANAEROBIC_DIGESTER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    protected boolean canRunWithoutPower() {
        return true;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.CROP_WASTE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.FERTILIZER.get()));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    @Override
    protected void finishBatch() {
        if (!hasFeed()) {
            return;
        }
        RoomState room = room();
        if (room == null) {
            return; // guarded by workState, but never digest a batch against no room at all
        }
        AnaerobicDigestion.Batch batch = AnaerobicDigestion.run();
        room.addGasAt(Gas.METHANE, batch.methaneMol(), room.temperatureK());
        room.addGasAt(Gas.CARBON_DIOXIDE, batch.co2Mol(), room.temperatureK());
        pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.FERTILIZER.get()));
        getItem(SLOT_INPUT).shrink(1);
    }
}
