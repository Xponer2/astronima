package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.item.DataCellItem;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.storage.DataLedger;
import play.xponer.astronima.sim.storage.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * The real ME-style browsing screen — see {@code design/data-cells.md} §12. Reads every
 * {@code data_cell} sitting in every {@code storage_drive} directly touching this block (§12.1:
 * this pass reads only direct neighbours, not the whole connected {@code storage_frame} network,
 * a stated scope decision rather than a shortcut discovered later), combines them into one live
 * {@link TerminalView}, and shows it as {@link #MAX_VISIBLE_ROWS} representative, real
 * {@link ItemStack}s — real icon, real name, real total count, exactly as vanilla already knows
 * how to render, so no bespoke item-rendering was needed for the list itself.
 *
 * <p><strong>No separate input slot — §27's own simplification.</strong> §25 already made every
 * display row a real, clickable slot accepting real vanilla placement; a dedicated "drop it here"
 * slot became pure redundancy once that landed; drag-and-drop onto a row, or shift-click from the
 * inventory, are the only deposit gestures a player ever used. {@link #setItem} is reached the
 * same way for every index now — there is no slot 0 special case left to reason about.
 *
 * <p><strong>Display rows are real, clickable slots — §25's own rework.</strong> Earlier passes
 * (§14, §21-23) refused all pickup on display rows and routed withdrawal through a bespoke click
 * packet, reasoning that both vanilla's {@code AbstractContainerMenu#moveItemStackTo} and
 * LDLib2's own {@code ModularUIContainerMenu#quickMoveStack} mutate whatever stack a slot's
 * {@code getItem()} returns *directly* — true, but only for the shift-click/quick-move path.
 * Vanilla's own plain single-click pickup (verified against {@code AbstractContainerMenu#doClick}'s
 * real decompiled source) goes through {@code Slot#tryRemove} → {@code Slot#remove} →
 * {@link #removeItem}, and its deposit path through {@code Slot#safeInsert} →
 * {@code Slot#setByPlayer} → {@link #setItem} — both real, both safely interceptable here.
 * Display rows are therefore plain, ordinary {@code Slot}s now (see {@code StorageTerminalUi}):
 * {@link #removeItem} executes a real, capped {@link #withdraw} for any index, and
 * {@link #setItem} deposits the real delta into the connected cells via {@link #depositReal}. The
 * one path still genuinely unsafe this way is shift-click/quick-move —
 * {@code menu/StorageTerminalMenu#quickMoveStack} intercepts display-row indices explicitly and
 * routes them through {@link #withdraw} directly, never falling through to the generic,
 * direct-mutation implementation for those indices.
 *
 * <p><strong>{@link #MAX_VISIBLE_ROWS} real slots are a viewport, not a cap on distinct
 * items.</strong> {@link TerminalView#entries()} itself has no limit; {@link #scrollRow} names
 * which page of {@link #COLUMNS}-wide rows currently occupies the real slots, so a drive holding
 * more distinct item types than fit on screen at once is reachable by scrolling rather than
 * simply invisible past row 54 (design/data-cells.md §18 — the player's own reported "added more
 * than fits, and there is no way to scroll to it").
 */
public class StorageTerminalBlockEntity extends BlockEntity implements Container {
    /** How many real display slots exist — the viewport size, not a limit on how many distinct
     *  item types a scrollable view can reach (rule 8: a real, generous, but still finite window,
     *  the same scale the drive's own double-chest worth of cells already uses). */
    public static final int MAX_VISIBLE_ROWS = 54;

    /** The grid is {@value #COLUMNS} wide; scrolling moves a whole row of that width at once, so
     *  the grid never shows a partial row. */
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = MAX_VISIBLE_ROWS / COLUMNS;

    /** No separate input slot (§27) — every real slot is a display row, and deposit reaches it
     *  the same way pickup does: real vanilla placement, straight onto the row itself. */
    public static final int FIRST_DISPLAY_SLOT = 0;
    public static final int CONTAINER_SIZE = MAX_VISIBLE_ROWS;

    private TerminalView.Sort sort = TerminalView.Sort.NAME;
    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** Which page of {@link #COLUMNS}-wide rows is currently shown — always left in a valid,
     *  clamped state by {@link #refreshView()}, the only place that ever writes it besides a
     *  request to change it (design doc §18). */
    private int scrollRow = 0;
    /** How many rows the current (possibly unscrolled-to) view actually has — synced to the
     *  client via {@link play.xponer.astronima.menu.StorageTerminalUiHolder}'s own
     *  {@code ContainerData} so the scrollbar can size itself against a real number, not a guess. */
    private int totalRows = 0;

    public StorageTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_TERMINAL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StorageTerminalBlockEntity terminal) {
        terminal.refreshView();
    }

    public void setSort(TerminalView.Sort sort) {
        this.sort = sort;
        refreshView();
    }

    public TerminalView.Sort sort() {
        return sort;
    }

    /** Requests a new scroll page — clamped for real by the very next {@link #refreshView()},
     *  called immediately rather than waiting for the next tick so a click feels responsive. */
    public void setScrollRow(int row) {
        this.scrollRow = row;
        refreshView();
    }

    public void scrollBy(int rows) {
        setScrollRow(scrollRow + rows);
    }

    public int scrollRow() {
        return scrollRow;
    }

    /** How many {@link #COLUMNS}-wide rows the current view has, after the last refresh — the
     *  real number a scrollbar sizes itself against. */
    public int totalRows() {
        return totalRows;
    }

    /** Every {@code storage_drive} this terminal directly touches — §12.1's own stated scope. */
    public List<StorageDriveBlockEntity> connectedDrives() {
        List<StorageDriveBlockEntity> drives = new ArrayList<>();
        if (level == null) {
            return drives;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction))
                    instanceof StorageDriveBlockEntity drive) {
                drives.add(drive);
            }
        }
        return drives;
    }

    /** Every real cell (as its own ledger) currently sitting in a connected drive, in a fixed,
     *  stable order — the same list index {@link TerminalView.Withdrawal#cellIndex} and
     *  {@link TerminalView.Deposit#cellIndex} address. */
    public List<DataLedger> connectedLedgers() {
        List<DataLedger> ledgers = new ArrayList<>();
        for (StorageDriveBlockEntity drive : connectedDrives()) {
            for (int slot = 0; slot < drive.getContainerSize(); slot++) {
                ItemStack stack = drive.getItem(slot);
                if (stack.is(ModItems.DATA_CELL.get())) {
                    ledgers.add(DataCellItem.ledgerOf(stack));
                }
            }
        }
        return ledgers;
    }

    /** The same list {@link #connectedLedgers()} builds, but naming which real
     *  {@code (drive, slot)} each entry came from, so a plan's {@code cellIndex} can be written
     *  back to the one real cell it actually named. */
    private List<CellRef> connectedCellRefs() {
        List<CellRef> refs = new ArrayList<>();
        for (StorageDriveBlockEntity drive : connectedDrives()) {
            for (int slot = 0; slot < drive.getContainerSize(); slot++) {
                if (drive.getItem(slot).is(ModItems.DATA_CELL.get())) {
                    refs.add(new CellRef(drive, slot));
                }
            }
        }
        return refs;
    }

    private record CellRef(StorageDriveBlockEntity drive, int slot) {
        DataLedger ledger() {
            return DataCellItem.ledgerOf(drive.getItem(slot));
        }

        void setLedger(DataLedger ledger) {
            ItemStack cell = drive.getItem(slot);
            cell.set(play.xponer.astronima.registry.ModDataComponents.DATA_CELL_LEDGER.get(), ledger);
            drive.setChanged();
        }
    }

    /** Recomputes the live aggregated view and refreshes the display slots from it — called every
     *  tick (design doc's own real correctness requirement: it must keep working while the
     *  screen is open, not just when it was last opened). Windows the (unbounded) entry list by
     *  {@link #scrollRow}, re-clamping it against the current entry count every time, so a drive
     *  that shrinks (a cell removed, everything withdrawn) while scrolled down never leaves the
     *  view stranded past the real end of the list. */
    public void refreshView() {
        TerminalView view = TerminalView.of(connectedLedgers()).sortedBy(sort);
        List<TerminalView.Entry> entries = view.entries();
        totalRows = (entries.size() + COLUMNS - 1) / COLUMNS;
        int maxScrollRow = Math.max(0, totalRows - VISIBLE_ROWS);
        scrollRow = Math.clamp(scrollRow, 0, maxScrollRow);
        int firstEntry = scrollRow * COLUMNS;
        for (int row = 0; row < MAX_VISIBLE_ROWS; row++) {
            int entryIndex = firstEntry + row;
            ItemStack shown = ItemStack.EMPTY;
            if (entryIndex < entries.size()) {
                TerminalView.Entry entry = entries.get(entryIndex);
                Item item = itemOf(entry.itemId());
                if (item != null) {
                    shown = new ItemStack(item,
                            (int) Math.min(entry.totalCount(), Integer.MAX_VALUE));
                }
            }
            items.set(FIRST_DISPLAY_SLOT + row, shown);
        }
    }

    /** The one real place a deposit ever lands in the connected cells — reached from
     *  {@link #setItem} for a real click or drag depositing directly onto a display row. Returns
     *  how much was actually absorbed, which may be less than {@code amount} if the connected
     *  cells do not have room for all of it — the caller decides what happens to the remainder,
     *  the same "never silently eat it" rule this mod's storage blocks already keep. */
    private long depositReal(String itemId, long amount) {
        if (amount <= 0) {
            return 0;
        }
        List<CellRef> refs = connectedCellRefs();
        List<DataLedger> ledgers = refs.stream().map(CellRef::ledger).toList();
        List<TerminalView.Deposit> plan = TerminalView.depositPlan(ledgers, itemId, amount);
        long absorbed = 0;
        for (TerminalView.Deposit deposit : plan) {
            CellRef ref = refs.get(deposit.cellIndex());
            ref.setLedger(ref.ledger().withAdded(itemId, deposit.amount(), DataLedger.SLOT_CAPACITY));
            absorbed += deposit.amount();
        }
        if (absorbed > 0) {
            setChanged();
        }
        return absorbed;
    }

    /** Executes a real withdrawal against the connected drives — reached from {@link #removeItem}
     *  (a real single-click pickup) and from {@code menu/StorageTerminalMenu#quickMoveStack}'s own
     *  explicit interception of display-row indices (the one path that still cannot be allowed to
     *  fall through to the generic, direct-mutation implementation — design/data-cells.md §14/§25).
     *  Capped to one real stack per call, here rather than at every caller, so nobody can ask this
     *  method for more than a click could plausibly mean.
     *
     *  <p><strong>"{@code amount}" means "up to this many," not "exactly this many."</strong>
     *  {@link TerminalView#withdrawalPlan} itself is deliberately all-or-nothing — asking it for
     *  more than a ledger set actually holds refuses outright, the same "never a silent
     *  short-count" honesty {@link DataLedger} itself keeps for a full cell. That is exactly
     *  wrong for a quick-move-style request ("give me a full stack, or whatever's actually
     *  there if that's less"): a shift-click asking for a full 64-stack of a row that only has
     *  30 logged must not walk away with nothing. Clamped to the real available total here,
     *  before the plan is ever asked for one, so the pure planner's own strict contract never has
     *  to change (design/data-cells.md §22 — reported directly: shift-click on anything under a
     *  full stack withdrew zero). */
    public long withdraw(String itemId, long amount) {
        Item item = itemOf(itemId);
        if (item == null) {
            return 0;
        }
        long cap = new ItemStack(item).getMaxStackSize();
        long requested = Math.min(amount, cap);
        List<CellRef> refs = connectedCellRefs();
        List<DataLedger> ledgers = refs.stream().map(CellRef::ledger).toList();
        long available = ledgers.stream().mapToLong(ledger -> ledger.countOf(itemId)).sum();
        requested = Math.min(requested, available);
        List<TerminalView.Withdrawal> plan = TerminalView.withdrawalPlan(ledgers, itemId, requested);
        if (plan.isEmpty()) {
            return 0;
        }
        long withdrawn = 0;
        for (TerminalView.Withdrawal withdrawal : plan) {
            CellRef ref = refs.get(withdrawal.cellIndex());
            ref.setLedger(ref.ledger().withRemoved(itemId, withdrawal.amount()));
            withdrawn += withdrawal.amount();
        }
        setChanged();
        refreshView();
        return withdrawn;
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

    // -------------------------------------------------------------------- Container

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    /** Every real slot is a computed display row (§27 — no separate input slot); nothing is ever
     *  physically held here for a break-drop or hopper-style check to find. */
    @Override
    public boolean isEmpty() {
        return true;
    }

    /** Display rows return a defensive copy, deliberately — found live, writing {@link #setItem}'s
     *  own delta logic: vanilla's own {@code Slot#safeInsert} reads {@code getItem()}, mutates
     *  that exact object in place ({@code slotStack.grow(...)}), and only then calls
     *  {@code setItem} with it. A live reference means {@code items.get(slot)} inside
     *  {@link #setItem} would already reflect the *post*-grow count by the time it runs — the
     *  "before" and "after" being literally the same object — making every delta compute as zero.
     *  A copy here is what actually lets {@link #setItem} see the real, un-mutated previous value. */
    @Override
    public ItemStack getItem(int slot) {
        if (slot >= FIRST_DISPLAY_SLOT && slot < CONTAINER_SIZE) {
            return items.get(slot).copy();
        }
        return items.get(slot);
    }

    /** A real vanilla single-click pickup (left click takes the whole shown total, right click
     *  takes half — {@code AbstractContainerMenu#doClick}'s own real logic) reaches this through
     *  {@code Slot#tryRemove} → {@code Slot#remove}, both untouched vanilla code. {@link #withdraw}
     *  already caps whatever is asked for to one real stack of the item, so a left-click "take it
     *  all" on a row of 400 still only ever hands over a sane 64 — the same honest "a pickup
     *  gesture means one real stack" rule §14 already established for the old click-packet path. */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < FIRST_DISPLAY_SLOT || slot >= CONTAINER_SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack shown = items.get(slot);
        if (shown.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long withdrawn = withdraw(idOf(shown), amount);
        if (withdrawn <= 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(shown.getItem(), (int) withdrawn);
    }

    /** Every real slot is a computed display row; there is no raw, physically-held item to take
     *  "without update" the way a real container's own backing slot would have. */
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    /** A real vanilla single-click or drag-and-drop deposit ({@code Slot#safeInsert} →
     *  {@code Slot#setByPlayer}) reaches this directly on whichever display row it landed on,
     *  computing the real amount just deposited as the delta against what that row showed a
     *  moment ago (still valid: {@link #refreshView} is the only other writer of {@code items},
     *  and it always runs before this could ever be reached again). A brand new item type
     *  landing on a row past the real end of the list (still showing empty) counts its whole
     *  incoming stack as the deposit. The row itself is never actually overwritten here —
     *  {@link #refreshView} recomputes its real, aggregated value from the cells themselves on
     *  the very next tick regardless. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < FIRST_DISPLAY_SLOT || slot >= CONTAINER_SIZE || stack.isEmpty()) {
            return;
        }
        ItemStack previous = items.get(slot);
        long delta = !previous.isEmpty() && ItemStack.isSameItemSameComponents(previous, stack)
                ? stack.getCount() - previous.getCount()
                : stack.getCount();
        depositReal(idOf(stack), delta);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /** Every real slot is a computed display row (§27) — there is nothing physically held here
     *  to clear; {@link #refreshView} would simply recompute the same rows again regardless. */
    @Override
    public void clearContent() {}
}
