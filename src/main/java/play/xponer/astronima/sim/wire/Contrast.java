package play.xponer.astronima.sim.wire;

/**
 * Picking a colour that can actually be seen against another one.
 *
 * <h2>Why this is arithmetic and not a constant</h2>
 * The charge animation lit a travelling pip by adding a fixed amount to every channel. On a red
 * wire that is a bright pink pip and reads perfectly; on a <strong>white</strong> wire it adds
 * nothing at all, so the charge marched invisibly down the one colour every player wires their
 * first circuit in. Reported exactly that way: <em>"по белому проводу ничего не видно, раньше на
 * нём было"</em>.
 *
 * <p>The lesson generalises past that one bug, which is why it is a class: <strong>a highlight is
 * a relationship between two colours, not a property of one.</strong> Anything that says "make it
 * brighter" or "make it darker" has a colour it silently fails on, and that colour is always the
 * one at the end of the range nobody tested.
 *
 * <p>Minecraft-free (rule 1), so the property that matters — <em>the pip is visible on every
 * insulation colour there is</em> — can be swept over the whole colour cube instead of over the
 * three colours somebody happened to try.
 */
public final class Contrast {

    /**
     * Perceived brightness, 0 to 1.
     *
     * <p>The usual weights: the eye is far more sensitive to green than to blue, so a plain
     * average would call pure blue "mid-bright" and put a mid-grey pip on it.
     */
    public static double luminance(int rgb) {
        return (((rgb >> 16) & 0xFF) * 0.3 + ((rgb >> 8) & 0xFF) * 0.6 + (rgb & 0xFF) * 0.1)
                / 255.0;
    }

    /** Above this the wire is pale enough that a white pip would disappear into it. */
    public static final double PALE = 0.62;

    /** White-hot: what charge looks like on anything that is not already pale. */
    public static final int PIP_ON_DARK = 0xFFFFFF;

    /** Deep arc-blue, for the pale runs where white vanishes. */
    public static final int PIP_ON_PALE = 0x1B49C8;

    /**
     * The colour a travelling pip should be drawn in, on a wire of this colour.
     *
     * <p>Two answers rather than a formula, deliberately: a computed complement would drift
     * through the palette as the wire colour changed, and charge that is white on one line and
     * lime on the next stops reading as one thing. Two colours, both of which say "energy", and a
     * threshold between them.
     */
    public static int pip(int wire) {
        return luminance(wire) > PALE ? PIP_ON_PALE : PIP_ON_DARK;
    }

    /**
     * How far apart two colours look, 0 to 1.
     *
     * <p>Brightness difference dominates, because that is what survives a dark corridor, a
     * shader, and the eight per cent of players who cannot separate the hues. Hue distance is
     * worth a fraction of it rather than nothing.
     */
    public static double distance(int a, int b) {
        double byBrightness = Math.abs(luminance(a) - luminance(b));
        double byChannel = (Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF))) / (3.0 * 255.0);
        return byBrightness * 0.75 + byChannel * 0.25;
    }

    /**
     * Blends two colours, {@code t} of the way from {@code a} to {@code b}.
     *
     * <p>Here rather than in a renderer because it is arithmetic, and because the charge animation
     * is the one place in this mod where a colour is computed per frame per pixel — a blend that
     * clipped or wrapped would show up as a band of the wrong colour marching down a wire, which
     * is exactly the kind of thing that gets called a lighting bug and hunted for in the shader.
     */
    public static int mix(int a, int b, double t) {
        double amount = Math.clamp(t, 0, 1);
        int r = channel(a, b, 16, amount);
        int g = channel(a, b, 8, amount);
        int blue = channel(a, b, 0, amount);
        return (r << 16) | (g << 8) | blue;
    }

    private static int channel(int a, int b, int shift, double t) {
        int from = (a >> shift) & 0xFF;
        int to = (b >> shift) & 0xFF;
        return Math.clamp((int) Math.round(from + (to - from) * t), 0, 255);
    }

    private Contrast() {}
}
