package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.ore.TitaniumReduction;

/**
 * FFC-Cambridge titanium reduction: {@code TiO2 -> Ti + O2}, one charge of titania in, real
 * stoichiometric titanium out. See {@code design/titanium-reduction.md} — no room reagent, no
 * dial, and no separate sponge-to-ingot consolidation step (the same simplification the carbonyl
 * refiner already makes for the Mond process).
 */
public class TitaniumCellBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Long: the hardest, latest-tier reduction in the mod (design/titanium-reduction.md §3) —
     *  the real ~30 kWh/kg industrial cost, expressed as pacing rather than a derived voltage. */
    public static final int BATCH_WORK = 700;

    public TitaniumCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TITANIUM_CELL.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.TITANIUM_CELL;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_FEED).is(ModItems.TITANIA.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT,
                new ItemStack(ModItems.TITANIUM.get(), TitaniumReduction.titaniumItemsPerCharge()));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_FEED);
        if (!feed.is(ModItems.TITANIA.get())) {
            return;
        }
        int yield = TitaniumReduction.titaniumItemsPerCharge();
        if (yield > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.TITANIUM.get(), yield));
        }
        feed.shrink(1);
    }
}
