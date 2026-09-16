package play.xponer.astronima.sim.optics;

/**
 * The reticle's own arithmetic: how close the current aim is to a target, and whether holding
 * still on it has lasted long enough to count as a capture — MC-free so the "getting warmer" feel
 * and the lock-on threshold are provably right before any reticle is drawn (rule 1). This is the
 * direct fix for the old spectrograph's silent in-or-out tolerance check
 * (design/astra-telescope.md §4): here, closeness is continuous, and a capture is a sustained hold
 * rather than an instant click.
 */
public final class TelescopeSearch {

    /** Ticks of sustained, in-tolerance aim needed before a capture fires — a few seconds, long
     * enough that "holding on target" reads as a deliberate act rather than a passing sweep. */
    public static final int LOCK_TICKS = 60;

    /**
     * 1.0 dead-centre, 0.0 at the exact edge of tolerance, negative beyond it. Returning a signed
     * value rather than clamping to zero lets a caller that wants "how close, even when short"
     * read the magnitude, while {@link #isOnTarget} answers the simple in/out question.
     */
    public static double closeness(double angleDegrees, double toleranceDegrees) {
        return 1.0 - (angleDegrees / toleranceDegrees);
    }

    public static boolean isOnTarget(double closeness) {
        return closeness >= 0.0;
    }

    /**
     * The next hold-tick count: grows while on target, resets the instant it is not — a hold that
     * drifts off target for even one tick starts over, the same way a real long exposure is ruined
     * by the instrument moving mid-shot.
     */
    public static int advanceHold(int currentHoldTicks, boolean onTargetThisTick) {
        return onTargetThisTick ? currentHoldTicks + 1 : 0;
    }

    public static boolean isCaptureComplete(int holdTicks) {
        return holdTicks >= LOCK_TICKS;
    }

    private TelescopeSearch() {}
}
