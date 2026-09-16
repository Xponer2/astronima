package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.storage.ConnectedRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Where real {@code data_cell} items get inserted, and where the player-built structure's own
 * real size is measured. See {@code design/data-cells.md} §9.
 *
 * <p><strong>The container never resizes; access does.</strong> A vanilla {@link Container}'s
 * slot count is fixed at construction, so this always has {@link #MAX_CELL_SLOTS} real slots (a
 * double chest's own worth) — what changes with the structure is how many of them
 * {@link #canPlaceItem} actually accepts a cell into, computed live from {@link #usableSlots()}
 * every time it is asked rather than cached, so a structure grown or broken since the screen was
 * last opened is honest the next time a player reaches for a slot.
 *
 * <p><strong>Implements {@code Container} directly, not {@code BaseContainerBlockEntity}</strong>
 * — the same choice {@link ProcessingBlockEntity} already made, for the same reason: a block
 * entity can only extend one class, and {@link ReadableBlockEntity} is the one that gets a gauge
 * to the client at all (rule 26). {@link #usableSlots()} is exactly that kind of gauge — a vanilla
 * {@code ChestMenu} has no way to show <em>why</em> a slot refuses a cell, so it is published the
 * same way every other machine's invisible state is, via {@code BlockReadings}.
 *
 * <p><strong>No longer a {@code MenuProvider} itself.</strong> The real screen is
 * {@link play.xponer.astronima.menu.StorageDriveUiHolder} now, this mod's own LDLib2 chrome
 * instead of a plain vanilla {@code ChestMenu} — see {@code design/data-cells.md} §14: a
 * hand-rolled vanilla menu never gets this project's own panel/slot styling at all, reported back
 * directly as flat, textureless grey slots.
 */
public class StorageDriveBlockEntity extends ReadableBlockEntity implements Container {
    /** A double chest's own real slot count — a recognisable real number, not an invented one. */
    public static final int MAX_CELL_SLOTS = 54;

    /** The drive's own free slot, present with zero structure connected — chosen (rule 8) so a
     *  bare drive is still a real, working device rather than a brick that needs building
     *  around before it does anything at all. */
    public static final int FREE_SLOTS = 1;

    /** How often the live connected-frame count is rechecked (design/data-cells.md §9's own
     *  "poll, don't chase hooks" reasoning — a frame placed or broken next to the drive fires no
     *  event this block entity is otherwise told about). */
    private static final int RECHECK_INTERVAL_TICKS = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(MAX_CELL_SLOTS, ItemStack.EMPTY);

    public StorageDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_DRIVE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StorageDriveBlockEntity drive) {
        if (level.getGameTime() % RECHECK_INTERVAL_TICKS == 0) {
            drive.setChanged();
        }
    }

    /** How many of this drive's real slots are actually usable right now: the one free slot plus
     *  however much real, connected {@code storage_frame} structure the player has built
     *  ({@code ConnectedRegion}'s own flood fill, Minecraft-free — design doc §9). */
    public int usableSlots() {
        if (level == null) {
            return FREE_SLOTS;
        }
        int connected = ConnectedRegion.size(worldPosition, StorageDriveBlockEntity::neighboursOf,
                pos -> level.getBlockState(pos).is(ModBlocks.STORAGE_FRAME.get()));
        return FREE_SLOTS + connected;
    }

    private static List<BlockPos> neighboursOf(BlockPos pos) {
        List<BlockPos> neighbours = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            neighbours.add(pos.relative(direction));
        }
        return neighbours;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ModItems.DATA_CELL.get()) && slot < usableSlots();
    }

    // -------------------------------------------------------------------- Container

    @Override
    public int getContainerSize() {
        return MAX_CELL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // -------------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
    }
}
