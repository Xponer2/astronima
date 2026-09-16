package play.xponer.astronima.sim.optics;

/**
 * The curl a rising ember actually has — real convection off an uneven hot surface carries a mote
 * up and outward, and small vortices shedding off the plume spin it as it goes. A closed-form
 * function of age rather than accumulated velocity, so a test can hold the exact offset at any
 * tick without simulating every tick before it, and so a widened preview burst still looks the
 * same shape whether it is watched for one second or ten.
 */
public final class EmberDrift {

    /** How far out the spiral swings at its widest, in blocks — visible, not a shiver. */
    public static final double MAX_RADIUS_BLOCKS = 0.45;

    /** One full turn of the curl, in ticks. */
    public static final double TICKS_PER_TURN = 24.0;

    /** How long the spiral takes to reach its full width, in ticks. */
    public static final double WIDEN_TICKS = TICKS_PER_TURN;

    /** Horizontal offset from the spawn point at this age, on the X axis. */
    public static double outwardX(double ageTicks, double phase) {
        return radiusAt(ageTicks) * Math.cos(angleAt(ageTicks, phase));
    }

    /** Horizontal offset from the spawn point at this age, on the Z axis. */
    public static double outwardZ(double ageTicks, double phase) {
        return radiusAt(ageTicks) * Math.sin(angleAt(ageTicks, phase));
    }

    private static double angleAt(double ageTicks, double phase) {
        return phase + ageTicks / TICKS_PER_TURN * 2.0 * Math.PI;
    }

    /** The spiral widens as the ember rises, like a real plume does, then holds its width. */
    private static double radiusAt(double ageTicks) {
        return MAX_RADIUS_BLOCKS * Math.min(1.0, Math.max(0.0, ageTicks) / WIDEN_TICKS);
    }

    private EmberDrift() {}
}
