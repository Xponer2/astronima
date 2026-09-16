package play.xponer.astronima.sim.physio;

/**
 * Where on the body a condition presents, so the biomonitor can tint a diagram rather
 * than only listing names. Harm with a location reads far faster than harm with a
 * label.
 */
public enum BodyRegion {
    HEAD("Head"),
    CHEST("Chest"),
    ABDOMEN("Abdomen"),
    ARMS("Arms"),
    LEGS("Legs"),
    /** Affects the whole body rather than any one place. */
    SYSTEMIC("Systemic");

    private final String displayName;

    BodyRegion(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
