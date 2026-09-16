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
import play.xponer.astronima.sim.machine.WorkState;

/**
 * The real ISS technology: {@code CO2 + 4 H2 -> CH4 + 2 H2O}, over a nickel catalyst, drawing
 * both reagents off the room this sits in and venting both products into it.
 *
 * <p>The exhaled carbon dioxide this reactor wants is the same molecule
 * {@code ScrubberBlockEntity} already removes and discards — a room running both is a real,
 * named tension (design/chemistry-loop.md §1), not a bug: real ISS mission planning balances the
 * CDRA against the Sabatier system the same way.
 *
 * <p>Nickel is spent as a token catalyst per batch, the same named simplification the
 * polymerizer's titania already carries ({@code design/petrochemicals.md} §2/§6) — a real
 * heterogeneous catalyst is not stoichiometrically consumed, and "installed once, forever" would
 * leave {@code PURE_NICKEL} with one fewer real use than it has today.
 */
public class SabatierReactorBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_CATALYST = 0;

    public static final int BATCH_WORK = 320;

    public static final int NICKEL_PER_BATCH = 1;

    /** CO2 and H2 a batch needs, moles — real Sabatier stoichiometry is 1:4. */
    public static final double CO2_PER_BATCH_MOL = 2.0;
    public static final double H2_PER_BATCH_MOL = 8.0;

    /** CH4 and H2O a batch yields, moles — the same 1:2 the stoichiometry above implies. */
    public static final double CH4_PER_BATCH_MOL = 2.0;
    public static final double H2O_PER_BATCH_MOL = 4.0;

    public SabatierReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SABATIER_REACTOR.get(), pos, state, 1);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.SABATIER_REACTOR;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_CATALYST).is(ModItems.PURE_NICKEL.get());
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
        ItemStack nickel = getItem(SLOT_CATALYST);
        if (!nickel.is(ModItems.PURE_NICKEL.get())) {
            return;
        }
        RoomState room = room();
        if (room == null || !hasBothReagents()) {
            return; // guarded by workState, but never react a batch out of nowhere
        }
        room.removeGas(Gas.CARBON_DIOXIDE, CO2_PER_BATCH_MOL);
        room.removeGas(Gas.HYDROGEN, H2_PER_BATCH_MOL);
        room.addGasAt(Gas.METHANE, CH4_PER_BATCH_MOL, room.temperatureK());
        room.addGasAt(Gas.WATER_VAPOR, H2O_PER_BATCH_MOL, room.temperatureK());
        nickel.shrink(NICKEL_PER_BATCH);
    }
}
