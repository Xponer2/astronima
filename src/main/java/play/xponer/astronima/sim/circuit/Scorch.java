package play.xponer.astronima.sim.circuit;

/**
 * What an overheating wire <em>looks</em> like, and why it is not a glow.
 *
 * <h2>The design document promised something the physics forbids</h2>
 * {@code design/electrical.md} E6/E7/E8 says the player should see <em>"an overheating run glowing
 * dull red, which is real black-body and not a UI convention"</em>. It cannot: the same document
 * sets the insulation limit at <strong>400 K</strong> (§3.4), and visible incandescence does not
 * begin until about <strong>798 K</strong> — the Draper point. A wire in this mod fails at half the
 * temperature it would need to glow at. Writing the glow anyway would have been a UI convention
 * wearing black-body's name, which is exactly what that sentence set out to avoid.
 *
 * <p><strong>So the warning is the thing that really happens instead: the insulation cooks.</strong>
 * Polymer insulation darkens, browns and finally chars well below any temperature the copper cares
 * about, and an electrician reads a run's history off its colour. That is a real signal, it is
 * legible at the temperatures this mod actually reaches, and it arrives long before the failure —
 * which is what rule 7 asks of it.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Scorch {

    /**
     * Where visible incandescence begins, K.
     *
     * <p>Kept as a named constant despite nothing using it, because it is the number that decides
     * the design: if a later tier raises the insulation limit past this, a real glow becomes
     * honest and this class should gain one.
     */
    public static final double DRAPER_POINT_K = 798.0;

    /**
     * The share of the insulation's limit at which discolouration starts to show.
     *
     * <p>Six tenths — about 240 K for a 400 K insulation, which is above anything a healthy run
     * reaches and below anything in trouble. A warning that appeared on every working circuit
     * would be worth exactly nothing.
     */
    public static final double SHOWS_FROM = 0.6;

    /**
     * How cooked the insulation looks, 0 for factory-fresh and 1 for charred through.
     *
     * <p>Squared rather than linear: cooking is a rate that runs away with temperature, so the
     * last stretch before failure discolours far faster than the first. That is what makes the
     * colour a <em>warning</em> rather than a gauge — the change accelerates as the margin
     * disappears.
     */
    public static double of(double wireK, double ambientK) {
        double limit = Ampacity.INSULATION_LIMIT_K;
        double from = ambientK + (limit - ambientK) * SHOWS_FROM;
        if (wireK <= from) {
            return 0;
        }
        double through = Math.clamp((wireK - from) / Math.max(1e-6, limit - from), 0, 1);
        return through * through;
    }

    /** Past this, the insulation has failed and the run is lost. */
    public static boolean hasFailed(double wireK) {
        return wireK >= Ampacity.INSULATION_LIMIT_K;
    }

    /**
     * Whether it is worth telling the player, in words, that this run is in trouble.
     *
     * <p>Deliberately lower than the point the colour becomes obvious: the meter should be ahead
     * of the eye, not behind it.
     */
    public static boolean isWorthWarning(double wireK, double ambientK) {
        return of(wireK, ambientK) > 0.05;
    }

    private Scorch() {}
}
