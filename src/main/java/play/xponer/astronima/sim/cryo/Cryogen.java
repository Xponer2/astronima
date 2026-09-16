package play.xponer.astronima.sim.cryo;

import play.xponer.astronima.sim.Gas;

/**
 * The three gases this mod can store liquefied (design/cryogenics.md §1), and the real,
 * published thermodynamic constants that make liquefaction, boil-off and BLEVE all
 * checkable arithmetic rather than tuned numbers.
 *
 * <p>Every constant here is a real value for the pure substance at one standard atmosphere
 * unless stated otherwise. Liquid specific heat and vapor specific heat are both treated as
 * constant across the (narrow, cryogenic) temperature ranges this mod's machines operate in —
 * the same ideal-gas-adjacent simplification {@link play.xponer.astronima.sim.GasMixture}
 * already makes for the whole atmosphere model.
 */
public enum Cryogen {

    /** Liquid nitrogen. Tb 77.36 K, L 5560 J/mol (199.19 kJ/kg), critical point 126.19 K / 3395.8 kPa. */
    LN2(Gas.NITROGEN, 77.36, 5560.0, 807.0, 57.18, 29.12, 126.19, 3395.8),

    /** Liquid oxygen. Tb 90.19 K, L 6819 J/mol (213.1 kJ/kg), critical point 154.58 K / 5043 kPa. */
    LOX(Gas.OXYGEN, 90.19, 6819.0, 1141.0, 54.37, 29.38, 154.58, 5043.0),

    /** Liquid (normal) hydrogen. Tb 20.28 K, L 904 J/mol (452 kJ/kg), critical point 33.15 K / 1296.4 kPa. */
    LH2(Gas.HYDROGEN, 20.28, 904.0, 70.85, 19.6, 28.82, 33.15, 1296.4);

    private final Gas gas;
    private final double boilingPointK;
    private final double latentHeatVaporizationJPerMol;
    private final double liquidDensityKgPerM3;
    private final double liquidMolarHeatCapacityJPerMolK;
    private final double gasMolarHeatCapacityJPerMolK;
    private final double criticalTemperatureK;
    private final double criticalPressureKPa;

    Cryogen(Gas gas, double boilingPointK, double latentHeatVaporizationJPerMol,
            double liquidDensityKgPerM3, double liquidMolarHeatCapacityJPerMolK,
            double gasMolarHeatCapacityJPerMolK, double criticalTemperatureK,
            double criticalPressureKPa) {
        this.gas = gas;
        this.boilingPointK = boilingPointK;
        this.latentHeatVaporizationJPerMol = latentHeatVaporizationJPerMol;
        this.liquidDensityKgPerM3 = liquidDensityKgPerM3;
        this.liquidMolarHeatCapacityJPerMolK = liquidMolarHeatCapacityJPerMolK;
        this.gasMolarHeatCapacityJPerMolK = gasMolarHeatCapacityJPerMolK;
        this.criticalTemperatureK = criticalTemperatureK;
        this.criticalPressureKPa = criticalPressureKPa;
    }

    /** The room/network gas species this liquid condenses from and boils back into. */
    public Gas gas() {
        return gas;
    }

    /** Normal boiling point at one atmosphere, in kelvin. */
    public double boilingPointK() {
        return boilingPointK;
    }

    /** Latent heat of vaporization at the normal boiling point, J/mol. */
    public double latentHeatVaporizationJPerMol() {
        return latentHeatVaporizationJPerMol;
    }

    /** Liquid density at the normal boiling point, kg/m^3. */
    public double liquidDensityKgPerM3() {
        return liquidDensityKgPerM3;
    }

    /** Liquid specific heat, J/(mol*K) — how much energy raises the *liquid's* temperature
     * once it is superheated above its 1-atm boiling point (the BLEVE flash-fraction input). */
    public double liquidMolarHeatCapacityJPerMolK() {
        return liquidMolarHeatCapacityJPerMolK;
    }

    /** Vapor specific heat at constant pressure, J/(mol*K) — the sensible-heat cost of cooling
     * room-temperature gas down to the boiling point before it can condense at all. */
    public double gasMolarHeatCapacityJPerMolK() {
        return gasMolarHeatCapacityJPerMolK;
    }

    /** Critical temperature, K — above this no liquid/vapor boundary exists at any pressure. */
    public double criticalTemperatureK() {
        return criticalTemperatureK;
    }

    /** Critical pressure, kPa (absolute) — above this no liquid/vapor boundary exists at any
     * temperature. {@link CryoVessel#BURST_KPA} must stay well under every one of these, or the
     * BLEVE model's "there is a liquid to flash" premise is physically false. */
    public double criticalPressureKPa() {
        return criticalPressureKPa;
    }
}
