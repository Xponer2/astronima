package play.xponer.astronima.sim.pipe;

/**
 * How readily a pipe passes gas.
 *
 * <p>Flow through a pipe is not a flat rate. For viscous laminar flow the
 * Hagen–Poiseuille equation gives the volumetric rate:
 *
 * <pre>
 *   Q = π · r⁴ · Δp / (8 · μ · L)
 * </pre>
 *
 * <p>The term that matters is <strong>r⁴</strong>. Doubling a pipe's radius gives
 * sixteen times the flow, and halving it gives a sixteenth. That exponent is the whole
 * reason pipe sizing is an engineering decision rather than a cosmetic one, and it is
 * far more dramatic than anyone expects — which is exactly why it is worth surfacing
 * instead of smoothing away.
 *
 * <p>Length is only linear, so a long run of wide pipe still beats a short run of narrow
 * pipe. That is the trade the whole network is built around.
 *
 * <p>Written as a <em>conductance</em> rather than a resistance, {@code Q = C · Δp},
 * because conductance is what {@code GasFlow.equalize} already takes and because it
 * composes the way electrical conductance does — which is what makes a network of them
 * tractable.
 */
public final class Conduit {

    /**
     * Dynamic viscosity used for every gas, in Pa·s.
     *
     * <p>Roughly that of air and oxygen near room temperature. Real values differ
     * between species by tens of percent, which is genuinely true and is also swamped
     * by the r⁴ term — see design/plumbing.md §5.
     */
    public static final double VISCOSITY_PA_S = 1.9e-5;

    /** Radius of the standard pipe, in metres. A comfortable habitat line. */
    public static final double STANDARD_RADIUS_M = 0.05;

    /** One pipe block is one metre, matching the world's own scale. */
    public static final double BLOCK_LENGTH_M = 1.0;

    /**
     * Conductance of a single straight run, in m³ per second per kPa.
     *
     * @param radiusM  internal radius
     * @param lengthM  length of the run
     */
    public static double conductance(double radiusM, double lengthM) {
        if (radiusM <= 0 || lengthM <= 0) {
            return 0;
        }
        double r4 = radiusM * radiusM * radiusM * radiusM;
        // The 1e3 converts the kPa the rest of the mod speaks into the Pa the SI form
        // of the equation expects.
        return Math.PI * r4 * 1e3 / (8.0 * VISCOSITY_PA_S * lengthM);
    }

    /** Conductance of a run of {@code blocks} standard pipes. */
    public static double ofBlocks(int blocks) {
        return conductance(STANDARD_RADIUS_M, Math.max(1, blocks) * BLOCK_LENGTH_M);
    }

    /**
     * Two conductances in series, as a run of pipe is.
     *
     * <p>Resistances add, so conductances combine reciprocally. A blockage anywhere in
     * a run therefore throttles the whole run, which is the behaviour anyone who has
     * met a kinked hose expects.
     */
    public static double inSeries(double a, double b) {
        if (a <= 0 || b <= 0) {
            return 0; // anything fully blocked blocks the run
        }
        return 1.0 / (1.0 / a + 1.0 / b);
    }

    /** Two routes between the same pair of nodes: conductances add. */
    public static double inParallel(double a, double b) {
        return Math.max(0, a) + Math.max(0, b);
    }

    /**
     * Turns a conductance into the per-second fraction {@code GasFlow.equalize} wants.
     *
     * <p>{@code equalize} closes a fraction of the remaining imbalance each second,
     * which is exponential relaxation. A conductance driving two volumes toward each
     * other relaxes with a time constant set by the conductance and the smaller volume,
     * so that is what this converts.
     *
     * <p>Clamped below 1 because a fraction above 1 would overshoot equilibrium, and
     * beyond that point the honest statement is "effectively instant" rather than a
     * larger number.
     */
    public static double relaxationPerSecond(double conductance, double volumeM3) {
        if (conductance <= 0 || volumeM3 <= 0) {
            return 0;
        }
        return Math.min(1.0, conductance / volumeM3);
    }

    private Conduit() {}
}
