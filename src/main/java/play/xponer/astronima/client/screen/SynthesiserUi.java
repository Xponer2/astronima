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
import play.xponer.astronima.block.entity.SynthesiserBlockEntity;
import play.xponer.astronima.client.hud.SynthesiserLayout;
import play.xponer.astronima.menu.SynthesiserUiHolder;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.lab.Antibiotic;

/**
 * The chrome around {@link SynthesiserBody}: the one vial slot, the player's own inventory, and
 * one click overlay per antibiotic row.
 */
public final class SynthesiserUi {

    private SynthesiserUi() {}

    public static ModularUI build(SynthesiserUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(SynthesiserBody.WIDTH).height(SynthesiserBody.HEIGHT));

        SynthesiserBody body = new SynthesiserBody(holder);
        root.addChild(body);

        var vialSlot = new Slot(holder.container(), SynthesiserBlockEntity.SLOT_VIAL, 80, 32) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.DOSE.get());
            }
        };
        root.addChild(GuiSlots.at(vialSlot, 80, 32));

        addInventorySlots(root, player.getInventory());
        addInventoryLabel(root);
        addRowOverlays(root, body);

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addInventorySlots(UIElement root, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = SynthesiserLayout.INVENTORY_Y + row * 18;
                root.addChild(GuiSlots.at(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = SynthesiserLayout.HOTBAR_Y;
            root.addChild(GuiSlots.at(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root) {
        var label = new Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(SynthesiserLayout.INVENTORY_LABEL_Y);
        root.addChild(label);
    }

    private static void addRowOverlays(UIElement root, SynthesiserBody body) {
        var drugs = Antibiotic.all();
        for (int i = 0; i < drugs.size(); i++) {
            int index = i;
            int y = SynthesiserBody.ROW_Y + i * (SynthesiserBody.ROW_H + 2);
            UIElement overlay = new UIElement();
            overlay.getLayout().positionType(TaffyPosition.ABSOLUTE);
            overlay.getLayout().left(SynthesiserBody.ROW_X);
            overlay.getLayout().top(y);
            overlay.getLayout().width(SynthesiserBody.ROW_W);
            overlay.getLayout().height(SynthesiserBody.ROW_H);
            overlay.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
            overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> body.onRowClicked(index));
            root.addChild(overlay);
        }
    }
}
