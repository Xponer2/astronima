package play.xponer.astronima.sim.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a storage terminal actually shows: every real item logged across a set of {@link DataLedger}
 * cells, combined into one list — the union a player expects ("how much of this do I have, total,
 * anywhere in this drive"), per {@code design/data-cells.md} §12.
 *
 * <p>Minecraft-free (rule 1), the same discipline {@link DataLedger} and {@link ConnectedRegion}
 * already keep: this only ever computes a <em>plan</em> — which cell to touch and by how much —
 * and the real {@code ItemStack}/data-component wiring happens at the block-entity call site.
 */
public record TerminalView(List<Entry> entries) {

    /** One distinct item type and its real total across every source cell. */
    public record Entry(String itemId, long totalCount) {}

    public enum Sort { NAME, COUNT, TYPE }

    /** One step of a withdrawal: take {@code amount} of the requested item out of cell
     *  {@code cellIndex} (an index into the same list of ledgers the plan was built from). */
    public record Withdrawal(int cellIndex, long amount) {}

    /** One step of a deposit: add {@code amount} of the item into cell {@code cellIndex}. */
    public record Deposit(int cellIndex, long amount) {}

    /** The union of every entry across every source ledger — same item id in two cells sums. */
    public static TerminalView of(List<DataLedger> ledgers) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (DataLedger ledger : ledgers) {
            for (DataLedger.Entry entry : ledger.entries()) {
                totals.merge(entry.itemId(), entry.count(), Long::sum);
            }
        }
        List<Entry> entries = new ArrayList<>();
        totals.forEach((id, count) -> entries.add(new Entry(id, count)));
        return new TerminalView(entries);
    }

    public long countOf(String itemId) {
        for (Entry entry : entries) {
            if (entry.itemId().equals(itemId)) {
                return entry.totalCount();
            }
        }
        return 0;
    }

    /**
     * Re-ordered for display. {@code TYPE} keeps the encounter order {@link #of} already built —
     * once every entry is already one distinct item id, "type" has no real axis left to sort by
     * other than the order its first unit was logged, which is what a player who groups by type
     * expects: related deposits landing near each other rather than alphabetised apart.
     */
    public TerminalView sortedBy(Sort sort) {
        List<Entry> sorted = new ArrayList<>(entries);
        switch (sort) {
            case NAME -> sorted.sort(Comparator.comparing(Entry::itemId));
            case COUNT -> sorted.sort(Comparator.comparingLong(Entry::totalCount).reversed());
            case TYPE -> { /* already in encounter order */ }
        }
        return new TerminalView(sorted);
    }

    /**
     * How to pull {@code amount} of {@code itemId} out of {@code cells}, in list order. Refused
     * (empty plan, nothing touched) if the combined total across every cell falls short — the
     * same "nothing happens rather than a partial, silent short-count" honesty {@link DataLedger}
     * itself already keeps for a full cell.
     */
    public static List<Withdrawal> withdrawalPlan(List<DataLedger> cells, String itemId, long amount) {
        if (amount <= 0) {
            return List.of();
        }
        long total = 0;
        for (DataLedger cell : cells) {
            total += cell.countOf(itemId);
        }
        if (total < amount) {
            return List.of();
        }
        List<Withdrawal> plan = new ArrayList<>();
        long remaining = amount;
        for (int i = 0; i < cells.size() && remaining > 0; i++) {
            long available = cells.get(i).countOf(itemId);
            if (available <= 0) {
                continue;
            }
            long take = Math.min(available, remaining);
            plan.add(new Withdrawal(i, take));
            remaining -= take;
        }
        return plan;
    }

    /**
     * How to distribute {@code amount} of {@code itemId} into {@code cells}, in list order,
     * filling one cell's real slot budget before spilling into the next — the auto-distribute the
     * player asked for. Short (not necessarily empty) if the combined real capacity across every
     * cell cannot hold all of {@code amount}; the caller is responsible for what happens to the
     * remainder (dropping it, the same "never eat the player's items" rule the cell's own
     * dispense verb already keeps).
     */
    public static List<Deposit> depositPlan(List<DataLedger> cells, String itemId, long amount) {
        List<Deposit> plan = new ArrayList<>();
        long remaining = amount;
        for (int i = 0; i < cells.size() && remaining > 0; i++) {
            long accepted = maxAcceptable(cells.get(i), itemId, remaining);
            if (accepted > 0) {
                plan.add(new Deposit(i, accepted));
                remaining -= accepted;
            }
        }
        return plan;
    }

    /** A closed-form answer, not a search: how many more of {@code itemId} one cell's own real
     *  slot budget can still take, derived directly from {@link DataLedger#slotsUsed()} rather
     *  than probing {@link DataLedger#withAdded} repeatedly for a value it can compute exactly. */
    private static long maxAcceptable(DataLedger cell, String itemId, long amount) {
        long itemsPerSlot = cell.itemsPerSlot();
        long existingCount = cell.countOf(itemId);
        long existingSlots = (existingCount + itemsPerSlot - 1) / itemsPerSlot;
        long otherSlots = cell.slotsUsed() - existingSlots;
        long freeSlotsForThisType = DataLedger.SLOT_CAPACITY - otherSlots;
        if (freeSlotsForThisType <= 0) {
            return 0;
        }
        long maxNewCount = freeSlotsForThisType * itemsPerSlot;
        long maxAdditional = maxNewCount - existingCount;
        return Math.max(0, Math.min(amount, maxAdditional));
    }
}
