package play.xponer.astronima.sim.suit;

/**
 * The parts of an EVA suit that can be broken, and what each one costs you.
 *
 * <p>The suit is the first machine a castaway has to understand. It comes out of the
 * wreck damaged, and every subsystem restored is a capability regained — which makes
 * repairing it the actual opening loop rather than a menu of stat bonuses.
 */
public enum SuitSubsystem {
    HELMET_SEAL("Helmet seal", "Cracked: the suit leaks and cannot hold pressure", "sealant patch"),
    TANK_MOUNT("Tank mount", "Broken: no tank can feed the suit, so it never seals", "latch set"),
    REGULATOR("Regulator", "Faulty: the tank drains twice as fast", "calibrated valve"),
    SCRUBBER_BAY("Scrubber bay", "Empty: exhaled CO2 collects inside the helmet", "LiOH cartridge"),
    THERMAL_LAYER("Thermal layer", "Stripped: no protection from cold or heat", "insulation weave"),
    STATUS_DISPLAY("Status display", "Dark: the suit reports nothing about itself", "salvaged circuit"),

    /**
     * Sealed gloves.
     *
     * <p>Added last and deliberately at the end of the enum: {@link #bit()} is an ordinal, and a
     * saved suit is a packed mask. Inserting this anywhere else would have quietly re-read every
     * existing save's thermal layer as its status display (rule 5).
     *
     * <p>Torn, it is not a slower glove — it is no glove. Everything a bare hand touches goes
     * straight onto skin. See {@code design/transmission.md}.
     */
    SEALED_GLOVES("Sealed gloves", "Split: anything you handle goes straight onto your skin",
            "insulation weave");

    private final String displayName;
    private final String faultDescription;
    private final String repairPart;

    SuitSubsystem(String displayName, String faultDescription, String repairPart) {
        this.displayName = displayName;
        this.faultDescription = faultDescription;
        this.repairPart = repairPart;
    }

    public String displayName() {
        return displayName;
    }

    /** What is wrong while this subsystem is broken. */
    public String faultDescription() {
        return faultDescription;
    }

    /** The component needed to fix it. */
    public String repairPart() {
        return repairPart;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Bit for this subsystem in a packed repair mask. */
    public int bit() {
        return 1 << ordinal();
    }
}
