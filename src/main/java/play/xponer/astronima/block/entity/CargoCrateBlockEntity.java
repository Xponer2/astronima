package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A shipping crate: somewhere to put things, in a world that has no wood.
 *
 * <p>The mod removed wooden recipes and an asteroid has no trees, so a chest and a barrel are
 * both unreachable — which left the player with no storage block whatsoever. This is that
 * block, and it is deliberately unsurprising: {@value #ROWS} rows, the chest screen everyone
 * already knows, nothing to learn.
 *
 * <p>Its one real property is not in this class at all — it is that the block is a full cube,
 * so the room scan reads it as airtight and a wall of crates is a wall. See
 * {@code design/cargo-crate.md} §G2; a chest is fourteen-sixteenths of a block and would be a
 * hole in the hull.
 */
public class CargoCrateBlockEntity extends BaseContainerBlockEntity {
    /** Rows of nine, matching a chest. Storage should not be a puzzle. */
    public static final int ROWS = 3;
    public static final int SLOTS = ROWS * 9;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public CargoCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARGO_CRATE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.astronima.cargo_crate");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        this.items = replacement;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /**
     * Everything held, on the floor.
     *
     * <p>The standing rule in this mod is that nothing ever eats a player's items — every
     * machine drops its contents when broken — and storage is where breaking that rule would
     * hurt most.
     */
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }
}
