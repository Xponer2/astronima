package play.xponer.astronima.sim.pathogen;

import java.util.Set;

/**
 * One moment where contamination could reach a body, and how much of it actually does.
 *
 * <h2>The chain, and why it is worth spelling out</h2>
 * <pre>
 *   source  →  surface  →  glove  →  face  →  you
 * </pre>
 * Four hops, each with its own barrier. The consequence real laboratories drill hardest falls out
 * of it directly: <strong>contaminated gloves are not the danger — contaminated gloves that touch
 * your face are.</strong> A player can work all day with a dirty glove and stay well, and lose to
 * one scratched nose. That is a rule nobody has to be told, because the chain is short enough to
 * reason through and the instrument shows every link of it.
 *
 * <p>Minecraft-free (rule 1). See {@code design/transmission.md}.
 */
public final class Exposure {

    /**
     * How much of a load reaches the body through this route with these barriers standing.
     *
     * <p>An intact barrier stops the route outright rather than reducing it, for the reason in
     * {@link Barrier}: a seal holds or it does not.
     */
    public static double reaching(Strain.Route route, double load, Set<Barrier> intact) {
        return stoppedBy(route).stream().anyMatch(intact::contains) ? 0.0 : load;
    }

    /**
     * The barriers that stop a route. Any one of them intact is enough.
     *
     * <p>Airborne takes two, and either alone will do: a sealed helmet keeps the room's air out
     * entirely, and a filter cleans the air you are already breathing. They are different answers
     * to the same route, which is why the player can solve it either by suiting up or by fixing
     * the habitat.
     */
    public static Set<Barrier> stoppedBy(Strain.Route route) {
        return switch (route) {
            case CONTACT -> Set.of(Barrier.GLOVES);
            case AIRBORNE -> Set.of(Barrier.HELMET, Barrier.FILTER);
            case INGESTION -> Set.of(Barrier.STERILE_STOCK);
            case PUNCTURE -> Set.of(Barrier.OUTER_LAYER);
        };
    }

    /**
     * The classic mistake: a dirty glove touching the helmet seal.
     *
     * <p>This is the one hop gloves do <em>not</em> protect, and that is not an oversight in the
     * model — it is the whole lesson. The glove is between the surface and your hand. Once you
     * lift it to your face, the glove is on the wrong side of you.
     *
     * @return what is left on the glove, and what is now on the skin
     */
    public static Contamination.Handover touchFace(Contamination glove, Contamination skin) {
        return glove.transferTo(skin, Contamination.FACE_FRACTION);
    }

    /**
     * Handling something contaminated.
     *
     * <p>With gloves the load lands on the glove; without them it lands straight on the skin, and
     * a torn glove is <strong>not a slower glove, it is no glove.</strong>
     *
     * @return what is left on the surface, and where the rest went
     */
    public static Handled handle(Contamination surface, Contamination glove, Contamination skin,
                                 boolean glovesIntact) {
        Contamination.Handover moved =
                surface.transferTo(glovesIntact ? glove : skin, Contamination.TOUCH_FRACTION);
        return glovesIntact
                ? new Handled(moved.from(), moved.to(), skin)
                : new Handled(moved.from(), glove, moved.to());
    }

    /**
     * Breathing room air that has something in it.
     *
     * <p>The slow one, and deliberately so. Contact and puncture are moments — you did a thing, at
     * a place, at a time. This is a condition, and its lesson is about the habitat rather than
     * about a mistake: a damp room with mould in it is a room that is slowly making you ill, and
     * the fix is a dehumidifier, not gloves.
     *
     * @param airborne concentration in the room, 0..1
     * @param seconds  time spent breathing it
     */
    public static Contamination breathe(Contamination lungs, Strain.Source source, double airborne,
                                        double seconds, Set<Barrier> intact) {
        double reaching = reaching(Strain.Route.AIRBORNE, airborne, intact);
        return reaching <= 0 ? lungs : lungs.add(source, reaching * seconds * BREATH_RATE);
    }

    /** Share of the room concentration that lands per second of breathing it. */
    public static final double BREATH_RATE = 0.0008;

    /** Where a handled load ended up. */
    public record Handled(Contamination surface, Contamination glove, Contamination skin) {}

    private Exposure() {}
}
