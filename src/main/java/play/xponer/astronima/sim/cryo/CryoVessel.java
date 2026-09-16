package play.xponer.astronima.sim.cryo;

/**
 * A cryogenic dewar's own pressure rating (design/cryogenics.md §1.3) — deliberately **not**
 * {@link play.xponer.astronima.sim.pipe.PressureVessel}, and rule 46 is why: that class's
 * numbers (3000/7500 kPa) describe a compressed-gas cylinder built to take that load. A dewar is
 * built light for insulation and weight, and reusing the gas-cylinder numbers here would also be
 * physically wrong in a way that breaks {@link Bleve}: nitrogen's real critical pressure is
 * 3395.8 kPa, so a 7500 kPa burst threshold sits *above* the point where "liquid nitrogen" stops
 * meaning anything at all.
 */
public final class CryoVessel {

    /** Absolute burst pressure, kPa. Chosen safely under every cryogen's real critical pressure
     * (N2 3395.8, O2 5043, H2 1296.4 kPa) with margin to spare — {@link #belowEveryCriticalPressure()}
     * checks this directly rather than trusting the arithmetic once. */
    public static final double BURST_KPA = 600.0;

    /** Working pressure, kPa — burst divided by the same safety factor
     * {@link play.xponer.astronima.sim.pipe.PressureVessel} already uses, for consistency of
     * convention even though the absolute numbers differ. */
    public static final double WORKING_KPA = BURST_KPA / 2.5;

    public enum Condition { NOMINAL, OVERPRESSURE, BURST }

    public static Condition classify(double pressureKPa) {
        if (pressureKPa >= BURST_KPA) {
            return Condition.BURST;
        }
        if (pressureKPa >= WORKING_KPA) {
            return Condition.OVERPRESSURE;
        }
        return Condition.NOMINAL;
    }

    /** Fill fraction of the gauge against working pressure, for the field indicator (rule 9). */
    public static double gauge(double pressureKPa) {
        return Math.clamp(pressureKPa / WORKING_KPA, 0.0, 1.0);
    }

    /** True only if {@link #BURST_KPA} sits below every {@link Cryogen}'s own critical pressure
     * — the structural guard for this whole model's premise that a burst dewar still has a real
     * liquid/vapor boundary to flash across. Mutation-verified: raising {@link #BURST_KPA} above
     * nitrogen's 3395.8 kPa must fail this. */
    public static boolean belowEveryCriticalPressure() {
        return belowEveryCriticalPressure(BURST_KPA);
    }

    /** The testable core of {@link #belowEveryCriticalPressure()} — parameterized so a test can
     * prove the guard actually fails for a bad burst pressure, not just pass for the real one. */
    static boolean belowEveryCriticalPressure(double burstKPa) {
        for (Cryogen cryogen : Cryogen.values()) {
            if (burstKPa >= cryogen.criticalPressureKPa()) {
                return false;
            }
        }
        return true;
    }

    private CryoVessel() {}
}
