package play.xponer.astronima.sim.ore;

/**
 * What temperature a mirror can actually reach out here.
 *
 * <p>{@code design/machines.md} §5 fixes that there is no power in tier 1, on purpose: a
 * castaway in the first hour has none, so a machine that needed one would be unreachable.
 * Concentrated sunlight is the way out of that, and it is also how asteroid water
 * extraction is genuinely proposed — no grid, no fuel, and above all no oxygen, which
 * would be an absurd thing to spend on making water.
 *
 * <p><strong>The physics.</strong> A concentrator raises the flux on its target by its
 * concentration ratio; the target heats until it radiates that flux away again. Radiative
 * balance gives T ∝ (flux)^¼, which is why the returns on focusing are so steep at first
 * and so flat later — doubling the concentration buys only about 19 % more temperature.
 * That is the shape the dial should have, and it is the reason the useful part of its
 * travel is at the bottom rather than spread evenly.
 */
public final class SolarConcentrator {

    /**
     * Equilibrium temperature of an unconcentrated black surface at this distance.
     *
     * <p>Roughly the sunlit surface of a main-belt body: cold enough that bare rock in
     * daylight is nowhere near giving up its water, which is exactly why a mirror is
     * needed at all rather than simply leaving the rock outside.
     */
    public static final double BARE_SUNLIT_K = 235.0;

    /**
     * Concentration ratio at full focus. A parabolic dish reaching several hundred suns is
     * ordinary engineering; solar furnaces go into the thousands.
     *
     * <p>Chosen so that <strong>full focus overshoots into sintering</strong>. That is the
     * point of the number: a dish that could not ruin the charge would make the dial a
     * "more is better" slider, and the chemistry would be doing no work. With this ratio
     * the useful window sits in the bottom third of the travel and the top half is a
     * mistake — which is what makes finding it worth anything.
     */
    public static final double MAX_CONCENTRATION = 900.0;

    /**
     * Temperature this retort reaches.
     *
     * @param sunlight fraction of full daylight on the mirror: 0 at night or buried
     * @param focus    the dial, 0 (spread) to 1 (tightest)
     */
    public static double temperatureK(double sunlight, double focus) {
        double lit = Math.clamp(sunlight, 0.0, 1.0);
        if (lit <= 0) {
            // No sun, no mirror, nothing. Not "cooler" — a mirror with nothing to
            // concentrate is not a heater at all.
            return 0;
        }
        double concentration = 1.0 + Math.clamp(focus, 0.0, 1.0) * (MAX_CONCENTRATION - 1.0);
        // Radiative equilibrium: the fourth root is what makes the last half of the dial
        // buy so little, and what puts the useful window in reach of a careful hand.
        return BARE_SUNLIT_K * Math.pow(lit * concentration, 0.25);
    }

    /**
     * The focus that would land exactly on a wanted temperature, or 1 if it cannot reach.
     *
     * <p>Used to mark the useful window on the panel's temperature bar, so the target is
     * somewhere the player can see rather than a number they have to be told (rule 9).
     */
    public static double focusFor(double sunlight, double temperatureK) {
        double lit = Math.clamp(sunlight, 0.0, 1.0);
        if (lit <= 0 || temperatureK <= 0) {
            return 1.0;
        }
        double neededConcentration = Math.pow(temperatureK / BARE_SUNLIT_K, 4.0) / lit;
        double focus = (neededConcentration - 1.0) / (MAX_CONCENTRATION - 1.0);
        return Math.clamp(focus, 0.0, 1.0);
    }

    private SolarConcentrator() {}
}
