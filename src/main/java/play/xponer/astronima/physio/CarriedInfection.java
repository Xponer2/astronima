package play.xponer.astronima.physio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import play.xponer.astronima.sim.pathogen.Infection;
import play.xponer.astronima.sim.pathogen.Pathogens;
import play.xponer.astronima.sim.pathogen.Strain;
import play.xponer.astronima.sim.pathogen.Treatment;

import java.util.List;
import java.util.Locale;

/**
 * An infection as it is carried between sessions: what it is, and how far along.
 *
 * <p>The living model is {@link Infection}, which is Minecraft-free and knows nothing about saving.
 * This is the thin thing that survives a relog and can be rebuilt into one — so the physics of an
 * illness stays where a test can reach it and the save format stays where the game can version it.
 *
 * <p><strong>Everything optional, defaulting to nothing.</strong> A player who has never been ill
 * carries this record with an empty source, and an older save simply has none of the fields — which
 * is the same discipline every other attachment in this mod uses (rule 5).
 *
 * @param source      which organism, by where it came from; blank when healthy
 * @param elapsed     seconds since it took hold
 * @param stage       how far it has got
 * @param pressure    part-way progress toward the next stage, -1..1
 * @param resistances treatments it has already learned to shrug off
 */
public record CarriedInfection(String source, double elapsed, String stage, double pressure,
                               List<String> resistances, String course, double courseTaken,
                               double coverLeft) {

    public static final CarriedInfection NONE =
            new CarriedInfection("", 0, Strain.Stage.HIDDEN.name(), 0, List.of(), "", 0, 0);

    public static final Codec<CarriedInfection> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("source", "").forGetter(CarriedInfection::source),
                    Codec.DOUBLE.optionalFieldOf("elapsed", 0.0)
                            .forGetter(CarriedInfection::elapsed),
                    Codec.STRING.optionalFieldOf("stage", Strain.Stage.HIDDEN.name())
                            .forGetter(CarriedInfection::stage),
                    Codec.DOUBLE.optionalFieldOf("pressure", 0.0)
                            .forGetter(CarriedInfection::pressure),
                    Codec.STRING.listOf().optionalFieldOf("resistances", List.of())
                            .forGetter(CarriedInfection::resistances),
                    Codec.STRING.optionalFieldOf("course", "")
                            .forGetter(CarriedInfection::course),
                    Codec.DOUBLE.optionalFieldOf("course_taken", 0.0)
                            .forGetter(CarriedInfection::courseTaken),
                    Codec.DOUBLE.optionalFieldOf("cover_left", 0.0)
                            .forGetter(CarriedInfection::coverLeft)
            ).apply(instance, CarriedInfection::new));

    /** The treatment currently being taken, or null when nobody is treating anything. */
    public Treatment runningCourse(List<Treatment> known) {
        if (course.isBlank() || coverLeft <= 0) {
            return null;
        }
        return known.stream().filter(treatment -> treatment.name().equals(course))
                .findFirst().orElse(null);
    }

    /** The same record with a fresh dose in it. */
    public CarriedInfection dosed(String drug, double covers) {
        return new CarriedInfection(source, elapsed, stage, pressure, resistances,
                drug, drug.equals(course) ? courseTaken : 0, covers);
    }

    /** The same record with the cover clock moved on. */
    public CarriedInfection withCover(double left) {
        return new CarriedInfection(source, elapsed, stage, pressure, resistances,
                left <= 0 ? "" : course, courseTaken, left);
    }

    public boolean isIll() {
        return !source.isBlank();
    }

    /** The strain this is, or null when healthy — an unknown name costs the illness, not the save. */
    public Strain strain() {
        if (!isIll()) {
            return null;
        }
        for (Strain.Source known : Strain.Source.values()) {
            if (known.name().equalsIgnoreCase(source)) {
                return Pathogens.of(known);
            }
        }
        return null;
    }

    /** Rebuilds the living model from what was saved. */
    public Infection revive(List<Treatment> known) {
        Strain strain = strain();
        if (strain == null) {
            return null;
        }
        Infection infection = new Infection(strain);
        infection.restore(elapsed, stageOr(Strain.Stage.HIDDEN), pressure);
        for (String name : resistances) {
            known.stream().filter(treatment -> treatment.name().equals(name)).findFirst()
                    .ifPresent(infection::remember);
        }
        return infection;
    }

    private Strain.Stage stageOr(Strain.Stage fallback) {
        for (Strain.Stage known : Strain.Stage.values()) {
            if (known.name().equalsIgnoreCase(stage)) {
                return known;
            }
        }
        return fallback;
    }

    /** Writes a living infection back down, or {@link #NONE} once it has cleared. */
    public static CarriedInfection of(Infection infection) {
        if (infection == null || infection.isCleared()) {
            return NONE;
        }
        return new CarriedInfection(
                infection.strain().source().name().toLowerCase(Locale.ROOT),
                infection.elapsed(), infection.stage().name(), infection.pressure(),
                infection.resistances().stream().map(Treatment::name).toList(),
                infection.course() == null ? "" : infection.course().name(),
                infection.taken(), 0);
    }
}
