package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.chem.HeavyWaterCascade;
import play.xponer.astronima.sim.machine.WorkState;

/**
 * One electrolytic enrichment stage: a water bottle in, the same bottle out with its own D2O
 * fraction advanced one step up {@link HeavyWaterCascade}'s curve. See {@code
 * design/heavy-water.md}. Feeding a machine's own output back into its own feed slot *is* the
 * cascade — the same "run it through again" idiom the magnetic separator already uses for a
 * second pass at a purer stream.
 *
 * <p>One bottle per batch, not the real ratio's own five — vanilla potion stacks cap at one item,
 * so a feed slot could never hold five bottles to begin with (design/heavy-water.md §3's own
 * named simplification). The real 5:1 loss is still true and still quoted (the JEI page,
 * {@code /astronima heavywater sweep}); it is just not an item cost this machine enforces.
 */
public class HeavyWaterCellBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED = 0;
    public static final int SLOT_OUTPUT = 1;

    public static final int BATCH_WORK = 400;

    public HeavyWaterCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAVY_WATER_CELL.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.HEAVY_WATER_CELL;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return WaterElectrolyzerBlockEntity.isWaterBottle(getItem(SLOT_FEED));
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, outputStack());
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    /** The D2O fraction of whatever is in the feed slot right now — the panel's own live
     *  reading, absent (natural water) when the slot is empty or not a water bottle at all. */
    public double feedD2OFraction() {
        return fractionOf(getItem(SLOT_FEED));
    }

    private static double fractionOf(ItemStack stack) {
        Float stamped = stack.get(ModDataComponents.HEAVY_WATER_FRACTION.get());
        return stamped != null ? stamped : HeavyWaterCascade.NATURAL_D2O_FRACTION;
    }

    private ItemStack outputStack() {
        double fraction = HeavyWaterCascade.fractionAfterStages(fractionOf(getItem(SLOT_FEED)), 1);
        ItemStack output = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        output.set(ModDataComponents.HEAVY_WATER_FRACTION.get(), (float) fraction);
        return output;
    }

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_FEED);
        if (!WaterElectrolyzerBlockEntity.isWaterBottle(feed)) {
            return;
        }
        pushOutput(SLOT_OUTPUT, outputStack());
        feed.shrink(1);
    }
}
