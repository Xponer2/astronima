package play.xponer.astronima.sim.physio;

/**
 * Injury from the surroundings dropping faster than the lungs can vent.
 *
 * <p>Distinct from {@link DecompressionModel}, and deliberately not the same mechanic with
 * different constants (rule 8). The bends are about a <em>history</em> of dissolved
 * nitrogen and are avoided by ascending slowly; this is about the <em>rate</em> of a single
 * event and is avoided by not opening that door. One is a debt, the other is an accident.
 *
 * <p><strong>The physics.</strong> Lungs vent through a finite airway. While the pressure
 * around you falls slowly enough, the glottis keeps up and the transpulmonary difference
 * stays near zero. Past that rate the difference climbs and the alveoli tear — which is why
 * rapid decompression injures and a slow bleed to the same final pressure does not. Standing
 * in vacuum is survivable in a suit; <em>arriving</em> there in a quarter of a second is not.
 *
 * <p>That distinction is the whole gameplay point: it makes an airlock, which bleeds the
 * difference off gradually, the answer — rather than a thicker door.
 */
public final class Barotrauma {

    /**
     * Pressure fall the airway can keep up with, in kPa per second.
     *
     * <p>Below this the lungs equalise and nothing happens. A chamber pumped down by an
     * airlock takes tens of seconds to cross an atmosphere, which is far under this; a
     * bulkhead opened straight onto vacuum crosses it in a tick, which is far over.
     */
    public static final double SAFE_FALL_KPA_PER_S = 12.0;

    /**
     * Fall rate at which the injury is as bad as the model goes.
     *
     * <p>An entire atmosphere in about a second — the explosive-decompression case, where
     * there is no partial outcome left to model.
     */
    public static final double SEVERE_FALL_KPA_PER_S = 100.0;

    /**
     * How badly this step injures, 0 (nothing) to 1 (as severe as modelled).
     *
     * @param previousKPa ambient pressure last step
     * @param currentKPa  ambient pressure now
     * @param dtSeconds   time between the two
     */
    public static double severity(double previousKPa, double currentKPa, double dtSeconds) {
        if (dtSeconds <= 0) {
            return 0;
        }
        // Only a fall injures. Repressurising squeezes rather than tears, and the ears
        // complain long before anything else does — so coming home is never harmful.
        double fall = previousKPa - currentKPa;
        if (fall <= 0) {
            return 0;
        }
        double rate = fall / dtSeconds;
        if (rate <= SAFE_FALL_KPA_PER_S) {
            return 0;
        }
        double excess = rate - SAFE_FALL_KPA_PER_S;
        double span = SEVERE_FALL_KPA_PER_S - SAFE_FALL_KPA_PER_S;
        return Math.clamp(excess / span, 0.0, 1.0);
    }

    /**
     * True when this step would injure an unprotected person at all.
     *
     * <p>Separate from the severity so callers can ask the cheap question — the common case
     * every tick is "nothing happened", and it should not cost a division to find out.
     */
    public static boolean injures(double previousKPa, double currentKPa, double dtSeconds) {
        return severity(previousKPa, currentKPa, dtSeconds) > 0;
    }

    /** Damage the mildest injury does, so that being hurt at all is never a scratch. */
    public static final float MINIMUM_DAMAGE = 2f;

    /** Extra damage at full severity, on top of {@link #MINIMUM_DAMAGE}. */
    public static final float SEVERITY_DAMAGE = 16f;

    /**
     * Hearts this step costs the person it happens to, 0 when it costs nothing.
     *
     * <p>Takes the suit's answer rather than the person, so the whole decision — including
     * the part that keeps EVA survivable — can be tested without a world. A sealed suit
     * holds its wearer at its own regulator pressure, so the surroundings never reach their
     * lungs at all; that is the entire reason to wear one on the surface.
     */
    public static float damage(boolean sealedSuit, double previousKPa, double currentKPa,
                               double dtSeconds) {
        if (sealedSuit) {
            return 0f;
        }
        double severity = severity(previousKPa, currentKPa, dtSeconds);
        return severity <= 0 ? 0f : (float) (MINIMUM_DAMAGE + severity * SEVERITY_DAMAGE);
    }

    private Barotrauma() {}
}
