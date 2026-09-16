package play.xponer.astronima.sim.chem;

/**
 * The real industrial route to hydrofluoric acid (the "salt cake"/Bertrand process, still how
 * most of the world's HF is made): {@code CaF2 + H2SO4 -> CaSO4 + 2 HF}. Minecraft-free (rule 1);
 * see {@code design/halogens.md} §15 (Part C1) for the real hazard this deliberately does not
 * model yet, and for why HF production needed its own split from the contact-hazard and
 * cleanroom mechanisms rather than shipping all three at once.
 */
public final class FluoriteDigestion {

    /** Real molar masses, g/mol. */
    public static final double CAF2_MOLAR_MASS = 78.075;
    public static final double H2SO4_MOLAR_MASS = 98.079;
    public static final double CASO4_MOLAR_MASS = 136.14;
    public static final double HF_MOLAR_MASS = 20.006;

    /** Real standard formation enthalpies, kJ/mol. */
    private static final double CAF2_FORMATION_KJ = -1228.0;
    private static final double H2SO4_FORMATION_KJ = -814.0;
    private static final double CASO4_FORMATION_KJ = -1434.5;
    private static final double HF_FORMATION_KJ = -273.3;

    /**
     * Net reaction enthalpy per mole of {@code CaF2}: {@code [CaSO4 + 2*HF] - [CaF2 + H2SO4]}.
     * Positive (endothermic) — real plants run this in a heated kiln (~200-250C), not a cold
     * mix, which is why {@code HfDigesterBlockEntity} draws real power rather than reacting for
     * free the moment both reagents are present.
     */
    public static final double ENTHALPY_KJ_PER_MOL_CAF2 =
            (CASO4_FORMATION_KJ + 2.0 * HF_FORMATION_KJ) - (CAF2_FORMATION_KJ + H2SO4_FORMATION_KJ);

    /** Real stoichiometry: moles of {@code HF} per mole of {@code CaF2} digested. */
    public static final double HF_PER_CAF2 = 2.0;

    /** Real stoichiometry: moles of {@code CaSO4} (gypsum) per mole of {@code CaF2} digested. */
    public static final double GYPSUM_PER_CAF2 = 1.0;

    /** Real 1:1 stoichiometry: moles of {@code H2SO4} a charge of {@code caf2Mol} needs. */
    public static double sulfuricAcidMolRequired(double caf2Mol) {
        return Math.max(caf2Mol, 0.0);
    }

    /** Real stoichiometric HF yield from a mole quantity of {@code CaF2} digested. */
    public static double hfMolFrom(double caf2Mol) {
        return caf2Mol <= 0 ? 0.0 : caf2Mol * HF_PER_CAF2;
    }

    /** Real stoichiometric gypsum yield from a mole quantity of {@code CaF2} digested. */
    public static double gypsumMolFrom(double caf2Mol) {
        return caf2Mol <= 0 ? 0.0 : caf2Mol * GYPSUM_PER_CAF2;
    }

    private FluoriteDigestion() {}
}
