package play.xponer.astronima.sim.magic;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * design/astra-incognita.md §5 — the whole design in one line:
 *
 * <pre>
 *   1/τ  =  1/τ_thermal + 1/τ_vibration + 1/τ_radiation + 1/τ_field + 1/τ_gas + 1/τ_observer
 * </pre>
 *
 * Six causes add as rates, so the worst one dominates — there is always exactly one thing worth
 * fixing next, and {@link #breakdown} is what names it. Every term is driven by a system this
 * mod already has (thermal, acoustics, radiation, wire, gas, physiology); none of it is a new
 * energy source to generate and spend.
 *
 * <h2>What the constants are, and are not</h2>
 * These are not measured values — nothing in this game is a real superconducting qubit. They are
 * <strong>internally consistent and tuned to feel right</strong>: cabin pressure and room
 * temperature give a τ in the tens of microseconds (unusable), a pumped-down cryostat gives
 * whole seconds (a real run), and a player standing in the room clamps everything to about a
 * microsecond regardless of how good the rest of the site is. Every constant is named, and rule
 * 41's rule applies — retuning one is a decision to argue with, not a magic number.
 */
public final class Coherence {

    public enum Term { THERMAL, VIBRATION, RADIATION, FIELD, GAS, OBSERVER }

    /** τ·T product, kelvin-seconds. 4 K (a real cryostat's cold stage) gives τ_thermal = 1 s. */
    private static final double THERMAL_K_S = 4.0;
    /** τ at perfect silence (0 dB). Never infinite — there is no such thing as total silence. */
    private static final double VIBRATION_AT_ZERO_DB_S = 10.0;
    /** τ·(dose rate) product. No source in the game emits a dose yet (design/astra-incognita.md
     *  §7's "no ice-lens block exists" problem, one tier over) — see {@link #radiationSeconds}. */
    private static final double RADIATION_S_PER_UNIT_DOSE = 1.0;
    /** τ·(field exposure) product, at full unshielded exposure (1.0). */
    private static final double FIELD_S_AT_FULL_EXPOSURE = 0.05;
    /** τ·P product, kPa-seconds, set so cabin pressure (101.325 kPa) gives exactly 100 µs. */
    private static final double GAS_KPA_S = 0.0101325;
    /** The hard floor while anyone is in the room. Nothing below this is reachable by any stage. */
    private static final double OBSERVER_PRESENT_S = 1.0e-6;

    /** No photons, no continuum, nothing to disturb thermally either — cold is cold. */
    public static double thermalSeconds(double temperatureK) {
        return temperatureK <= 0.0 ? Double.POSITIVE_INFINITY : THERMAL_K_S / temperatureK;
    }

    /** {@code sim.Acoustics}' own dB, the same number {@code Acoustics.blocksRest} reads. */
    public static double vibrationSeconds(double soundLevelDb) {
        return VIBRATION_AT_ZERO_DB_S / Math.pow(10.0, soundLevelDb / 10.0);
    }

    /** No dose, no correlated errors — true today because nothing in the game emits one yet. */
    public static double radiationSeconds(double doseRatePerHour) {
        return doseRatePerHour <= 0.0
                ? Double.POSITIVE_INFINITY : RADIATION_S_PER_UNIT_DOSE / doseRatePerHour;
    }

    /** 0 = fully shielded or far from any live conductor, 1 = pressed against a bare one. */
    public static double fieldSeconds(double exposure01) {
        return exposure01 <= 0.0
                ? Double.POSITIVE_INFINITY : FIELD_S_AT_FULL_EXPOSURE / exposure01;
    }

    /** True vacuum: nothing to collide with, no ceiling on this term at all. */
    public static double gasSeconds(double pressureKPa) {
        return pressureKPa <= 0.0 ? Double.POSITIVE_INFINITY : GAS_KPA_S / pressureKPa;
    }

    /** design/astra-incognita.md §5: "while a player is inside the shield... there is no
     * upgrade. You leave." A hard clamp, not a curve — presence is binary. */
    public static double observerSeconds(boolean observerPresent) {
        return observerPresent ? OBSERVER_PRESENT_S : Double.POSITIVE_INFINITY;
    }

    /** Every term's τ, keyed by cause — what the six-bar survey meter draws (§8.2). */
    public static Map<Term, Double> breakdown(double temperatureK, double soundLevelDb,
                                              double doseRatePerHour, double fieldExposure01,
                                              double pressureKPa, boolean observerPresent) {
        Map<Term, Double> seconds = new EnumMap<>(Term.class);
        seconds.put(Term.THERMAL, thermalSeconds(temperatureK));
        seconds.put(Term.VIBRATION, vibrationSeconds(soundLevelDb));
        seconds.put(Term.RADIATION, radiationSeconds(doseRatePerHour));
        seconds.put(Term.FIELD, fieldSeconds(fieldExposure01));
        seconds.put(Term.GAS, gasSeconds(pressureKPa));
        seconds.put(Term.OBSERVER, observerSeconds(observerPresent));
        return seconds;
    }

    /** The combined τ: rates add, so whichever term's rate is largest (τ is smallest) wins. */
    public static double totalSeconds(Map<Term, Double> breakdown) {
        double rate = 0.0;
        for (double seconds : breakdown.values()) {
            rate += 1.0 / seconds; // 1 / Infinity is 0.0 in IEEE 754 - no term needs a special case
        }
        return rate <= 0.0 ? Double.POSITIVE_INFINITY : 1.0 / rate;
    }

    /** Every term, shortest τ (worst, most worth fixing) first — the Pareto order §8.2 asks for. */
    public static List<Term> worstFirst(Map<Term, Double> breakdown) {
        return breakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * A τ value as a player reads it — the one authority, so the debug command, the meter's chat
     * report and its HUD never disagree about what "1.0 us" versus "1.0 ms" means.
     */
    public static String formatSeconds(double seconds) {
        if (Double.isInfinite(seconds)) {
            return "no limit";
        }
        if (seconds < 1.0e-3) {
            return String.format(Locale.ROOT, "%.1f us", seconds * 1.0e6);
        }
        if (seconds < 1.0) {
            return String.format(Locale.ROOT, "%.1f ms", seconds * 1000.0);
        }
        return String.format(Locale.ROOT, "%.2f s", seconds);
    }

    private Coherence() {}
}
