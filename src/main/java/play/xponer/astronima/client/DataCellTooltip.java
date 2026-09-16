package play.xponer.astronima.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.item.DataCellItem;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.storage.DataLedger;

/**
 * A cell's own real manifest, on the cell — an instrument, not a debug command (rule 7's smallest
 * version): a player never needs a screen to know what a data cell holds. See
 * {@code design/data-cells.md} §3.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class DataCellTooltip {

    /** How many logged entries the tooltip lists by name before summarising the rest — a full
     *  128-type cell should not print 128 lines over the player's own chat. */
    private static final int MAX_LINES = 8;

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.DATA_CELL.get())) {
            return;
        }
        DataLedger ledger = DataCellItem.ledgerOf(event.getItemStack());
        if (ledger.entries().isEmpty()) {
            event.getToolTip().add(Component.translatable("astronima.data_cell.empty")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        int shown = 0;
        for (DataLedger.Entry entry : ledger.entries()) {
            if (shown >= MAX_LINES) {
                break;
            }
            // The slot count sits right next to the real count so "x64 compression" never reads
            // as a cap on its own — a player sees the real total AND what it actually costs in
            // the same line, rather than having to do the division themselves.
            int entrySlots = (int) ((entry.count() + ledger.itemsPerSlot() - 1) / ledger.itemsPerSlot());
            event.getToolTip().add(Component.translatable("astronima.data_cell.entry",
                            displayName(entry.itemId()), entry.count(), entrySlots)
                    .withStyle(ChatFormatting.AQUA));
            shown++;
        }
        int hidden = ledger.typesUsed() - shown;
        if (hidden > 0) {
            event.getToolTip().add(Component.translatable("astronima.data_cell.more", hidden)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        // Real slots used, not bare type count: two types at 65 items each already cost four
        // slots, and a player checking "how full is this" needs the real number (design doc §2).
        // Compression is named as "N per slot", never "xN" alone — "x64" read as a hard cap on
        // quantity is the exact confusion a player reported (PLAN.md rule 134).
        event.getToolTip().add(Component.translatable("astronima.data_cell.capacity",
                        ledger.slotsUsed(), DataLedger.SLOT_CAPACITY, ledger.itemsPerSlot())
                .withStyle(ChatFormatting.GRAY));
    }

    /** The real item's own display name, off the registry rather than the raw id. */
    private static String displayName(String itemId) {
        var location = net.minecraft.resources.Identifier.tryParse(itemId);
        if (location == null) {
            return itemId;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(location)
                .map(item -> new net.minecraft.world.item.ItemStack(item).getHoverName().getString())
                .orElse(itemId);
    }

    private DataCellTooltip() {}
}
