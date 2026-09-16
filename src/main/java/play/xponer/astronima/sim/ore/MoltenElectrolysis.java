package play.xponer.astronima.sim.ore;

import java.util.Map;

/**
 * A molten-oxide electrolysis cell, tuned to one metal at a time.
 *
 * <p>Real ISRU chemistry (Sirona Technologies / MSFC molten regolith electrolysis
 * research): {@code design/electrolysis.md} §1 has the numbers. What a cell reaches is
 * set entirely by which {@link ElectrolysisSpecies} its installed electrode targets — a
 * crafted, swappable component the block entity reads, never a runtime dial and never a
 * function of how much power happens to be arriving (`design/power.md` §4's rule, which
 * an earlier draft of this design broke and which this class structurally cannot: it has
 * no watts parameter at all).
 *
 * <p>Minecraft-free (rule 1): an {@link OreBody} and a target in, moles out.
 */
public final class MoltenElectrolysis {

    /** Molar mass of O2, g/mol. */
    public static final double M_O2 = 32.0;

    /**
     * Grams of a target's own oxide per mole of metal it yields — derived, not stored,
     * from the metal's molar mass and its own oxygen stoichiometry
     * ({@code FeO}: 55.845 + 0.5×32 = 71.845 g/mol, the real figure; checked the same way
     * for {@code SiO2} and {@code Al2O3} in {@code MoltenElectrolysisTest}).
     */
    public static double oxideMolarMassGPerMetalMol(ElectrolysisSpecies species) {
        return species.molarMassGPerMol() + species.o2PerMetalMol() * M_O2;
    }

    /** Total mass of every {@link Mineral.Tier#ELECTROLYTIC}-tagged mineral in a body. */
    public static double electrolyticMassGrams(OreBody body) {
        double sum = 0;
        for (Map.Entry<Mineral, Double> entry : body.masses().entrySet()) {
            if (entry.getKey().tier() == Mineral.Tier.ELECTROLYTIC) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    /**
     * A cell's own charge: how much of the target's own oxide is left to convert, and
     * the residue mass that never will be, regardless of how long the batch runs.
     *
     * @param target                which metal this charge is tuned for
     * @param remainingOxideGrams   oxide mass not yet converted
     * @param residueGrams          the rest of the body — fixed at load time, never converts
     */
    public record Charge(ElectrolysisSpecies target, double remainingOxideGrams,
                         double residueGrams) {

        /**
         * Loads a fresh charge from an ore body: the target's own share of whatever
         * {@link Mineral.Tier#ELECTROLYTIC} mass is present converts; everything else —
         * the non-electrolytic mass and the electrolytic mass belonging to species this
         * electrode is not tuned for — is residue from the first tick, per
         * {@code design/electrolysis.md} §3's "one target, not a mixture" simplification.
         */
        public static Charge of(OreBody body, ElectrolysisSpecies target) {
            double oxide = electrolyticMassGrams(body) * target.yieldFraction();
            double residue = Math.max(body.totalMass() - oxide, 0);
            return new Charge(target, oxide, residue);
        }

        public static Charge empty(ElectrolysisSpecies target) {
            return new Charge(target, 0, 0);
        }

        public boolean isSpent() {
            return remainingOxideGrams <= 1e-9;
        }
    }

    /**
     * One step of a charge converting at a given rate.
     *
     * @param charge the cell's current charge
     * @param rate   fraction of the remaining oxide this step converts, 0..1 — the same
     *               "possible movement per tick" shape every sibling machine's step function
     *               uses; the block entity supplies it from {@code work}/{@code workRequired}
     *               the same inherited way it draws power, so more watts means more ticks of
     *               real progress per second and never a different rate curve (§4's rule again)
     */
    public static Step step(Charge charge, double rate) {
        double r = Math.clamp(rate, 0, 1);
        double convertedOxide = Math.max(charge.remainingOxideGrams(), 0) * r;
        double oxideMolarMass = oxideMolarMassGPerMetalMol(charge.target());
        double metalMol = oxideMolarMass <= 0 ? 0 : convertedOxide / oxideMolarMass;
        double o2Mol = metalMol * charge.target().o2PerMetalMol();

        Charge next = new Charge(charge.target(),
                charge.remainingOxideGrams() - convertedOxide, charge.residueGrams());
        return new Step(metalMol, o2Mol, next);
    }

    /**
     * What one step produced.
     *
     * @param metalMol moles of the target metal freed this step
     * @param o2Mol    moles of oxygen released this step — goes straight into the receiving
     *                 room, the same {@code room.addGasAt} shape the retort already established
     * @param charge   the cell after this step
     */
    public record Step(double metalMol, double o2Mol, Charge charge) {}

    private MoltenElectrolysis() {}
}
