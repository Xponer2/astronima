package play.xponer.astronima.sim.organic;

/**
 * Cold plastic goes brittle — the general rule, applied to whatever this mod ships that is a
 * polymer product.
 *
 * <p>Rather than a hard refusal (a repair part that is sometimes simply unusable, with no visible
 * reason on the item itself), this reuses {@code EvaSuitItem.repair}'s existing starting-life
 * {@code quality} parameter: a repair made cold starts life shorter, the honest way to show a
 * rushed-looking patch that fails sooner, using a number the suit already tracks rather than
 * inventing a new one. See {@code design/petrochemicals.md} §4.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Polymer {

    /** Below this, a polymer part embrittles. −20 °C, the real onset for a bulk polyolefin. */
    public static final double EMBRITTLEMENT_ONSET_K = 253.15;

    /** How much of the part's quality survives a fully cold application. Not zero: the seal still
     * takes, it just will not last — a stiff, cracked-at-the-edges patch, not a failed one. */
    public static final double COLD_QUALITY_FLOOR = 0.35;

    /**
     * Derates a repair's quality for the temperature it was applied at.
     *
     * <p>Full quality at or above the onset; linearly down to {@link #COLD_QUALITY_FLOOR} by 40 K
     * colder, which is deep-shadow-side-of-an-airless-rock cold rather than merely a chilly room —
     * the floor is a real number, not zero, so a desperate repair in the open still helps.
     */
    public static double embrittledQuality(double baseQuality, double temperatureK) {
        if (temperatureK >= EMBRITTLEMENT_ONSET_K) {
            return baseQuality;
        }
        double coldSpan = 40.0;
        double below = EMBRITTLEMENT_ONSET_K - temperatureK;
        double factor = Math.clamp(1.0 - below / coldSpan, 0.0, 1.0);
        double floor = COLD_QUALITY_FLOOR * baseQuality;
        return floor + (baseQuality - floor) * factor;
    }

    private Polymer() {}
}
