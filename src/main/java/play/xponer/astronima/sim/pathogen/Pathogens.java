package play.xponer.astronima.sim.pathogen;

import play.xponer.astronima.sim.physio.Ailment;

import java.util.List;
import java.util.Map;

/**
 * The organisms that exist, published once as a set (rule 20).
 *
 * <p>Six sources, six behaviours, and each is scientifically defensible. Every symptom is
 * borrowed from something the environment can also do, which is the hook: an infection first reads
 * as the room doing something to you.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Pathogens {

    /**
     * Frozen in an ice lens for geologic time, and slow when it wakes.
     *
     * <p>Microbes genuinely survive that. It hides for a long time, moves slowly, and is weak
     * enough that a well-rested person throws it off — so the first one a player meets teaches
     * that doing nothing is sometimes correct.
     */
    public static final Strain CRYOPHILE = new Strain("B-2", Strain.Source.CRYOPHILIC,
            Strain.Route.CONTACT, 240, 90, 0.4,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.NOISE_FATIGUE),
                    Strain.Stage.SEVERE, List.of(Ailment.HYPOXIA)));

    /**
     * Your own flora, turned by a badly kept habitat.
     *
     * <p>Closed environments plus radiation measurably raise virulence; it has been documented on
     * the ISS. It hides longest of the three, because it did not arrive — it grew.
     */
    public static final Strain COMMENSAL = new Strain("C-9", Strain.Source.COMMENSAL,
            Strain.Route.AIRBORNE, 420, 70, 0.55,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.NOISE_FATIGUE),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.SEVERE, List.of(Ailment.HYPERCAPNIA)));

    /** A sealed biological payload that failed. Short fuse, and it beats a healthy body. */
    public static final Strain WRECK = new Strain("W-1", Strain.Source.WRECK,
            Strain.Route.PUNCTURE, 90, 45, 0.95,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.HYPOXIA),
                    Strain.Stage.SEVERE, List.of(Ailment.THERMAL_STRESS)));

    /**
     * Carried in on dust, and it goes for the lungs because that is how it arrives.
     *
     * <p>Slow and unspectacular on purpose: it is the illness of a habitat where the airlock is
     * treated as a door, and the fix is housekeeping rather than heroics.
     */
    public static final Strain DUST = new Strain("D-4", Strain.Source.DUST,
            Strain.Route.AIRBORNE, 360, 80, 0.5,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.MOLD_SPORES),
                    Strain.Stage.SEVERE, List.of(Ailment.HYPOXIA)));

    /**
     * Grown in your own water loop, and picked up by touching the plumbing.
     *
     * <p>The one whose source is a machine you built and maintain. Nothing brought it; a film grew
     * on the inside of a pipe because the loop is closed, which is exactly what a closed loop
     * does.
     */
    public static final Strain RECYCLER = new Strain("S-7", Strain.Source.RECYCLER,
            Strain.Route.CONTACT, 180, 60, 0.7,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.HYPERCAPNIA),
                    Strain.Stage.SEVERE, List.of(Ailment.THERMAL_STRESS)));

    /**
     * Out of the suit itself, and it takes hold fast once it is in you.
     *
     * <p>Short incubation and a mean streak, because it did not have to travel: it was already in
     * contact with skin for hours before anything went wrong.
     */
    public static final Strain SUIT = new Strain("E-1", Strain.Source.SUIT,
            Strain.Route.CONTACT, 120, 55, 0.75,
            Map.of(Strain.Stage.EARLY, List.of(Ailment.UNKNOWN_INFECTION),
                    Strain.Stage.ESTABLISHED, List.of(Ailment.NOISE_FATIGUE),
                    Strain.Stage.SEVERE, List.of(Ailment.HYPERCAPNIA)));

    public static List<Strain> all() {
        return List.of(CRYOPHILE, COMMENSAL, WRECK, DUST, RECYCLER, SUIT);
    }

    public static Strain of(Strain.Source source) {
        return all().stream().filter(strain -> strain.source() == source).findFirst()
                .orElse(CRYOPHILE);
    }

    private Pathogens() {}
}
