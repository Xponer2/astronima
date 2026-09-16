package play.xponer.astronima.sim.pathogen;

import play.xponer.astronima.sim.physio.Ailment;

import java.util.List;
import java.util.Map;

/**
 * What an organism is: where it came from, how it spreads, and what it does to you.
 *
 * <h2>Its symptoms are borrowed, and that is the hook</h2>
 * A pathogen does not invent a status effect. It expresses through ailments the player already
 * knows from the environment, so an infection first reads as <em>"the room is doing something to
 * me"</em> — and the player checks the atmosphere, finds nothing wrong, and has to work out that
 * the thing wrong with them is not the room.
 *
 * <p>A symptom that only ever comes from a disease would be a label saying <em>you are ill</em>,
 * which is a debuff bar wearing a costume. {@code StrainTest} refuses one.
 *
 * <p>Minecraft-free (rule 1).
 */
public record Strain(String designator, Source source, Route route,
                     double incubationSeconds, double stageSeconds, double virulence,
                     Map<Stage, List<Ailment>> symptoms) {

    /**
     * Where it came from. Each behaves differently, and each is scientifically defensible.
     *
     * <p>Six sources rather than one, because "you got sick" is not a story. <em>Where</em> it
     * came from is what tells the player which of their habits is the problem.
     */
    public enum Source {
        /**
         * Frozen in an ice lens for geologic time. Microbes genuinely survive that, and panspermia
         * is a serious hypothesis — this is the one that arrives because you mined ice.
         */
        CRYOPHILIC,
        /**
         * Your own flora, turned. Closed environments plus radiation measurably raise virulence;
         * it has been documented on the ISS. This is the one a badly kept habitat grows itself.
         */
        COMMENSAL,
        /** A sealed biological payload that failed. Fast, aggressive, tied to a wreck. */
        WRECK,
        /**
         * Riding on regolith dust.
         *
         * <p>Asteroid and lunar dust is jagged, electrostatic, and it goes everywhere — it is one
         * of the genuinely unsolved problems of working on an airless body, and the Apollo crews
         * complained about breathing it inside the lander. This is the one you bring indoors on
         * your own suit.
         */
        DUST,
        /**
         * Biofilm in the water loop.
         *
         * <p>Documented on the ISS: a closed water recycling system grows films on every surface
         * it touches, and they are stubborn. This one is not something that arrived. It is
         * something your plumbing made.
         */
        RECYCLER,
        /**
         * The inside of a suit nobody has ever cleaned.
         *
         * <p>Real, documented, and unglamorous: a pressure suit is a warm damp closed space worn
         * by a person, and what grows in one is the thing the crew brought with them. It is the
         * source that arrives because of a chore you skipped rather than a place you went.
         */
        SUIT
    }

    /** How it gets into you — and therefore which piece of equipment stops it. */
    public enum Route { CONTACT, AIRBORNE, INGESTION, PUNCTURE }

    /**
     * How far along it is.
     *
     * <p>Named rather than numbered, because the biomonitor has to say something and "stage 3"
     * says nothing to somebody deciding whether to spend a dose.
     */
    public enum Stage {
        /** Present, silent, and not yet showing anything at all. */
        HIDDEN,
        EARLY,
        ESTABLISHED,
        SEVERE;

        public Stage worse() {
            return this == SEVERE ? SEVERE : values()[ordinal() + 1];
        }

        public Stage better() {
            return this == HIDDEN ? HIDDEN : values()[ordinal() - 1];
        }

        /** True once it is doing something the player can feel. */
        public boolean isShowing() {
            return this != HIDDEN;
        }
    }

    public Strain {
        symptoms = Map.copyOf(symptoms);
    }

    /**
     * Everything it is expressing at this stage — this stage's symptoms and every earlier one's.
     *
     * <p>Cumulative on purpose: an illness that <em>swapped</em> symptoms as it worsened would read
     * as a different illness rather than as a worse one, and the player would go looking for a
     * second cause.
     */
    public List<Ailment> showing(Stage stage) {
        if (!stage.isShowing()) {
            return List.of();
        }
        List<Ailment> found = new java.util.ArrayList<>();
        for (Stage at : Stage.values()) {
            if (at.isShowing() && at.ordinal() <= stage.ordinal()) {
                symptoms.getOrDefault(at, List.of()).forEach(ailment -> {
                    if (!found.contains(ailment)) {
                        found.add(ailment);
                    }
                });
            }
        }
        return List.copyOf(found);
    }

    /** Every symptom this strain can ever show, for the guard that checks they are all borrowed. */
    public List<Ailment> everySymptom() {
        return showing(Stage.SEVERE);
    }
}
