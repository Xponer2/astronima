package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.ore.OreGrade;
import play.xponer.astronima.sim.ore.TroiliteRoasting;

/**
 * Roasts the troilite out of a batch of crushed ore: {@code 4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2},
 * real and balanced (see {@link TroiliteRoasting}). The iron comes out as hematite — the same
 * oxide {@code hematite_ore} already smelts — and the sulfur leaves as SO2, real and dangerous,
 * into the room this sits in. Oxygen is genuinely spent, drawn from that same room, the trade
 * this machine is actually about.
 *
 * <p>Reads the whole batch's own {@code OreGrade} the same way {@code ElectrolysisCellBlockEntity}
 * already reads a whole assemblage from one item — no separate "troilite concentrate" item was
 * invented for this.
 */
public class TroiliteRoasterBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 300;

    /** One full crushed-ore item's worth, the same convention {@code ElectrolysisCellBlockEntity}
     * and {@code SolarRetortBlockEntity} already use for "one charge". */
    public static final double CHARGE_GRAMS = 4000.0;

    public TroiliteRoasterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROILITE_ROASTER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.TROILITE_ROASTER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.CRUSHED_ORE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModBlocks.HEMATITE_ORE.get()));
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
        return hasEnoughOxygen() ? general : WorkState.NO_OXYGEN;
    }

    private boolean hasEnoughOxygen() {
        RoomState room = room();
        if (room == null) {
            return false;
        }
        TroiliteRoasting.Step step = TroiliteRoasting.roast(chargeBody());
        return room.gases().get(Gas.OXYGEN) >= step.o2ConsumedMol();
    }

    /** How much of the currently-loaded ore's own real O2 need the room actually has, 0..1
     *  clamped — genuinely dynamic, unlike every sibling reagent-stock reading, since the fed
     *  ore's own grade decides how much oxygen one batch actually needs (design/machines.md's
     *  own Update section). Reads whatever ore is currently in the feed slot regardless of
     *  {@link #hasFeed()}, so the reading stays honest even while the machine is idle. */
    public double oxygenFraction() {
        RoomState room = room();
        if (room == null || !getItem(SLOT_INPUT).is(ModItems.CRUSHED_ORE.get())) {
            return 0.0;
        }
        double needed = TroiliteRoasting.roast(chargeBody()).o2ConsumedMol();
        return needed <= 0 ? 1.0 : Math.clamp(room.gases().get(Gas.OXYGEN) / needed, 0.0, 1.0);
    }

    private OreBody chargeBody() {
        ItemStack feed = getItem(SLOT_INPUT);
        Integer packed = feed.get(ModDataComponents.ORE_BATCH.get());
        OreGrade grade = packed == null ? OreGrade.CHONDRITE : OreGrade.gradeOf(packed);
        return grade.body(CHARGE_GRAMS);
    }

    private RoomState room() {
        return level instanceof ServerLevel serverLevel
                ? Atmosphere.get(serverLevel).roomTouching(worldPosition) : null;
    }

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (!feed.is(ModItems.CRUSHED_ORE.get())) {
            return;
        }
        RoomState room = room();
        TroiliteRoasting.Step step = TroiliteRoasting.roast(chargeBody());
        if (room == null || room.gases().get(Gas.OXYGEN) < step.o2ConsumedMol()) {
            return; // guarded by workState, but never roast a charge out of nowhere
        }
        room.removeGas(Gas.OXYGEN, step.o2ConsumedMol());
        if (step.so2Mol() > 0) {
            room.addGasAt(Gas.SULFUR_DIOXIDE, step.so2Mol(), room.temperatureK());
        }
        int hematiteItems = (int) Math.floor(step.fe2O3Mol());
        if (hematiteItems > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(ModBlocks.HEMATITE_ORE.get(), hematiteItems));
        }
        feed.shrink(1);
    }
}
