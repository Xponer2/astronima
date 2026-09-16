package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Status display — <strong>solder without bridging</strong>.
 *
 * <p>Reflowing joints on a salvaged board is pure spatial accuracy. Nothing is moving
 * and nothing is timed: you can take all night. But the pads are small and close
 * together, and a stray touch bridges two of them — which then has to be wicked clean
 * before you can carry on, because a bridged board will not power up no matter how
 * many good joints surround it.
 *
 * <p>The verb is <em>accuracy under no time pressure</em>, which is the exact opposite
 * of the tank latches. It is also the only job where a mistake creates new work rather
 * than undoing old work, and the reason the status display is worth leaving until
 * you have somewhere safe to sit.
 */
public final class SolderJoints implements RepairTask {
    /** How close a touch must land to count as a joint, in board units. */
    public static final double PAD_RADIUS = 0.055;

    /** A touch this close to an already-soldered joint bridges the two. */
    public static final double BRIDGE_RADIUS = 0.11;

    /** Pad positions on the board, x and y in 0..1. */
    private static final double[][] PADS = {
            {0.18, 0.22}, {0.34, 0.20}, {0.50, 0.26}, {0.66, 0.20},
            {0.82, 0.24}, {0.24, 0.62}, {0.44, 0.70}, {0.62, 0.64},
    };

    private final boolean[] soldered = new boolean[PADS.length];
    /** A bridge sits between two pads and blocks both until it is wicked away. */
    private final boolean[] bridged = new boolean[PADS.length];
    private int misses;

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.STATUS_DISPLAY;
    }

    public int padCount() {
        return PADS.length;
    }

    public double padX(int index) {
        return PADS[index][0];
    }

    public double padY(int index) {
        return PADS[index][1];
    }

    public boolean isSoldered(int index) {
        return soldered[index];
    }

    public boolean isBridged(int index) {
        return bridged[index];
    }

    public int misses() {
        return misses;
    }

    /** What a touch at this point did, so the screen can say something useful. */
    public enum Touch { SOLDERED, WICKED, BRIDGED, MISSED }

    /**
     * Touches the iron to the board.
     *
     * <p>Order of resolution matters and is deliberate: clearing a bridge takes
     * priority, so a board in trouble can always be recovered by touching the mess
     * rather than by guessing which pad is safe.
     */
    public Touch touch(double x, double y) {
        int nearest = nearestPad(x, y);
        if (nearest < 0) {
            misses++;
            return Touch.MISSED;
        }
        double distance = distanceTo(nearest, x, y);

        if (bridged[nearest]) {
            bridged[nearest] = false;
            return Touch.WICKED;
        }
        if (distance <= PAD_RADIUS && !soldered[nearest]) {
            soldered[nearest] = true;
            return Touch.SOLDERED;
        }
        if (distance <= BRIDGE_RADIUS) {
            // Too close to the pad but not on it: solder wicks across and bridges.
            bridged[nearest] = true;
            misses++;
            return Touch.BRIDGED;
        }
        misses++;
        return Touch.MISSED;
    }

    private int nearestPad(double x, double y) {
        int best = -1;
        double bestDistance = BRIDGE_RADIUS;
        for (int i = 0; i < PADS.length; i++) {
            double distance = distanceTo(i, x, y);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private double distanceTo(int pad, double x, double y) {
        return Math.hypot(x - PADS[pad][0], y - PADS[pad][1]);
    }

    @Override
    public void tick(double dtSeconds) {
        // Nothing moves: this job has no clock, which is the point of it.
    }

    @Override
    public boolean isComplete() {
        for (int i = 0; i < PADS.length; i++) {
            if (!soldered[i] || bridged[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public float progress() {
        int done = 0;
        for (int i = 0; i < PADS.length; i++) {
            if (soldered[i] && !bridged[i]) {
                done++;
            }
        }
        return (float) done / PADS.length;
    }

    /**
     * A board that took a lot of rework has flux residue and lifted pads under the
     * joints. It boots, but it is not the board it could have been.
     */
    @Override
    public float quality() {
        return Math.max(0.4f, 1f - misses * 0.07f);
    }

    @Override
    public String hintKey() {
        for (int i = 0; i < PADS.length; i++) {
            if (bridged[i]) {
                return "wick_bridge";
            }
        }
        return isComplete() ? "done" : "solder_pads";
    }
}
