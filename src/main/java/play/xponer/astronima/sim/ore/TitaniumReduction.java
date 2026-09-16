package play.xponer.astronima.sim.ore;

/**
 * {@code TiO2 -> Ti + O2} by the FFC-Cambridge process — molten CaCl2 stripping oxide ions
 * straight out of a solid titania cathode, rather than melting the oxide the way
 * {@link MoltenElectrolysis} does for iron/silicon/aluminium. Minecraft-free (rule 1); see
 * {@code design/titanium-reduction.md} for why this process was chosen over the (also real)
 * Kroll process, and for what this deliberately does not model.
 */
public final class TitaniumReduction {

    /** Real molar masses, g/mol. */
    public static final double TITANIUM_MOLAR_MASS = 47.867;
    public static final double TITANIA_MOLAR_MASS = 79.866;

    /**
     * The real decomposition potential: {@code delta-G-f(TiO2) / (n * F)}, {@code n = 4}
     * electrons (Ti4+ + 4e- -> Ti), {@code delta-G-f(TiO2, rutile) = 944 700 J/mol} at 298 K.
     * Lower than aluminium's own equivalent (~2.73 V) — titanium's real difficulty is slow
     * solid-state diffusion, not raw voltage, which this constant is not trying to hide.
     */
    public static final double DECOMPOSITION_VOLTS = 944_700.0 / (4.0 * 96_485.0);

    /** A titania item's own declared mass — it has no {@link OreBody}/gram backing the way
     *  crushed ore does, so one is invented here the same way charge-grams already are for
     *  other plain crafted-item feeds (design/titanium-reduction.md §3). */
    public static final double TITANIA_GRAMS_PER_ITEM = 1000.0;

    /** Titanium's own declared mass per item — smaller than titania's, purely a unit choice
     *  (design/titanium-reduction.md §3), so one charge of feed yields a satisfying handful of
     *  metal rather than a fraction that floors to nothing. */
    public static final double TITANIUM_GRAMS_PER_ITEM = 200.0;

    /** How many titanium items one full charge of {@link #TITANIA_GRAMS_PER_ITEM} yields, real
     *  stoichiometry floored to a whole item count. */
    public static int titaniumItemsPerCharge() {
        return (int) Math.floor(titaniumGramsFrom(TITANIA_GRAMS_PER_ITEM) / TITANIUM_GRAMS_PER_ITEM);
    }

    /** Real stoichiometric yield: titanium grams recoverable from a mass of titania, at the
     *  real Ti:TiO2 molar-mass ratio (~59.9%). */
    public static double titaniumGramsFrom(double titaniaGrams) {
        if (titaniaGrams <= 0) {
            return 0.0;
        }
        return titaniaGrams * (TITANIUM_MOLAR_MASS / TITANIA_MOLAR_MASS);
    }

    private TitaniumReduction() {}
}
