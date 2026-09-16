package play.xponer.astronima.sim.pathogen;

/**
 * Getting a contamination load back down, and why you can never quite finish.
 *
 * <h2>Every equal dose kills the same fraction of what is left</h2>
 * Sterilisation is not subtraction. It is the D-value: the dose that takes a population down by
 * one decade, and the next decade costs exactly the same again.
 *
 * <pre>
 *   survivors = load × 10 ^ (−dose / D)
 * </pre>
 *
 * <p>That is counter-intuitive in a useful way. The first half of the job is cheap and the last
 * half is expensive; 1.0 → 0.1 costs what 0.1 → 0.01 costs; and there is no sterile button, only a
 * target you choose and pay for. Which is why the instrument built on this reads in <em>decades</em>
 * rather than percent — a log scale is the honest axis, and it is the same one the disc-diffusion
 * plate already taught.
 *
 * <p>Minecraft-free (rule 1). See {@code design/decontamination.md}.
 */
public final class Decontamination {

    /**
     * Ultraviolet dose for one decade, J/m².
     *
     * <p>Real germicidal figures for vegetative organisms sit around 20–40 J/m² per log
     * reduction at 254 nm, so this is the middle of the honest range rather than a number picked
     * to feel good.
     */
    public static final double D_ULTRAVIOLET = 30.0;

    /** Rinse volume for one decade, litres — mechanical removal plus a biocide, and it is slow. */
    public static final double D_RINSE = 0.9;

    /**
     * The share of a load ultraviolet simply cannot reach.
     *
     * <p><strong>This is why the machine has two controls.</strong> Light cleans what it can see,
     * and a suit has folds, a glove has a palm, a boot has a sole. Run the lamp forever and the
     * readout stalls here — which the player has to be able to <em>see</em>, or they will stand in
     * the booth wondering why the bar stopped.
     */
    public static final double SHADOWED_FRACTION = 0.06;

    /**
     * What is left after standing under the lamp.
     *
     * @param seconds       time under it
     * @param irradiance    W/m² at the surface, so a better lamp is more decades in the same time
     */
    public static double afterUltraviolet(double load, double seconds, double irradiance) {
        if (load <= 0 || seconds <= 0 || irradiance <= 0) {
            return load;
        }
        double lit = load * (1 - SHADOWED_FRACTION);
        double shadowed = load * SHADOWED_FRACTION;
        return shadowed + lit * Math.pow(10, -(irradiance * seconds) / D_ULTRAVIOLET);
    }

    /** What is left after a rinse. Slower, and it reaches everywhere the water runs. */
    public static double afterRinse(double load, double litres) {
        if (load <= 0 || litres <= 0) {
            return load;
        }
        return load * Math.pow(10, -litres / D_RINSE);
    }

    /**
     * Whether the lamp phase has anything left to give this tick — game-design audit #2, finding
     * G's fix: {@code litSeconds} is real exposure time scaled by whatever fraction of the lamp's
     * wanted power actually arrived, so zero power makes this false regardless of how dirty the
     * load is, and partial power still gives partial, real exposure rather than a cliff (the same
     * "power buys rate, never a discontinuity" law {@code ProcessingBlockEntity} already keeps).
     * Pulled out of {@code DeconStationBlockEntity} specifically so this decision is directly
     * unit-testable (rule 1) — the block entity itself cannot be loaded on the plain-JUnit
     * classpath at all, pure static method or not.
     */
    public static boolean lampShouldRun(double load, double litSeconds, double irradiance) {
        // No separate litSeconds > 0 guard: afterUltraviolet already returns load unchanged for
        // seconds <= 0, so the comparison below already means "zero exposure changed nothing."
        return load > load * SHADOWED_FRACTION * 1.02 + 1e-6
                && afterUltraviolet(load, litSeconds, irradiance) < load - 1e-9;
    }

    /**
     * How many decades came off — the axis the instrument actually draws.
     *
     * <p>Zero when nothing changed; one when a tenth of it is left. A percentage would flatter the
     * first few seconds and hide every second after that.
     */
    public static double decadesRemoved(double before, double after) {
        if (before <= 0 || after <= 0 || after >= before) {
            return 0;
        }
        return Math.log10(before / after);
    }

    /**
     * How long under the lamp to get to a target, or {@link Double#POSITIVE_INFINITY} if the lamp
     * cannot get there at all.
     *
     * <p>Told to the player <em>before</em> they commit rather than discovered after. An infinite
     * answer is a real answer and the machine says it plainly: the shadowed fraction is below this
     * target and no amount of light will do it.
     */
    public static double secondsOfUltravioletToReach(double load, double target, double irradiance) {
        if (load <= target) {
            return 0;
        }
        double floor = load * SHADOWED_FRACTION;
        if (target <= floor || irradiance <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double lit = load * (1 - SHADOWED_FRACTION);
        return D_ULTRAVIOLET * Math.log10(lit / (target - floor)) / irradiance;
    }

    /** How much water to get to a target. The rinse always gets there; it is only ever a price. */
    public static double litresOfRinseToReach(double load, double target) {
        if (load <= target || target <= 0) {
            return 0;
        }
        return D_RINSE * Math.log10(load / target);
    }

    private Decontamination() {}
}
