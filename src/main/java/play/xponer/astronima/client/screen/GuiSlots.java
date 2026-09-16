package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.inventory.Slot;

/**
 * One real {@link ItemSlot}, positioned to sit exactly over a well some element underneath it
 * already painted (with {@code MachineFrame.slot}/{@code feedSlot}/{@code productSlot}), so it
 * contributes only the item render and the click/drag/tooltip machinery rather than a second,
 * competing well drawn on top of the first. Shared by every LDLib2 screen this project's
 * machine-io panels have — see {@code design/ui-ldlib2-machines.md} §2.
 */
final class GuiSlots {

    private GuiSlots() {}

    /** @param wellX the well's own top-left, matching what {@code MachineFrame.slot} etc. drew
     *               it at — the {@code ItemSlot} is offset one pixel up/left from this, the same
     *               {@code slotX - 1, slotY - 1} anchor {@code MachineFrame.well} always used. */
    static ItemSlot at(Slot slot, int wellX, int wellY) {
        return at(new ItemSlot(slot), wellX, wellY);
    }

    /** Same positioning/styling as {@link #at(Slot, int, int)}, for a caller that needs its own
     *  {@link ItemSlot} subclass (e.g. one overriding {@code drawItemStack} - see
     *  {@code StorageTerminalUi}'s compact-count display rows, design/data-cells.md §26) instead
     *  of a plain one built from a bare {@link Slot}. */
    static ItemSlot at(ItemSlot itemSlot, int wellX, int wellY) {
        itemSlot.getLayout().positionType(TaffyPosition.ABSOLUTE);
        itemSlot.getLayout().left(wellX - 1);
        itemSlot.getLayout().top(wellY - 1);
        itemSlot.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        itemSlot.slotStyle(ss -> ss.slotOverlay(IGuiTexture.EMPTY));
        return itemSlot;
    }
}
