package play.xponer.astronima.sim.optics;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A believable night sky: many faint stars and a few brilliant ones — real luminosity
 * functions are steeply weighted toward faint, which a uniform scatter never is — each
 * coloured by its own temperature via {@link BlackBody}, the same class that colours the
 * retort's glow. A star genuinely is a black body; using one function for both is this mod's
 * whole thesis in one line of reuse.
 *
 * <p>Deterministic per {@code (index, seed)} rather than stateful, so a single star's
 * properties can be asked for and tested without generating the rest of the sky, and so two
 * clients with the same seed see the same star in the same place (design/sky.md §7).
 */
public final class StarField {

    /** Real stellar temperatures span roughly this — red dwarfs to blue giants. */
    public static final double MIN_TEMPERATURE_K = 2500.0;
    public static final double MAX_TEMPERATURE_K = 30000.0;

    /** How steeply the population favours faint stars over bright ones. Higher = more faint. */
    public static final double BRIGHTNESS_SKEW = 3.0;

    /** How steeply the population favours cool stars — real skies are dominated by K/M dwarfs,
     * not by the rare blue giants that a flat temperature roll would hand out equally often. */
    public static final double TEMPERATURE_SKEW = 2.2;

    /**
     * A star: a direction (on the unit sphere), how bright it reads, and its true colour.
     *
     * <p>{@code temperatureK} rides along even though {@code colorRgb} is derived from it,
     * because {@link BlackBody#rgb} saturates its blue channel at 6600 K — reconstructing
     * temperature from colour above that point is lossy, and a test that needs the real value
     * (population skew, say) should read it rather than approximate it back out of a colour.
     */
    public record Star(double x, double y, double z, double brightness01,
                        double temperatureK, int colorRgb) {}

    /**
     * One star, purely as a function of its own index and the field's seed.
     *
     * @param index which star, 0-based
     * @param seed  the field's own seed — two different seeds are two different skies
     */
    public static Star starAt(int index, long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ (index * 0x9E3779B97F4A7C15L + 1));

        double brightness = Math.pow(random.nextDouble(), BRIGHTNESS_SKEW);
        double temperatureK = MIN_TEMPERATURE_K
                + (MAX_TEMPERATURE_K - MIN_TEMPERATURE_K) * Math.pow(random.nextDouble(), TEMPERATURE_SKEW);

        // Uniform direction on a unit sphere: a height u in [-1,1] and an azimuth theta, which
        // is the standard construction that does not clump stars at the poles the way naively
        // sampling two independent angles does.
        double u = random.nextDouble() * 2.0 - 1.0;
        double theta = random.nextDouble() * 2.0 * Math.PI;
        double ringRadius = Math.sqrt(Math.max(0.0, 1.0 - u * u));
        double x = ringRadius * Math.cos(theta);
        double y = ringRadius * Math.sin(theta);
        double z = u;

        return new Star(x, y, z, brightness, temperatureK, BlackBody.rgb(temperatureK));
    }

    /** {@code count} stars, in index order — what a renderer actually asks for. */
    public static List<Star> generate(int count, long seed) {
        List<Star> stars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stars.add(starAt(i, seed));
        }
        return stars;
    }

    private StarField() {}
}
