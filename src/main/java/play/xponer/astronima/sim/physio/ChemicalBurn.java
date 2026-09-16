package play.xponer.astronima.sim.physio;

/**
 * Real hydrofluoric-acid contact dose (design/halogens.md §22-23, Part C2).
 *
 * <p>Every other hazard in this mod is inhalation/exposure through a room's partial pressure
 * ({@link play.xponer.astronima.sim.tox.GasToxicity}) or an accumulated dose gated on the suit
 * ({@link play.xponer.astronima.sim.rad.RadiationDose}, {@link Barotrauma}). This one is neither:
 * real HF crosses skin on contact, painlessly, and the fluoride ion it releases binds calcium and
 * magnesium in the tissue beneath — deep enough to reach bone, and in high enough dose,
 * systemically, depleting the blood's own ionised calcium into a fatal arrhythmia. That is the real
 * fact behind PLAN.md's own one-line spec: "contact = deep-tissue damage ignoring armor." Every
 * method here is a pure function of a dose in real grams, Minecraft-free (rule 1) — and none of
 * them takes a suit or barrier parameter, on purpose. See {@code HydrofluoricAcidItem} and
 * {@code AtmosphereEvents.applyChemicalBurn} for where that fact actually bites.
 */
public final class ChemicalBurn {

    /**
     * Real order of magnitude, not a tuned number: occupational-toxicology case literature
     * documents fatal systemic fluoride poisoning from concentrated (&gt;50%) hydrofluoric acid
     * contacting only a few percent of body-surface area, at absorbed masses on the order of a few
     * grams to a few tens of grams. This mod's own {@code hydrofluoric_acid} item already sits at
     * one mole per item ({@code HfDigesterBlockEntity.MOL_PER_ITEM}) and HF's own real molar mass
     * ({@code FluoriteDigestion.HF_MOLAR_MASS} = 20.006 g/mol) — so a single mishandled item is, on
     * its own, already inside that real lethal range. These three thresholds are this design's own
     * reasoned pick inside that real range, the same "real practitioner's free parameter, not a
     * measured material constant" treatment {@code design/halogens.md} §9.4 already gives
     * {@code ZONE_LENGTH_FRACTION} — chosen so one full item's own real mass (20.006 g) always
     * lands at CRITICAL, never short of it.
     */
    public static final double MILD_AT_GRAMS = 2.0;

    public static final double SEVERE_AT_GRAMS = 8.0;

    public static final double CRITICAL_AT_GRAMS = 20.0;

    /**
     * How fast the body clears absorbed fluoride with no further exposure, g/s — sized so clearing
     * the lowest real band alone ({@link #MILD_AT_GRAMS}) takes about a real day, the same "about a
     * day" sizing {@code RadiationDose.RECOVERY_SV_PER_S} already uses for its own lowest band.
     * Real systemic fluoride poisoning genuinely does recover over days, not minutes — slower than
     * any gas dose this mod models, which is the honest direction to be wrong in.
     */
    public static final double RECOVERY_GRAMS_PER_S = MILD_AT_GRAMS / (24.0 * 3600.0);

    /** Real thresholds, not tuned numbers (see {@link #MILD_AT_GRAMS}'s own javadoc). */
    public enum Severity {
        NONE, MILD, SEVERE, CRITICAL;

        public static Severity classify(double doseGrams) {
            if (doseGrams < MILD_AT_GRAMS) {
                return NONE;
            }
            if (doseGrams < SEVERE_AT_GRAMS) {
                return MILD;
            }
            if (doseGrams < CRITICAL_AT_GRAMS) {
                return SEVERE;
            }
            return CRITICAL;
        }
    }

    /** A real contact event: {@code contactGrams} of HF just crossed onto skin. Never negative. */
    public static double contact(double doseGrams, double contactGrams) {
        return doseGrams + Math.max(0.0, contactGrams);
    }

    /** The body clearing what it can, {@code dtSeconds} later. Never negative. */
    public static double recover(double doseGrams, double dtSeconds) {
        return Math.max(0.0, doseGrams - RECOVERY_GRAMS_PER_S * dtSeconds);
    }

    private ChemicalBurn() {}
}
