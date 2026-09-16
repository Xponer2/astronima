package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
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
import play.xponer.astronima.block.entity.StorageDriveBlockEntity;
import play.xponer.astronima.client.screen.StorageDriveUi;
import play.xponer.astronima.registry.ModMenus;

/**
 * The drive's own real menu — the same {@code IContainerUIHolder} pattern every machine in this
 * mod already uses ({@code design/ui-ldlib2-machines.md}), reused here in place of the plain
 * vanilla menu this block shipped with first. See {@code design/data-cells.md} §14 for why: a
 * hand-rolled vanilla {@code AbstractContainerMenu}/{@code AbstractContainerScreen} pair never
 * gets this project's own LDLib2 chrome (the panel, the slot styling) at all, since that chrome is
 * drawn by LDLib2's own UI tree, not by anything a vanilla screen calls into — confirmed the hard
 * way, reported directly as flat grey slots with no background.
 */
public final class StorageDriveUiHolder implements MenuProvider, IContainerUIHolder {

    private final Container container;
    private final BlockPos pos;

    /** Server-side: the real drive backs every slot. */
    public StorageDriveUiHolder(StorageDriveBlockEntity drive, BlockPos pos) {
        this(drive, pos, true);
    }

    /** Client-side: a disconnected container the same size as the real one — vanilla's own
     *  slot-sync fills in every real stack regardless of which object backs the menu locally,
     *  the same split every other machine's own UI holder already uses. */
    public StorageDriveUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(new SimpleContainer(StorageDriveBlockEntity.MAX_CELL_SLOTS), buffer.readBlockPos(), false);
    }

    private StorageDriveUiHolder(Container container, BlockPos pos, boolean ignored) {
        this.container = container;
        this.pos = pos;
    }

    public Container container() {
        return container;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.storage_drive");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ModularUIContainerMenu(ModMenus.STORAGE_DRIVE.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return StorageDriveUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return container.stillValid(player);
    }
}
