package play.xponer.astronima.sim.ore;

/**
 * Turns the retort's temperature into a sentence a person can act on.
 *
 * <p>Same reasoning as {@link SeparatorAdvice}: "847 K" is exact and completely opaque
 * unless you already know what dehydroxylation wants. A gauge says <em>what</em>; it takes
 * a sentence to say <em>so what</em>, and this machine has a window rather than a direction,
 * so the sentence has to say which side of it you are on.
 *
 * <p>Nothing here invents information. Every verdict is a band of the same temperature the
 * bar draws, so the words and the instrument cannot disagree.
 */
public final class RetortAdvice {

    /** Where the vessel is, relative to the window that actually produces water. */
    public enum Verdict {
        /** No sun on the mirror at all: buried, roofed over, or the night side. */
        DARK,
        /** Lit, but nowhere near starting the reaction. Nothing will happen, ever. */
        COLD,
        /** In the window but not through it: some comes out, the rest stays behind. */
        PARTIAL,
        /** The place to be: complete conversion, nothing spoiled. */
        IDEAL,
        /** Past useful, not yet ruinous. Wasted focus, nothing gained. */
        EXCESS,
        /** Over the ceiling, in whichever way this charge goes wrong. */
        SPOILING;

        /**
         * The sentence, for the charge actually in the vessel.
         *
         * <p>Composed from the process rather than stored per verdict, because the two feeds
         * are wrong in the same <em>shape</em> and wrong about completely different things:
         * one traps its water, the other fills the room with chlorine. A single fixed string
         * would have to be vague enough to cover both, and a panel that says "too hot" when
         * it could say "chlorine" is withholding the only part that matters.
         */
        public String advice(RetortProcess process) {
            return switch (this) {
                case DARK -> "No sun on the mirror — it needs open sky, in daylight";
                // The mirror aims itself now (SolarRetortBlockEntity#focus()), so reaching
                // COLD or PARTIAL means it is already at full concentration and the sun
                // itself is the shortfall — nothing left for a player to tighten.
                case COLD -> "Too cool — no " + process.product()
                        + " is coming off. Mirror's already at full concentration; needs more direct sun";
                case PARTIAL -> "Some " + process.product()
                        + " coming out — mirror's maxed, waiting on more sun for the rest";
                case IDEAL -> "In the window — all the " + process.product()
                        + ", " + process.idealNote();
                case EXCESS -> "Hotter than it needs to be — no more "
                        + process.product() + " up here";
                case SPOILING -> "Too hot — " + process.spoilage();
            };
        }

        /** True when this reading is the player doing it right. */
        public boolean isGood() {
            return this == IDEAL;
        }

        /** True when this reading is actively destroying value rather than merely idle. */
        public boolean isHarmful() {
            return this == SPOILING;
        }
    }

    /**
     * How much of the safe band is spent warning that the ceiling is coming.
     *
     * <p>A fraction rather than a fixed number of kelvin, and that is not tidiness. The
     * chlorate window is a third the width of the water one, so a fixed 50 K margin would
     * eat half of it and leave the player being warned almost everywhere it is safe — which
     * is how a warning stops being read. Proportional, each process warns for the same
     * <em>share</em> of its own approach.
     */
    private static final double EXCESS_SHARE = 0.3;

    /**
     * Reads the vessel.
     *
     * @param sunlight     sun on the mirror, 0 when there is none
     * @param temperatureK what the vessel reached
     * @param process      what is in the vessel, and therefore which window applies
     */
    public static Verdict read(double sunlight, double temperatureK, RetortProcess process) {
        if (sunlight <= 0) {
            return Verdict.DARK;
        }
        if (temperatureK < process.onsetK()) {
            return Verdict.COLD;
        }
        if (temperatureK < process.completeK()) {
            return Verdict.PARTIAL;
        }
        if (temperatureK >= process.spoilK()) {
            return Verdict.SPOILING;
        }
        // Between complete conversion and the ceiling nothing more is gained, and the
        // approaching ceiling is worth warning about before it is reached rather than after.
        return temperatureK > process.spoilK() - process.safeBandK() * EXCESS_SHARE
                ? Verdict.EXCESS : Verdict.IDEAL;
    }

    /**
     * The temperature the bar is drawn against, so the window sits somewhere sensible on it.
     *
     * <p>Scaled to the ceiling plus a margin rather than to whatever the mirror can reach:
     * a bar whose useful band is a sliver at the left is a bar that teaches nothing.
     *
     * <p><strong>One scale for both processes</strong>, deliberately. Rescaling per charge
     * would make the same painted position mean two different temperatures, and the reading
     * a player builds up over a hundred batches — "about a third along" — would silently
     * become wrong the day they load chlorate. Sharing the scale costs the chlorate window
     * some width and buys the instrument the thing an instrument is for: the low, narrow
     * band is visibly low and visibly narrow next to the water one.
     */
    public static final double BAR_FULL_SCALE_K = Dehydroxylation.SINTER_K + 200;

    /** Where a temperature sits on that bar, 0..1. */
    public static double barFraction(double temperatureK) {
        return Math.clamp(temperatureK / BAR_FULL_SCALE_K, 0.0, 1.0);
    }

    private RetortAdvice() {}
}
