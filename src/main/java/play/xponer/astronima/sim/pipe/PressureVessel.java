package play.xponer.astronima.sim.pipe;

/**
 * What a gas tank can take before it stops being a tank.
 *
 * <p>A pressure vessel has two numbers that matter. The <strong>working pressure</strong>
 * is what it is rated to hold indefinitely. The <strong>burst pressure</strong> is where
 * the wall actually fails. Real vessels are built with a safety factor between them —
 * typically around 2.5 for a welded steel cylinder, more for anything people stand next
 * to — and the gap is not slack to be used, it is the margin that makes the rating
 * meaningful.
 *
 * <p>The gameplay consequence is what makes this worth modelling rather than capping a
 * number: a pump stalls at the working pressure, so filling a tank normally can never
 * burst it. Getting past that takes deliberate effort, and the tank tells you exactly
 * where you are the whole time. That is rule 7 — the hazard is instrumentable before it
 * can hurt you — falling out of the physics instead of being bolted on.
 */
public final class PressureVessel {

    /** Rated working pressure of the standard tank, in kPa. About 30 atmospheres. */
    public static final double WORKING_PRESSURE_KPA = 3000.0;

    /**
     * Safety factor between working and burst pressure.
     *
     * <p>2.5 is the ordinary figure for a welded steel gas cylinder. It is deliberately
     * not larger: a margin so wide that overpressure never happens is a mechanic that
     * never fires.
     */
    public static final double SAFETY_FACTOR = 2.5;

    public static final double BURST_PRESSURE_KPA = WORKING_PRESSURE_KPA * SAFETY_FACTOR;

    /** Internal volume of the standard tank, in m³. */
    public static final double TANK_VOLUME_M3 = 2.0;

    /** How a vessel is doing, in the terms its gauge shows. */
    public enum Condition {
        /** Below working pressure: normal service. */
        NOMINAL,
        /** Above working pressure but below burst: running on the safety margin. */
        OVERPRESSURE,
        /** Past burst: the wall has failed. */
        BURST
    }

    public static Condition classify(double pressureKPa) {
        if (pressureKPa >= BURST_PRESSURE_KPA) {
            return Condition.BURST;
        }
        return pressureKPa > WORKING_PRESSURE_KPA
                ? Condition.OVERPRESSURE : Condition.NOMINAL;
    }

    /**
     * Fill fraction against the <em>working</em> pressure, which is what a gauge reads.
     *
     * <p>Deliberately not against burst. A gauge that showed 40 % at the working
     * pressure would be telling you there is plenty of room left in a tank that is
     * already as full as it is rated to be.
     */
    public static double gauge(double pressureKPa) {
        return Math.clamp(pressureKPa / WORKING_PRESSURE_KPA, 0.0, 1.0);
    }

    /**
     * How far into the safety margin a vessel is, 0 at working pressure and 1 at burst.
     *
     * <p>Only meaningful once past working pressure; below that there is no margin being
     * consumed and it reads zero.
     */
    public static double marginUsed(double pressureKPa) {
        if (pressureKPa <= WORKING_PRESSURE_KPA) {
            return 0;
        }
        return Math.clamp(
                (pressureKPa - WORKING_PRESSURE_KPA)
                        / (BURST_PRESSURE_KPA - WORKING_PRESSURE_KPA), 0.0, 1.0);
    }

    private PressureVessel() {}
}
