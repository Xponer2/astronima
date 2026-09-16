package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Helmet seal — <strong>apply, then watch</strong>.
 *
 * <p>The real job is spreading sealant over a crack and then pressure-testing the
 * result: you cannot tell whether it worked by looking at it, only by pressurising
 * the helmet and seeing whether the needle holds. So the verb here is patience and
 * reading a graph, not reflexes.
 *
 * <p>Hold to lay down sealant. Too little leaves the crack open; too much leaves a
 * bead that the crack's edges cannot close over, which leaks in its own way — the
 * band is genuinely two-sided, and there is no way to be safe by simply holding
 * longer. Then the test runs and the trace either stays flat or droops. A drooping
 * trace can be abandoned early to scrape it back and start over, which costs time
 * rather than materials.
 */
public final class SealPressureTest implements RepairTask {
    /** Sealant laid down per second of holding, as a fraction of ideal coverage. */
    public static final double APPLY_RATE = 0.45;

    /** Coverage below this leaves the crack open. */
    public static final double COVERAGE_MIN = 0.85;

    /** Coverage above this leaves a proud bead that will not close. */
    public static final double COVERAGE_MAX = 1.15;

    /** Seconds the pressure test runs before the seal is accepted. */
    public static final double HOLD_SECONDS = 6.0;

    /** Test pressure, kPa — the trace the operator watches. */
    public static final double TEST_PRESSURE_KPA = 40.0;

    /** Below this the test has visibly failed and is worth abandoning. */
    public static final double FAIL_PRESSURE_KPA = 30.0;

    public enum Phase { APPLYING, TESTING, DONE }

    private Phase phase = Phase.APPLYING;
    private double coverage;
    private double pressureKPa = TEST_PRESSURE_KPA;
    private double heldSeconds;
    private int attempts = 1;

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.HELMET_SEAL;
    }

    /** Called while the operator holds the applicator down. */
    public void applyHold(double dtSeconds) {
        if (phase == Phase.APPLYING) {
            coverage += APPLY_RATE * dtSeconds;
        }
    }

    /** Releasing the applicator commits the coverage and starts the pressure test. */
    public void release() {
        if (phase == Phase.APPLYING && coverage > 0) {
            phase = Phase.TESTING;
        }
    }

    /** Scrapes the sealant back to try again — the escape hatch from a bad trace. */
    public void restart() {
        if (phase == Phase.TESTING) {
            phase = Phase.APPLYING;
            coverage = 0;
            pressureKPa = TEST_PRESSURE_KPA;
            heldSeconds = 0;
            attempts++;
        }
    }

    @Override
    public void tick(double dtSeconds) {
        if (phase != Phase.TESTING) {
            return;
        }
        pressureKPa = Math.max(0, pressureKPa - leakRateKPaPerSecond() * dtSeconds);
        heldSeconds += dtSeconds;
        if (heldSeconds >= HOLD_SECONDS) {
            phase = pressureKPa > FAIL_PRESSURE_KPA ? Phase.DONE : Phase.APPLYING;
            if (phase == Phase.APPLYING) {
                coverage = 0;
                pressureKPa = TEST_PRESSURE_KPA;
                heldSeconds = 0;
                attempts++;
            }
        }
    }

    /**
     * How fast the test pressure bleeds away, from how far the coverage sits outside
     * the acceptable band. Inside the band the seal holds perfectly flat, which is
     * what makes a good trace unmistakable.
     */
    public double leakRateKPaPerSecond() {
        double error = coverage < COVERAGE_MIN ? COVERAGE_MIN - coverage
                : coverage > COVERAGE_MAX ? coverage - COVERAGE_MAX
                : 0.0;
        return error * 12.0;
    }

    public Phase phase() {
        return phase;
    }

    public double coverage() {
        return coverage;
    }

    public double pressureKPa() {
        return pressureKPa;
    }

    /** 0..1 through the pressure test, for drawing the trace. */
    public double testProgress() {
        return Math.clamp(heldSeconds / HOLD_SECONDS, 0.0, 1.0);
    }

    public int attempts() {
        return attempts;
    }

    @Override
    public boolean isComplete() {
        return phase == Phase.DONE;
    }

    @Override
    public float progress() {
        return switch (phase) {
            case APPLYING -> (float) Math.clamp(coverage / COVERAGE_MAX, 0.0, 0.5);
            case TESTING -> 0.5f + (float) testProgress() * 0.5f;
            case DONE -> 1f;
        };
    }

    /**
     * A seal that took several goes is a seal with scraped-back residue under it. It
     * holds, but not for as long.
     */
    @Override
    public float quality() {
        return Math.max(0.4f, 1f - (attempts - 1) * 0.15f);
    }

    @Override
    public String hintKey() {
        return switch (phase) {
            case APPLYING -> "apply";
            case TESTING -> "watch";
            case DONE -> "done";
        };
    }
}
