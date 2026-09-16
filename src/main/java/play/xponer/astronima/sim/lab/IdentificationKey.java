package play.xponer.astronima.sim.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * The dichotomous key: what is still possible, given what you have found out.
 *
 * <h2>A narrowing, not a lookup</h2>
 * The interesting state is <strong>partial</strong>. "Gram-positive cocci in clusters" is not an
 * answer — it is three organisms, and one drop of hydrogen peroxide makes it one. A model that only
 * spoke when it had everything would make the intermediate steps pointless, and the intermediate
 * steps <em>are</em> the game.
 *
 * <p>So this holds observations and reports the candidates that fit. Each test the player runs is
 * worth exactly what it eliminates, which is how a real key is read and why the order does not
 * matter: two people who ran the same tests in different orders end up in the same place.
 *
 * <p><strong>It never guesses.</strong> Two candidates left is reported as two candidates. Handing
 * back the likelier one would quietly turn the whole laboratory into a slot machine that is usually
 * right.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class IdentificationKey {

    private Organism.Gram gram;
    private Organism.Shape shape;
    private Organism.Arrangement arrangement;
    private Boolean catalase;
    private Boolean oxidase;

    /** What the stain said — which is not always what the organism is. */
    public IdentificationKey stained(GramStain.Result result) {
        if (result != GramStain.Result.SPOILED) {
            gram = result.asGram();
        }
        return this;
    }

    public IdentificationKey seen(Organism.Shape underMicroscope,
                                  Organism.Arrangement howTheySit) {
        shape = underMicroscope;
        arrangement = howTheySit;
        return this;
    }

    /** One drop of hydrogen peroxide: bubbles or nothing. */
    public IdentificationKey catalase(boolean bubbled) {
        catalase = bubbled;
        return this;
    }

    public IdentificationKey oxidase(boolean colourChanged) {
        oxidase = colourChanged;
        return this;
    }

    /** Everything still consistent with what has been observed. */
    public List<Organism> candidates(List<Organism> known) {
        List<Organism> left = new ArrayList<>();
        for (Organism organism : known) {
            if (gram != null && organism.gram() != gram) {
                continue;
            }
            if (shape != null && organism.shape() != shape) {
                continue;
            }
            if (arrangement != null && organism.arrangement() != arrangement) {
                continue;
            }
            if (catalase != null && organism.catalase() != catalase) {
                continue;
            }
            if (oxidase != null && organism.oxidase() != oxidase) {
                continue;
            }
            left.add(organism);
        }
        return List.copyOf(left);
    }

    /** The answer, or null while there is still more than one thing it could be. */
    public Organism identified(List<Organism> known) {
        List<Organism> left = candidates(known);
        return left.size() == 1 ? left.get(0) : null;
    }

    /**
     * Whether what has been observed could describe a real organism at all.
     *
     * <p>Gram-negative cocci in clusters is not one, and that is the player's own alarm bell: the
     * microscope and the stain disagree, so one of them is a slide made badly. This is the only
     * warning the game gives about a ruined Gram stain, and it is the same one a real bench worker
     * gets.
     */
    public boolean isSelfConsistent() {
        if (gram == null || shape == null || arrangement == null) {
            return true;                 // not enough to contradict anything yet
        }
        return Organism.isPlausible(gram, shape, arrangement);
    }

    /** How much is still unknown, so a screen can say what to do next rather than just how many. */
    public List<String> outstanding() {
        List<String> todo = new ArrayList<>();
        if (gram == null) {
            todo.add("gram stain");
        }
        if (shape == null || arrangement == null) {
            todo.add("microscopy");
        }
        if (catalase == null) {
            todo.add("catalase");
        }
        if (oxidase == null) {
            todo.add("oxidase");
        }
        return List.copyOf(todo);
    }
}
