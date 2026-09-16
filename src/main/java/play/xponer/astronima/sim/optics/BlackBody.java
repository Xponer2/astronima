package play.xponer.astronima.sim.optics;

/**
 * What colour a hot thing glows.
 *
 * <p>Tanner Helland's polynomial fit to the Planckian locus (tannerhelland.com, 2012) — a
 * photo-editing approximation, not a spectral integration against the CIE colour-matching
 * functions, but a named, citable, real one rather than an invented ramp. Valid 1000–40000 K,
 * which comfortably covers a wire from a dull red warning to the white a fault arc reaches.
 *
 * <p>The shape is recognisable on sight: red until ~6600 K, climbing through orange and yellow,
 * crossing white blue-white beyond it — the same order a blacksmith reads a billet in, and the
 * same order a hot star outshines a cool one in.
 */
public final class BlackBody {

    /** Below this, nothing is glowing yet — see {@link #isVisiblyGlowing}. */
    public static final double DRAPER_POINT_K = 798.0;

    private BlackBody() {}

    /** Packed 0xRRGGBB for a black body at {@code temperatureK}. */
    public static int rgb(double temperatureK) {
        double t = Math.clamp(temperatureK, 1000.0, 40000.0) / 100.0;

        double red = t <= 66.0 ? 255.0 : 329.698727446 * Math.pow(t - 60.0, -0.1332047592);
        double green = t <= 66.0
                ? 99.4708025861 * Math.log(t) - 161.1195681661
                : 288.1221695283 * Math.pow(t - 60.0, -0.0755148492);
        double blue = t >= 66.0 ? 255.0 : t <= 19.0 ? 0.0
                : 138.5177312231 * Math.log(t - 10.0) - 305.0447927307;

        return (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    /**
     * Whether a surface at this temperature is incandescent to a human eye at all.
     *
     * <p>The Draper point: real metal starts to visibly glow (a dull red) around 798 K /
     * 525 °C, well below where {@link #rgb} would already report a saturated red — the formula
     * has no floor of its own, so without this a "glowing" wire at 300 K would draw at full
     * brightness in a colour meaning nothing.
     */
    public static boolean isVisiblyGlowing(double temperatureK) {
        return temperatureK >= DRAPER_POINT_K;
    }

    /**
     * How bright the glow reads, 0 at the Draper point and 1 by the time a black body would
     * already be reported as pure white (6600 K, where {@link #rgb} saturates).
     */
    public static double intensity01(double temperatureK) {
        if (temperatureK <= DRAPER_POINT_K) {
            return 0.0;
        }
        return Math.clamp((temperatureK - DRAPER_POINT_K) / (6600.0 - DRAPER_POINT_K), 0.0, 1.0);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }
}
