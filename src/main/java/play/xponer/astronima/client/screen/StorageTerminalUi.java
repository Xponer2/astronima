package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;
import play.xponer.astronima.menu.StorageTerminalUiHolder;
import play.xponer.astronima.sim.storage.TerminalView;

/**
 * The chrome around {@link StorageTerminalBody}: just
 * {@value StorageTerminalBlockEntity#MAX_VISIBLE_ROWS} display rows now — no separate input slot
 * (§27, {@code design/data-cells.md}). Every display row is a plain, ordinary {@link Slot} — real
 * vanilla pickup (left click takes the whole shown total, right click takes half — vanilla's own
 * {@code AbstractContainerMenu#doClick}) and real vanilla placement (drag an item onto a row, or
 * shift-click it from the inventory, to deposit it) both reach
 * {@link StorageTerminalBlockEntity#removeItem}/{@code #setItem} directly, the same real, safe
 * path (`Slot#remove`/`Slot#set`) every slot in this menu now uses. Shift-click/quick-move is the
 * one path still unsafe for a computed row (it mutates whatever {@code Slot#getItem()} returns
 * directly rather than going through either of those) — {@code menu/StorageTerminalMenu
 * #quickMoveStack} intercepts display-row indices explicitly so that path never reaches the
 * generic, unsafe implementation for them.
 */
public final class StorageTerminalUi {

    private StorageTerminalUi() {}

    public static ModularUI build(StorageTerminalUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(StorageTerminalBody.WIDTH).height(StorageTerminalBody.HEIGHT));

        StorageTerminalBody body = new StorageTerminalBody(holder);
        root.addChild(body);

        addSortButtons(root, body);
        addDisplaySlots(root, holder.container());
        addScrollbar(root, body);
        addInventorySlots(root, player.getInventory());
        addInventoryLabel(root);

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addSortButtons(UIElement root, StorageTerminalBody body) {
        addSortButton(root, 0, TerminalView.Sort.NAME, body);
        addSortButton(root, 1, TerminalView.Sort.COUNT, body);
        addSortButton(root, 2, TerminalView.Sort.TYPE, body);
    }

    private static void addSortButton(UIElement root, int index, TerminalView.Sort sort,
                                      StorageTerminalBody body) {
        UIElement overlay = new UIElement();
        overlay.getLayout().positionType(TaffyPosition.ABSOLUTE);
        overlay.getLayout().left(StorageTerminalBody.BUTTON_X[index]);
        overlay.getLayout().top(StorageTerminalBody.BUTTON_Y);
        overlay.getLayout().width(StorageTerminalBody.BUTTON_W);
        overlay.getLayout().height(StorageTerminalBody.BUTTON_H);
        overlay.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> body.onSortClicked(sort));
        root.addChild(overlay);
    }

    /** A plain {@link Slot} per row — no overrides at all beyond {@link CompactCountItemSlot}'s
     *  own display text. Placement/pickup both default to {@code true}, and that is exactly
     *  right now: real vanilla single-click pickup and real vanilla drop-to-deposit both route
     *  through the block entity's own real, safe {@code removeItem}/{@code setItem} (this
     *  class's own doc comment); vanilla's own item-type match check inside
     *  {@code Slot#safeInsert} is what stops a drop onto a row showing a *different* item from
     *  doing anything unexpected. */
    private static void addDisplaySlots(UIElement root, Container container) {
        for (int row = 0; row < StorageTerminalBlockEntity.VISIBLE_ROWS; row++) {
            for (int column = 0; column < StorageTerminalBlockEntity.COLUMNS; column++) {
                int index = StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT
                        + row * StorageTerminalBlockEntity.COLUMNS + column;
                int x = 8 + column * 18;
                int y = StorageTerminalBody.GRID_TOP + row * 18;
                Slot slot = new Slot(container, index, x, y);
                root.addChild(GuiSlots.at(new CompactCountItemSlot(slot), x, y));
            }
        }
    }

    /** A row's real aggregate can exceed any real stack's own max — draw that number
     *  abbreviated ({@link CompactCount#format}, e.g. "1k"/"12k"/"1.5M") past three digits,
     *  through the exact same drawing vanilla itself already does. {@code GuiGraphicsExtractor
     *  #itemDecorations(Font, ItemStack, int, int, String)}'s own {@code countText} parameter,
     *  when non-null, replaces {@code String.valueOf(itemStack.getCount())} — nothing else about
     *  positioning, drop shadow, the item bar, cooldown overlay, or NeoForge's own item-decorator
     *  hook changes. See design/data-cells.md §26 — the earlier, reverted attempt at this
     *  (§22/§23) painted a whole extra opaque patch over the vanilla badge from a second
     *  {@code UIElement} sitting on top of the real slot, which intercepted hover and broke
     *  highlighting; this one instead reaches into the same real call the slot's own renderer
     *  already makes and swaps only the string it is handed. */
    private static final class CompactCountItemSlot extends ItemSlot {
        private CompactCountItemSlot(Slot slot) {
            super(slot);
        }

        @Override
        protected void drawItemStack(IGUIContext context, ItemStack itemStack) {
            if (context instanceof GUIContext guiContext) {
                guiContext.graphics.item(itemStack, 0, 0);
                String countText = itemStack.getCount() >= 1000
                        ? CompactCount.format(itemStack.getCount())
                        : null;
                guiContext.graphics.itemDecorations(guiContext.mc.font, itemStack, 0, 0, countText);
            }
        }
    }

    /** The scrollbar's own hit region: click or drag anywhere in the track jumps straight to
     *  that row, the same "click the bar, land there" feel as the dial/knob overlays every other
     *  screen in this mod already uses for a continuous drag (see {@code IncubatorUi}'s own dial
     *  handle). Sized to the whole track rather than the thumb's own (data-dependent) bounds, so
     *  the hit region never has to be repositioned as {@code totalRows} changes. */
    private static void addScrollbar(UIElement root, StorageTerminalBody body) {
        UIElement track = new UIElement();
        track.getLayout().positionType(TaffyPosition.ABSOLUTE);
        track.getLayout().left(StorageTerminalBody.TRACK_X);
        track.getLayout().top(StorageTerminalBody.TRACK_Y);
        track.getLayout().width(StorageTerminalBody.TRACK_W);
        track.getLayout().height(StorageTerminalBody.TRACK_H);
        track.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        track.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            body.onTrackInteracted(e.y);
            track.startDrag(null, null);
        });
        track.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, e -> body.onTrackInteracted(e.y));
        root.addChild(track);
    }

    private static void addInventorySlots(UIElement root, Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = StorageTerminalBody.INVENTORY_Y + row * 18;
                root.addChild(GuiSlots.at(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = StorageTerminalBody.HOTBAR_Y;
            root.addChild(GuiSlots.at(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root) {
        var label = new Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(StorageTerminalBody.INVENTORY_LABEL_Y);
        root.addChild(label);
    }
}
