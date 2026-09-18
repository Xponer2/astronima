package play.xponer.astronima.sim.physio;

import play.xponer.astronima.sim.physio.Macronutrition.MacroProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Real per-food macronutrient shares, the same roster shape {@code sim.chem.Chemistry} already
 * established for "what a stack actually is": a small, hand-written table of real facts, looked
 * up by qualified item id rather than derived, because no formula can honestly produce "how much
 * of this food is protein" the way {@code Formula} derives elemental mass fractions.
 *
 * <p>An id with no entry is not an error — it is a real food this design has no composition data
 * for, and it contributes nothing to any macro (design/macronutrients.md §2, §6).
 */
public final class MacroProfiles {

    private static final Map<String, MacroProfile> ROSTER = new LinkedHashMap<>();

    private static String qualify(String id) {
        return id.contains(":") ? id : "astronima:" + id;
    }

    private static void profile(String id, double proteinFraction, double carbohydrateFraction,
            double fatFraction) {
        ROSTER.put(qualify(id), new MacroProfile(proteinFraction, carbohydrateFraction, fatFraction));
    }

    static {
        // Real Spirulina/Chlorella dry mass: roughly 55-70% protein, 15-25% carbohydrate,
        // 5-8% fat (design/hydroponics.md §1.3's own "60%+ protein by mass" citation).
        profile("algae_biomass", 0.60, 0.20, 0.06);
        // Real raw romaine lettuce is >95% water; of its small dry content, roughly 1.2 g
        // protein, 2.9 g carbohydrate (mostly fibre) and 0.3 g fat per 100 g - low across the
        // board (design/hydroponics.md §4.6's own "mostly water and fibre").
        profile("lettuce", 0.012, 0.029, 0.003);
    }

    public static Optional<MacroProfile> of(String id) {
        return Optional.ofNullable(ROSTER.get(id));
    }

    private MacroProfiles() {}
}
