package play.xponer.astronima.sim.ore;

/**
 * Baking oxygen out of sodium chlorate, and what happens when it is baked too hard.
 *
 * <p>NaClO₃ → NaCl + 1½ O₂ at around 500 °C. That is the whole of an emergency oxygen
 * candle: a chlorate bed that decomposes and gives its oxygen back. The iron powder mixed
 * into a real canister is <em>fuel</em> — it burns to keep the bed hot, because a canister
 * bolted to a bulkhead has no other heat source.
 *
 * <p><strong>Given a solar furnace you do not need the fuel.</strong> That is the whole
 * point of this route: the retort already reaches far past 770 K, so it can decompose
 * chlorate directly and the iron a candle costs stops being necessary. Breathing stops
 * competing with tools.
 *
 * <h2>And what makes it a decision rather than a better recipe</h2>
 * Heated past decomposition, chlorates do not simply give up more oxygen — they
 * disproportionate, and the halogen comes off as <strong>chlorine</strong> instead of
 * staying behind as salt. Chlorine is heavier than air, attacks the lungs, and is dangerous
 * at partial pressures a thousand times below anything else the mod models. So the dial on
 * the retort stops being "find the window" and becomes "find the window or poison the room
 * you are standing in".
 *
 * <p>Minecraft-free (rule 1): temperatures in, moles out.
 */
public final class ChlorateDecomposition {

    /**
     * Where sodium chlorate begins to give up oxygen, K.
     *
     * <p>About 250 °C. Melting comes first (248 °C) and decomposition follows closely, which
     * is why a candle needs only a squib to start and then sustains itself.
     */
    public static final double ONSET_K = 523.15;

    /** Fully decomposing, K — about 500 °C, the working temperature of a real canister. */
    public static final double COMPLETE_K = 773.15;

    /**
     * Above this the bed starts giving off chlorine instead of oxygen, K.
     *
     * <p>Around 600 °C. The margin above {@link #COMPLETE_K} is deliberately narrow — a
     * hundred and twenty kelvin on a dial whose useful span is hundreds — because a wide
     * safe band would make the hazard a formality. It is also roughly true: the window
     * between "decomposes cleanly" and "starts throwing halogen" is genuinely tight, which
     * is why real canisters regulate their own temperature rather than being driven hot.
     */
    public static final double CHLORINE_K = 873.15;

    /**
     * Oxygen from one gram of sodium chlorate, in moles.
     *
     * <p>NaClO₃ is 106.44 g/mol and yields 1.5 O₂, so a gram gives 1.5 / 106.44 ≈ 0.0141
     * mol. Not a balance figure — it is the stoichiometry, and every yield below is this
     * number multiplied by how much of the bed actually reacted.
     */
    public static final double O2_MOL_PER_GRAM = 1.5 / 106.44;

    /**
     * How much chlorine comes off, as a fraction of the oxygen that would have been made,
     * once past {@link #CHLORINE_K}.
     *
     * <p>Ramped rather than switched: the disproportionation competes with clean
     * decomposition and wins gradually as the bed gets hotter. A step change would make the
     * dial a pass/fail check instead of something to hold steady.
     */
    public static final double CHLORINE_RAMP_K = 200.0;

    /** What one charge gave up. */
    public record Bake(double oxygenMoles, double chlorineMoles) {
        /** True when the bed was cooked hard enough to put halogen into the room. */
        public boolean gassed() {
            return chlorineMoles > 0;
        }
    }

    /**
     * How much of the bed decomposes at this temperature, 0..1.
     *
     * <p>Zero below onset — a cold chlorate bed is a salt, not a supply — then rising to
     * complete. It does <em>not</em> fall off again past the chlorine threshold: the bed
     * still gives up its oxygen up there, it just gives up chlorine as well, which is a
     * worse failure than getting nothing and the reason this is a hazard rather than an
     * inefficiency.
     */
    public static double conversion(double temperatureK) {
        if (temperatureK < ONSET_K) {
            return 0;
        }
        if (temperatureK >= COMPLETE_K) {
            return 1;
        }
        return (temperatureK - ONSET_K) / (COMPLETE_K - ONSET_K);
    }

    /**
     * The fraction of the reaction that goes to chlorine instead of oxygen, 0..1.
     *
     * <p>Zero anywhere in the working window, which is what makes the window worth finding.
     */
    public static double chlorineFraction(double temperatureK) {
        if (temperatureK <= CHLORINE_K) {
            return 0;
        }
        return Math.clamp((temperatureK - CHLORINE_K) / CHLORINE_RAMP_K, 0.0, 1.0);
    }

    /**
     * Bakes a charge of chlorate.
     *
     * @param chargeGrams grams of sodium chlorate in the vessel
     * @param temperatureK the retort's vessel temperature
     */
    public static Bake bake(double chargeGrams, double temperatureK) {
        double reacted = chargeGrams * conversion(temperatureK);
        if (reacted <= 0) {
            return new Bake(0, 0);
        }
        double potential = reacted * O2_MOL_PER_GRAM;
        double spoiled = chlorineFraction(temperatureK);
        // Two chlorines per formula unit's worth of lost oxygen is not the real
        // stoichiometry of a messy disproportionation, and pretending to a precise ratio
        // would be false precision. What is true, and what the number has to carry, is that
        // the halogen released is a small fraction of the moles of oxygen forgone — and that
        // even a small fraction is lethal, because chlorine's thresholds are a thousandfold
        // lower than anything else here.
        return new Bake(potential * (1 - spoiled), potential * spoiled * CHLORINE_PER_O2_LOST);
    }

    /** Moles of chlorine released per mole of oxygen the overshoot cost. */
    public static final double CHLORINE_PER_O2_LOST = 0.35;

    private ChlorateDecomposition() {}
}
