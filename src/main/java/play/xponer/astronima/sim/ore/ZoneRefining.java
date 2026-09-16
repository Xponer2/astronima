package play.xponer.astronima.sim.ore;

/**
 * Zone refining: a molten zone dragged along a rod, rejecting impurities into the melt it is
 * about to leave rather than letting them freeze into the crystal behind it. Minecraft-free
 * (rule 1); see {@code design/halogens.md} §9 for why iron is the impurity this models (this
 * mod's electrolytic silicon shares a melt with iron reduction — {@link ElectrolysisSpecies} —
 * rather than carrying an invented flaw) and why silicon's real dopants (boron, phosphorus) are
 * not modelled here at all: nothing in this mod's own silicon story ever introduces one.
 */
public final class ZoneRefining {

    /**
     * Real segregation coefficient of iron in silicon, {@code k = C_solid / C_liquid} at the
     * freeze front (Trumbore 1960, <em>Solid Solubilities of Impurity Elements in Germanium and
     * Silicon</em>, Bell System Technical Journal — still the standard reference table).
     * Astronomically favourable, which is exactly why zone refining works on metallic
     * contamination in a single pass and does nothing for real dopants like boron
     * ({@code k ≈ 0.8}, not modelled — see class doc).
     */
    public static final double SEGREGATION_COEFFICIENT_IRON = 8.0e-6;

    /**
     * The molten zone's own length, as a fraction of the whole charge — a real, free process
     * parameter (a coil-width/travel-speed tradeoff an operator sets), not a material constant.
     * Real float-zone practice runs roughly 5%-20% of charge length; this design picks the
     * middle of that real range (design/halogens.md §9.4).
     */
    public static final double ZONE_LENGTH_FRACTION = 0.1;

    /**
     * Fraction of a charge that ends up as usable, purified stock — the rest is the final
     * zone-length segment, which no pass ever sweeps and which freezes in place carrying
     * whatever it accumulated (real practice crops and discards it).
     */
    public static final double YIELD_FRACTION = 1.0 - ZONE_LENGTH_FRACTION;

    /**
     * Pfann's own single-pass zone-melting result (W.G. Pfann, <em>Zone Melting</em>, 1958):
     * the impurity concentration remaining at position {@code x} (0..1, along a unit-length rod)
     * once a zone of {@link #ZONE_LENGTH_FRACTION} has swept from {@code x = 0}.
     *
     * @param x position along the rod, {@code 0 <= x <= 1 - ZONE_LENGTH_FRACTION} — beyond that
     *          point is the uncropped tail this class does not model a concentration for
     */
    public static double concentrationRatio(double x) {
        double k = SEGREGATION_COEFFICIENT_IRON;
        return 1.0 - (1.0 - k) * Math.exp(-k * x / ZONE_LENGTH_FRACTION);
    }

    /** Real stoichiometric yield: how many wafer-grade items a full charge of silicon items
     *  produces, real yield fraction floored to a whole item count. */
    public static int wafersPerBatch(int feedItems) {
        if (feedItems <= 0) {
            return 0;
        }
        return (int) Math.floor(feedItems * YIELD_FRACTION);
    }

    private ZoneRefining() {}
}
