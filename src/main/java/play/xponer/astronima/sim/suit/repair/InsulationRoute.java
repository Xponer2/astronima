package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Thermal layer — <strong>thread the channel</strong>.
 *
 * <p>Insulation is threaded through routing channels in the garment, and the thread
 * has to stay in the channel: pull it taut across a corner and it bridges, leaving a
 * cold spot exactly where the fabric flexes. The work is slow, continuous, and
 * unglamorous — you are steering, not timing.
 *
 * <p>This is the only job measured by <em>sustained steadiness over distance</em>
 * rather than by moments. Slipping out of the channel drops you back to the last
 * anchor, so a long careful run is worth more than a fast sloppy one, and the failure
 * mode is losing ground rather than breaking anything.
 */
public final class InsulationRoute implements RepairTask {
    /** How far off the channel centreline the thread may sit, in route units. */
    public static final double CHANNEL_HALF_WIDTH = 0.2;

    /**
     * Momentary overshoot is not a slip.
     *
     * <p>Real thread has slack in it, and a task that punished every single frame
     * outside the line was not steadiness, it was twitch — the one interaction here
     * that has no business testing reflexes. The grace window is what turns this back
     * into steering: you can drift out and correct, and only sustained wandering
     * costs you.
     */
    public static final double SLIP_GRACE_SECONDS = 0.45;

    /** Fraction of the route between anchors, where a slip drops you back to. */
    public static final int ANCHORS = 5;

    /** How fast the thread feeds along the route while it is in the channel. */
    public static final double FEED_RATE = 0.14;

    private double position;
    private double lateral;
    private double outOfChannelSeconds;
    private int slips;

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.THERMAL_LAYER;
    }

    /**
     * Where the channel centreline sits at a point along the route, as a lateral
     * offset in -1..1. Two out-of-phase waves make a path that curves both ways and
     * has no straight stretch to coast through.
     */
    public static double channelCentreAt(double routePosition) {
        return 0.5 * Math.sin(routePosition * Math.PI * 2.2)
                + 0.16 * Math.sin(routePosition * Math.PI * 4.5 + 1.1);
    }

    /** Steers the thread; the value is the operator's lateral position, -1..1. */
    public void steer(double lateralPosition) {
        lateral = Math.clamp(lateralPosition, -1.0, 1.0);
    }

    public double lateral() {
        return lateral;
    }

    /** How far the thread currently sits from the centre of the channel. */
    public double deviation() {
        return Math.abs(lateral - channelCentreAt(position));
    }

    public boolean isInChannel() {
        return deviation() <= CHANNEL_HALF_WIDTH;
    }

    @Override
    public void tick(double dtSeconds) {
        if (isComplete()) {
            return;
        }
        if (isInChannel()) {
            outOfChannelSeconds = 0;
            position = Math.min(1.0, position + FEED_RATE * dtSeconds);
            return;
        }
        // Out of the channel: the feed stops immediately, but the thread only pulls
        // back once you have been out long enough that the slack is gone.
        outOfChannelSeconds += dtSeconds;
        if (outOfChannelSeconds < SLIP_GRACE_SECONDS) {
            return;
        }
        outOfChannelSeconds = 0;
        double anchored = Math.floor(position * ANCHORS) / ANCHORS;
        if (position > anchored) {
            slips++;
            position = anchored;
        }
    }

    public double position() {
        return position;
    }

    public int slips() {
        return slips;
    }

    /** How close the thread is to slipping, 0..1, so the screen can warn before it does. */
    public double slipPressure() {
        return Math.clamp(outOfChannelSeconds / SLIP_GRACE_SECONDS, 0.0, 1.0);
    }

    /** The anchor the thread would fall back to right now, for drawing the route. */
    public double lastAnchor() {
        return Math.floor(position * ANCHORS) / ANCHORS;
    }

    @Override
    public boolean isComplete() {
        return position >= 1.0;
    }

    @Override
    public float progress() {
        return (float) position;
    }

    /**
     * Every slip leaves a kink in the weave. The layer still insulates, but the cold
     * spots are where it will fail first.
     */
    @Override
    public float quality() {
        return Math.max(0.4f, 1f - slips * 0.08f);
    }

    @Override
    public String hintKey() {
        return isComplete() ? "done" : isInChannel() ? "follow_channel" : "off_channel";
    }
}
