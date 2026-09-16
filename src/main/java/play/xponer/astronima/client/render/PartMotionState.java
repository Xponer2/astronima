package play.xponer.astronima.client.render;

import play.xponer.astronima.sim.logic.PartMotion;
import play.xponer.astronima.wire.WirePart;

import java.util.HashMap;
import java.util.Map;

/**
 * When each part last changed, so its travel can be drawn.
 *
 * <h2>Why the client has to remember this itself</h2>
 * A part's {@code held} flag is synced; <em>when it changed</em> is not. It deliberately is not —
 * the release deadline is a server clock reading, and a client counting down a tick timer it
 * cannot verify would be the only thing in the mod doing so. So the client watches the flag it
 * does have and notes the moment it moves, which is all an animation needs and is exactly what a
 * player is looking at anyway.
 *
 * <p>Bounded on purpose. A base can hold hundreds of parts and this is pure decoration, so when
 * the table outgrows a sensible working set it is dropped wholesale: every part is then drawn at
 * rest for a fraction of a second, which is invisible, and nothing leaks.
 */
public final class PartMotionState {

    /** Beyond this many remembered parts the table is dropped rather than grown. */
    private static final int KEEP = 256;

    private record Seen(boolean held, long atMs) { }

    private static final Map<Long, Seen> LAST = new HashMap<>();

    /**
     * How far through its travel this part is right now, 0 at rest and 1 fully engaged.
     *
     * <p>Asked while drawing, so it also does the noticing: the first frame a part is seen in a
     * new state is the frame its travel starts from.
     */
    public static double travelOf(WirePart part) {
        long key = key(part);
        long now = System.currentTimeMillis();
        Seen seen = LAST.get(key);
        if (seen == null || seen.held() != part.held()) {
            if (LAST.size() > KEEP) {
                LAST.clear();
            }
            seen = new Seen(part.held(), now);
            LAST.put(key, seen);
        }
        double since = (now - seen.atMs()) / 1000.0;
        return part.type() == play.xponer.astronima.sim.logic.PartType.BUTTON
                ? PartMotion.plunger(since, part.held())
                : PartMotion.lever(since, part.held());
    }

    /** One part's slot, packed — its cell, its face and its corner name it uniquely. */
    private static long key(WirePart part) {
        return part.cell().asLong() * 31 + part.face().ordinal() * 257L
                + part.u() * 17L + part.v();
    }

    private PartMotionState() {}
}
