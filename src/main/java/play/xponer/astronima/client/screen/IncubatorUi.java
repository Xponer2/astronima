package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import play.xponer.astronima.block.entity.IncubatorBlockEntity;
import play.xponer.astronima.client.hud.IncubatorLayout;
import play.xponer.astronima.menu.IncubatorUiHolder;
import play.xponer.astronima.registry.ModItems;

/**
 * The chrome around {@link IncubatorBody}: the one feed slot (a dish goes in and the same dish
 * comes back changed, unlike {@code ProcessingUi}'s feed/product split), the player's own
 * inventory, and the thermostat's own drag overlay.
 */
public final class IncubatorUi {

    private IncubatorUi() {}

    public static ModularUI build(IncubatorUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(IncubatorBody.WIDTH).height(IncubatorBody.HEIGHT));

        IncubatorBody body = new IncubatorBody(holder);
        root.addChild(body);

        var dishSlot = new Slot(holder.container(), IncubatorBlockEntity.SLOT_DISH,
                IncubatorLayout.SLOT_X, IncubatorLayout.SLOT_Y) {
            @Override
            public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
                return stack.is(ModItems.PETRI_DISH.get());
            }
        };
        root.addChild(GuiSlots.at(dishSlot, IncubatorLayout.SLOT_X, IncubatorLayout.SLOT_Y));

        addInventorySlots(root, player.getInventory());
        addInventoryLabel(root);
        addDialOverlay(root, body);

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addInventorySlots(UIElement root, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = IncubatorLayout.INVENTORY_Y + row * 18;
                root.addChild(GuiSlots.at(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = IncubatorLayout.HOTBAR_Y;
            root.addChild(GuiSlots.at(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root) {
        var label = new Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(IncubatorLayout.INVENTORY_LABEL_Y);
        root.addChild(label);
    }

    private static void addDialOverlay(UIElement root, IncubatorBody body) {
        UIElement handle = new UIElement();
        handle.getLayout().positionType(TaffyPosition.ABSOLUTE);
        handle.getLayout().left(IncubatorBody.DIAL_X);
        handle.getLayout().top(IncubatorBody.DIAL_Y - 4);
        handle.getLayout().width(IncubatorBody.DIAL_W);
        handle.getLayout().height(14);
        handle.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        handle.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            body.onDialMouseDown(e.x);
            handle.startDrag(null, null);
        });
        handle.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, e -> body.onDialDrag(e.x));
        handle.addEventListener(UIEvents.DRAG_END, e -> body.onDialReleased());
        root.addChild(handle);
    }
}
