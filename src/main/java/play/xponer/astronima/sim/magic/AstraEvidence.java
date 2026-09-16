package play.xponer.astronima.sim.magic;

/**
 * The real astra evidence tokens {@link Requirement.Identified} asks for — one spelling, shared
 * between {@link Claims} (what a claim requires) and the real player action that marks one
 * identified ({@code item.AstraFieldMeterItem}), so a typo in a second copy can never quietly
 * stop matching (rule 46) — the same reasoning {@link Claims#objectId} gives sky evidence.
 *
 * <p>Unlike a sky object, astra has no discrete named target to decode: the field is a
 * continuous gradient (design/asteroid-body.md's own {@code shellCoordinate}), so "identifying"
 * it means a real player has taken a real reading somewhere the gradient's two ends actually
 * differ, not that they own a specific plate. Two tokens, not one, for the same reason
 * {@code same_elements} needs two objects: a single reading proves nothing about a gradient —
 * only a real comparison between a rich site and a poor one does.
 */
public final class AstraEvidence {

    /** A real field reading taken at working depth or shallower — {@code shellCoordinate} at or
     *  past this counts, the poor end of the gradient a player can reach without any real
     *  digging. */
    public static final double SHALLOW_THRESHOLD = 0.5;

    /** A real field reading taken close to the core — {@code shellCoordinate} under this counts,
     *  the rich end of the gradient. Deliberately not merely "less than shallow": a reading taken
     *  at ordinary working depth, in between, proves nothing about the gradient's shape and must
     *  identify neither token. */
    public static final double DEEP_THRESHOLD = 0.15;

    /** Identified once a real reading lands at {@link #SHALLOW_THRESHOLD} or past it. */
    public static final String SHALLOW_READING = "astra_reading_shallow";

    /** Identified once a real reading lands under {@link #DEEP_THRESHOLD}. */
    public static final String DEEP_READING = "astra_reading_deep";

    private AstraEvidence() {}
}
