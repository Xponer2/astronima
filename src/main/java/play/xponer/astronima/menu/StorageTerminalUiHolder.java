package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;
import play.xponer.astronima.client.screen.StorageTerminalUi;
import play.xponer.astronima.registry.ModMenus;

/**
 * The terminal's own real menu — see {@code design/data-cells.md} §14; same LDLib2 pattern as
 * {@link StorageDriveUiHolder}, for the same reason.
 *
 * <p>Also carries a two-int {@link ContainerData} (scroll row, total rows — §18) for the
 * scrollbar, actually wired to sync via {@link StorageTerminalMenu}'s own constructor
 * (`addDataSlots` is `protected` on `AbstractContainerMenu`, unreachable from this unrelated
 * class — {@link #data()} exists so that subclass can reach it instead). Unlike every other
 * {@code ContainerData} in this codebase (Incubator's setpoint, Airlock's status ints,
 * Processing's progress, Synthesiser's selection) — none of which ever call
 * {@code addDataSlots} at all, meaning none of them are actually pushed to the client (PLAN.md
 * rule 135, flagged as a follow-up rather than fixed here, since this pass is scoped to the
 * terminal's own scrolling).
 */
public final class StorageTerminalUiHolder implements MenuProvider, IContainerUIHolder {

    private static final int DATA_SIZE = 2;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** Server-side: the real terminal backs every slot. */
    public StorageTerminalUiHolder(StorageTerminalBlockEntity terminal, BlockPos pos) {
        this.container = terminal;
        this.data = dataFor(terminal);
        this.pos = pos;
    }

    /** Client-side: a disconnected container, vanilla's own slot-sync fills it in. */
    public StorageTerminalUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this.container = unlimitedContainer(StorageTerminalBlockEntity.CONTAINER_SIZE);
        this.data = new SimpleContainerData(DATA_SIZE);
        this.pos = buffer.readBlockPos();
    }

    /**
     * The actual root cause behind PLAN.md rules 137/138's own long investigation, found only
     * after both of those fixes shipped and the freeze didn't move: a bare {@code SimpleContainer}
     * is a real vanilla inventory-shaped container, and vanilla's own {@code SimpleContainer#setItem}
     * calls {@code itemStack.limitSize(this.getMaxStackSize(itemStack))} — silently, permanently
     * truncating whatever stack a sync packet hands it down to that real item's own real max stack
     * size (64 for a normal block, 16 for a snowball — the exact, per-item fingerprint reported).
     * `Slot#set` routes every incoming sync update through exactly this method
     * (`container.setItem(...)`), so the client's own disconnected placeholder container was
     * quietly truncating every display row's real total the instant vanilla's own slot-sync wrote
     * it in — nothing downstream (rendering included) was ever wrong; the client's own copy of the
     * data genuinely held the truncated number. A plain {@code SimpleContainer} is exactly the
     * wrong tool for a container whose whole point is holding *aggregated* totals larger than any
     * one real item's own stack limit — this override removes the one behaviour that assumption
     * breaks, nothing else about {@code SimpleContainer} needed to change.
     */
    private static Container unlimitedContainer(int size) {
        return new SimpleContainer(size) {
            @Override
            public int getMaxStackSize() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getMaxStackSize(ItemStack itemStack) {
                return Integer.MAX_VALUE;
            }
        };
    }

    /** The two numbers the scrollbar needs, read straight off the real terminal. */
    private static ContainerData dataFor(StorageTerminalBlockEntity terminal) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> terminal.scrollRow();
                    case 1 -> terminal.totalRows();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    terminal.setScrollRow(value);
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public Container container() {
        return container;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Which page of rows is currently shown — the client's own synced copy. */
    public int scrollRow() {
        return data.get(0);
    }

    /** How many rows the list currently has in total — the client's own synced copy. */
    public int totalRows() {
        return data.get(1);
    }

    /** Package-visible for {@link StorageTerminalMenu}'s own constructor, the one place that
     *  actually registers this for sync. */
    ContainerData data() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.storage_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StorageTerminalMenu(ModMenus.STORAGE_TERMINAL.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return StorageTerminalUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return container.stillValid(player);
    }
}
