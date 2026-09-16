package play.xponer.astronima.sim.organic;

import play.xponer.astronima.sim.Gas;

/**
 * Thermal cracking of a hydrocarbon charge: split the long chain into a shorter alkene and
 * lighter gas.
 *
 * <p>The reaction is real — cracking a long-chain hydrocarbon really does yield a shorter alkene
 * plus lighter gases. What is not real is giving {@code Mineral.THOLIN} ("kerogen") an exact
 * stoichiometric equation: kerogen is a class of organic macromolecules with no fixed formula
 * ({@code Formula.tryParse("kerogen")} is empty by design, see {@code FormulaTest}), and inventing
 * one would be exactly the dishonesty rule 14 exists to refuse. So this is an
 * <strong>engineering yield split</strong>, named as such in {@code design/petrochemicals.md} §2:
 * a charge converts entirely to gas, most of it ethylene and the rest methane, in a fixed mass
 * ratio. No solid residue is tracked.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Cracking {

    /** Share of a cracked charge's mass that comes off as ethylene, the rest as methane. */
    public static final double ETHYLENE_MASS_FRACTION = 0.65;

    public static final double METHANE_MASS_FRACTION = 1.0 - ETHYLENE_MASS_FRACTION;

    /** One item of feed, in grams — sized so a batch yields a legible amount of gas. */
    public static final double CHARGE_GRAMS = 200.0;

    /** What a charge of feedstock cracks into. */
    public record Yield(double ethyleneMol, double methaneMol) {}

    /** Cracks {@code feedGrams} of tholin/sludge into its ethylene and methane yield. */
    public static Yield crack(double feedGrams) {
        if (feedGrams <= 0) {
            return new Yield(0, 0);
        }
        double ethyleneGrams = feedGrams * ETHYLENE_MASS_FRACTION;
        double methaneGrams = feedGrams * METHANE_MASS_FRACTION;
        double ethyleneMol = ethyleneGrams / (Gas.ETHYLENE.molarMassKgPerMol() * 1000.0);
        double methaneMol = methaneGrams / (Gas.METHANE.molarMassKgPerMol() * 1000.0);
        return new Yield(ethyleneMol, methaneMol);
    }

    private Cracking() {}
}
