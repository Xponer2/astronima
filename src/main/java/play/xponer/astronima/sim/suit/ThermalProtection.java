package play.xponer.astronima.sim.suit;

/**
 * What the suit's thermal layer is actually for.
 *
 * <p>Vacuum is not cold — it has no temperature at all, because there is nothing there
 * to have one. What it does is remove the two ways a body normally sheds or gains
 * heat, conduction and convection, leaving only radiation. So an unprotected person in
 * shadow radiates heat away with nothing replacing it, and one in sunlight absorbs it
 * with no way to dump it. Both directions kill, which is why the layer matters in
 * places that sound harmless.
 *
 * <p>The full thermal system is a later tier; what this models is the honest minimum —
 * whether the surroundings are trying to change your body temperature, and whether the
 * layer is between you and that. Without the layer the effect is immediate and named,
 * so a player is never left guessing why they are dying in an empty room.
 */
public final class ThermalProtection {
    /** Below this, an unprotected person is losing heat faster than they make it. */
    public static final double COLD_STRESS_C = 5.0;

    /** Above this, they cannot shed what they are taking on. */
    public static final double HOT_STRESS_C = 45.0;

    /** Beyond this margin past the stress point, the exposure turns dangerous. */
    public static final double SEVERE_MARGIN_C = 25.0;

    /** How badly the surroundings are working on an unprotected body. */
    public enum Exposure { NONE, STRESSFUL, SEVERE }

    /**
     * Classifies the surroundings for someone with no working thermal layer.
     *
     * @param celsius ambient temperature; vacuum should pass its radiative equivalent
     */
    public static Exposure classify(double celsius) {
        double excess = celsius < COLD_STRESS_C ? COLD_STRESS_C - celsius
                : celsius > HOT_STRESS_C ? celsius - HOT_STRESS_C
                : 0.0;
        if (excess <= 0) {
            return Exposure.NONE;
        }
        return excess >= SEVERE_MARGIN_C ? Exposure.SEVERE : Exposure.STRESSFUL;
    }

    /**
     * The temperature an unprotected body effectively faces in vacuum.
     *
     * <p>A body in shadow radiates toward the ~3 K background but is not instantly at
     * it; what matters for gameplay is that vacuum always counts as severe exposure,
     * so this returns a value comfortably past the severe threshold rather than
     * pretending to model radiative equilibrium properly.
     */
    public static double vacuumEquivalentCelsius() {
        return COLD_STRESS_C - SEVERE_MARGIN_C - 10.0;
    }

    /** True when the layer is doing real work, and therefore wearing. */
    public static boolean isStressful(double celsius) {
        return classify(celsius) != Exposure.NONE;
    }

    private ThermalProtection() {}
}
