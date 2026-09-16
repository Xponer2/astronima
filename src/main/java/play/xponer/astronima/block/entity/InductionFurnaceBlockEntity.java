package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;

/**
 * Melts iron powder into an ingot without touching the room's air at all — the oxygen-free
 * counterpart to a vanilla furnace, which {@code AbstractFurnaceBlockEntityMixin} already makes
 * draw real oxygen and exhale CO2 while lit. See {@code design/induction-furnace.md}.
 *
 * <p>Same feed and product a vanilla furnace already gives {@code iron_powder}
 * ({@code CraftingTree}'s own `Cooking` recipe) — this is a second, oxygen-free path to an
 * already-reachable item, not a new one, so no new reachability entry is needed for the output.
 */
public class InductionFurnaceBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_FEED = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Short: real melting is a far smaller job than reducing an oxide from nothing
     *  (design/induction-furnace.md §2 — about a tenth of the titanium cell's own real cost). */
    public static final int BATCH_WORK = 150;

    public InductionFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INDUCTION_FURNACE.get(), pos, state, 2);
    }

    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.INDUCTION_FURNACE;
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_FEED).is(ModItems.IRON_POWDER.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_FEED);
        if (!feed.is(ModItems.IRON_POWDER.get())) {
            return;
        }
        // No Atmosphere/RoomState touched anywhere in this class — that omission is the whole
        // machine (design/induction-furnace.md §4): melting real metal without competing with
        // the player's own lungs for the room's air.
        pushOutput(SLOT_OUTPUT, new ItemStack(Items.IRON_INGOT));
        feed.shrink(1);
    }
}
