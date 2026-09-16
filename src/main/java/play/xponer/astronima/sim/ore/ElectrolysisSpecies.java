package play.xponer.astronima.sim.ore;

/**
 * The metals a molten-oxide electrolysis cell can be tuned for, in the order their real
 * decomposition voltages actually rank.
 *
 * <p>Iron(II) oxide has by far the lowest decomposition voltage of the oxides an asteroid
 * regolith carries, which is why every published MOE study uses it as the reference case
 * and why it needs no crafted electrode at all here — see {@code design/electrolysis.md}
 * §1. Silicon dioxide needs roughly double that voltage; aluminium oxide harder again.
 * {@link #relativeVoltage} exists only to justify that ordering and the electrode crafting
 * cost curve it drives (`design/electrolysis.md` §5) — nothing in {@link MoltenElectrolysis}
 * compares a voltage at runtime, because nothing in this cell chooses one any more: which
 * species a cell reaches is set by which electrode is installed, a crafted and swappable
 * component, never by how much power happens to be arriving (`design/power.md` §4:
 * <em>"powered crushers should be faster, not better — the physics must not change with
 * the energy source"</em>).
 *
 * <p>{@link #yieldFraction} is a deliberate simplification, named in
 * {@code design/electrolysis.md} §7: rather than tracking which specific mineral in an
 * {@link OreBody} carries which oxide, every {@link Mineral.Tier#ELECTROLYTIC} mineral's
 * mass is pooled, and each species recovers a fixed real-ish share of that pool. A player
 * cannot perceive the difference between that and five separately-modelled minerals, only
 * the ordering and the relative scarcity, both of which this preserves.
 */
public enum ElectrolysisSpecies {
    /** {@code FeO -> Fe + 1/2 O2}. No electrode needed — the cell's default target. */
    IRON(55.845, 0.5, 1.0, 0.55),
    /** {@code SiO2 -> Si + O2}. Needs a {@code silicon_electrode}. */
    SILICON(28.085, 1.0, 2.0, 0.18),
    /** {@code Al2O3 -> 2 Al + 3/2 O2}. Needs an {@code aluminum_electrode}; the rarest and
     * hardest target this cell reaches. */
    ALUMINUM(26.982, 0.75, 3.2, 0.02);

    private final double molarMassGPerMol;
    private final double o2PerMetalMol;
    private final double relativeVoltage;
    private final double yieldFraction;

    ElectrolysisSpecies(double molarMassGPerMol, double o2PerMetalMol,
                        double relativeVoltage, double yieldFraction) {
        this.molarMassGPerMol = molarMassGPerMol;
        this.o2PerMetalMol = o2PerMetalMol;
        this.relativeVoltage = relativeVoltage;
        this.yieldFraction = yieldFraction;
    }

    /** Grams per mole of the metal itself — real periodic-table figures. */
    public double molarMassGPerMol() {
        return molarMassGPerMol;
    }

    /** Moles of O2 released per mole of metal, from this species' own oxide stoichiometry. */
    public double o2PerMetalMol() {
        return o2PerMetalMol;
    }

    /** Decomposition voltage relative to iron's own baseline — ordering only, see class doc. */
    public double relativeVoltage() {
        return relativeVoltage;
    }

    /**
     * Share of a body's total {@link Mineral.Tier#ELECTROLYTIC}-tagged mass this species
     * can recover, at full conversion. Iron dominates a typical chondrite's electrolytic
     * mass (magnetite plus the two silicates it shares gangue with); silicon and aluminium
     * are minor shares of the same pool, matching real chondritic abundances loosely rather
     * than exactly, per the class doc's named simplification.
     */
    public double yieldFraction() {
        return yieldFraction;
    }
}
