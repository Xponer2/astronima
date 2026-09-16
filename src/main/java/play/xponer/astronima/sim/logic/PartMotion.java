package play.xponer.astronima.sim.logic;

/**
 * How far through its travel a moving part is.
 *
 * <h2>Why a plunger has a model</h2>
 * A switch that snapped between two frames told the player it had registered the click and nothing
 * else. <strong>Motion is what separates a control from a picture of one</strong> (rule 23): a
 * thing you watch move is a thing you believe you operated, and a thing that changes between frames
 * is a thing you wonder whether you touched.
 *
 * <p>It lives here, MC-free, for the same reason {@code ChargePulse} does — rule 25. An animation
 * is a decision like any other, and "the plunger actually travels, and comes all the way back" is
 * something a test can hold rather than something somebody has to sit and watch.
 */
public final class PartMotion {

    /**
     * How long a button's plunger takes to bottom out, in seconds.
     *
     * <p>Fast. A real button is down before you have finished deciding to press it, and an
     * animation slower than the decision reads as lag rather than as travel.
     */
    public static final double PRESS_SECONDS = 0.06;

    /**
     * How long it takes to come back up.
     *
     * <p>Slower than it went down, because a spring is weaker than a finger. That asymmetry is
     * most of what makes a press read as a press rather than as a flicker.
     */
    public static final double RELEASE_SECONDS = 0.18;

    /** A hand switch throws over about a tenth of a second — a lever, not a button. */
    public static final double THROW_SECONDS = 0.11;

    /**
     * How far through the travel, 0 at rest and 1 fully engaged.
     *
     * <p>Smoothstep rather than linear: its slope is zero at both ends, so the part eases out of
     * rest and settles into its stop instead of starting and stopping dead. Linear travel is the
     * single most recognisable sign of an animation nobody looked at.
     *
     * @param secondsSince how long ago the part changed state
     * @param engaged      what it changed <em>to</em> — down, or closed
     * @param seconds      how long that direction of travel takes
     */
    public static double travel(double secondsSince, boolean engaged, double seconds) {
        double through = seconds <= 0 ? 1 : Math.clamp(secondsSince / seconds, 0, 1);
        double eased = through * through * (3 - 2 * through);
        return engaged ? eased : 1 - eased;
    }

    /** A button's plunger: quick down, slower back. */
    public static double plunger(double secondsSince, boolean held) {
        return travel(secondsSince, held, held ? PRESS_SECONDS : RELEASE_SECONDS);
    }

    /** A switch's lever, which takes the same time whichever way it is going. */
    public static double lever(double secondsSince, boolean closed) {
        return travel(secondsSince, closed, THROW_SECONDS);
    }

    private PartMotion() {}
}
