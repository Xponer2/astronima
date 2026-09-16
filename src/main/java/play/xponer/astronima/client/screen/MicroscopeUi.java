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
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.MicroscopeBlockEntity;
import play.xponer.astronima.client.hud.MicroscopeLayout;
import play.xponer.astronima.menu.MicroscopeUiHolder;
import play.xponer.astronima.registry.ModItems;

/**
 * The chrome around {@link MicroscopeBody}: the one slide slot, the player's own inventory, the
 * fine-focus knob's drag overlay, and the field's own click — a circular hit region, since a
 * click on the corner of the field's square bounding box was never meant to read; the old
 * {@code onField} used {@code Math.hypot(...) <= radius} and this overlay checks the same
 * distance itself rather than acting on every point in its rectangular hit box.
 */
public final class MicroscopeUi {

    private MicroscopeUi() {}

    public static ModularUI build(MicroscopeUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(MicroscopeBody.WIDTH).height(MicroscopeBody.HEIGHT));

        MicroscopeBody body = new MicroscopeBody(holder);
        root.addChild(body);

        var slideSlot = new Slot(holder.container(), MicroscopeBlockEntity.SLOT_DISH, 8, 92) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.PETRI_DISH.get());
            }
        };
        root.addChild(GuiSlots.at(slideSlot, 8, 92));

        addInventorySlots(root, player.getInventory());
        addInventoryLabel(root);
        addKnobOverlay(root, body);
        addFieldOverlay(root, body);

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addInventorySlots(UIElement root, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = MicroscopeLayout.INVENTORY_Y + row * 18;
                root.addChild(GuiSlots.at(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = MicroscopeLayout.HOTBAR_Y;
            root.addChild(GuiSlots.at(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root) {
        var label = new Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(MicroscopeLayout.INVENTORY_LABEL_Y);
        root.addChild(label);
    }

    private static void addKnobOverlay(UIElement root, MicroscopeBody body) {
        UIElement handle = new UIElement();
        handle.getLayout().positionType(TaffyPosition.ABSOLUTE);
        handle.getLayout().left(MicroscopeBody.KNOB_X - 4);
        handle.getLayout().top(MicroscopeBody.KNOB_Y);
        handle.getLayout().width(16);
        handle.getLayout().height(MicroscopeBody.KNOB_H);
        handle.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        handle.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            body.onKnobMouseDown(e.y);
            handle.startDrag(null, null);
        });
        handle.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, e -> body.onKnobDrag(e.y));
        root.addChild(handle);
    }

    /** A square overlay over the field's own bounding box, but the click only acts inside the
     *  circle actually drawn there — see this class's own doc. */
    private static void addFieldOverlay(UIElement root, MicroscopeBody body) {
        UIElement overlay = new UIElement();
        overlay.getLayout().positionType(TaffyPosition.ABSOLUTE);
        overlay.getLayout().left(MicroscopeBody.FIELD_X);
        overlay.getLayout().top(MicroscopeBody.FIELD_Y);
        overlay.getLayout().width(MicroscopeBody.FIELD);
        overlay.getLayout().height(MicroscopeBody.FIELD);
        overlay.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            double cx = overlay.getContentX() + MicroscopeBody.FIELD / 2.0;
            double cy = overlay.getContentY() + MicroscopeBody.FIELD / 2.0;
            if (Math.hypot(e.x - cx, e.y - cy) <= MicroscopeBody.FIELD / 2.0) {
                body.onFieldClicked();
            }
        });
        root.addChild(overlay);
    }
}
