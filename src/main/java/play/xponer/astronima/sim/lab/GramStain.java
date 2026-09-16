package play.xponer.astronima.sim.lab;

import java.util.List;

/**
 * The Gram stain: four reagents, in order, and one of them is a timer.
 *
 * <h2>The single most useful test in bacteriology, and the easiest to ruin</h2>
 * Crystal violet stains everything. Iodine fixes it into the cell as a complex too big to wash out
 * of a thick wall. <strong>Alcohol is the decolorizer</strong> — it strips the complex from a
 * thin-walled Gram-negative and leaves a thick-walled Gram-positive purple. Safranin then stains
 * whatever has been emptied, pink.
 *
 * <p>So the whole test rests on one step being done for the right length of time, and it has a
 * famous failure: <strong>leave the alcohol on too long and a Gram-positive decolorizes too</strong>,
 * reads pink, and you now believe you are looking at a different organism entirely. Real students
 * do this constantly. Here it costs the same thing it costs in a real laboratory: you take the wrong
 * branch of the key, you choose from the wrong drug list, and you breed resistance to something that
 * was never going to work.
 *
 * <p>The one saving grace is the one a careful worker uses: the microscope still shows the
 * arrangement. Gram-negative cocci in clusters is not an organism, and noticing that contradiction
 * is how you catch your own bad slide.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class GramStain {

    /** The four reagents, in the order they have to go on. */
    public enum Reagent { CRYSTAL_VIOLET, IODINE, ALCOHOL, SAFRANIN }

    /**
     * How long the alcohol may sit before it starts stripping thick walls too.
     *
     * <p>A few seconds in reality, and that is genuinely how tight it is — the standard instruction
     * is to decolorize "until the runoff is clear" and then stop immediately.
     */
    public static final double SAFE_DECOLORIZE_SECONDS = 5.0;

    /** Below this the alcohol has not done its job and a Gram-negative stays purple as well. */
    public static final double MINIMUM_DECOLORIZE_SECONDS = 1.5;

    /** What came out of the stain. */
    public enum Result {
        /** Purple. */
        POSITIVE,
        /** Pink. */
        NEGATIVE,
        /** Nothing usable: the wrong reagents, or the wrong order. */
        SPOILED;

        public Organism.Gram asGram() {
            return this == POSITIVE ? Organism.Gram.POSITIVE : Organism.Gram.NEGATIVE;
        }
    }

    /**
     * Runs a stain.
     *
     * @param steps            the reagents applied, in the order they were applied
     * @param decolorizeSeconds how long the alcohol was left on
     * @return what the slide looks like — which is not always what the organism is
     */
    public static Result run(Organism organism, List<Reagent> steps, double decolorizeSeconds) {
        if (!steps.equals(List.of(Reagent.CRYSTAL_VIOLET, Reagent.IODINE,
                Reagent.ALCOHOL, Reagent.SAFRANIN))) {
            // Not an error message and not a lucky guess. A slide done in the wrong order is a
            // slide you throw away, and finding that out costs a sample.
            return Result.SPOILED;
        }
        if (decolorizeSeconds < MINIMUM_DECOLORIZE_SECONDS) {
            // Under-decolorized: the violet never came out of anything, so everything reads
            // positive. The opposite error, and just as wrong.
            return Result.POSITIVE;
        }
        if (decolorizeSeconds > SAFE_DECOLORIZE_SECONDS) {
            // Over-decolorized: even a thick wall gives up the complex. Everything reads negative.
            return Result.NEGATIVE;
        }
        return organism.gram() == Organism.Gram.POSITIVE ? Result.POSITIVE : Result.NEGATIVE;
    }

    /** The correct sequence, for anything that needs to name it. */
    public static List<Reagent> properOrder() {
        return List.of(Reagent.CRYSTAL_VIOLET, Reagent.IODINE, Reagent.ALCOHOL, Reagent.SAFRANIN);
    }

    private GramStain() {}
}
