package play.xponer.astronima.sim.ore;

/**
 * {@code MgCO3 -> MgO + CO2} — real magnesite decomposition, standing in for the whole real
 * (Mg,Fe)CO3 breunnerite range the same way every other solid-solution mineral in {@link Mineral}
 * already does silently. See {@code design/carbonate-calcination.md}. Minecraft-free (rule 1).
 */
public final class Calcination {

    /** Real molar masses, g/mol — MgO + CO2 balances MgCO3 exactly. */
    public static final double CARBONATE_MOLAR_MASS = 84.31;
    public static final double OXIDE_MOLAR_MASS = 40.30;
    public static final double CO2_MOLAR_MASS = 44.01;

    /** Real molar heat of calcination, endothermic — quoted, not yet consumed by a machine. */
    public static final double ENTHALPY_J_PER_MOL = 117_000.0;

    /**
     * Where real magnesite calcination begins, K — about 900 °C.
     *
     * <p>The retort's own mirror tops out at 1287 K (900 suns of concentration on full
     * daylight — {@code SolarConcentrator.MAX_CONCENTRATION}), so this process sits right at
     * the edge of what the rig can reach at all rather than comfortably inside its range the
     * way dehydroxylation and chlorate both do. That is real: a modest solar concentrator
     * genuinely struggles to reach industrial calcination temperatures, and the narrow margin
     * below is the honest consequence rather than an invented hazard.
     */
    public static final double ONSET_K = 1173.15;

    /** Fully decomposing, K — about 977 °C, comfortably inside the real 900-1000 °C range. */
    public static final double COMPLETE_K = 1250.15;

    /**
     * Past this the charge decrepitates, K — real, violent fracturing from internal CO2
     * pressure building faster than it can diffuse out, well documented in real lime/magnesia
     * kilns run too hard. Only 30 K above {@link #COMPLETE_K}: with the mirror's own hard
     * ceiling at 1287.15 K only 37 K past that, this is deliberately the tightest margin of
     * any retort process — full sun and near-full focus is already most of the way there.
     */
    public static final double DECREPITATION_K = 1280.15;

    /** Fraction of the batch lost to scattering once decrepitation starts — real fines loss
     *  from a kiln run too hot, not a clean stoichiometric side reaction like chlorine is. */
    public static final double DECREPITATION_LOSS = 0.35;

    /** CO2 grams recoverable from a mass of carbonate, real stoichiometry. */
    public static double co2GramsFrom(double carbonateGrams) {
        if (carbonateGrams <= 0) {
            return 0.0;
        }
        return carbonateGrams * (CO2_MOLAR_MASS / CARBONATE_MOLAR_MASS);
    }

    /** The oxide left behind, real stoichiometry — mass balance's other half. */
    public static double oxideGramsFrom(double carbonateGrams) {
        if (carbonateGrams <= 0) {
            return 0.0;
        }
        return carbonateGrams * (OXIDE_MOLAR_MASS / CARBONATE_MOLAR_MASS);
    }

    /** How much of the charge decomposes at this temperature, 0..1 — zero below onset, then
     *  rising linearly to complete, the same shape every other retort process uses. */
    public static double conversion(double temperatureK) {
        if (temperatureK < ONSET_K) {
            return 0;
        }
        if (temperatureK >= COMPLETE_K) {
            return 1;
        }
        return (temperatureK - ONSET_K) / (COMPLETE_K - ONSET_K);
    }

    /** What one charge gave up. */
    public record Bake(double mgOGrams, double co2Grams, boolean decrepitated) {
        /** CO2, in moles, for a room's gas mixture. */
        public double co2Moles() {
            return co2Grams / CO2_MOLAR_MASS;
        }
    }

    /**
     * Bakes a charge of carbonate-bearing rock.
     *
     * @param carbonateGrams grams of real breunnerite in the charge (already the caller's own
     *                       assumed share of a bulk item, e.g. baked silicate's own carbonate
     *                       fraction — this function only ever sees the real carbonate mass)
     * @param temperatureK   the retort's vessel temperature
     */
    public static Bake bake(double carbonateGrams, double temperatureK) {
        double reacted = carbonateGrams * conversion(temperatureK);
        if (reacted <= 0) {
            return new Bake(0, 0, false);
        }
        boolean decrepitated = temperatureK >= DECREPITATION_K;
        double recovered = decrepitated ? reacted * (1 - DECREPITATION_LOSS) : reacted;
        return new Bake(oxideGramsFrom(recovered), co2GramsFrom(recovered), decrepitated);
    }

    private Calcination() {}
}
