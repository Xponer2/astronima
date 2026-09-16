package play.xponer.astronima.sim.cryo;

/**
 * A Boiling Liquid Expanding Vapor Explosion (design/cryogenics.md §1.4) — real, published
 * industrial hazard-analysis arithmetic, not invented for this mod. A vessel holding liquid
 * above its normal (1 atm) boiling point ruptures; pressure collapses to ambient instantly; the
 * liquid is now superheated relative to the *new*, lower boiling point, and the only way it can
 * shed that excess heat is to flash some of itself to vapor immediately.
 */
public final class Bleve {

    /** The gas constant, J/(mol*K). */
    private static final double R = 8.314;

    /** One standard atmosphere, kPa. */
    public static final double ATM_KPA = 101.325;

    /** Real energy density of TNT, J/kg — the standard conversion for blast-energy comparisons. */
    public static final double TNT_ENERGY_J_PER_KG = 4.184e6;

    /** The largest in-world explosion power this model will ever produce — dangerous, never
     * world-ending, regardless of how much liquid a tank theoretically holds. */
    public static final float MAX_EXPLOSION_POWER = 12.0f;

    /**
     * The saturation temperature of {@code cryogen} at {@code pressureKPa}, via the
     * Clausius-Clapeyron relation: {@code T = 1 / (1/Tb - (R/L) * ln(P/P_atm))}. Clamped at the
     * critical temperature: near and above the critical point the constant-latent-heat
     * assumption behind Clausius-Clapeyron breaks down and the raw formula can overshoot past
     * where a liquid/vapor boundary exists at all, which {@link CryoVessel#belowEveryCriticalPressure()}
     * exists specifically to keep this model clear of in practice.
     */
    public static double saturationTemperatureK(Cryogen cryogen, double pressureKPa) {
        double invTb = 1.0 / cryogen.boilingPointK();
        double term = (R / cryogen.latentHeatVaporizationJPerMol())
                * Math.log(pressureKPa / ATM_KPA);
        double denom = invTb - term;
        if (denom <= 0.0) {
            return cryogen.criticalTemperatureK();
        }
        double tSat = 1.0 / denom;
        return Math.min(tSat, cryogen.criticalTemperatureK());
    }

    /** How far above its own 1-atm boiling point the liquid's saturation temperature has climbed
     * at {@code pressureKPa} — zero at or below atmospheric pressure. */
    public static double superheatK(Cryogen cryogen, double pressureKPa) {
        return Math.max(0.0, saturationTemperatureK(cryogen, pressureKPa) - cryogen.boilingPointK());
    }

    /**
     * The fraction of the liquid that flashes to vapor when a vessel at {@code pressureKPa}
     * ruptures to atmospheric — {@code Cp_liquid * superheat / L}, the standard industrial
     * flash-fraction formula.
     */
    public static double flashFraction(Cryogen cryogen, double pressureKPa) {
        double superheat = superheatK(cryogen, pressureKPa);
        double fraction = cryogen.liquidMolarHeatCapacityJPerMolK() * superheat
                / cryogen.latentHeatVaporizationJPerMol();
        return Math.clamp(fraction, 0.0, 1.0);
    }

    /** Energy released by the flashed fraction of {@code liquidMoles} at rupture, joules. Zero
     * liquid means zero energy regardless of how red the gauge reads — a vented-empty tank does
     * not explode just because its gauge shows burst. */
    public static double explosionEnergyJ(Cryogen cryogen, double pressureKPa, double liquidMoles) {
        if (liquidMoles <= 0.0) {
            return 0.0;
        }
        return liquidMoles * flashFraction(cryogen, pressureKPa)
                * cryogen.latentHeatVaporizationJPerMol();
    }

    /** {@code explosionEnergyJ} expressed as an equivalent mass of TNT, kg. */
    public static double tntEquivalentKg(double explosionEnergyJ) {
        return explosionEnergyJ / TNT_ENERGY_J_PER_KG;
    }

    /**
     * The in-world explosion power for {@code explosionEnergyJ} — a real blast follows a
     * cube-root energy-to-radius scaling law, capped at {@link #MAX_EXPLOSION_POWER} so a full
     * tank cannot exceed a bounded, tested maximum. Vanilla TNT is power 4.0; this scales from
     * that same reference point.
     */
    public static float explosionPower(double explosionEnergyJ) {
        double tntKg = tntEquivalentKg(explosionEnergyJ);
        if (tntKg <= 0.0) {
            return 0.0f;
        }
        float power = (float) (4.0 * Math.cbrt(tntKg));
        return Math.min(power, MAX_EXPLOSION_POWER);
    }

    private Bleve() {}
}
