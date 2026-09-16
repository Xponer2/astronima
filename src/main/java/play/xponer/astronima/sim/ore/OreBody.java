package play.xponer.astronima.sim.ore;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;

/**
 * What a mined block is actually made of: a mass of rock and the fraction of each
 * mineral in it.
 *
 * <p>An assemblage rather than a loot table. The difference matters because every
 * process downstream operates on <em>mass of a mineral</em>, so the same block can
 * yield very different things depending on how well it is processed — which is the
 * point of having processing at all.
 *
 * <p>Masses are in grams and are conserved by every operation in this package. That
 * invariant is what stops the chain quietly becoming a lookup table with physics-shaped
 * names on it.
 */
public final class OreBody {
    private final Map<Mineral, Double> massGrams;

    private OreBody(Map<Mineral, Double> massGrams) {
        this.massGrams = massGrams;
    }

    public static OreBody empty() {
        return new OreBody(new EnumMap<>(Mineral.class));
    }

    /** Builds an assemblage from mass fractions, scaled to a total mass. */
    public static OreBody of(double totalGrams, Map<Mineral, Double> fractions) {
        double sum = fractions.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return empty();
        }
        Map<Mineral, Double> masses = new EnumMap<>(Mineral.class);
        fractions.forEach((mineral, fraction) ->
                masses.put(mineral, totalGrams * fraction / sum));
        return new OreBody(masses);
    }

    /**
     * A typical CM chondrite: mostly hydrated silicate, a few percent native metal,
     * sulfides carrying the nickel, and organics throughout.
     *
     * <p>These are real abundances rounded to something a player can reason about.
     * Native metal being only a few percent is exactly why crushing and separating
     * matters — you cannot simply pick it out.
     */
    public static OreBody chondrite(double totalGrams) {
        return of(totalGrams, Map.of(
                Mineral.SERPENTINE, 0.52,
                Mineral.OLIVINE, 0.18,
                Mineral.MAGNETITE, 0.09,
                Mineral.TROILITE, 0.06,
                Mineral.KAMACITE, 0.045,
                Mineral.PENTLANDITE, 0.025,
                Mineral.CARBONATE, 0.04,
                Mineral.THOLIN, 0.03));
    }

    /**
     * A metal-rich seam. Rarer, and the reason prospecting is worth doing: the same
     * tier-1 process gets far more out of it.
     *
     * <p>Carries a genuine trace of {@link Mineral#PLATINUM_GROUP} — real iron meteorites report
     * PGMs at parts-per-million to low parts-per-thousand of the metal phase, so {@code 0.003}
     * here is deliberately tiny next to kamacite's {@code 0.22}, not an oversight
     * ({@code design/platinum-group.md} §2.2). {@link #of} renormalises against the fraction
     * sum, so this entry did not require hand-adjusting every other fraction to make room.
     */
    public static OreBody metalRich(double totalGrams) {
        return of(totalGrams, Map.of(
                Mineral.KAMACITE, 0.22,
                Mineral.MAGNETITE, 0.20,
                Mineral.TROILITE, 0.09,
                Mineral.PENTLANDITE, 0.07,
                Mineral.OLIVINE, 0.26,
                Mineral.SERPENTINE, 0.14,
                Mineral.THOLIN, 0.02,
                Mineral.PLATINUM_GROUP, 0.003));
    }

    public double massOf(Mineral mineral) {
        return massGrams.getOrDefault(mineral, 0.0);
    }

    public double totalMass() {
        return massGrams.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Mass fraction of one mineral, 0 when the body is empty. */
    public double fractionOf(Mineral mineral) {
        double total = totalMass();
        return total <= 0 ? 0 : massOf(mineral) / total;
    }

    public Map<Mineral, Double> masses() {
        return Collections.unmodifiableMap(massGrams);
    }

    public boolean isEmpty() {
        return totalMass() <= 1e-9;
    }

    /** A copy with one mineral's mass changed. */
    public OreBody with(Mineral mineral, double grams) {
        Map<Mineral, Double> copy = new EnumMap<>(massGrams);
        if (grams <= 1e-9) {
            copy.remove(mineral);
        } else {
            copy.put(mineral, grams);
        }
        return new OreBody(copy);
    }

    /** Combines two streams, as a bin of tailings accumulates. */
    public OreBody plus(OreBody other) {
        Map<Mineral, Double> copy = new EnumMap<>(massGrams);
        other.massGrams.forEach((mineral, grams) -> copy.merge(mineral, grams, Double::sum));
        return new OreBody(copy);
    }

    /** Scales every mineral by the same factor, for splitting a stream. */
    public OreBody scaled(double factor) {
        Map<Mineral, Double> copy = new EnumMap<>(Mineral.class);
        massGrams.forEach((mineral, grams) -> copy.put(mineral, grams * factor));
        return new OreBody(copy);
    }

    /**
     * Total recoverable metal, if every mineral present could be fully processed.
     * The gap between this and what a tier actually yields is the reason to advance.
     */
    public double containedMetalGrams() {
        return massGrams.entrySet().stream()
                .mapToDouble(entry -> entry.getKey().metalMassFraction() * entry.getValue())
                .sum();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        massGrams.forEach((mineral, grams) -> text.append(mineral.displayName())
                .append(' ').append(Math.round(grams)).append("g "));
        return text.toString().trim();
    }
}
