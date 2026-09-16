package play.xponer.astronima.sim.ore;

/**
 * Baking the water out of hydrated rock — the reaction that makes an asteroid habitable.
 *
 * <p>The water in a carbonaceous chondrite is not ice and it is not damp. It is hydroxyl
 * bonded into the crystal lattice of the phyllosilicates, about an eighth of their mass,
 * and getting it out means breaking that bond with heat:
 *
 * <pre>
 *   Mg3Si2O5(OH)4   →   Mg3Si2O7  +  2 H2O↑
 *   serpentine          anhydrous     steam
 * </pre>
 *
 * <p>Deliberately not the same mechanic as anything else here (rule 8). The crusher is a
 * curve you ride and the forge is a trade you stop at the right moment; this is a
 * <em>window you have to find</em>, with a hard floor below it and an irreversible ruin
 * above it. Different failure, different feel, different verb.
 *
 * <p><strong>Why the floor is hard.</strong> Below the onset the bond does not break at
 * all — not slowly, not eventually. A cold retort left running overnight produces exactly
 * nothing, and that is the honest behaviour of the chemistry rather than a punishment.
 */
public final class Dehydroxylation {

    /**
     * Below this nothing comes out. Dehydroxylation of serpentine begins near 400 °C.
     */
    public static final double ONSET_K = 673.15;

    /** By this temperature the reaction has run to completion — about 700 °C. */
    public static final double COMPLETE_K = 973.15;

    /**
     * Past this the residue sinters into glassy clinker: fused, unusable, unrecoverable.
     *
     * <p>The whole reason the dial is a decision rather than a "more is better" slider.
     * Around 800 °C the collapsed silicate begins to melt at its grain contacts.
     */
    public static final double SINTER_K = 1073.15;

    /**
     * Water as a fraction of serpentine mass — {@code Mg3Si2O5(OH)4} carries 2 H₂O per
     * formula unit, which is 36 of its 277 g/mol. Stoichiometry, not a tuned number.
     */
    public static final double WATER_FRACTION_OF_SERPENTINE = 0.13;

    /** Grams per mole of water, for turning a baked mass into gas the room can hold. */
    public static final double WATER_G_PER_MOL = 18.015;

    /**
     * What one bake produced.
     *
     * <p>{@code residueHydration} is carried here rather than recomputed by callers because
     * the first version left it to them and it was wrong: the fraction that is left is a
     * fraction of the <em>residue</em>, which is lighter than what went in by exactly the
     * water that left. Expressing it against the original mass quietly destroyed some of
     * the water on every re-bake, and only the "two half bakes equal one full bake" test
     * noticed. Anything a caller can get wrong belongs where they cannot reach it.
     *
     * @param waterGrams      water driven off as steam
     * @param residueGrams    everything that stayed behind
     * @param residueHydration still-hydrated fraction <em>of the residue</em>
     * @param sintered        true when the residue was fused and is no longer processable
     */
    public record Bake(double waterGrams, double residueGrams, double residueHydration,
                       boolean sintered) {

        /** Steam, in moles, for handing to a room's gas mixture. */
        public double waterMoles() {
            return waterGrams / WATER_G_PER_MOL;
        }

        /** True when there is still water in there worth another, hotter run. */
        public boolean worthReBaking() {
            return !sintered && residueHydration > 1e-6;
        }
    }

    /**
     * How completely the reaction runs at this temperature, 0 to 1.
     *
     * <p>Linear between onset and completion. A real conversion curve is sigmoid and
     * time-dependent; that shape is not perceivable across one batch and would obscure
     * the two things that are — the floor and the ceiling.
     */
    public static double conversion(double temperatureK) {
        if (temperatureK < ONSET_K) {
            return 0;
        }
        if (temperatureK < COMPLETE_K) {
            return (temperatureK - ONSET_K) / (COMPLETE_K - ONSET_K);
        }
        if (temperatureK < SINTER_K) {
            return 1;
        }
        // Past the ceiling the grains fuse and the escape paths close, so the hydroxyl
        // that has not left yet is sealed into the glass. Over-firing a phyllosilicate
        // really does trap its volatiles, and it is what makes the ceiling a cost rather
        // than a cosmetic one: at full focus you get a ruined charge *and* less water,
        // which is the difference between a wrong end of the dial and a decorative one.
        double over = (temperatureK - SINTER_K) / SEAL_SPAN_K;
        return Math.clamp(1.0 - over * (1.0 - SEALED_CONVERSION), SEALED_CONVERSION, 1.0);
    }

    /** Degrees above the ceiling over which the melt finishes sealing the grains. */
    public static final double SEAL_SPAN_K = 400.0;

    /** How much still escapes a fully fused charge — through cracks, not through pores. */
    public static final double SEALED_CONVERSION = 0.25;

    /** True when this temperature would fuse the residue rather than merely dry it. */
    public static boolean sinters(double temperatureK) {
        return temperatureK >= SINTER_K;
    }

    /**
     * Bakes a charge of rock.
     *
     * <p>Mass is conserved: whatever water leaves is exactly what the residue lost. That
     * invariant is asserted for randomised inputs, and it is the one property that keeps
     * this a process rather than a lookup table with chemistry-shaped names on it.
     *
     * @param chargeGrams      total mass going in
     * @param hydratedFraction how much of that mass is still-hydrated phyllosilicate
     * @param temperatureK     what the retort reached
     */
    public static Bake bake(double chargeGrams, double hydratedFraction, double temperatureK) {
        if (chargeGrams <= 0) {
            return new Bake(0, 0, 0, false);
        }
        double hydrated = chargeGrams * Math.clamp(hydratedFraction, 0.0, 1.0);
        double converted = conversion(temperatureK);
        double water = hydrated * WATER_FRACTION_OF_SERPENTINE * converted;
        double residue = chargeGrams - water;

        // What did not convert is still hydrated mineral, and it is now a larger share of
        // a lighter charge. Water comes out of rock once, but only the part that actually
        // got hot enough — so a cold run is recoverable and a sintered one is not, which
        // is the asymmetry the whole mechanic rests on.
        double stillHydrated = hydrated * (1.0 - converted);
        double residueHydration = residue > 0 ? stillHydrated / residue : 0;

        return new Bake(water, residue, residueHydration, sinters(temperatureK));
    }

    private Dehydroxylation() {}
}
