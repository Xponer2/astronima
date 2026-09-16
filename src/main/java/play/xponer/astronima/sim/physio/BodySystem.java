package play.xponer.astronima.sim.physio;

/**
 * The physiological systems the biomonitor reports on.
 *
 * <p>Systems are what make an <em>unidentified</em> illness legible: a pathogen with no
 * name can still be shown as "immune response, abdomen", which is genuinely useful
 * information long before the player can say what the organism is.
 */
public enum BodySystem {
    RESPIRATORY("Respiratory"),
    CIRCULATORY("Circulatory"),
    NERVOUS("Nervous"),
    IMMUNE("Immune"),
    INTEGUMENTARY("Skin & tissue");

    private final String displayName;

    BodySystem(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
