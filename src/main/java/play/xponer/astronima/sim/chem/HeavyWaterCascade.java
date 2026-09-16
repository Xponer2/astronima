package play.xponer.astronima.sim.chem;

/**
 * One electrolytic enrichment stage of {@code design/heavy-water.md}'s own cascade — Minecraft-free
 * (rule 1), so the isotope maths this whole phase rests on is checkable without a level, a block
 * entity, or a running game.
 *
 * <p>Electrolysis breaks O-H bonds faster than O-D bonds (the kinetic isotope effect — the real
 * mechanism the 1934 Vemork plant used, and the reason it was a 1943 sabotage target). The water
 * left behind after a batch is electrolysed away is measurably richer in D2O than what went in.
 * That enrichment multiplies the isotope <em>ratio</em>, not the fraction, by a real, declared
 * separation factor — the same shape a centrifuge enrichment cascade uses, applied here to a
 * chemical rather than a mechanical separation.
 */
public final class HeavyWaterCascade {

    /** Natural abundance of D2O in ordinary water, by mole fraction — real, not tuned. */
    public static final double NATURAL_D2O_FRACTION = 1.56e-4;

    /** Historical electrolytic separation factors for water sit in the 5-8 range; this mod
     *  declares one rather than picking a curve that "feels right" (design/heavy-water.md §2). */
    public static final double ELECTROLYTIC_SEPARATION_FACTOR = 6.0;

    /** One water bottle of feed yields this share of a bottle's worth of next-stage water — the
     *  named, flat stand-in for a real McCabe-Thiele stage-efficiency derivation this mod has no
     *  model for (design/heavy-water.md §3). */
    public static final double OUTPUT_FRACTION_PER_STAGE = 0.2;

    /** How many bottles one real batch consumes to advance one bottle's worth of water by one
     *  stage — the fixed per-batch cost {@code HeavyWaterCellBlockEntity} actually runs on.
     *  {@link #bottlesConsumedFor} answers a different question (how many stage-advances a given
     *  pile of bottles could eventually support); this is what one batch itself costs. */
    public static final int BOTTLES_PER_BATCH = (int) Math.round(1.0 / OUTPUT_FRACTION_PER_STAGE);

    /**
     * The D2O fraction after {@code stages} electrolytic passes starting from {@code startFraction}.
     *
     * <p>Works on the isotope ratio (D2O molecules per H2O molecule), not the fraction directly:
     * a fraction-only multiply would run past 1.0 well before real enrichment does, since a ratio
     * has no upper bound but a fraction is capped at 1. Converting to a ratio, multiplying by the
     * separation factor once per stage, then converting back keeps the result honestly bounded in
     * {@code [0, 1]} at any stage count.
     */
    public static double fractionAfterStages(double startFraction, int stages) {
        if (stages <= 0) {
            return startFraction;
        }
        double ratio = startFraction / (1.0 - startFraction);
        ratio *= Math.pow(ELECTROLYTIC_SEPARATION_FACTOR, stages);
        // A stage count nobody would ever actually run (hundreds+) overflows the ratio to
        // infinity, and infinity / (1 + infinity) is NaN, not 1.0, in IEEE 754 arithmetic — found
        // by neverExceedsValidFractionBoundsEvenAtExtremeStageCounts(). Physically this is just
        // "fully enriched"; the formula should say so rather than hand back a value that fails
        // every bounds check downstream.
        if (Double.isInfinite(ratio)) {
            return 1.0;
        }
        return ratio / (1.0 + ratio);
    }

    /**
     * How many of {@code fedBottles} a batch actually consumes to produce one stage's worth of
     * output — the volume half of the cascade (§3): most of what goes in leaves as waste, not as
     * next-stage feed. Floored at 1 so a partial batch can never dispense free enrichment for
     * nothing consumed.
     */
    public static int bottlesConsumedFor(int fedBottles) {
        // Integer division, not a floating multiply by OUTPUT_FRACTION_PER_STAGE: this gates how
        // many real items vanish from an inventory, and a value that decides item counts should
        // not depend on 0.2's own binary floating-point rounding at every input, however unlikely
        // an actual off-by-one would be.
        return Math.max(1, fedBottles / 5);
    }

    private HeavyWaterCascade() {}
}
