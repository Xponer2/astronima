package play.xponer.astronima.physio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import play.xponer.astronima.sim.physio.Chronic;

/**
 * A player's {@link Chronic} on the wire and in the save.
 *
 * <p>Thin, for the reason {@code SurfaceContamination} records: the model belongs in {@code sim/}
 * where it can be unit-tested, and a codec drags Mojang's serialization library in with it.
 *
 * <p>Every condition is written even when it is at zero, so adding a third one later cannot
 * silently read as "already scarred" from an old save (rule 5).
 */
public record CarriedChronic(Map<String, Double> insults, List<String> scars) {

    public static final CarriedChronic NONE = new CarriedChronic(Map.of(), List.of());

    public static final Codec<CarriedChronic> CODEC = RecordCodecBuilder.create(it -> it.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("insults", Map.of())
                    .forGetter(CarriedChronic::insults),
            Codec.STRING.listOf().optionalFieldOf("scars", List.of())
                    .forGetter(CarriedChronic::scars))
            .apply(it, CarriedChronic::new));

    public Chronic revive() {
        Map<Chronic.Condition, Double> insult = new EnumMap<>(Chronic.Condition.class);
        Map<Chronic.Condition, Boolean> scarred = new EnumMap<>(Chronic.Condition.class);
        for (Chronic.Condition condition : Chronic.Condition.values()) {
            insult.put(condition, insults.getOrDefault(condition.key(), 0.0));
            scarred.put(condition, scars.contains(condition.key()));
        }
        return new Chronic(insult, scarred);
    }

    public static CarriedChronic of(Chronic body) {
        Map<String, Double> insults = new java.util.LinkedHashMap<>();
        List<String> scars = new java.util.ArrayList<>();
        for (Chronic.Condition condition : Chronic.Condition.values()) {
            insults.put(condition.key(), body.insultOf(condition));
            if (body.hasScarred(condition)) {
                scars.add(condition.key());
            }
        }
        return new CarriedChronic(insults, scars);
    }
}
