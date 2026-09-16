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
import play.xponer.astronima.sim.machine.WorkState;

/**
 * The sulfur chain's second half: the real Contact Process folded into one net equation,
 * {@code 2 SO2 + O2 + 2 H2O -> 2 H2SO4}, the same way {@link TroiliteRoasterBlockEntity}'s own
 * doc comment already folds oxidation and decomposition into one honest line
 * (design/chemistry-loop.md §2.7).
 *
 * <p>The first machine in this mod to read three room reagents at once — the exact SO2 the
 * roaster vents, the oxygen it also spends, and the water vapor {@link SabatierReactorBlockEntity}
 * and {@link BoschReactorBlockEntity} already vent as their own byproduct. Build all of these in
 * one sealed room and every reagent this machine needs is already being produced there.
 *
 * <p>Catalyst is {@code hematite_ore} — the roaster's own product, one bench over — named
 * honestly in the design doc as a real but non-industrial-standard stand-in for the platinum or
 * vanadium(V) oxide a real Contact Process actually runs on, neither of which this mod has a
 * mineral for yet.
 */
public class SulfuricAcidPlantBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_CATALYST = 0;
    public static final int SLOT_ACID = 1;

    public static final int BATCH_WORK = 340;

    public static final int HEMATITE_PER_BATCH = 1;

    /** Real net stoichiometry: 2 SO2 + O2 + 2 H2O -> 2 H2SO4. */
    public static final double SO2_PER_BATCH_MOL = 2.0;
    public static final double O2_PER_BATCH_MOL = 1.0;
    public static final double H2O_PER_BATCH_MOL = 2.0;
    public static final double ACID_PER_BATCH_MOL = 2.0;

    public SulfuricAcidPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SULFURIC_ACID_PLANT.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.SULFURIC_ACID_PLANT;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_CATALYST).is(ModBlocks.HEMATITE_ORE.get().asItem());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_ACID, new ItemStack(ModItems.SULFURIC_ACID.get()));
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
        return hasAllReagents() ? general : WorkState.NO_ACID_FEEDSTOCK;
    }

    private boolean hasAllReagents() {
        RoomState room = room();
        return room != null
                && room.gases().get(Gas.SULFUR_DIOXIDE) >= SO2_PER_BATCH_MOL
                && room.gases().get(Gas.OXYGEN) >= O2_PER_BATCH_MOL
                && room.gases().get(Gas.WATER_VAPOR) >= H2O_PER_BATCH_MOL;
    }

    /** How much of a batch's own real SO2 need the room actually has, 0..1 clamped — the panel's
     *  own honest "which of the three reagents is missing" reading, since this is the only
     *  machine in the mod reading three room gases at once (design/machines.md's own Update
     *  section). */
    public double so2Fraction() {
        RoomState room = room();
        return room == null ? 0.0
                : Math.clamp(room.gases().get(Gas.SULFUR_DIOXIDE) / SO2_PER_BATCH_MOL, 0.0, 1.0);
    }

    /** As {@link #so2Fraction()}, for oxygen. */
    public double oxygenFraction() {
        RoomState room = room();
        return room == null ? 0.0
                : Math.clamp(room.gases().get(Gas.OXYGEN) / O2_PER_BATCH_MOL, 0.0, 1.0);
    }

    /** As {@link #so2Fraction()}, for water vapor. */
    public double waterVaporFraction() {
        RoomState room = room();
        return room == null ? 0.0
                : Math.clamp(room.gases().get(Gas.WATER_VAPOR) / H2O_PER_BATCH_MOL, 0.0, 1.0);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    @Override
    protected void finishBatch() {
        ItemStack hematite = getItem(SLOT_CATALYST);
        if (!hematite.is(ModBlocks.HEMATITE_ORE.get().asItem())) {
            return;
        }
        RoomState room = room();
        if (room == null || !hasAllReagents()) {
            return; // guarded by workState, but never react a batch out of nowhere
        }
        room.removeGas(Gas.SULFUR_DIOXIDE, SO2_PER_BATCH_MOL);
        room.removeGas(Gas.OXYGEN, O2_PER_BATCH_MOL);
        room.removeGas(Gas.WATER_VAPOR, H2O_PER_BATCH_MOL);
        int acidItems = (int) Math.floor(ACID_PER_BATCH_MOL);
        if (acidItems > 0) {
            pushOutput(SLOT_ACID, new ItemStack(ModItems.SULFURIC_ACID.get(), acidItems));
        }
        hematite.shrink(HEMATITE_PER_BATCH);
    }
}
