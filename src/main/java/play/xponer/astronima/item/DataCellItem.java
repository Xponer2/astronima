package play.xponer.astronima.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.storage.DataLedger;

/**
 * A portable real digital manifest, not a bag of holding — see {@code design/data-cells.md} §1-3
 * for why "logged in this cell" is the identical simplification {@code RoomState}'s own gas mols
 * already make for "somewhere in this sealed room."
 *
 * <p><strong>Sneaking is not a modifier here, it is the only door in.</strong> A plain right-click
 * on any real container opens that container's own menu before an item's {@code useOn} ever
 * runs — vanilla's own convention, not a bug — so sneak-right-click is the sole way to reach this
 * item's own interaction against a container at all: it logs everything the container holds into
 * the cell, up to the real slot budget. The reverse — dispensing — happens in open air (no
 * container to argue with), dropping every logged item back into the world rather than eating it,
 * the same standing rule {@code CargoCrateBlockEntity}'s own doc names first — also gated on
 * sneaking, and not only for symmetry: vanilla tries the main hand's own {@code use()} before ever
 * reaching the off hand, so a plain right-click while holding a loaded cell in the main hand (the
 * natural way to hold "the thing being upgraded" while a {@code cell_compressor} sits in the off
 * hand) would otherwise dump the entire cell before {@code CellUpgradeItem} ever got a turn — a
 * real, reported bug, not a hypothetical one. Putting items back into a *specific* container is
 * the terminal's own job (design doc's own D2/D3), not this cell's.
 */
public class DataCellItem extends Item {

    public DataCellItem(Properties properties) {
        super(properties);
    }

    public static DataLedger ledgerOf(ItemStack cell) {
        return cell.getOrDefault(ModDataComponents.DATA_CELL_LEDGER.get(), DataLedger.empty());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide() || player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return InteractionResult.PASS;
        }
        ItemStack cell = context.getItemInHand();
        DataLedger ledger = vacuumFrom(ledgerOf(cell), container);
        cell.set(ModDataComponents.DATA_CELL_LEDGER.get(), ledger);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ItemStack cell = player.getItemInHand(hand);
        DataLedger ledger = ledgerOf(cell);
        if (ledger.entries().isEmpty()) {
            return InteractionResult.PASS;
        }
        for (DataLedger.Entry entry : ledger.entries()) {
            dropAllOf(entry, player);
        }
        cell.set(ModDataComponents.DATA_CELL_LEDGER.get(), ledger.cleared());
        return InteractionResult.SUCCESS;
    }

    /** Logs every real stack a container holds into the cell, up to the real index capacity —
     *  a stack whose own type is already full logs nothing further and stays in place. */
    private static DataLedger vacuumFrom(DataLedger ledger, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            DataLedger next = ledger.withAdded(idOf(stack), stack.getCount(),
                    DataLedger.SLOT_CAPACITY);
            if (next != ledger) {
                container.setItem(slot, ItemStack.EMPTY);
                ledger = next;
            }
        }
        return ledger;
    }

    private static void dropAllOf(DataLedger.Entry entry, Player player) {
        Item item = itemOf(entry.itemId());
        if (item == null) {
            return;
        }
        long remaining = entry.count();
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int thisStack = (int) Math.min(remaining, maxStack);
            remaining -= thisStack;
            player.drop(new ItemStack(item, thisStack), false);
        }
    }

    private static String idOf(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString();
    }

    private static Item itemOf(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }
}
