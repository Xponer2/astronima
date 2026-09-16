package play.xponer.astronima.sim.magic;

import java.util.List;

/**
 * What a stage of a claim asks for, as a closed set (design/astra-atlas-s3-progression.md §2.2):
 * {@code Identified} / {@code Holds} answer from {@link ResearchState}, {@code Owns}/{@code
 * HandsIn} are answered by a real inventory ({@link InventoryView} is the MC-free seam so this
 * whole set stays on the plain JUnit runner).
 *
 * <p><strong>Closed, not an open hook.</strong> Each kind gets its own evaluation case, its own
 * generated page row, its own tick and its own row in the test plan; a fifth kind is a design
 * change, argued like one (astra-atlas.md §S3's own refusal of the open-ended "custom predicate"
 * kind).
 *
 * <p>{@code Owns} vs {@code HandsIn} is the required_craft / required_item split (umbrella §2.1):
 * a filter is equipment and is never consumed (its own item doc's rule); an exposed spectral
 * plate is ammunition and is spent. Which is which is a per-stage design decision recorded in
 * <a href="../../../../../../../../design/astra-atlas-s3c-completion.md">astra-atlas-s3c-completion.md</a>
 * §3.
 */
public sealed interface Requirement {

    /** The existing loop: this object must be identified (captured + decoded). */
    record Identified(String objectId) implements Requirement {}

    /** Another claim must already be held. */
    record Holds(String claimId) implements Requirement {}

    /** The player carries {@code count} of {@code itemId} — equipment, NOT consumed. */
    record Owns(String itemId, int count) implements Requirement {}

    /** The player hands in {@code count} of {@code itemId} — consumed on stage completion. */
    record HandsIn(String itemId, int count) implements Requirement {}
}
