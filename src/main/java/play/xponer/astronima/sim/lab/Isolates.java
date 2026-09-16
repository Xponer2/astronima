package play.xponer.astronima.sim.lab;

import java.util.List;
import java.util.Map;

/**
 * The organisms a laboratory can be handed, published once as a set (rule 20).
 *
 * <p>Six, and they are chosen so that <strong>every test does real work</strong>. The wall splits
 * them three and three. Among the Gram-positives, shape is no help at all and the arrangement and
 * the catalase drop each carry one decision. Among the Gram-negatives, one pair is separated only
 * by shape and another only by the oxidase strip — which is the job that test really does, telling
 * the coliforms from the pseudomonads.
 *
 * <p>That is a dichotomous key rather than a lookup, and it is the shape of a real first week of
 * microbiology: nothing is identified by one observation, and any single test you skip leaves you
 * with two answers instead of one.
 *
 * <p>Named as isolates rather than as species. A player who has not identified one should see a
 * designator, and giving them a Latin binomial in the code would be handing them the answer in the
 * one file they might read.
 */
public final class Isolates {

    /** Gram-positive cocci in clusters, catalase positive. The staphylococcus shape. */
    public static final Organism B2 = new Organism("B-2",
            Organism.Gram.POSITIVE, Organism.Shape.COCCUS, Organism.Arrangement.CLUSTERS,
            true, false,
            Map.of(Antibiotic.BROAD, 2.0, Antibiotic.NARROW, 0.25, Antibiotic.LAST_RESORT, 0.5));

    /** Gram-positive cocci in chains, catalase negative. The streptococcus shape. */
    public static final Organism C9 = new Organism("C-9",
            Organism.Gram.POSITIVE, Organism.Shape.COCCUS, Organism.Arrangement.CHAINS,
            false, false,
            Map.of(Antibiotic.BROAD, 0.5, Antibiotic.NARROW, 16.0, Antibiotic.LAST_RESORT, 0.25));

    /** Gram-negative rod, oxidase positive. The one the wrong stain sends you to. */
    public static final Organism W1 = new Organism("W-1",
            Organism.Gram.NEGATIVE, Organism.Shape.BACILLUS, Organism.Arrangement.SINGLE,
            true, true,
            Map.of(Antibiotic.BROAD, 32.0, Antibiotic.NARROW, 64.0,
                    Antibiotic.LAST_RESORT, 0.25));

    /**
     * Gram-positive cocci in <strong>pairs</strong>, catalase negative.
     *
     * <p>Deliberately one trait away from {@link #C9}: same wall, same shape, same enzymes, and
     * only the arrangement tells them apart. That is what makes the microscope worth focusing
     * properly — a chain read at the wrong plane looks like a pair, and now that mistake costs you
     * the identification rather than nothing.
     */
    public static final Organism D4 = new Organism("D-4",
            Organism.Gram.POSITIVE, Organism.Shape.COCCUS, Organism.Arrangement.PAIRS,
            false, false,
            Map.of(Antibiotic.BROAD, 1.0, Antibiotic.NARROW, 0.5, Antibiotic.LAST_RESORT, 8.0));

    /**
     * A Gram-negative <strong>spiral</strong>, oxidase positive.
     *
     * <p>One trait away from {@link #W1} in the other direction: same wall, same enzymes, and only
     * the shape separates them. Spirilla are real and they are what a water loop grows — and the
     * antibiotic that beats the rod is the one this shrugs off, so getting the shape wrong is not
     * an academic error, it is a wasted course and a bred resistance.
     */
    public static final Organism S7 = new Organism("S-7",
            Organism.Gram.NEGATIVE, Organism.Shape.SPIRILLUM, Organism.Arrangement.SINGLE,
            true, true,
            Map.of(Antibiotic.BROAD, 4.0, Antibiotic.NARROW, 64.0,
                    Antibiotic.LAST_RESORT, 32.0));

    /**
     * A Gram-negative rod, catalase positive, <strong>oxidase negative</strong>.
     *
     * <p>The one the oxidase test exists for. It is identical to {@link #W1} on the wall, the
     * shape and the peroxide drop, and the single strip of oxidase reagent is the only thing that
     * separates them — which is exactly the job that test does on a real bench, where it is what
     * tells the coliforms apart from the pseudomonads.
     *
     * <p>Added because without it {@link #B2} was the only isolate with this pair of enzymes, so
     * two cheap drops identified it outright and the stain and the microscope had nothing to do.
     * A test that can be skipped is a test that will be.
     */
    public static final Organism E1 = new Organism("E-1",
            Organism.Gram.NEGATIVE, Organism.Shape.BACILLUS, Organism.Arrangement.SINGLE,
            true, false,
            Map.of(Antibiotic.BROAD, 8.0, Antibiotic.NARROW, 32.0,
                    Antibiotic.LAST_RESORT, 1.0));

    public static List<Organism> all() {
        return List.of(B2, C9, W1, D4, S7, E1);
    }

    private Isolates() {}
}
