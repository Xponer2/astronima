package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.ore.ZoneRefining;

/**
 * Zone refining: a molten zone dragged along a rod of electrolytic silicon, rejecting real
 * metallic contamination into the melt it leaves behind rather than the crystal it forms. See
 * {@code design/halogens.md} §9-10 — no room reagent, no dial, and a whole-batch feed the same
 * shape {@code SlsPrinterBlockEntity.FEED_PER_BATCH} already uses, since the real yield fraction
 * only produces a clean whole-item count off a batch bigger than one.
 */
public class ZoneRefinerBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED = 0;
    public static final int SLOT_OUTPUT = 1;

    /** One rod's worth of electrolytic silicon, per batch. */
    public static final int FEED_PER_BATCH = 10;

    /** Slow, matching real float-zone practice: a molten zone crawling the length of a rod takes
     *  hours per pass in reality. Below the titanium cell's own 700 (this purifies already-
     *  elemental silicon; it does not reduce an oxide), above a plain reagent machine. */
    public static final int BATCH_WORK = 600;

    public ZoneRefinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ZONE_REFINER.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.ZONE_REFINER;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        ItemStack feed = getItem(SLOT_FEED);
        return feed.is(ModItems.SILICON.get()) && feed.getCount() >= FEED_PER_BATCH;
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT,
                new ItemStack(ModItems.WAFER_SILICON.get(),
                        ZoneRefining.wafersPerBatch(FEED_PER_BATCH)));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_FEED);
        if (!feed.is(ModItems.SILICON.get()) || feed.getCount() < FEED_PER_BATCH) {
            return;
        }
        int yield = ZoneRefining.wafersPerBatch(FEED_PER_BATCH);
        if (yield > 0) {
            pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.WAFER_SILICON.get(), yield));
        }
        feed.shrink(FEED_PER_BATCH);
    }
}
