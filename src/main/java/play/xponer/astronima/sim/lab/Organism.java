package play.xponer.astronima.sim.lab;

import java.util.Map;

/**
 * What an organism actually is, in the terms a laboratory works in.
 *
 * <p>Not a name with a difficulty number. These are the traits a real identification runs on, and
 * every one of them is something the player finds out by doing a physical test: the stain says
 * which wall, the microscope says the shape and how they sit together, a drop of peroxide says
 * whether it has catalase.
 *
 * <p><strong>The traits are the identification.</strong> There is no hidden id to look up — the key
 * narrows on these and nothing else, so partial information gives a partial answer and every test
 * the player runs is worth exactly what it eliminates.
 *
 * <p>Minecraft-free (rule 1).
 *
 * @param mic minimum inhibitory concentration per antibiotic, µg/mL — the number disc diffusion
 *            turns into a distance you can measure
 */
public record Organism(String name, Gram gram, Shape shape, Arrangement arrangement,
                       boolean catalase, boolean oxidase, Map<Antibiotic, Double> mic) {

    /**
     * Which cell wall.
     *
     * <p>Purple or pink, and the reason is real: a Gram-positive's thick peptidoglycan layer holds
     * the crystal violet when the alcohol comes through, and a Gram-negative's thin one lets it go
     * and takes up the counterstain instead.
     */
    public enum Gram { POSITIVE, NEGATIVE }

    /** Round or rod. The two you can tell apart down a light microscope without thinking. */
    public enum Shape { COCCUS, BACILLUS, SPIRILLUM }

    /**
     * How they sit together after dividing, which is where half of them get their names.
     *
     * <p><em>Staphylē</em> is a bunch of grapes; <em>streptos</em> is twisted, like a chain. The
     * arrangement is not decoration — it is the second half of "Gram-positive cocci in clusters",
     * which is a sentence that means something specific.
     */
    public enum Arrangement { SINGLE, PAIRS, CHAINS, CLUSTERS }

    public Organism {
        mic = Map.copyOf(mic);
    }

    /** How much drug it takes to stop this organism, or infinity for one that shrugs it off. */
    public double micFor(Antibiotic antibiotic) {
        return mic.getOrDefault(antibiotic, Double.POSITIVE_INFINITY);
    }

    /**
     * Whether these traits could belong to a real organism at all.
     *
     * <p>Gram-negative cocci in clusters is not a thing, and that is <strong>useful</strong>: it is
     * how a player catches their own over-decolorized stain. The microscope and the stain disagree,
     * and one of them is a slide they made badly.
     */
    public static boolean isPlausible(Gram gram, Shape shape, Arrangement arrangement) {
        if (shape == Shape.COCCUS && arrangement == Arrangement.CLUSTERS) {
            return gram == Gram.POSITIVE;      // staphylococci are the cluster-formers, and positive
        }
        if (shape == Shape.BACILLUS) {
            return arrangement != Arrangement.CLUSTERS;
        }
        return true;
    }
}
