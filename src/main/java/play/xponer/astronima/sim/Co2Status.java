package play.xponer.astronima.sim;

/**
 * Physiological effect of the CO2 partial pressure a person is breathing.
 *
 * <p>CO2 is toxic in its own right, independent of how much oxygen is present:
 * ~1 kPa (1 % at sea level) causes discomfort and headaches, ~2 kPa impairs
 * thinking, above ~4 kPa is rapidly incapacitating. In a sealed room CO2 becomes
 * dangerous long before the oxygen runs out — the Apollo 13 problem.
 */
public enum Co2Status {
    NORMAL,
    ELEVATED,
    HIGH,
    TOXIC;

    public static Co2Status classify(double ppCO2KPa) {
        if (ppCO2KPa < 1) {
            return NORMAL;
        }
        if (ppCO2KPa < 2) {
            return ELEVATED;
        }
        if (ppCO2KPa < 4) {
            return HIGH;
        }
        return TOXIC;
    }
}
