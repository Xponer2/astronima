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
 * Addition polymerization: strings ethylene into polyethylene over a titania catalyst.
 *
 * <p><strong>Why titania.</strong> Real industrial polyethylene is made with Ziegler-Natta
 * catalysts — titanium compounds — and {@code TITANIA} (TiO₂) is already reachable off the
 * ilmenite chain with no consuming recipe of its own. This is not a coincidence dressed up after
 * the fact; see {@code design/petrochemicals.md} §2.
 *
 * <p>The ethylene reagent is read straight off this block's own room, the same way
 * {@code FluidizedBedBlockEntity} already reads its hydrogen — except consumed once per finished
 * batch rather than continuously per tick, because polyethylene's yield has no spin regime to
 * track; batching it is the honest shape for what is actually a simple reaction.
 */
public class PolymerizerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 300;

    /** Titania consumed per batch — a small, named simplification: a real catalyst is not
     * stoichiometrically spent (see the design doc's §2 and §6). */
    public static final int TITANIA_PER_BATCH = 1;

    /** Ethylene a batch needs, moles — enough that one cracking-tower charge feeds several. */
    public static final double ETHYLENE_PER_BATCH_MOL = 3.0;

    public PolymerizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POLYMERIZER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.POLYMERIZER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.TITANIA.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.POLYETHYLENE.get()));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    /**
     * Starvation and a full output slot take priority — those are the operator's own doing and
     * fixed the same way on every machine. Only once the machine would otherwise run does the
     * reagent matter, the same ordering {@code FluidizedBedControl.workStateFor} already uses.
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        return roomEthylene() >= ETHYLENE_PER_BATCH_MOL ? general : WorkState.NO_ETHYLENE;
    }

    private double roomEthylene() {
        RoomState room = room();
        return room == null ? 0 : room.gases().get(Gas.ETHYLENE);
    }

    /** How much of a batch's own real ethylene need the room actually has, 0..1 clamped — the
     *  panel's own honest "why is this stuck" reading (design/machines.md's own Update section). */
    public double ethyleneFraction() {
        return Math.clamp(roomEthylene() / ETHYLENE_PER_BATCH_MOL, 0.0, 1.0);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    @Override
    protected void finishBatch() {
        ItemStack titania = getItem(SLOT_INPUT);
        if (!titania.is(ModItems.TITANIA.get())) {
            return;
        }
        RoomState room = room();
        if (room == null || room.gases().get(Gas.ETHYLENE) < ETHYLENE_PER_BATCH_MOL) {
            return; // guarded by workState, but never string a batch out of nowhere
        }
        room.removeGas(Gas.ETHYLENE, ETHYLENE_PER_BATCH_MOL);
        pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.POLYETHYLENE.get()));
        titania.shrink(TITANIA_PER_BATCH);
    }
}
