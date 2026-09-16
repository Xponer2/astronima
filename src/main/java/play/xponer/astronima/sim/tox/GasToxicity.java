package play.xponer.astronima.sim.tox;

import play.xponer.astronima.sim.Gas;

/**
 * Physiological effects of the toxic species, beyond simple asphyxiation.
 *
 * <p>Two mechanisms, both real: CO binds hemoglobin and accumulates as a body burden
 * that clears slowly (carboxyhemoglobin), while H2S/SO2/NH3 act acutely — their harm
 * tracks the current partial pressure and stops when exposure stops. H2S has the
 * famous property of being smellable far below harmful levels (and of deadening the
 * nose above them — the trace warning is a gift, not a guarantee).
 */
public final class GasToxicity {
    /** Dose gained per second per kPa of ppCO (≈4 min to saturate at 1 kPa). */
    public static final double CO_UPTAKE_PER_KPA_S = 0.004;
    /** Dose cleared per second breathing clean air (≈20 min from saturation). */
    public static final double CO_CLEARANCE_PER_S = 0.0008;

    /** Advances the 0..1 carboxyhemoglobin proxy dose by {@code dt} seconds. */
    public static double stepCoDose(double dose, double ppCoKPa, double dt) {
        double next = dose + (ppCoKPa * CO_UPTAKE_PER_KPA_S - CO_CLEARANCE_PER_S) * dt;
        return Math.max(0.0, Math.min(1.0, next));
    }

    public enum CoStatus {
        NONE, MILD, SEVERE, CRITICAL;

        public static CoStatus classify(double dose) {
            if (dose < 0.15) {
                return NONE;
            }
            if (dose < 0.5) {
                return MILD;
            }
            if (dose < 0.85) {
                return SEVERE;
            }
            return CRITICAL;
        }
    }

    /** Acute (exposure-tracking) severity for the irritant/toxic gases. */
    public enum Severity {
        NONE, TRACE_WARNING, IRRITATION, DAMAGE, SEVERE
    }

    /**
     * Acute effect of {@code gas} at partial pressure {@code ppKPa}; {@link Severity#NONE}
     * for species handled elsewhere (CO dose model, plain asphyxiants).
     */
    public static Severity acute(Gas gas, double ppKPa) {
        return switch (gas) {
            case HYDROGEN_SULFIDE -> thresholds(ppKPa, 0.002, 0.02, 0.1, 0.5);
            case SULFUR_DIOXIDE -> thresholds(ppKPa, Double.MAX_VALUE, 0.05, 0.2, 0.4);
            case AMMONIA -> thresholds(ppKPa, Double.MAX_VALUE, 0.1, 0.5, 1.0);
            // Chlorine is the worst of them by an order of magnitude: it is detectable by
            // smell around 0.3 ppm, irritating within a few, and lethal in minutes at 100 —
            // roughly 0.01 kPa. A chlorate bed cooked too hard puts that into the room the
            // player is standing in, which is why the retort's window has to be findable.
            case CHLORINE -> thresholds(ppKPa, 0.00003, 0.0003, 0.003, 0.01);
            // Worse than chlorine by another order of magnitude. The occupational limit is
            // 0.001 ppm - a thousandth of chlorine's - and the lethal exposure is measured in
            // single-figure ppm over half an hour. It is also the only gas here whose harm is
            // *delayed*, which is exactly why the instrument matters more than the symptom.
            case NICKEL_CARBONYL -> thresholds(ppKPa, 1e-7, 1e-6, 1e-5, 1e-4);
            default -> Severity.NONE;
        };
    }

    private static Severity thresholds(double pp, double trace, double irritation, double damage, double severe) {
        if (pp >= severe) {
            return Severity.SEVERE;
        }
        if (pp >= damage) {
            return Severity.DAMAGE;
        }
        if (pp >= irritation) {
            return Severity.IRRITATION;
        }
        if (pp >= trace) {
            return Severity.TRACE_WARNING;
        }
        return Severity.NONE;
    }

    private GasToxicity() {}
}
