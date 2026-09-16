package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.menu.StorageDriveUiHolder;

/**
 * The chrome around {@link StorageDriveBody}: the drive's own
 * {@value play.xponer.astronima.block.entity.StorageDriveBlockEntity#MAX_CELL_SLOTS} real cell
 * slots, each still routed through {@link Container#canPlaceItem} the way {@code StorageDriveMenu}
 * (the vanilla {@code ChestMenu} subclass this replaces) did — a plain {@link Slot} never consults
 * it (rule 130) — plus the player's own inventory. See {@code design/data-cells.md} §14.
 */
public final class StorageDriveUi {

    private StorageDriveUi() {}

    public static ModularUI build(StorageDriveUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(StorageDriveBody.WIDTH).height(StorageDriveBody.HEIGHT));

        StorageDriveBody body = new StorageDriveBody(holder);
        root.addChild(body);

        addDriveSlots(root, holder.container());
        addInventorySlots(root, player.getInventory());
        addInventoryLabel(root);

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addDriveSlots(UIElement root, Container container) {
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9;
                int x = 8 + column * 18;
                int y = StorageDriveBody.GRID_TOP + row * 18;
                Slot slot = new Slot(container, index, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(getSlotIndex(), stack);
                    }
                };
                root.addChild(GuiSlots.at(slot, x, y));
            }
        }
    }

    private static void addInventorySlots(UIElement root, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = StorageDriveBody.INVENTORY_Y + row * 18;
                root.addChild(GuiSlots.at(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = StorageDriveBody.HOTBAR_Y;
            root.addChild(GuiSlots.at(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root) {
        var label = new Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(StorageDriveBody.INVENTORY_LABEL_Y);
        root.addChild(label);
    }
}
