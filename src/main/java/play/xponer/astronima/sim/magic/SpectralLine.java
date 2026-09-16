package play.xponer.astronima.sim.magic;

/**
 * The alphabet of Astra Incognita (design/astra-incognita.md §6): real emission and absorption
 * lines, not invented "aspects". Wavelengths are the published values for each transition —
 * this is how real spectroscopy identifies what a light source is made of, and the game reuses
 * that fact rather than a metaphor for it.
 */
public enum SpectralLine {
    /** Balmer-alpha, hydrogen: the red of every nebula photograph ever taken. */
    H_ALPHA(656.3, false),
    /** The sodium doublet's first member — sodium contamination is a real, classic lab plague. */
    SODIUM_D1(589.6, false),
    /** The sodium doublet's second member. */
    SODIUM_D2(589.0, false),
    /** Calcium II H: the stronger of the two lines that show stellar activity. */
    CALCIUM_H(396.8, false),
    /** Calcium II K. */
    CALCIUM_K(393.4, false),
    /** Helium: found in the Sun's spectrum in 1868, decades before it was found on Earth. */
    HELIUM_I(587.6, false),
    /** One line of the iron forest — how a rock's iron content is known before it is crushed. */
    IRON_I(527.0, false),
    /**
     * [O III], 500.7 nm — a <strong>forbidden line</strong>. For sixty years it was believed to
     * be a new element, "nebulium", because it only appears where collisions cannot knock the
     * excited atom out of its metastable state first: a vacuum better than any laboratory on
     * Earth can make. {@link Spectrum#FORBIDDEN_LINE_MAX_KPA} is that condition, modelled.
     */
    FORBIDDEN_OIII(500.7, true);

    private final double wavelengthNm;
    private final boolean forbidden;

    SpectralLine(double wavelengthNm, boolean forbidden) {
        this.wavelengthNm = wavelengthNm;
        this.forbidden = forbidden;
    }

    public double wavelengthNm() {
        return wavelengthNm;
    }

    /** True for a line that only survives in a near-perfect vacuum. */
    public boolean forbidden() {
        return forbidden;
    }

    /** The registry id a filter token for this line is known by (design/astra-research-m4b.md
     *  §1/§3) — one spelling shared by {@code ModItems}, the language datagen and the crafting
     *  tree, so a typo in a second copy can never quietly stop matching. */
    public String id() {
        return "filter_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Plain-language name for a filter token's tooltip and the atlas strip's locked-line label
     *  (§6a.4's jargon rule) — an exhaustive switch on purpose, so a ninth catalogue line forces
     *  a decision here rather than falling back to a raw enum name. */
    public String displayName() {
        return switch (this) {
            case H_ALPHA -> "H-alpha";
            case SODIUM_D1 -> "Sodium D1";
            case SODIUM_D2 -> "Sodium D2";
            case CALCIUM_H -> "Calcium H";
            case CALCIUM_K -> "Calcium K";
            case HELIUM_I -> "Helium I";
            case IRON_I -> "Iron I";
            case FORBIDDEN_OIII -> "[O III]";
        };
    }
}
