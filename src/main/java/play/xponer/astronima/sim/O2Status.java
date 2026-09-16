package play.xponer.astronima.sim;

import play.xponer.astronima.sim.suit.SuitCondition;

/**
 * Physiological effect of the oxygen partial pressure a person is breathing.
 *
 * <p>Thresholds follow real physiology: sea-level ppO2 is ~21 kPa; hypoxia symptoms
 * begin below ~16 kPa; unconsciousness threatens below ~10 kPa; ppO2 above ~30 kPa
 * risks oxygen toxicity on continuous exposure. What matters is partial pressure,
 * not percentage — 100 % O2 at low total pressure is fine (Apollo flew it).
 *
 * <p>The 16 kPa row is not a coincidence with {@link SuitCondition#SEAL_BELOW_PPO2_KPA} —
 * it is the same physiological fact ("below this, a person's own lungs are no longer
 * enough") read from two directions: this classifies a room's air as already
 * symptomatic, that decides when a suit must take over instead of it. A third
 * independent literal here would have been exactly the drift rule 88/90 already found
 * twice, so this reads the shared constant rather than restating the number.
 */
public enum O2Status {
    SUFFOCATING,
    SEVERE_HYPOXIA,
    MILD_HYPOXIA,
    NORMAL,
    OXYGEN_TOXICITY;

    public static O2Status classify(double ppO2KPa) {
        if (ppO2KPa < 5) {
            return SUFFOCATING;
        }
        if (ppO2KPa < 10) {
            return SEVERE_HYPOXIA;
        }
        if (ppO2KPa < SuitCondition.SEAL_BELOW_PPO2_KPA) {
            return MILD_HYPOXIA;
        }
        if (ppO2KPa <= 30) {
            return NORMAL;
        }
        return OXYGEN_TOXICITY;
    }
}
