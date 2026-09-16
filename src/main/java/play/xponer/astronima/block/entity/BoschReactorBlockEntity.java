package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * The real alternative to the Sabatier reactor: {@code CO2 + 2 H2 -> C(s) + 2 H2O}, over an iron
 * catalyst. Same room reagents as {@link SabatierReactorBlockEntity} — a real, named three-way
 * tension over one room's CO2 and H2 (design/chemistry-loop.md §1/§2.5) — but real, different
 * stoichiometry: half the hydrogen per CO2 that Sabatier needs, because the carbon leaves as a
 * solid instead of being built into methane. Cheaper on hydrogen; the carbon has to be dealt
 * with rather than simply burned for power.
 *
 * <p>The one machine in this mod that combines both shapes at once: a solid catalyst feed and a
 * solid product ({@code PolymerizerBlockEntity}'s own shape) plus two room reagents read together
 * ({@code SabatierReactorBlockEntity}'s own shape).
 */
public class BoschReactorBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_CATALYST = 0;
    public static final int SLOT_CARBON = 1;

    public static final int BATCH_WORK = 320;

    public static final int IRON_PER_BATCH = 1;

    /** Real Bosch stoichiometry: 1 CO2 : 2 H2, half the hydrogen Sabatier needs for the same CO2. */
    public static final double CO2_PER_BATCH_MOL = 2.0;
    public static final double H2_PER_BATCH_MOL = 4.0;

    /** Carbon (1:1 with CO2 in) and water vapor (1:2 with CO2 in) a batch yields. */
    public static final double CARBON_PER_BATCH_MOL = 2.0;
    public static final double H2O_PER_BATCH_MOL = 4.0;

    public BoschReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOSCH_REACTOR.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.BOSCH_REACTOR;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_CATALYST).is(ModItems.IRON_POWDER.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_CARBON, new ItemStack(ModItems.CARBON_POWDER.get()));
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
        return hasBothReagents() ? general : WorkState.NO_FEEDSTOCK_GAS;
    }

    private boolean hasBothReagents() {
        RoomState room = room();
        return room != null && room.gases().get(Gas.CARBON_DIOXIDE) >= CO2_PER_BATCH_MOL
                && room.gases().get(Gas.HYDROGEN) >= H2_PER_BATCH_MOL;
    }

    /** How much of a batch's own real CO2 need the room actually has, 0..1 clamped — the panel's
     *  own honest "why is this stuck" reading (design/machines.md's own Update section). */
    public double co2Fraction() {
        RoomState room = room();
        return room == null ? 0.0
                : Math.clamp(room.gases().get(Gas.CARBON_DIOXIDE) / CO2_PER_BATCH_MOL, 0.0, 1.0);
    }

    /** As {@link #co2Fraction()}, for hydrogen. */
    public double h2Fraction() {
        RoomState room = room();
        return room == null ? 0.0
                : Math.clamp(room.gases().get(Gas.HYDROGEN) / H2_PER_BATCH_MOL, 0.0, 1.0);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    @Override
    protected void finishBatch() {
        ItemStack iron = getItem(SLOT_CATALYST);
        if (!iron.is(ModItems.IRON_POWDER.get())) {
            return;
        }
        RoomState room = room();
        if (room == null || !hasBothReagents()) {
            return; // guarded by workState, but never react a batch out of nowhere
        }
        room.removeGas(Gas.CARBON_DIOXIDE, CO2_PER_BATCH_MOL);
        room.removeGas(Gas.HYDROGEN, H2_PER_BATCH_MOL);
        room.addGasAt(Gas.WATER_VAPOR, H2O_PER_BATCH_MOL, room.temperatureK());
        int carbonItems = (int) Math.floor(CARBON_PER_BATCH_MOL);
        if (carbonItems > 0) {
            pushOutput(SLOT_CARBON, new ItemStack(ModItems.CARBON_POWDER.get(), carbonItems));
        }
        iron.shrink(IRON_PER_BATCH);
    }
}
