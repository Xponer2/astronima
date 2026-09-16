package play.xponer.astronima.physio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import play.xponer.astronima.sim.pathogen.Contamination;
import play.xponer.astronima.sim.pathogen.Strain;

/**
 * What the player is carrying on their gloves and on their skin.
 *
 * <p>Two loads, not one, because the whole system turns on the difference: a dirty glove is not a
 * problem and a dirty <em>hand</em> is. Keeping them apart is what lets the instrument show a
 * player that they are one mistake away rather than only telling them afterwards.
 *
 * <p>Versioned (rule 5) and every field is read back. The strain is stored once — a person
 * carrying two at a time is a different design problem, and {@link Contamination} refuses to
 * pretend otherwise.
 *
 * @param source which strain both loads belong to
 * @param glove  what is on the outside of the gloves
 * @param skin   what has reached the person
 */
public record CarriedContamination(Strain.Source source, double glove, double skin) {

    public static final CarriedContamination NONE =
            new CarriedContamination(Strain.Source.CRYOPHILIC, 0, 0);

    public static final Codec<CarriedContamination> CODEC = RecordCodecBuilder.create(it -> it.group(
            Codec.STRING.fieldOf("source").forGetter(c -> c.source.name()),
            Codec.DOUBLE.fieldOf("glove").forGetter(CarriedContamination::glove),
            Codec.DOUBLE.fieldOf("skin").forGetter(CarriedContamination::skin))
            .apply(it, (source, glove, skin) ->
                    new CarriedContamination(read(source), glove, skin)));

    private static Strain.Source read(String name) {
        for (Strain.Source source : Strain.Source.values()) {
            if (source.name().equals(name)) {
                return source;
            }
        }
        return Strain.Source.CRYOPHILIC;
    }

    public Contamination onGloves() {
        return new Contamination(source, glove);
    }

    public Contamination onSkin() {
        return new Contamination(source, skin);
    }

    public CarriedContamination with(Contamination gloves, Contamination body) {
        return new CarriedContamination(
                gloves.load() >= body.load() ? gloves.source() : body.source(),
                gloves.load(), body.load());
    }

    public boolean isClean() {
        return onGloves().isClean() && onSkin().isClean();
    }
}
