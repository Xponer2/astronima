package play.xponer.astronima.sim.lab;

/**
 * How the bench moves: colonies coming up, rings sweeping out, the pour bar breathing.
 *
 * <h2>Minecraft-free, because motion is a decision (rule 25)</h2>
 * A screen may draw, but it must not decide. <em>When</em> a colony becomes visible, <em>how far</em>
 * a ring has swept and <em>whether</em> the warning is pulsing are all arithmetic with right and
 * wrong answers, and this project has three previous animation models — the charge pulse, the part
 * motion, the scorch curve — for exactly this reason.
 *
 * <h2>Growth is not a fade</h2>
 * Colonies do not get gradually more opaque; they <strong>appear, one at a time, and then get
 * bigger</strong>. That is what a plate does overnight, and it is the difference between a plate
 * that reads as growing and one that reads as an image being loaded. So the count comes up with the
 * incubation and each one swells from nothing over its own first moments.
 */
public final class PlateMotion {

    /** How long one colony takes to swell to full size once it has appeared, in seconds. */
    public static final double EMERGE_SECONDS = 0.45;

    /** How long the rings take to sweep out when the discs go on. */
    public static final double SWEEP_SECONDS = 0.55;

    /** The pour warning breathes at roughly two beats a second: quick enough to mean hurry. */
    public static final double PULSE_HZ = 2.2;

    /**
     * How many of a plate's colonies are up yet.
     *
     * <p>Tied to the incubation rather than to a clock, so a plate taken out of the incubator and
     * looked at ten minutes later shows the same thing it showed when it came out. Growth is a
     * property of the plate; the animation is only the plate being honest about it.
     */
    public static int showing(double grownHours, int most) {
        double part = Math.clamp(grownHours / Culture.READY_HOURS, 0, 1);
        // Slow to start and quickening: a lag phase, then log phase. That shape is real and it is
        // also the one that reads as "something is happening" rather than "a bar is filling".
        return (int) Math.round(most * part * part * (3 - 2 * part));
    }

    /**
     * How big one colony is, 0..1, given how long it has been up.
     *
     * <p>Smoothstep so it arrives rather than snapping, and each colony gets its own start so they
     * do not all pop together — a plate where every colony appeared on the same frame would read as
     * a texture swap.
     */
    public static double emergence(double sinceSeconds) {
        double part = Math.clamp(sinceSeconds / EMERGE_SECONDS, 0, 1);
        return part * part * (3 - 2 * part);
    }

    /** How far the rings have swept out, 0..1. */
    public static double sweep(double sinceSeconds) {
        double part = Math.clamp(sinceSeconds / SWEEP_SECONDS, 0, 1);
        // Eased out only: a ring that overshot and settled would look like a splash, and this is a
        // measurement being revealed rather than a thing being thrown.
        return 1 - (1 - part) * (1 - part);
    }

    /**
     * The warning pulse, 0..1, for when the alcohol has been on too long.
     *
     * <p>A cosine rather than a sawtooth, because the eye reads a linear ramp with a hard reset as
     * a flicker and a sine as a heartbeat. The player is meant to feel hurried, not alarmed by the
     * screen itself.
     */
    public static double pulse(double seconds) {
        return 0.5 - 0.5 * Math.cos(seconds * PULSE_HZ * Math.PI * 2);
    }

    private PlateMotion() {}
}
