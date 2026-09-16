package play.xponer.astronima.sim.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * A real digital inventory manifest — what a data cell actually holds, per
 * {@code design/data-cells.md} §1: not matter, a record of which item and how many, with no
 * position finer than "logged in this cell." The identical move {@code RoomState}'s own gas mols
 * already make for a sealed room's air (real quantity, no per-molecule position); named here the
 * same honest way. Minecraft-free (rule 1): item ids are plain strings.
 *
 * <p><strong>Real count consumes capacity, not just distinct type.</strong> Every
 * {@link #itemsPerSlot} real items of one type fill one of this cell's {@link #SLOT_CAPACITY}
 * slots — 64 iron ingots is one slot, 65 is two — the same way a second, different item always
 * needs its own slot regardless of how few of it there are. {@link #itemsPerSlot} is itself the
 * one thing a real upgrade changes ({@link #upgraded}): a better compression component raises how
 * much one slot holds, never how many slots exist.
 */
public record DataLedger(int itemsPerSlot, List<Entry> entries) {

    /** One catalogued item type and how many are logged against it. */
    public record Entry(String itemId, long count) {}

    /** A real callback to {@code memory_ram}'s own capacity — sixteen 4-bit cells, 64 bits, 8
     *  bytes ({@code wire/MemoryLogic.ADDRESS_BITS}) — so a cell's own "byte" is the same real
     *  figure this mod's own memory chip already carries, not a fantasy unit (design doc §2). */
    public static final int BYTES_PER_TYPE = 8;

    /** A real binary kilobyte — the actual doubling convention real computer memory uses. */
    public static final int TIER_1_BYTES = 1024;

    /** How many real slots one cell has — fixed for the life of the cell; upgrading never
     *  changes this, only {@link #itemsPerSlot} (design doc §2). */
    public static final int SLOT_CAPACITY = TIER_1_BYTES / BYTES_PER_TYPE;

    /**
     * Real compression tiers, doubling each upgrade the same way real flash memory density
     * roughly doubles per generation — a named, real-flavoured progression (rule 8), not a
     * literal fabrication simulation. The base tier matches vanilla's own stack size exactly:
     * unmodified compression buys you one real stack per slot, nothing invented.
     */
    public static final int[] ITEMS_PER_SLOT_TIERS = {64, 128, 256, 512, 1024};

    public static DataLedger empty() {
        return new DataLedger(ITEMS_PER_SLOT_TIERS[0], List.of());
    }

    public long countOf(String itemId) {
        for (Entry entry : entries) {
            if (entry.itemId().equals(itemId)) {
                return entry.count();
            }
        }
        return 0;
    }

    /** Distinct item types logged — not the same as {@link #slotsUsed()} once any one type holds
     *  more than {@link #itemsPerSlot}. */
    public int typesUsed() {
        return entries.size();
    }

    public long totalItems() {
        long total = 0;
        for (Entry entry : entries) {
            total += entry.count();
        }
        return total;
    }

    /** Real slots one entry's own count actually needs, rounded up — 64 of 64 is one slot, 65 is
     *  two, matching a real stack never splitting across a fraction of a slot. */
    private int slotsFor(long count) {
        return (int) ((count + itemsPerSlot - 1) / itemsPerSlot);
    }

    /** Real total slots this ledger currently occupies, summed per entry — the one true measure
     *  of how full a cell is, not {@link #typesUsed()} alone. */
    public int slotsUsed() {
        int slots = 0;
        for (Entry entry : entries) {
            slots += slotsFor(entry.count());
        }
        return slots;
    }

    /**
     * Real capacity check: growing an already-logged type can itself cross into a new slot (64 to
     * 65 of the same item costs a second slot), and a genuinely new type needs at least one.
     * Refused, unchanged, if the real slot budget cannot cover the result — the same "index full"
     * honesty {@code design/data-cells.md} §2 already names, now measured in real items rather
     * than bare type count.
     */
    public DataLedger withAdded(String itemId, long amount, int slotCapacity) {
        if (amount <= 0) {
            return this;
        }
        List<Entry> updated = new ArrayList<>(entries);
        int otherSlots = 0;
        int foundIndex = -1;
        for (int i = 0; i < updated.size(); i++) {
            Entry entry = updated.get(i);
            if (entry.itemId().equals(itemId)) {
                foundIndex = i;
            } else {
                otherSlots += slotsFor(entry.count());
            }
        }
        long newCount = (foundIndex >= 0 ? updated.get(foundIndex).count() : 0) + amount;
        if (otherSlots + slotsFor(newCount) > slotCapacity) {
            return this;
        }
        if (foundIndex >= 0) {
            updated.set(foundIndex, new Entry(itemId, newCount));
        } else {
            updated.add(new Entry(itemId, newCount));
        }
        return new DataLedger(itemsPerSlot, updated);
    }

    /** Removes up to {@code amount} of one type; the entry disappears once its count reaches
     *  zero, exactly the way a real stock record drops a line item at zero rather than keeping
     *  an empty row. */
    public DataLedger withRemoved(String itemId, long amount) {
        if (amount <= 0) {
            return this;
        }
        List<Entry> updated = new ArrayList<>(entries);
        for (int i = 0; i < updated.size(); i++) {
            Entry entry = updated.get(i);
            if (entry.itemId().equals(itemId)) {
                long left = entry.count() - amount;
                if (left <= 0) {
                    updated.remove(i);
                } else {
                    updated.set(i, new Entry(itemId, left));
                }
                return new DataLedger(itemsPerSlot, updated);
            }
        }
        return this;
    }

    public DataLedger cleared() {
        return new DataLedger(itemsPerSlot, List.of());
    }

    /**
     * A real upgrade: a denser compression component swapped in, contents untouched. Always
     * legal in the direction that matters — a higher {@code itemsPerSlot} only ever needs the
     * same or fewer real slots for the same logged content, never more, so nothing already
     * stored can be invalidated by upgrading (the property the player specifically asked to be
     * true: upgrading must never make existing contents disappear).
     */
    public DataLedger upgraded(int newItemsPerSlot) {
        return new DataLedger(newItemsPerSlot, entries);
    }
}
