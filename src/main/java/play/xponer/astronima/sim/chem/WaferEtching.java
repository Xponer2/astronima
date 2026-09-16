package play.xponer.astronima.sim.chem;

/**
 * Real wet oxide etching (design/halogens.md §40-41, Part C4): {@code SiO2 + 6 HF -> H2SiF6 +
 * 2 H2O}, the real, industrial basis of wet oxide etching in wafer fabrication, still in use
 * today. Minecraft-free (rule 1).
 *
 * <p>Real wafers grow a native oxide skin (SiO2) on exposure to air; this mod's own
 * {@code wafer_silicon} represents one already carrying it, the same background-chemistry
 * restraint {@code design/halogens.md} §1.3 already takes for sodium's own ambient hazard. No
 * enthalpy is claimed here (unlike {@link FluoriteDigestion}): this design has no confident real
 * formation-enthalpy figure for H2SiF6(aq) to compute one honestly — the real reason this
 * machine draws power is process control (precise, repeatable timing/agitation/temperature), not
 * heat.
 */
public final class WaferEtching {

    /** Real molar mass, g/mol — this class's own new fact (silicon dioxide). HF's own molar mass
     *  is reused directly from {@link FluoriteDigestion#HF_MOLAR_MASS}, never redeclared (rule 16). */
    public static final double SIO2_MOLAR_MASS = 60.08;

    /** Real stoichiometry: moles of HF one mole of SiO2 (a wafer) needs. */
    public static final double HF_PER_WAFER = 6.0;

    /** Real stoichiometry: moles of {@code H2SiF6} (the real byproduct) per mole of SiO2 etched. */
    public static final double FLUOROSILICIC_ACID_PER_WAFER = 1.0;

    /**
     * A real practitioner's free parameter (§9.4-style), not a measured constant: how certified a
     * room's own tracked cleanliness (design/halogens.md §30-32, Part C3) must read before real
     * photolithography is safe to run in it. Deliberately demanding — C3's own controller was
     * sized so reaching it takes real, uninterrupted time.
     */
    public static final double ETCH_MIN_CLEANLINESS = 0.9;

    /** Real 1:6 stoichiometry: moles of HF a charge of {@code sio2Mol} needs. */
    public static double hfMolRequired(double sio2Mol) {
        return Math.max(sio2Mol, 0.0) * HF_PER_WAFER;
    }

    /** Real stoichiometric etched-die yield from a mole quantity of SiO2 (wafer) etched. */
    public static double dieMolFrom(double sio2Mol) {
        return Math.max(sio2Mol, 0.0);
    }

    /** Real stoichiometric fluorosilicic-acid yield from a mole quantity of SiO2 etched. */
    public static double fluorosilicicAcidMolFrom(double sio2Mol) {
        return sio2Mol <= 0 ? 0.0 : sio2Mol * FLUOROSILICIC_ACID_PER_WAFER;
    }

    /** True once a room's own certified cleanliness clears the real gate real photolithography
     *  needs (§41). */
    public static boolean readyRoom(double roomCleanliness) {
        return roomCleanliness >= ETCH_MIN_CLEANLINESS;
    }

    private WaferEtching() {}
}
