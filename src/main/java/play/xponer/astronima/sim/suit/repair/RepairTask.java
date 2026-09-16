package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * One subsystem's repair, as a machine you operate rather than a button you press.
 *
 * <p>Every implementation is a <em>different verb</em>, because different physical
 * work demands different actions: you do not solder a board the way you torque a
 * latch. A single timing minigame with six sets of constants is one mechanic
 * pretending to be six, and that is explicitly what this package exists to replace
 * (design/suit.md §3.2).
 *
 * <p>Two rules bind all of them:
 * <ul>
 *   <li><strong>Failure costs time, never the part.</strong> A player who keeps trying
 *       always finishes. The price of fumbling is minutes and a worse-quality fix, so
 *       nothing here can strand someone.</li>
 *   <li><strong>The rules live here, free of Minecraft, and are unit-tested.</strong>
 *       The screen renders state and forwards input; it decides nothing.</li>
 * </ul>
 */
public interface RepairTask {
    SuitSubsystem subsystem();

    /**
     * Advances continuous state.
     *
     * @param dtSeconds real seconds since the last call
     */
    void tick(double dtSeconds);

    /** True once the job is seated and the part can be consumed. */
    boolean isComplete();

    /** 0..1, for a progress readout. */
    float progress();

    /**
     * Quality of the finished job, 0..1, which becomes the subsystem's starting
     * condition and therefore how long it lasts before it needs doing again.
     */
    float quality();

    /** A short key naming what the operator should do right now, for the screen. */
    String hintKey();
}
