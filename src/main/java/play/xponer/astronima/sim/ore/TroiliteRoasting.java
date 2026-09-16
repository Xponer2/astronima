package play.xponer.astronima.sim.ore;

import play.xponer.astronima.sim.chem.Formula;

/**
 * Roasting troilite: {@code 4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2} — real, balanced, and the answer to
 * {@code Mineral.TROILITE}'s own doc comment: <em>"the sulfur has to go somewhere."</em> It goes
 * into the room as sulfur dioxide, real and dangerous, and the iron comes out as hematite — the
 * same oxide {@code ModBlocks.HEMATITE_ORE} already smelts, so roasted troilite needs no new
 * downstream item at all.
 *
 * <p><strong>Real oxygen cost, not decoration.</strong> Dead roasting genuinely consumes oxygen —
 * scarce, valuable, breathable oxygen — which is the trade this machine is actually about: is
 * troilite's iron and the sulfuric-acid feedstock worth the O2 it costs to free them?
 *
 * <p>Minecraft-free (rule 1).
 */
public final class TroiliteRoasting {

    /** Per mole of FeS roasted, from the balanced equation's own 4:7:2:4 ratio. */
    public static final double O2_PER_MOL_FES = 7.0 / 4.0;
    public static final double FE2O3_PER_MOL_FES = 2.0 / 4.0;
    public static final double SO2_PER_MOL_FES = 1.0;

    /** What one roast of a charge yields. */
    public record Step(double o2ConsumedMol, double fe2O3Mol, double so2Mol) {}

    /** Roasts the troilite in {@code body}; every other mineral in it is untouched. */
    public static Step roast(OreBody body) {
        double troiliteGrams = body.massOf(Mineral.TROILITE);
        if (troiliteGrams <= 0) {
            return new Step(0, 0, 0);
        }
        double troiliteMolarMass = Formula.parse(Mineral.TROILITE.formula()).molarMass();
        double troiliteMol = troiliteGrams / troiliteMolarMass;
        return new Step(troiliteMol * O2_PER_MOL_FES, troiliteMol * FE2O3_PER_MOL_FES,
                troiliteMol * SO2_PER_MOL_FES);
    }

    private TroiliteRoasting() {}
}
