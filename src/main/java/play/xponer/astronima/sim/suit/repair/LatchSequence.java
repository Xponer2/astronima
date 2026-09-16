package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Tank mount — <strong>torque in sequence</strong>.
 *
 * <p>A tank mount is a ring of latches, and a ring of latches is always tightened in
 * a set order: going round in sequence cocks the flange and the tank seats crooked.
 * Each latch also has a torque spec — loose and it will shake free under load,
 * over-torqued and the thread strips.
 *
 * <p>So the verb is <em>order and restraint</em>. You must pick the right latch, and
 * you must stop holding at the right moment. Neither speed nor precision alone gets
 * you through: both are cheap individually, and the combination is the test.
 */
public final class LatchSequence implements RepairTask {
    public static final int LATCH_COUNT = 6;

    /** Torque built per second of holding, in units where spec is 1.0. */
    public static final double TORQUE_RATE = 0.55;

    /** Below this the latch is loose. */
    public static final double SPEC_MIN = 0.8;

    /** Above this the thread strips. */
    public static final double SPEC_MAX = 1.25;

    /** How a single latch ended up. */
    public enum LatchState { UNTOUCHED, TIGHT, STRIPPED }

    /**
     * The order latches must be torqued in — a star pattern, the same reason a wheel
     * is tightened across the hub rather than round the rim.
     */
    private static final int[] ORDER = {0, 3, 1, 4, 2, 5};

    private final LatchState[] states = new LatchState[LATCH_COUNT];
    private int nextInOrder;
    private int holding = -1;
    private double torque;
    private int mistakes;

    public LatchSequence() {
        java.util.Arrays.fill(states, LatchState.UNTOUCHED);
    }

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.TANK_MOUNT;
    }

    /** Which latch the operator should be on now, or -1 when finished. */
    public int expectedLatch() {
        return nextInOrder >= ORDER.length ? -1 : ORDER[nextInOrder];
    }

    /**
     * Starts torquing a latch. The wrong latch is a mistake but not a disaster — you
     * notice, let go, and move to the right one.
     */
    public void beginTorque(int latch) {
        if (isComplete() || latch < 0 || latch >= LATCH_COUNT) {
            return;
        }
        if (latch != expectedLatch()) {
            mistakes++;
            return;
        }
        holding = latch;
        torque = 0;
    }

    @Override
    public void tick(double dtSeconds) {
        if (holding >= 0) {
            torque += TORQUE_RATE * dtSeconds;
        }
    }

    /** Releasing sets the latch at whatever torque it reached. */
    public void release() {
        if (holding < 0) {
            return;
        }
        if (torque > SPEC_MAX) {
            // Stripped: back it out and run it again. Time lost, nothing destroyed.
            states[holding] = LatchState.STRIPPED;
            mistakes++;
        } else if (torque < SPEC_MIN) {
            // Loose: it simply is not done, so the sequence does not advance.
            states[holding] = LatchState.UNTOUCHED;
            mistakes++;
        } else {
            states[holding] = LatchState.TIGHT;
            nextInOrder++;
        }
        holding = -1;
        torque = 0;
    }

    /** A stripped latch is re-cut and returns to untouched, at the cost of the time. */
    public void rework(int latch) {
        if (latch >= 0 && latch < LATCH_COUNT && states[latch] == LatchState.STRIPPED) {
            states[latch] = LatchState.UNTOUCHED;
        }
    }

    public LatchState stateOf(int latch) {
        return states[latch];
    }

    /** Current torque on the held latch, 0 when nothing is held. */
    public double torque() {
        return torque;
    }

    public int holding() {
        return holding;
    }

    public int mistakes() {
        return mistakes;
    }

    @Override
    public boolean isComplete() {
        return nextInOrder >= ORDER.length;
    }

    @Override
    public float progress() {
        return (float) nextInOrder / ORDER.length;
    }

    @Override
    public float quality() {
        return Math.max(0.4f, 1f - mistakes * 0.1f);
    }

    @Override
    public String hintKey() {
        return isComplete() ? "done" : holding >= 0 ? "release_at_spec" : "torque_next";
    }
}
