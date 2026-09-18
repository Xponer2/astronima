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
import play.xponer.astronima.sim.chem.Photosynthesis;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * Real photosynthesis, run in a box: {@code 6 CO2 + 6 H2O + light -> C6H12O6 + 6 O2}, real and
 * balanced (see {@link Photosynthesis}, {@code design/hydroponics.md} §1). A real water bottle in,
 * this mod's first real, edible food and real O2 out — spent against whatever CO2 the room
 * actually has.
 *
 * <p><strong>Requires real power to run at all</strong> ({@link #canRunWithoutPower()} = false),
 * the same real reasoning {@link WaterElectrolyzerBlockEntity} already gives its own reaction:
 * nothing about photosynthesis has a manual-labour equivalent, and a real photobioreactor's LED
 * panel is exactly that load. This is <em>why</em> it needs no real sky access at all, unlike a
 * lit greenhouse crop (design/hydroponics.md §0) — the light is electrical, not solar.
 */
public class AlgaeBioreactorBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 260;

    public AlgaeBioreactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALGAE_BIOREACTOR.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.ALGAE_BIOREACTOR;
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
        return WaterElectrolyzerBlockEntity.isWaterBottle(getItem(SLOT_INPUT));
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.ALGAE_BIOMASS.get()));
    }

    /**
     * The one stall this machine adds beyond the shared ones: too little CO2 in the room to cover
     * a whole real batch. Held, not rationed — the same all-or-nothing reagent gate
     * {@link Photosynthesis#run} itself already enforces.
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        return roomCo2Mol() < Photosynthesis.CO2_PER_BOTTLE_MOL ? WorkState.NO_CARBON_DIOXIDE
                : general;
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    private double roomCo2Mol() {
        RoomState room = room();
        return room == null ? 0 : room.gases().get(Gas.CARBON_DIOXIDE);
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
            return; // guarded by workState, but never react a bottle against no room at all
        }
        Photosynthesis.Batch batch = Photosynthesis.run(room.gases().get(Gas.CARBON_DIOXIDE));
        if (!batch.ran()) {
            return; // guarded by workState, but never spend a bottle for nothing
        }
        room.removeGas(Gas.CARBON_DIOXIDE, batch.co2ConsumedMol());
        room.addGasAt(Gas.OXYGEN, batch.o2ProducedMol(), room.temperatureK());
        pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.ALGAE_BIOMASS.get()));
        getItem(SLOT_INPUT).shrink(1);
    }
}
