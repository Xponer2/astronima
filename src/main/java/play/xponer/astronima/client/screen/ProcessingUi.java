package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import play.xponer.astronima.client.hud.MachineKind;
import play.xponer.astronima.client.hud.MachinePanel;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.menu.ProcessingMenu.Kind;
import play.xponer.astronima.menu.ProcessingUiHolder;

/**
 * The chrome around {@link MachineBody}: real item slots wired to real vanilla {@link Slot}s
 * (so shift-click, drag-split and hotbar-swap all keep working unchanged — {@code
 * design/ui-ldlib2-machines.md} §2), the player's own inventory grid, and the two interactive
 * overlays (the dial, the SLS plane) that call back into {@link MachineBody}'s own drag
 * handlers rather than hand-rolling hit-testing this framework already does correctly for any
 * ordinary child element.
 *
 * <p>No stylesheet of its own. {@link MachineBody} draws the entire panel body, the slot wells
 * and the arrows in and out with the same {@code MachineFrame} calls the old screen used — an
 * {@link ItemSlot} here contributes only the real item render, the hover highlight and the
 * click/drag machinery, so its own background and overlay textures are cleared rather than
 * drawn a second time on top of the well already painted underneath it.
 */
public final class ProcessingUi {

    private ProcessingUi() {}

    public static ModularUI build(ProcessingUiHolder holder, Player player) {
        Kind kind = holder.kind();
        var panelKind = ProcessingMenu.panelKind(kind);
        int panelHeight = MachinePanel.height(panelKind);

        UIElement root = new UIElement();
        root.layout(l -> l.width(MachinePanel.WIDTH).height(panelHeight));

        MachineBody body = new MachineBody(holder);
        root.addChild(body);

        addMachineSlots(root, holder, kind);
        addInventorySlots(root, player.getInventory(), panelKind);
        addInventoryLabel(root, panelKind);
        addDialOverlay(root, body);
        if (kind == Kind.SLS) {
            addPlaneOverlay(root, body);
        }

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    /** {@link GuiSlots#at} under its old, screen-local name — kept so the rest of this file's
     *  calls did not all need touching when the helper moved out to be shared. */
    private static ItemSlot slotAt(Slot slot, int wellX, int wellY) {
        return GuiSlots.at(slot, wellX, wellY);
    }

    /**
     * The feed, product(s) and (electrolysis only) electrode slots — the same layout switch
     * {@code ProcessingMenu}'s own constructor used to run, ported here since building these real
     * {@link Slot}s is now this class's job rather than a Minecraft menu's.
     */
    private static void addMachineSlots(UIElement root, ProcessingUiHolder holder, Kind kind) {
        Container container = holder.container();
        int row = ProcessingMenu.SLOT_ROW_Y;
        boolean oneSlot = kind == Kind.CRACKING_TOWER || kind == Kind.WATER_ELECTROLYZER
                || kind == Kind.SABATIER_REACTOR;
        boolean threeSlots = ProcessingMenu.slotCount(kind) == 3;

        if (kind == Kind.IRON_SMELTER) {
            // The only machine with two consumed feed slots at once (design/iron-smelter.md):
            // ore and flux stacked on the feed side, mirroring the vertical split the
            // two-product machines already use on the output side.
            addTwoFeedTwoProductSlots(root, container, kind, row);
            return;
        }

        ItemSlot feed = slotAt(new ProcessingMenu.FeedSlot(kind, container, 0,
                ProcessingMenu.SLOT_IN_X, row), ProcessingMenu.SLOT_IN_X, row);
        root.addChild(feed);

        if (oneSlot) {
            // No product well at all: the whole product is gas, vented into the room.
        } else if (!threeSlots) {
            ItemSlot product = slotAt(new ProcessingMenu.OutputSlot(container, 1,
                    ProcessingMenu.SLOT_OUT_X, row), ProcessingMenu.SLOT_OUT_X, row);
            product.slotStyle(ss -> ss.acceptQuickMove(false));
            root.addChild(product);
        } else if (kind == Kind.ELECTROLYSIS) {
            // The middle slot is equipment the player swaps, not a second product: an electrode
            // retunes the cell, it is never produced by one.
            root.addChild(slotAt(new ProcessingMenu.ElectrodeSlot(container, 1,
                    ProcessingMenu.SLOT_OUT_X, row - 10), ProcessingMenu.SLOT_OUT_X, row - 10));
            ItemSlot output = slotAt(new ProcessingMenu.OutputSlot(container, 2,
                    ProcessingMenu.SLOT_OUT_X, row + 18), ProcessingMenu.SLOT_OUT_X, row + 18);
            output.slotStyle(ss -> ss.acceptQuickMove(false));
            root.addChild(output);
        } else {
            // Separator, fluidized bed, winnower, and refiner all split the feed two ways.
            ItemSlot first = slotAt(new ProcessingMenu.OutputSlot(container, 1,
                    ProcessingMenu.SLOT_OUT_X, row - 10), ProcessingMenu.SLOT_OUT_X, row - 10);
            first.slotStyle(ss -> ss.acceptQuickMove(false));
            root.addChild(first);
            ItemSlot second = slotAt(new ProcessingMenu.OutputSlot(container, 2,
                    ProcessingMenu.SLOT_OUT_X, row + 18), ProcessingMenu.SLOT_OUT_X, row + 18);
            second.slotStyle(ss -> ss.acceptQuickMove(false));
            root.addChild(second);
        }

        wireFeedTooltip(feed, kind);
    }

    /** Ore and flux on the feed side, iron and slag on the product side — {@code Kind#IRON_SMELTER}
     *  is the one machine with two of each, so it gets its own layout rather than stretching the
     *  single-feed one. */
    private static void addTwoFeedTwoProductSlots(UIElement root, Container container, Kind kind, int row) {
        ItemSlot ore = slotAt(new ProcessingMenu.FeedSlot(kind, container,
                play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_ORE,
                ProcessingMenu.SLOT_IN_X, row - 10), ProcessingMenu.SLOT_IN_X, row - 10);
        root.addChild(ore);
        ItemSlot flux = slotAt(new ProcessingMenu.FeedSlot(kind, container,
                play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_FLUX,
                ProcessingMenu.SLOT_IN_X, row + 18), ProcessingMenu.SLOT_IN_X, row + 18);
        root.addChild(flux);

        ItemSlot iron = slotAt(new ProcessingMenu.OutputSlot(container,
                play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_IRON,
                ProcessingMenu.SLOT_OUT_X, row - 10), ProcessingMenu.SLOT_OUT_X, row - 10);
        iron.slotStyle(ss -> ss.acceptQuickMove(false));
        root.addChild(iron);
        ItemSlot slag = slotAt(new ProcessingMenu.OutputSlot(container,
                play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_SLAG,
                ProcessingMenu.SLOT_OUT_X, row + 18), ProcessingMenu.SLOT_OUT_X, row + 18);
        slag.slotStyle(ss -> ss.acceptQuickMove(false));
        root.addChild(slag);

        wireFeedTooltip(ore, kind, play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_ORE);
        wireFeedTooltip(flux, kind, play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_FLUX);
    }

    /**
     * Names the role of a hovered machine slot (machine-io.md M3).
     *
     * <p>Two cases, both about a slot with nothing to say for itself: an <em>empty</em> slot names
     * its role, and the feed slot hovered <em>while carrying something it refuses</em> says what
     * it wanted — because a slot that just will not take the click is indistinguishable from a
     * bug. Item tooltips take precedence untouched (LDLib2's own {@code HOVER_TOOLTIPS} already
     * runs the vanilla item tooltip first); this only speaks when the slot is otherwise silent —
     * the feed slot is the first child added by {@link #addMachineSlots}, so it is always found.
     */
    private static void wireFeedTooltip(ItemSlot feed, Kind kind) {
        feed.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (!feed.getValue().isEmpty()) {
                return;
            }
            var mui = feed.getModularUI();
            var carried = mui == null || mui.getMenu() == null
                    ? net.minecraft.world.item.ItemStack.EMPTY : mui.getMenu().getCarried();
            boolean refusing = !carried.isEmpty() && !ProcessingMenu.feedAccepts(kind, carried);
            String text = refusing
                    ? "Won't take this — feed is " + ProcessingMenu.feedDescription(kind)
                    : "Feed — " + ProcessingMenu.feedDescription(kind);
            event.hoverTooltips = com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.create(
                    Component.literal(text));
        });
    }

    /** {@link #wireFeedTooltip(ItemSlot, Kind)}'s own logic, for a machine with more than one
     *  feed slot whose slots each want a different name — {@code Kind#IRON_SMELTER} is the one
     *  case today. */
    private static void wireFeedTooltip(ItemSlot feed, Kind kind, int slotIndex) {
        feed.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (!feed.getValue().isEmpty()) {
                return;
            }
            var mui = feed.getModularUI();
            var carried = mui == null || mui.getMenu() == null
                    ? net.minecraft.world.item.ItemStack.EMPTY : mui.getMenu().getCarried();
            boolean refusing = !carried.isEmpty()
                    && !ProcessingMenu.feedAccepts(kind, slotIndex, carried);
            String text = refusing
                    ? "Won't take this — feed is " + ProcessingMenu.feedDescription(kind, slotIndex)
                    : "Feed — " + ProcessingMenu.feedDescription(kind, slotIndex);
            event.hoverTooltips = com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.create(
                    Component.literal(text));
        });
    }

    private static void addInventorySlots(UIElement root, Inventory inventory, MachineKind panelKind) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = 8 + column * 18;
                int y = MachinePanel.inventoryY(panelKind) + row * 18;
                root.addChild(slotAt(new Slot(inventory, index, x, y), x, y));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = 8 + column * 18;
            int y = MachinePanel.hotbarY(panelKind);
            root.addChild(slotAt(new Slot(inventory, column, x, y), x, y));
        }
    }

    private static void addInventoryLabel(UIElement root, MachineKind panelKind) {
        var label = new com.lowdragmc.lowdraglib2.gui.ui.elements.Label();
        label.setValue(Component.translatable("container.inventory"));
        label.textStyle(s -> s.textColor(play.xponer.astronima.client.hud.MachineFrame.TEXT_DIM));
        label.getLayout().positionType(TaffyPosition.ABSOLUTE);
        label.getLayout().left(8);
        label.getLayout().top(MachinePanel.inventoryLabelY(panelKind));
        root.addChild(label);
    }

    /**
     * The dial's own hit region, sized to whatever {@link MachineBody#controlBoxLocal} says —
     * zero-sized for a machine with no control, which is already a no-hit box. The kind/wrench
     * exclusion and the geometric bounds check both still happen inside
     * {@link MachineBody#overDial}, called from {@link MachineBody#onDialMouseDown} — this
     * overlay's only job is to be a real, LDLib2-hit-tested element sitting over the right pixels.
     */
    private static void addDialOverlay(UIElement root, MachineBody body) {
        var box = body.controlBoxLocal();
        UIElement handle = new UIElement();
        handle.getLayout().positionType(TaffyPosition.ABSOLUTE);
        handle.getLayout().left(8 + box.x() - 4);
        handle.getLayout().top(ProcessingMenu.READOUT_Y + box.y());
        handle.getLayout().width(box.width() + 8);
        handle.getLayout().height(14);
        handle.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        handle.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            body.onDialMouseDown();
            handle.startDrag(null, null);
        });
        handle.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, e -> body.onDialDrag());
        handle.addEventListener(UIEvents.DRAG_END, e -> body.onDialReleased());
        root.addChild(handle);
    }

    /** The SLS printer's process plane: a 2D drag, its own hit region over
     *  {@code SlsPrinterView.plane()}'s own box. */
    private static void addPlaneOverlay(UIElement root, MachineBody body) {
        var plane = play.xponer.astronima.client.hud.SlsPrinterView.plane();
        UIElement handle = new UIElement();
        handle.getLayout().positionType(TaffyPosition.ABSOLUTE);
        handle.getLayout().left(8 + plane.x());
        handle.getLayout().top(ProcessingMenu.READOUT_Y + plane.y());
        handle.getLayout().width(plane.width());
        handle.getLayout().height(plane.height());
        handle.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        handle.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            body.onPlaneMouseDown();
            handle.startDrag(null, null);
        });
        handle.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, e -> body.onPlaneDrag());
        root.addChild(handle);
    }
}
