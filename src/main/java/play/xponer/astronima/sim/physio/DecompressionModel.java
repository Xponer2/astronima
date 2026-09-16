package play.xponer.astronima.sim.physio;

/**
 * Dissolved-nitrogen tissue model — the physics of the bends (decompression sickness).
 *
 * <p>Henry's law: the nitrogen dissolved in body tissue equilibrates toward the
 * ambient N2 partial pressure, exponentially with a characteristic tissue half-time
 * (~40 min for a medium compartment — modeled as one compartment for gameplay). If
 * ambient pressure drops faster than tissue can off-gas, the tissue becomes
 * supersaturated; past a threshold ratio the excess comes out as bubbles — pain,
 * then damage. The fix is real: breathe pure oxygen first (a "pre-breathe") to wash
 * the nitrogen out before you decompress.
 */
public final class DecompressionModel {
    /** Tissue N2 half-time in seconds (~40 min), scaled by the metabolism factor. */
    public static final double HALF_TIME_S = 40 * 60.0;

    /** Supersaturation ratio (tissue N2 / ambient N2) above which bubbles form. */
    public static final double CRITICAL_RATIO = 1.6;

    /**
     * Advances tissue N2 loading toward the ambient N2 partial pressure.
     *
     * @param tissueN2KPa   current dissolved N2 tension (kPa)
     * @param ambientN2KPa  N2 partial pressure being breathed (0 on pure O2 / a tank)
     * @param dtSeconds     elapsed sim seconds
     * @param metabolism    gameplay time-compression factor (faster on/off-gassing)
     * @return new tissue N2 tension
     */
    public static double step(double tissueN2KPa, double ambientN2KPa, double dtSeconds, double metabolism) {
        double k = Math.log(2) / (HALF_TIME_S / metabolism);
        double f = 1.0 - Math.exp(-k * dtSeconds);
        return tissueN2KPa + (ambientN2KPa - tissueN2KPa) * f;
    }

    /**
     * Decompression severity given current tissue load and the ambient pressure the
     * player just moved to.
     *
     * @param tissueN2KPa       dissolved N2 tension
     * @param ambientPressureKPa total ambient pressure now
     */
    public static Severity severity(double tissueN2KPa, double ambientPressureKPa) {
        // Ratio of dissolved gas to what the current pressure can hold in solution.
        double ratio = tissueN2KPa / Math.max(1.0, ambientPressureKPa);
        if (ratio < CRITICAL_RATIO) {
            return Severity.SAFE;
        }
        if (ratio < CRITICAL_RATIO * 1.5) {
            return Severity.PAIN;
        }
        return Severity.SEVERE;
    }

    public enum Severity { SAFE, PAIN, SEVERE }

    private DecompressionModel() {}
}
