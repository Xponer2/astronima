package play.xponer.astronima.sim.metal;

/**
 * Reacting a real flux against real silicate gangue — the "fluxing and slagging" PLAN.md's own
 * v0.33 line names, and the reason a bare furnace recipe was never going to be the honest answer
 * for hematite. See {@code design/iron-smelter.md}.
 *
 * <p>CaO (limestone) is the textbook real flux for a blast furnace; this mod's own real flux is
 * MgO instead, off breunnerite's own calcination ({@code design/carbonate-calcination.md}) — a
 * real, documented alternative in real steelmaking (dolomitic/magnesian flux), chosen because it
 * is what this mod's own ore chemistry actually produces rather than inventing a second carbonate
 * mineral nobody mines.
 *
 * <pre>
 *   MgO + SiO2 -> MgSiO3        (real slag formation, 1:1 molar)
 *   40.30 60.08  100.38 g/mol   (balances)
 * </pre>
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Fluxing {

    /** Real hematite gangue content — representative of real ore grades (5-15%). */
    public static final double GANGUE_FRACTION_OF_HEMATITE = 0.10;

    /** SiO2, standing in for hematite's real silicate gangue — one real acidic oxide, the same
     *  "one real end-member" simplification {@code Calcination} already makes for breunnerite. */
    public static final double GANGUE_MOLAR_MASS = 60.08;

    /** MgO, real molar mass — the same constant {@code Calcination.OXIDE_MOLAR_MASS} already is,
     *  restated here so this class reads standalone. */
    public static final double FLUX_MOLAR_MASS = 40.30;

    /** MgSiO3 (enstatite), real molar mass — 40.30 + 60.08, balances exactly. */
    public static final double SLAG_MOLAR_MASS = 100.38;

    /** Fe2O3 -> 2Fe: real stoichiometry, 2 * 55.85 / 159.69. */
    public static final double IRON_FRACTION_OF_HEMATITE = 0.6995;

    /**
     * What an unfluxed batch still yields, as a fraction of what a fully-fluxed one would.
     *
     * <p>Not zero: real bloomery iron was smelted for millennia before anyone added flux on
     * purpose, just dirtier and at real cost in usable metal — the gangue stayed mixed through
     * the whole batch instead of separating out as slag. Zero here would say flux is an
     * arbitrary lock on the process rather than real chemistry making an already-possible
     * process cleaner.
     */
    public static final double UNFLUXED_IRON_FRACTION = 0.2;

    /** What one smelted charge gave up. */
    public record Batch(double ironGrams, double slagGrams, double fluxRatio) { }

    /**
     * How much of the flux this ore's own gangue actually needs was supplied, 0..1 — clamped,
     * since flux beyond what the gangue needs does nothing further (it does not un-react).
     */
    public static double fluxRatio(double oreGrams, double fluxGrams) {
        double neededFluxGrams = oreGrams * GANGUE_FRACTION_OF_HEMATITE
                * (FLUX_MOLAR_MASS / GANGUE_MOLAR_MASS);
        if (neededFluxGrams <= 0) {
            return 1.0;
        }
        return Math.clamp(Math.max(0, fluxGrams) / neededFluxGrams, 0.0, 1.0);
    }

    /** Smelts a charge of hematite with whatever flux was actually supplied alongside it. */
    public static Batch smelt(double oreGrams, double fluxGrams) {
        if (oreGrams <= 0) {
            return new Batch(0, 0, 0);
        }
        double ratio = fluxRatio(oreGrams, fluxGrams);
        double gangueGrams = oreGrams * GANGUE_FRACTION_OF_HEMATITE;
        double reactedMol = (gangueGrams / GANGUE_MOLAR_MASS) * ratio;
        double slagGrams = reactedMol * SLAG_MOLAR_MASS;
        double ironFraction = UNFLUXED_IRON_FRACTION + (1 - UNFLUXED_IRON_FRACTION) * ratio;
        double ironGrams = oreGrams * IRON_FRACTION_OF_HEMATITE * ironFraction;
        return new Batch(ironGrams, slagGrams, ratio);
    }

    private Fluxing() {}
}
