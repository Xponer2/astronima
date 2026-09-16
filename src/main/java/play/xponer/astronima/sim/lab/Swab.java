package play.xponer.astronima.sim.lab;

import play.xponer.astronima.sim.pathogen.Contamination;

/**
 * Taking a sample off a surface, and whether there was enough there to find.
 *
 * <h2>A negative result is a result</h2>
 * The interesting part of this is not that swabbing works — it is that swabbing a nearly clean
 * bench comes back <strong>sterile</strong>. Few organisms land on the agar, none of them grow into
 * a colony, and the plate you incubated overnight tells you nothing.
 *
 * <p>That is real, and it is the thing that makes a swab a decision rather than a formality. You
 * have a limited number of plates and a night of incubation each; sampling the worst surface you
 * can find is the skill, and a blank plate is the cost of guessing. A version where every swab
 * grew something would make the goggles pointless — you would never need to know *where* the
 * contamination was.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Swab {

    /**
     * Share of a surface's load that comes away on the agar.
     *
     * <p>Lower than a hand's {@link Contamination#TOUCH_FRACTION}: a swab touches a small area on
     * purpose, and taking half the bench with you would make sampling a cleaning method.
     */
    public static final double PICKED_UP = 0.15;

    /**
     * Load on the agar below which nothing grows into a visible colony.
     *
     * <p>Set so that a surface at roughly a fifth of an infectious dose is the break-even point:
     * anything a player would actually be hurt by is findable, and the faint traces they would
     * never catch anything from are not. An instrument that reported every trace would be an alarm
     * that is always on.
     */
    public static final double MINIMUM_TO_GROW = 0.012;

    /**
     * What lands on the agar from a surface carrying this much.
     */
    public static double inoculum(double surfaceLoad) {
        return Math.max(0, surfaceLoad) * PICKED_UP;
    }

    /** Whether a swab of this surface will grow anything at all. */
    public static boolean willGrow(double surfaceLoad) {
        return inoculum(surfaceLoad) >= MINIMUM_TO_GROW;
    }

    /**
     * What is left on the surface after swabbing it.
     *
     * <p>Sampling takes a little away — it is a real transfer onto real agar, not an observation.
     * Not enough to be a cleaning method, which is why {@link #PICKED_UP} is small.
     */
    public static Contamination remaining(Contamination surface) {
        return surface.transferTo(Contamination.clean(surface.source()), PICKED_UP).from();
    }

    /**
     * The streak a swab arrives with.
     *
     * <p>Zero: a swab is a smear across the agar, not a four-quadrant streak, so <em>every</em>
     * environmental plate has to be streaked out at the bench before it can give single colonies.
     * The swab tells you something is there; the streak is what lets you work with it.
     */
    public static final double SWAB_STREAK = 0.0;

    private Swab() {}
}
