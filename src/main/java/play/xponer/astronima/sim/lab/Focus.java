package play.xponer.astronima.sim.lab;

/**
 * The fine focus: how sharp the field is, and how badly it lies when it is not.
 *
 * <h2>Why focusing is a thing you do rather than a thing that happens</h2>
 * At a thousand times, with oil, the depth of field is under a micron. You do not point a
 * microscope at something — you hunt for the plane it is in, and the difference between "cocci in
 * clusters" and "an indistinct smear" is a fraction of a turn of the knob.
 *
 * <p>That is the one operation in the whole laboratory that is genuinely physical, so it is the one
 * the player performs rather than requests. Everything else on the bench is chemistry; this is a
 * hand on a wheel.
 *
 * <h2>Out of focus does not mean invisible</h2>
 * It means <strong>wrong</strong>, which is worse and far more useful. A blurred field still shows
 * shapes, and shapes read at the wrong plane are how somebody records chains as clusters. So the
 * model gives back a sharpness that the screen can blur with and a threshold below which the
 * reading must not be written down.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Focus {

    /** Where the specimen actually is, as a knob position 0..1. */
    public static final double PLANE = 0.5;

    /** How far off you can be and still resolve anything at all. */
    public static final double DEPTH = 0.16;

    /** Sharp enough to write down. Deliberately tight: this is a thousand times, with oil. */
    public static final double READABLE = 0.82;

    /**
     * How sharp the field is at that knob position, 0..1.
     *
     * <p>A squared falloff, because that is what defocus does — the blur circle grows with the
     * distance from the plane, and the light in it spreads over the square of that radius. It also
     * gives the right feel: nearly nothing over most of the travel, then everything in the last
     * fraction, which is exactly the hunt.
     */
    public static double sharpness(double knob) {
        double off = Math.abs(Math.clamp(knob, 0, 1) - PLANE) / DEPTH;
        return Math.clamp(1 - off * off, 0, 1);
    }

    /** True when the field is crisp enough that what you write down is what is there. */
    public static boolean isReadable(double knob) {
        return sharpness(knob) >= READABLE;
    }

    /**
     * How far a cell wanders from where it really is, in cell widths.
     *
     * <p>Not noise for its own sake. A blurred field is why an arrangement gets misread, and the
     * wander is what makes a chain look like a clump at the wrong plane.
     */
    public static double scatter(double knob) {
        return (1 - sharpness(knob)) * 1.8;
    }

    /** How much of the field's light is left, so a badly focused field is also dimmer. */
    public static double contrast(double knob) {
        double sharp = sharpness(knob);
        return 0.25 + 0.75 * sharp;
    }

    private Focus() {}
}
