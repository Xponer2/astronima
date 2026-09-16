package play.xponer.astronima.sim.pathogen;

/**
 * How hard a body is pushing back, 0..1.
 *
 * <h2>A rate, not a shield</h2>
 * A shield makes the outcome binary: either you are healthy enough to be untouchable or you are
 * not. As a rate one number gives the three outcomes the tier wants — a rested body shrugs off a
 * mild strain, the same body loses to an aggressive one, and an exhausted body loses to both.
 *
 * <p>Rest and nutrition are the two inputs because they are the two the mod already tracks and the
 * two a player can actually do something about. Neither is a potion.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Immunity {

    /** Slept, fed, and not being kept awake by machinery. */
    public static final double RESTED = 0.85;

    /** Fed but tired, or rested but hungry. The ordinary state of somebody working. */
    public static final double WORKING = 0.5;

    /** Neither, for long enough that it shows. */
    public static final double SPENT = 0.15;

    /**
     * Strength from how well slept and how well fed somebody is.
     *
     * <p>The lower of the two dominates rather than the average, because they are not
     * interchangeable: eating well does not make up for not sleeping, and a model that let it
     * would make food the answer to everything.
     */
    public static double of(double rest, double nutrition) {
        double worse = Math.min(Math.clamp(rest, 0, 1), Math.clamp(nutrition, 0, 1));
        double better = Math.max(Math.clamp(rest, 0, 1), Math.clamp(nutrition, 0, 1));
        return SPENT + (RESTED - SPENT) * (worse * 0.75 + better * 0.25);
    }

    private Immunity() {}
}
