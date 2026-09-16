package play.xponer.astronima.client.hud;

/**
 * How a floating readout arrives and leaves.
 *
 * <h2>An instrument should appear, not blink</h2>
 * A panel that pops into existence the frame the crosshair crosses a block reads as a glitch, and
 * one that vanishes the same way makes a player doubt they saw it. Worse, when the crosshair
 * wanders across two blocks the panel flickers between them, which is the most distracting thing a
 * HUD can do.
 *
 * <p>So it fades and lifts a little: a short rise in, a shorter fall out, and — the part that
 * actually matters — <strong>a panel that is already showing does not restart</strong>. Looking
 * from one machine to the next swaps the contents of a panel that is already there rather than
 * closing and reopening one.
 *
 * <p>Minecraft-free (rule 25), so the curve is arithmetic a test can check rather than something
 * that needs an eye and a stopwatch.
 */
public final class Reveal {

    /** How long a panel takes to arrive, seconds. Short: this is a readout, not a cutscene. */
    public static final double IN_SECONDS = 0.14;

    /** And to leave. Faster, because a stale panel is worse than an abrupt one. */
    public static final double OUT_SECONDS = 0.09;

    /**
     * How far it lifts as it arrives, pixels.
     *
     * <p>Was four, and reported as the panel appearing <em>out of nowhere</em> — which it was: at
     * four pixels the movement is below the threshold at which an eye reads it as movement at all,
     * so all that was left was a fade, and a fade alone is a thing blinking on. Twelve is a
     * gesture: the panel comes <em>up</em> to where it belongs.
     */
    public static final int RISE = 12;

    /**
     * How much narrower it starts, as a fraction of its width.
     *
     * <p>An instrument switching on opens out. Growing from the middle rather than sliding in from
     * an edge keeps it anchored to the thing it describes — a panel about what is under the
     * crosshair should not appear to have come from the corner of the screen.
     */
    public static final double NARROW = 0.35;

    /**
     * How much of a panel's width is drawn at this point in its arrival.
     *
     * <p>Never zero: a panel that starts at no width has a frame where it is a vertical line, and
     * that reads as a glitch rather than as an opening.
     */
    public static double widthFraction(double progress) {
        return NARROW + (1 - NARROW) * Math.clamp(progress, 0.0, 1.0);
    }

    /**
     * How visible the contents are while the panel is still opening.
     *
     * <p>Text has to wait for the plate. Drawn at the same time it would be laid over a panel
     * narrower than itself and spill out of both sides — which is worse than the abruptness this
     * whole curve exists to fix. It comes in over the last part of the opening, so the plate
     * arrives and then fills.
     */
    public static double contentFade(double progress) {
        return Math.clamp((progress - CONTENT_WAITS_UNTIL) / (1 - CONTENT_WAITS_UNTIL), 0.0, 1.0);
    }

    /** How far into the opening the contents start to show. */
    public static final double CONTENT_WAITS_UNTIL = 0.55;

    /** How far in from the left edge to start drawing, so it opens from its own middle. */
    public static int inset(int width, double progress) {
        return (int) Math.round(width * (1 - widthFraction(progress)) / 2);
    }

    /**
     * How far through the arrival it is, 0..1.
     *
     * <p>Eased rather than linear: a linear fade reads as a dimmer switch, and an eased one reads
     * as something settling into place. Smoothstep, because it starts and ends at rest.
     */
    public static double progress(double secondsShowing, boolean leaving) {
        double span = leaving ? OUT_SECONDS : IN_SECONDS;
        double t = span <= 0 ? 1 : Math.clamp(secondsShowing / span, 0.0, 1.0);
        double eased = t * t * (3 - 2 * t);
        return leaving ? 1 - eased : eased;
    }

    /** The alpha byte to draw a panel at, given how far through it is. */
    public static int alpha(double progress, int fullAlpha) {
        return (int) Math.round(Math.clamp(progress, 0.0, 1.0) * fullAlpha);
    }

    /** Applies that alpha to a packed ARGB colour, leaving the colour itself alone. */
    public static int fade(int argb, double progress) {
        int full = (argb >>> 24) & 0xFF;
        return (alpha(progress, full) << 24) | (argb & 0x00FFFFFF);
    }

    /** How far above its resting place a panel still is. Zero once it has arrived. */
    public static int offset(double progress) {
        return (int) Math.round((1 - Math.clamp(progress, 0.0, 1.0)) * RISE);
    }

    private Reveal() {}
}
