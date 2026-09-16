package play.xponer.astronima.sim;

/**
 * Heat leaving (or entering) a gas volume through the structure around it.
 *
 * <p>Gas has very little heat capacity compared with the rock and metal enclosing it,
 * so a room's air is dragged toward the temperature of its surroundings: a puff of
 * 900 K oxygen warms a cabin briefly, not permanently, and a room does not stay at
 * flame temperature once the fire is out.
 *
 * <p><strong>Interim model.</strong> Until the thermal phase gives rooms real heat
 * capacity, radiators, and heaters, the surrounding structure is treated as an
 * infinite bath at a fixed temperature. That is the conservative choice: it prevents
 * heat accumulating without inventing a freezing mechanic players have no way to
 * counter yet.
 *
 * <p><strong>Its replacement now exists and is not connected yet, on purpose.</strong>
 * {@code sim/thermal/HeatBalance} is the real thing — radiative loss against a 2.7 K sky,
 * with a view factor — and it stays unwired until v0.4's T3 gives the player something that
 * puts heat back. Swapping it in today would do exactly what the paragraph above has been
 * warning against. See {@code design/thermal.md} §3.
 */
public final class ThermalRelaxation {
    /** Sealed habitats are assumed to sit at shirtsleeve temperature (20 °C). */
    public static final double HABITAT_BATH_K = 293.0;

    /** How fast air equilibrates with the structure; ~1 minute to close 63 % of a gap. */
    public static final double TIME_CONSTANT_S = 60.0;

    /**
     * Moves {@code currentK} toward {@code bathK} exponentially.
     *
     * @param timeConstantS seconds to close 63 % of the remaining difference
     */
    public static double step(double currentK, double bathK, double dtSeconds, double timeConstantS) {
        if (timeConstantS <= 0) {
            return bathK;
        }
        double f = 1.0 - Math.exp(-dtSeconds / timeConstantS);
        return currentK + (bathK - currentK) * f;
    }

    private ThermalRelaxation() {}
}
