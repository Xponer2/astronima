package play.xponer.astronima.sim.chem;

/**
 * The Downs process: molten rock salt, split into sodium metal and chlorine gas.
 *
 * <p>Real, and still how sodium metal is made industrially today: {@code 2 NaCl (l) -> 2 Na (l)
 * + Cl2 (g)}. See {@code design/halogens.md} §1 for the real cell voltage (4.07 V theoretical —
 * the highest of anything this mod produces, since sodium's own electropositivity is exactly what
 * makes it hard to reduce) and the energy-per-kilogram figure it implies.
 *
 * <p>Simpler than {@code MoltenElectrolysis} on purpose: rock salt is a near-pure evaporite
 * mineral, not a silicate assemblage, so there is no {@code OreBody}/{@code Mineral} mixture to
 * read a fraction out of — one substance, one reaction, moles in proportion to what melted.
 * Minecraft-free (rule 1): grams in, moles out.
 */
public final class HaliteElectrolysis {

    /** g/mol, real. */
    public static final double MOLAR_MASS_NACL = 58.44;
    public static final double MOLAR_MASS_NA = 22.99;

    /** 2 mol Na per 2 mol NaCl: one-to-one. */
    private static final double NA_PER_NACL = 1.0;
    /** 1 mol Cl2 per 2 mol NaCl. */
    private static final double CL2_PER_NACL = 0.5;

    /**
     * A cell's own charge: how much halite is left to convert.
     *
     * @param remainingHaliteGrams halite mass not yet electrolyzed
     */
    public record Charge(double remainingHaliteGrams) {
        public static Charge of(double haliteGrams) {
            return new Charge(Math.max(haliteGrams, 0));
        }

        public static Charge empty() {
            return new Charge(0);
        }

        public boolean isSpent() {
            return remainingHaliteGrams <= 1e-9;
        }
    }

    /**
     * One step of a charge converting at a given rate.
     *
     * @param charge the cell's current charge
     * @param rate   fraction of the remaining halite this step converts, 0..1 — the same
     *               "possible progress this tick" shape every sibling {@code step} uses; the
     *               block entity supplies it from {@code work}/{@code workRequired}
     */
    public static Step step(Charge charge, double rate) {
        double r = Math.clamp(rate, 0, 1);
        double convertedGrams = Math.max(charge.remainingHaliteGrams(), 0) * r;
        double haliteMol = convertedGrams / MOLAR_MASS_NACL;
        double naMol = haliteMol * NA_PER_NACL;
        double cl2Mol = haliteMol * CL2_PER_NACL;

        Charge next = new Charge(charge.remainingHaliteGrams() - convertedGrams);
        return new Step(naMol, cl2Mol, next);
    }

    /**
     * What one step produced.
     *
     * @param sodiumMol moles of sodium metal freed this step
     * @param cl2Mol    moles of chlorine gas released this step — goes straight into the
     *                  receiving room, the same {@code room.addGasAt} shape the troilite roaster
     *                  and the electrolysis cell already establish
     * @param charge    the cell after this step
     */
    public record Step(double sodiumMol, double cl2Mol, Charge charge) {}

    private HaliteElectrolysis() {}
}
