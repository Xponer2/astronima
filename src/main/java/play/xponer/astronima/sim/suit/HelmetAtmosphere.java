package play.xponer.astronima.sim.suit;

import play.xponer.astronima.sim.Breathing;

/**
 * The air inside a sealed helmet.
 *
 * <p>A sealed suit is a very small closed volume — roughly 12 litres of free gas
 * around the head and torso — so exhaled carbon dioxide concentrates there far faster
 * than it ever would in a room. This is the Apollo 13 problem in miniature: the tank
 * decides how long you can breathe, but the <em>scrubber</em> decides how long you
 * stay conscious, and without one the suit becomes dangerous long before it runs out
 * of oxygen.
 *
 * <p><strong>This runs at real physiological rates, deliberately.</strong> The
 * gameplay time-compression applied elsewhere exists so that <em>room-scale</em>
 * oxygen depletion fits inside a play session; a helmet is four orders of magnitude
 * smaller and is already dramatic in real time — an unscrubbed one passes the danger
 * threshold in well under a minute. Compressing it too would make a sealed suit
 * lethal in under a second, which is physics misapplied rather than physics.
 *
 * <p>The activity factor below is therefore a <em>physiological</em> multiplier
 * (about 1 at rest, 3 or more working hard), not the gameplay clock.
 */
public final class HelmetAtmosphere {
    /** At rest; the value the suit uses unless the wearer is exerting themselves. */
    public static final double RESTING_ACTIVITY = 1.0;

    /** Hard work — mining, hauling — roughly triples CO2 output. */
    public static final double WORKING_ACTIVITY = 3.0;

    /** Free gas volume of a suit, m³ — about 12 litres around the head and chest. */
    public static final double VOLUME_M3 = 0.012;

    /** Suit regulators run below cabin pressure; NASA's EMU sits near 30 kPa. */
    public static final double SUIT_PRESSURE_KPA = 30.0;

    /**
     * CO2 partial pressure at which a sealed helmet becomes dangerous. Matches the
     * room thresholds in {@code Co2Status}, because it is the same physiology.
     */
    public static final double DANGEROUS_PPCO2_KPA = 2.0;

    /**
     * Carbon dioxide added to the helmet by one second of breathing, in kPa.
     *
     * <p>Derived rather than tuned: the wearer produces
     * {@code O2 consumption × respiratory quotient} moles per second, and the ideal
     * gas law converts that to a pressure rise in this small volume.
     */
    public static double co2RiseKPaPerSecond(double activityFactor, double temperatureK) {
        double molesPerSecond = Breathing.O2_CONSUMPTION_MOL_PER_S * activityFactor
                * Breathing.RESPIRATORY_QUOTIENT;
        return molesPerSecond * play.xponer.astronima.sim.GasMixture.R * temperatureK
                / VOLUME_M3 / 1000.0;
    }

    /**
     * Advances helmet CO2 for one step.
     *
     * @param ppCo2KPa        current partial pressure inside the helmet
     * @param scrubberFraction how much of the exhaled CO2 the fitted cartridge removes,
     *                         0 for no cartridge and 1 for a fresh one
     * @param activityFactor   physiological exertion: ~1 at rest, ~3 working hard
     * @return the new partial pressure
     */
    public static double step(double ppCo2KPa, double scrubberFraction,
                              double activityFactor, double temperatureK, double dtSeconds) {
        double produced = co2RiseKPaPerSecond(activityFactor, temperatureK) * dtSeconds;
        double removed = produced * Math.clamp(scrubberFraction, 0.0, 1.0);
        return Math.max(0.0, ppCo2KPa + produced - removed);
    }

    /** Venting the helmet — opening the visor in breathable air — clears it at once. */
    public static double vented() {
        return 0.0;
    }

    public static boolean isDangerous(double ppCo2KPa) {
        return ppCo2KPa >= DANGEROUS_PPCO2_KPA;
    }

    private HelmetAtmosphere() {}
}
