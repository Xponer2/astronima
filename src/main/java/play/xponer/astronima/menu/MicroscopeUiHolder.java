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
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.MicroscopeBlockEntity;
import play.xponer.astronima.client.screen.MicroscopeUi;
import play.xponer.astronima.registry.ModMenus;

/**
 * The microscope's real menu — {@code design/ui-ldlib2-machines.md}'s pattern, no
 * {@code ContainerData} at all: the fine-focus knob is purely client-side UI state (the server
 * never tracks it — it only ever receives a one-shot {@code FocusPayload} when the player
 * commits a reading), so there is nothing here for a synced view to carry beyond the one slide
 * slot vanilla's own item sync already handles.
 */
public final class MicroscopeUiHolder implements MenuProvider, IContainerUIHolder {

    private final Container stage;
    private final BlockPos pos;

    /** Server-side: a real machine backs the slot. */
    public MicroscopeUiHolder(MicroscopeBlockEntity machine, BlockPos pos) {
        this(machine.container(), pos);
    }

    /** Client-side: read out of the open-screen packet, filled in by vanilla's own sync. */
    public MicroscopeUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(new SimpleContainer(1), buffer.readBlockPos());
    }

    private MicroscopeUiHolder(Container stage, BlockPos pos) {
        this.stage = stage;
        this.pos = pos;
    }

    public BlockPos pos() {
        return pos;
    }

    public Container container() {
        return stage;
    }

    public ItemStack dish() {
        return stage.getItem(MicroscopeBlockEntity.SLOT_DISH);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.microscope");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ModularUIContainerMenu(ModMenus.MICROSCOPE.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return MicroscopeUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
