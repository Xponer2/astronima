package play.xponer.astronima.sim.suit;

/**
 * What right-clicking a cartridge on a suit should do once the scrubber bay already
 * works — the second of the item's two jobs, decided here so it can be proven correct
 * without a world (rule 1). {@code LiohCartridgeItem} wires the real Curios state
 * into these four booleans and acts on the answer.
 *
 * <p>Written because folding this decision into the bay-repair guard made the second
 * job disappear silently: that guard's job is "refuse once repaired, so the part is
 * not wasted", and it does that correctly — it was never asked whether *this* click
 * meant something else (PLAN.md rule 56).
 */
public final class CartridgeRefit {

    public enum Decision {
        /** No suit worn, or its bay is still broken — not this item's second job. */
        NOT_APPLICABLE,
        /** The worn cartridge is already fresh; swapping it for another wastes the held one. */
        ALREADY_FRESH,
        /** Fit the held cartridge into the worn slot, returning whatever was there. */
        SWAP
    }

    public static Decision decide(boolean suitWorn, boolean bayWorking,
                                   boolean fittedCartridgePresent, boolean fittedCartridgeFresh) {
        if (!suitWorn || !bayWorking) {
            return Decision.NOT_APPLICABLE;
        }
        if (fittedCartridgePresent && fittedCartridgeFresh) {
            return Decision.ALREADY_FRESH;
        }
        return Decision.SWAP;
    }

    private CartridgeRefit() {}
}
