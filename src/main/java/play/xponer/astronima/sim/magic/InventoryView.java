package play.xponer.astronima.sim.magic;

/**
 * The MC-free seam between stage evaluation and a real Minecraft inventory
 * (design/astra-atlas-s3-progression.md §2.2): a pure function over this interface stays on the
 * plain JUnit runner, and the one-line adapter over {@code Inventory#getItem} lives behind it.
 *
 * <p>One method on purpose — the only question evaluation asks is "how many of this item".
 * {@code Inventory#getItem(int)} already walks main + equipment + offhand uniformly (the decode
 * payload's own {@code ownsFilter} comment records this), so the adapter is honest about
 * curios-less inventory scanning, and its failure message says so rather than lying with "not
 * carrying" (astra-atlas-s3c-completion.md §4's named open question).
 */
public interface InventoryView {
    int countOf(String itemId);
}
