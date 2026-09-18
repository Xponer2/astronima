package play.xponer.astronima.sim.physio;

/**
 * Three real macronutrient reserves — protein, carbohydrate, fat — each a 0..1 gauge that drains
 * at its own real rate and refills on eating (design/macronutrients.md §2).
 *
 * <p>The three real drain rates are not tuned by feel: carbohydrate (glycogen) is the fastest
 * real reserve a body exhausts, substantially depleted within about a real day of fasting;
 * protein (lean tissue) is drawn down over real days-to-weeks once glycogen is gone (documented
 * muscle wasting in real starvation studies); fat is the real long-term reserve, with documented
 * real survival well past a month on stored fat alone (design/macronutrients.md §1).
 *
 * <p>{@link State#overallSufficiency()} is the real minimum of the three, not their average —
 * Liebig's Law of the Minimum: overall status is capped by the scarcest input, so a body fed
 * nothing but protein is not "two-thirds fed" while it starves of carbohydrate.
 *
 * <p>Minecraft-free (rule 1): real seconds and a real metabolism-speed multiplier in, a real
 * state out — the caller owns the player, the clock and the food item.
 */
public final class Macronutrition {

    private static final double CARBOHYDRATE_SECONDS_TO_EMPTY = 24.0 * 3600.0;
    private static final double PROTEIN_SECONDS_TO_EMPTY = 7.0 * 24.0 * 3600.0;
    private static final double FAT_SECONDS_TO_EMPTY = 30.0 * 24.0 * 3600.0;

    public enum Macro { PROTEIN, CARBOHYDRATE, FAT }

    /** A food's real macro composition, as a 0..1 share of it - see {@code MacroProfiles}. */
    public record MacroProfile(double proteinFraction, double carbohydrateFraction, double fatFraction) {
        public static final MacroProfile NONE = new MacroProfile(0.0, 0.0, 0.0);
    }

    public record State(double protein, double carbohydrate, double fat) {
        public static final State FULL = new State(1.0, 1.0, 1.0);

        public State {
            protein = Math.clamp(protein, 0.0, 1.0);
            carbohydrate = Math.clamp(carbohydrate, 0.0, 1.0);
            fat = Math.clamp(fat, 0.0, 1.0);
        }

        public double of(Macro macro) {
            return switch (macro) {
                case PROTEIN -> protein;
                case CARBOHYDRATE -> carbohydrate;
                case FAT -> fat;
            };
        }

        /** Liebig's Law of the Minimum: the scarcest reserve, not the average of the three. */
        public double overallSufficiency() {
            return Math.min(protein, Math.min(carbohydrate, fat));
        }

        /** Whichever reserve is worst off right now - ties break protein, then carbohydrate. */
        public Macro mostDeficient() {
            if (protein <= carbohydrate && protein <= fat) {
                return Macro.PROTEIN;
            }
            return carbohydrate <= fat ? Macro.CARBOHYDRATE : Macro.FAT;
        }
    }

    /**
     * The state after {@code seconds} of real time at {@code metabolismScale}x real physiology
     * (the same dial {@code Config.METABOLISM_SCALE} already runs real O2 consumption and real
     * tissue-nitrogen decompression modelling at - one real speed, not a second one invented for
     * this part alone).
     */
    public static State drained(State state, double seconds, double metabolismScale) {
        double scaledSeconds = seconds * metabolismScale;
        return new State(
                state.protein() - scaledSeconds / PROTEIN_SECONDS_TO_EMPTY,
                state.carbohydrate() - scaledSeconds / CARBOHYDRATE_SECONDS_TO_EMPTY,
                state.fat() - scaledSeconds / FAT_SECONDS_TO_EMPTY);
    }

    /**
     * The state after eating a food worth {@code nutritionPoints} (vanilla hunger points, 0..20 -
     * the same normalization {@code getFoodLevel()} itself uses) with real composition
     * {@code profile}.
     */
    public static State fed(State state, MacroProfile profile, double nutritionPoints) {
        double scale = nutritionPoints / 20.0;
        return new State(
                state.protein() + profile.proteinFraction() * scale,
                state.carbohydrate() + profile.carbohydrateFraction() * scale,
                state.fat() + profile.fatFraction() * scale);
    }

    private Macronutrition() {}
}
