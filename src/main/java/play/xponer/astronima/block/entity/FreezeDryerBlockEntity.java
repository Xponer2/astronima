package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.cryo.Cryogen;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * Silica aerogel by freeze-drying, not supercritical extraction (design/cryogenics.md §5): a
 * rehydrated {@code baked_silicate} gel is frozen against an adjacent dewar's LN2, then held in
 * real exterior vacuum while its pore water sublimes directly from solid to vapor — no liquid
 * phase, no surface tension, no pore collapse. A real, published, industrially-used alternative
 * to the classic Kistler process, chosen because this mod already has both ingredients it needs
 * (LN2, and free vacuum the instant a block sits outside a sealed room) and neither ingredient
 * the supercritical route would (a supercritical CO2 vessel this mod does not build).
 *
 * <p>Unpowered — freeze-drying is passive once cold and evacuated, the same way the winnowing
 * table's drying step needs nothing but the right place to stand.
 */
public class FreezeDryerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 800;

    public FreezeDryerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FREEZE_DRYER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.FREEZE_DRYER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_FEED).is(ModItems.BAKED_SILICATE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.SILICA_AEROGEL.get()));
    }

    @Override
    public boolean canRun() {
        return hasFeed() && hasRoomForProduct() && readyToDry();
    }

    @Override
    public WorkState workState() {
        WorkState base = WorkState.of(hasFeed(), hasRoomForProduct(), false);
        return base.isWorking() && !readyToDry() ? WorkState.NOT_READY_TO_DRY : base;
    }

    /** True with an adjacent dewar holding LN2 and this block standing in real exterior vacuum —
     * the same {@code reading == null || (openToSpace && outsideIsVacuum)} check every vacuum
     * gate in this codebase already uses. */
    private boolean readyToDry() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!adjacentLn2Available()) {
            return false;
        }
        Atmosphere atmosphere = Atmosphere.get(serverLevel);
        Atmosphere.RoomReading reading = atmosphere.readingAt(worldPosition);
        return reading == null || (reading.openToSpace() && atmosphere.outsideIsVacuum());
    }

    private boolean adjacentLn2Available() {
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof CryoTankBlockEntity tank
                    && tank.cryogen() == Cryogen.LN2 && tank.liquidMoles() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void finishBatch() {
        if (!hasFeed()) {
            return;
        }
        pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.SILICA_AEROGEL.get()));
        getItem(SLOT_FEED).shrink(1);
    }
}
