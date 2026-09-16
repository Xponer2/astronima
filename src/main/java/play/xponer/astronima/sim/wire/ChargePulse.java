package play.xponer.astronima.sim.wire;

/**
 * Where the charge is along a live run, and how brightly.
 *
 * <h2>Why an animation has a model at all</h2>
 * <strong>A wire that looks identical whether or not it is doing anything is the single biggest
 * reason this system was hard to debug.</strong> Every failure — a missed pad, a colour collision,
 * a gate wired backwards — has the same symptom: nothing happens. Moving charge turns that into a
 * picture: you follow it out from the source and the place it stops is the fault.
 *
 * <p>So this is not decoration, it is the tier's primary diagnostic, and it gets the same treatment
 * as anything else the player reads. The first version was a <em>binary</em> pip — a pixel was
 * either lit or not, four apart — which reads as blinking dots rather than as flow, and blinking
 * dots do not say which way the current is going. A crest with a trailing wake does, because the
 * asymmetry <em>is</em> the direction.
 *
 * <p>Minecraft-free (rule 1), and that is rule 25 applied to an animation: the renderer paints what
 * this decides, and "the pulse actually travels" becomes something a test can hold rather than
 * something somebody has to stand and watch.
 */
public final class ChargePulse {

    /**
     * How fast charge appears to move, in pixels per second.
     *
     * <p>Fourteen — a little under a metre a second. Fast enough to read as flow at a glance, slow
     * enough that the eye can follow one crest along a run and see where it stops, which is the
     * whole diagnostic use.
     */
    public static final double SPEED_PIXELS_PER_SECOND = 14.0;

    /**
     * Pixels between one crest and the next.
     *
     * <p>Six, so a metre of trace carries between two and three crests: enough that a short run
     * still visibly moves, few enough that a long one does not turn into a solid bright line.
     */
    public static final int SPACING = 6;

    /**
     * How far the wake trails behind a crest, in pixels.
     *
     * <p>The decay length, not the cut-off — an exponential has no edge, which is the point. A
     * hard-edged tail reads as a moving dash; a soft one reads as something passing.
     */
    public static final double TAIL_PIXELS = 2.2;

    /**
     * How lit a point of a live run is, 0 at rest and 1 at a crest.
     *
     * <p>The wake trails <strong>behind</strong> the crest, which is what makes the direction of
     * travel visible in a still frame. A symmetric glow would move and still tell the player
     * nothing about which end the power came from.
     *
     * @param along   distance along the run, in pixels
     * @param seconds how long the animation has been running
     */
    public static double intensityAt(long along, double seconds) {
        double behind = behindCrest(along, seconds);
        return Math.exp(-behind / TAIL_PIXELS);
    }

    /**
     * How far this point sits behind the nearest crest, in pixels, 0 at the crest itself.
     *
     * <p>Crests travel toward increasing {@code along}, so a point is *behind* the crest when the
     * crest has already passed it — which is the sign that decides which way the wake points, and
     * the single easiest thing to get backwards here.
     */
    public static double behindCrest(long along, double seconds) {
        double moved = seconds * SPEED_PIXELS_PER_SECOND;
        // moved MINUS along, not the other way round, and the sign is the whole of the direction:
        // crests travel toward increasing `along`, so a point the crest has already reached sits
        // at a SMALLER coordinate than the crest does. Written the other way the wake trails
        // *ahead* of the front, which still animates and still looks fine in a screenshot while
        // telling the player the current flows the wrong way.
        double phase = moved - along;
        // floorMod for doubles: the remainder is always in [0, SPACING), so there is exactly one
        // crest per spacing however far the animation has run or how long the world has been up.
        return phase - Math.floor(phase / SPACING) * SPACING;
    }

    /** Where the leading crest sits at this instant, in pixels along the run. */
    public static double crestAt(double seconds) {
        double moved = seconds * SPEED_PIXELS_PER_SECOND;
        return moved - Math.floor(moved / SPACING) * SPACING;
    }

    /** How long one crest takes to reach where the next one is now, in seconds. */
    public static double periodSeconds() {
        return SPACING / SPEED_PIXELS_PER_SECOND;
    }

    private ChargePulse() {}
}
