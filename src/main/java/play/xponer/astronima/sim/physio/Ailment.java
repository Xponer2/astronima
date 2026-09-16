package play.xponer.astronima.sim.physio;

/**
 * Every way the environment can be harming a person, as a named condition.
 *
 * <p>This exists because the symptoms overlap: hypoxia, carbon dioxide, carbon
 * monoxide, the bends and mould spores all produce nausea and weakness, so a player
 * watching their own screen cannot tell which one is killing them. Naming the
 * condition — and its remedy — is the instrument that makes the physiology fair.
 */
public enum Ailment {
    HYPOXIA("Hypoxia", "Not enough oxygen — raise ppO2", "Head swims, vision dims",
            BodySystem.RESPIRATORY, BodyRegion.CHEST),
    OXYGEN_TOXICITY("Oxygen toxicity", "Too much oxygen — vent some off", "Fingers twitch, vision tunnels",
            BodySystem.RESPIRATORY, BodyRegion.CHEST),
    HYPERCAPNIA("CO2 poisoning", "Carbon dioxide building up — run a scrubber", "Short of breath, panic rising",
            BodySystem.RESPIRATORY, BodyRegion.HEAD),
    CARBON_MONOXIDE("CO poisoning", "Carbon monoxide in the blood — leave and breathe clean air", "Head throbs, thoughts slow",
            BodySystem.CIRCULATORY, BodyRegion.SYSTEMIC),
    TOXIC_GAS("Toxic gas", "Corrosive or poisonous gas — filter it or get out", "Throat burns, eyes water",
            BodySystem.RESPIRATORY, BodyRegion.CHEST),
    DECOMPRESSION("Decompression sickness", "Nitrogen bubbling out — repressurize, then pre-breathe", "Joints ache, deep and sudden",
            BodySystem.CIRCULATORY, BodyRegion.LEGS),
    BAROTRAUMA("Lung barotrauma", "Pressure dropped faster than you could exhale — cycle an airlock, don't open the door", "Sharp pain, deep in the chest",
            BodySystem.RESPIRATORY, BodyRegion.CHEST),
    MOLD_SPORES("Spore irritation", "Mould in the air — dry the room out", "Itchy throat, a cough that won't stop",
            BodySystem.IMMUNE, BodyRegion.CHEST),
    NOISE_FATIGUE("Sleep deprivation", "Machinery too loud to rest — move away or dampen it", "Can't concentrate, bone tired",
            BodySystem.NERVOUS, BodyRegion.HEAD),
    /**
     * The body's own temperature falling, which is a different condition from the room's.
     *
     * <p>Its own ailment rather than a flavour of {@link #THERMAL_STRESS}, because the
     * remedies are different and only one of them mentions the suit. Someone chilling in a
     * habitat with an intact thermal layer is not short of equipment - they are short of a
     * warm room, and being told to repair a layer that is already fine sends them looking in
     * the wrong place while their core keeps falling.
     */
    HYPOTHERMIA("Hypothermia", "Core temperature falling — get somewhere warmer, or work", "Shivering, fingers going numb",
            BodySystem.NERVOUS, BodyRegion.SYSTEMIC),
    THERMAL_STRESS("Thermal stress", "Extreme temperature reaching you — repair the suit's thermal layer", "Skin stinging, sweat won't stop",
            BodySystem.INTEGUMENTARY, BodyRegion.SYSTEMIC),
    /**
     * The suit is present but not doing its job.
     *
     * <p>Its own condition, rather than a flavour of hypoxia, because the remedy is
     * completely different and "raise ppO2" is actively misleading here. A player with
     * a tank on their back and a broken tank mount is not short of oxygen — they are
     * short of a working suit, and telling them to find more air sends them the wrong
     * way while they suffocate.
     */
    SUIT_NOT_SEALING("Suit not sealing", "Suit cannot hold pressure — repair the helmet seal and tank mount", "Ears popping, breath coming short",
            BodySystem.RESPIRATORY, BodyRegion.HEAD),
    /** Kit carried rather than worn: the slots exist and are empty. */
    EQUIPMENT_NOT_FITTED("Equipment not fitted", "Put the suit and tank in their equipment slots", "Chest tight, the air feels thin",
            BodySystem.RESPIRATORY, BodyRegion.HEAD),
    /**
     * An infection the player cannot yet identify. The biomonitor shows the system and
     * region it is attacking but no name, because naming it is earned by research.
     */
    UNKNOWN_INFECTION("Unidentified infection", "Cause unknown — a medical analysis is needed", "Something is wrong, and it isn't the room",
            BodySystem.IMMUNE, BodyRegion.SYSTEMIC),
    /**
     * Accumulated gamma dose (design/radiation.md) — real acute radiation syndrome thresholds,
     * not a tuned number. Classified alongside {@link #CARBON_MONOXIDE} under
     * {@link BodySystem#CIRCULATORY}/{@link BodyRegion#SYSTEMIC}: both are a body burden that
     * travels in the blood rather than something felt at one place.
     */
    RADIATION_SICKNESS("Radiation sickness", "Gamma dose accumulating — get behind cover or away from the source", "Nausea, a bone-deep fatigue that will not lift",
            BodySystem.CIRCULATORY, BodyRegion.SYSTEMIC),
    /**
     * Real hydrofluoric-acid contact dose (design/halogens.md §22-25, Part C2) — grouped with
     * {@link #RADIATION_SICKNESS} and {@link #CARBON_MONOXIDE} for the same reason: a body burden
     * travelling in the blood, not something felt at one place. No suit repairs this — that is the
     * whole point of the mechanism it is attached to.
     */
    CHEMICAL_BURN("Chemical burn", "Fluoride bound your calcium — there is no suit for this; stay away from more of it and let your body clear it", "No pain at first, then a spreading numbness and a heartbeat that will not settle",
            BodySystem.CIRCULATORY, BodyRegion.SYSTEMIC);

    /** Severity bands, deliberately few so a colour can carry the meaning. */
    public enum Severity { NONE, MILD, SEVERE, CRITICAL }

    private final String displayName;
    private final String remedy;
    private final String rawSymptom;
    private final BodySystem system;
    private final BodyRegion region;

    Ailment(String displayName, String remedy, String rawSymptom, BodySystem system, BodyRegion region) {
        this.displayName = displayName;
        this.remedy = remedy;
        this.rawSymptom = rawSymptom;
        this.system = system;
        this.region = region;
    }

    /** Which physiological system this attacks — the axis the biomonitor groups by. */
    public BodySystem system() {
        return system;
    }

    /** Where it presents on the body diagram. */
    public BodyRegion region() {
        return region;
    }

    /**
     * True when the player has no way to know what this is yet. Such conditions show
     * their symptoms and location but withhold a cause, which is the whole point of
     * the diagnosis ladder.
     */
    public boolean isUnidentified() {
        return this == UNKNOWN_INFECTION;
    }

    public String displayName() {
        return displayName;
    }

    /** What the player should actually do about it. */
    public String remedy() {
        return remedy;
    }

    /**
     * What an uninstrumented body actually notices — vague, borrowed-feeling, no cause named.
     * This is all a player with no biomonitor chip at all can ever read (design/biomonitor-chips.md
     * §3): the real diagnosis behind it is earned by instrumentation, not given away for free.
     */
    public String rawSymptom() {
        return rawSymptom;
    }

    /** Lower-case key fragment for translation lookups. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
