package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Regulator — <strong>null out a drifting error</strong>.
 *
 * <p>Calibrating a flow regulator means chasing a reference that will not sit still:
 * you turn the dial, the error needle settles, thermal drift pulls it off again, and
 * you have to <em>keep</em> it nulled long enough for the reading to mean something.
 * A single well-timed action cannot do this — only continuous fine control can, which
 * is what makes it the hardest adjustment in the suit and unlike every other job.
 *
 * <p>The setpoint drifts as a sum of two slow sinusoids of different periods, so it is
 * smooth and followable but never repeats: you cannot learn a pattern and replay it.
 */
public final class RegulatorCalibration implements RepairTask {
    /** How close the dial must sit to the setpoint to count as nulled. */
    public static final double TOLERANCE = 0.06;

    /** Seconds the error must stay nulled, in total, to accept the calibration. */
    public static final double DWELL_REQUIRED_SECONDS = 4.0;

    /** How far the setpoint wanders from centre. */
    private static final double DRIFT_AMPLITUDE = 0.3;

    private double dial = 0.5;
    private double elapsed;
    private double dwell;
    private double errorIntegral;
    private double errorSamples;

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.REGULATOR;
    }

    /** Where the operator has set the dial, 0..1. */
    public void setDial(double value) {
        dial = Math.clamp(value, 0.0, 1.0);
    }

    public double dial() {
        return dial;
    }

    /** The reference the dial is chasing — never shown directly, only as the error. */
    public double setpoint() {
        return 0.5 + DRIFT_AMPLITUDE * (0.6 * Math.sin(elapsed * 0.7) + 0.4 * Math.sin(elapsed * 0.23));
    }

    /** Signed needle deflection, which is all the operator actually sees. */
    public double error() {
        return dial - setpoint();
    }

    public boolean isNulled() {
        return Math.abs(error()) <= TOLERANCE;
    }

    @Override
    public void tick(double dtSeconds) {
        elapsed += dtSeconds;
        if (isComplete()) {
            return;
        }
        if (isNulled()) {
            dwell += dtSeconds;
            errorIntegral += Math.abs(error()) * dtSeconds;
            errorSamples += dtSeconds;
        } else {
            // Losing the null does not reset the work, but it does cost some of it:
            // drifting off for a moment is recoverable, wandering away is not.
            dwell = Math.max(0, dwell - dtSeconds * 0.5);
        }
    }

    public double dwellSeconds() {
        return dwell;
    }

    @Override
    public boolean isComplete() {
        return dwell >= DWELL_REQUIRED_SECONDS;
    }

    @Override
    public float progress() {
        return (float) Math.clamp(dwell / DWELL_REQUIRED_SECONDS, 0.0, 1.0);
    }

    /**
     * How tightly the null was actually held. Sitting at the edge of tolerance for
     * four seconds passes, but it is a regulator that will drift out of spec sooner.
     */
    @Override
    public float quality() {
        if (errorSamples <= 0) {
            return 0.4f;
        }
        double meanError = errorIntegral / errorSamples;
        return (float) Math.clamp(1.0 - meanError / TOLERANCE * 0.6, 0.4, 1.0);
    }

    @Override
    public String hintKey() {
        return isComplete() ? "done" : isNulled() ? "hold_null" : "null_the_needle";
    }
}
