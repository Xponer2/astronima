package play.xponer.astronima.sim.optics;

/**
 * How a hot surface's own glow visibly breathes — a slow brightness/size oscillation layered on
 * top of the steady brightness {@link BlackBody#intensity01} alone would give. A perfectly static
 * glow reads as a lit texture, not a fire; real embers do not hold at one brightness.
 *
 * <p>Amplitude scales with the same {@code intensity01} the colour comes from, deliberately: a
 * vessel just past the Draper point should breathe almost imperceptibly and a white-hot one
 * visibly pulse, so the animation is carrying the reading rather than sitting on top of it.
 */
public final class GlowPulse {

    /** One full breath, in seconds — a slow heartbeat, not a strobe. */
    public static final double PERIOD_SECONDS = 1.6;

    /** How far the brightest and dimmest points swing from 1.0, at full intensity. */
    public static final double MAX_AMPLITUDE = 0.18;

    /** A multiplier around 1.0 to scale a glow's size or brightness by, this instant. */
    public static double multiplier(double ageSeconds, double intensity01) {
        double amplitude = MAX_AMPLITUDE * Math.clamp(intensity01, 0.0, 1.0);
        double phase = ageSeconds / PERIOD_SECONDS * 2.0 * Math.PI;
        return 1.0 + amplitude * Math.sin(phase);
    }

    private GlowPulse() {}
}
