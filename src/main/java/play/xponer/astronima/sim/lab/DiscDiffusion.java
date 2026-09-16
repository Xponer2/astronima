package play.xponer.astronima.sim.lab;

/**
 * Kirby–Bauer disc diffusion: the test where you measure a circle with a ruler.
 *
 * <h2>Why the diameter is a number and not a feeling</h2>
 * A paper disc loaded with a known amount of drug sits on a lawn of the organism. The drug diffuses
 * out into the agar down a concentration gradient — high at the disc, falling with distance. The
 * organism grows everywhere the local concentration is below its <strong>minimum inhibitory
 * concentration</strong>, and does not grow where it is above.
 *
 * <p>So the edge of the clear zone <em>is</em> the MIC contour. That is the whole idea, and it is
 * why measuring a distance tells you a concentration. A more susceptible organism — a lower MIC —
 * is stopped further out, so the zone is bigger. The relation is logarithmic because the diffusion
 * gradient is: {@code zone ∝ −log(MIC)}.
 *
 * <h2>The inoculum is why real laboratories own a turbidity standard</h2>
 * A heavier lawn means more organisms to inhibit at every radius, so the zone closes in. A plate
 * swabbed too thickly reads <strong>everything as resistant</strong>, is completely
 * self-consistent, and is completely wrong. That is a real and common error, and it is worth having
 * because it is a mistake in the player's technique rather than in their reasoning.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class DiscDiffusion {

    /** The lawn density a plate is supposed to be swabbed to. Everything is calibrated to this. */
    public static final double STANDARD_INOCULUM = 1.0;

    /** No drug reaches beyond this, whatever the organism — the plate is only so big. */
    public static final double LARGEST_ZONE_MM = 40.0;

    /** The disc itself, which is always in the middle of the zone you measure. */
    public static final double DISC_DIAMETER_MM = 6.0;

    /**
     * How much wider the zone gets per tenfold drop in the MIC.
     *
     * <p>About ten millimetres, which is what real plates do: a susceptible organism against a
     * 30 µg disc gives twenty to twenty-five millimetres, and a resistant one gives none at all. A
     * shallower slope than that would put every organism in the intermediate band and the ruler
     * would stop being worth reading.
     */
    public static final double MM_PER_DECADE = 10.0;

    /**
     * How far out the drug still stops growth, in millimetres.
     *
     * @param inoculum how heavy the lawn is, against {@link #STANDARD_INOCULUM}
     */
    public static double zoneMm(Organism organism, Antibiotic antibiotic, double inoculum) {
        double mic = organism.micFor(antibiotic);
        if (!Double.isFinite(mic) || mic <= 0) {
            return DISC_DIAMETER_MM;      // no zone at all: growth right up to the paper
        }
        // The Hill-Bailey relation this whole method rests on: the zone tracks the logarithm of
        // the concentration, because the diffusion gradient does.
        double fromMic = MM_PER_DECADE * Math.log10(antibiotic.discContentUg() / mic);
        double thicker = Math.log10(Math.max(0.05, inoculum)) * 8.0;
        return Math.clamp(DISC_DIAMETER_MM + fromMic - thicker,
                DISC_DIAMETER_MM, LARGEST_ZONE_MM);
    }

    /** The same, on a properly swabbed plate. */
    public static double zoneMm(Organism organism, Antibiotic antibiotic) {
        return zoneMm(organism, antibiotic, STANDARD_INOCULUM);
    }

    /** The finished call for one drug, which is what a prescription is made from. */
    public static Antibiotic.Call read(Organism organism, Antibiotic antibiotic, double inoculum) {
        return antibiotic.read(zoneMm(organism, antibiotic, inoculum));
    }

    private DiscDiffusion() {}
}
