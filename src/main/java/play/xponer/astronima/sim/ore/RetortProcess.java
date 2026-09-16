package play.xponer.astronima.sim.ore;

/**
 * What the retort is currently cooking, and therefore which window the dial is hunting.
 *
 * <p>The machine is a solar furnace: a mirror and a sealed vessel. It has no opinion about
 * what is in the vessel, and that is exactly why it can do more than one job — which is how
 * a real solar furnace is used, and why oxygen did not need a new machine invented for it.
 *
 * <p><strong>Each process brings its own window and its own way of going wrong</strong>, and
 * that second half is what stops this being one mechanic with two sets of constants (rule 8).
 * Bake rock too hard and the charge fuses, keeping its water — you lose the batch. Bake
 * chlorate too hard and the halogen comes off as chlorine — you lose the room. The dial is
 * the same control; what it is worth getting right is not.
 */
public enum RetortProcess {

    /** Hydrated silicate giving up its bound water — the retort's first job. */
    DEHYDROXYLATION(
            Dehydroxylation.ONSET_K,
            Dehydroxylation.COMPLETE_K,
            Dehydroxylation.SINTER_K,
            "water",
            "residue intact",
            "the charge is fusing and trapping its own water"),

    /**
     * Sodium chlorate giving up its oxygen.
     *
     * <p>Its window is a narrow slice sitting <em>inside</em> the water one — a hundred kelvin
     * against four hundred and fifty. So the two feeds do share some safe settings, and that
     * is better than them being disjoint: the trap is that a dial parked where rock comes out
     * properly dry is well past where chlorine starts, not that every rock setting is fatal.
     */
    CHLORATE(
            ChlorateDecomposition.ONSET_K,
            ChlorateDecomposition.COMPLETE_K,
            ChlorateDecomposition.CHLORINE_K,
            "oxygen",
            "salt behind, no chlorine",
            "the bed is throwing chlorine into the room"),

    /**
     * Breunnerite giving up its CO2 — the retort's third job, and its hottest: real magnesite
     * calcination sits right at the edge of what this mirror can reach at all
     * ({@code design/carbonate-calcination.md} §1). Fed baked silicate rather than raw
     * tailings/rock, because the carbonate a charge carries is untouched by dehydroxylation —
     * a second, hotter bake of the same rock reaches it next.
     */
    CALCINATION(
            Calcination.ONSET_K,
            Calcination.COMPLETE_K,
            Calcination.DECREPITATION_K,
            "magnesium oxide",
            "clean MgO, no fines lost",
            "the charge is decrepitating - fracturing and scattering as fines");

    private final double onsetK;
    private final double completeK;
    private final double spoilK;
    private final String product;
    private final String idealNote;
    private final String spoilage;

    RetortProcess(double onsetK, double completeK, double spoilK,
                  String product, String idealNote, String spoilage) {
        this.onsetK = onsetK;
        this.completeK = completeK;
        this.spoilK = spoilK;
        this.product = product;
        this.idealNote = idealNote;
        this.spoilage = spoilage;
    }

    /** Below this nothing happens at all. */
    public double onsetK() {
        return onsetK;
    }

    /** At or above this the charge reacts completely. */
    public double completeK() {
        return completeK;
    }

    /** At or above this it starts going wrong, in whatever way this process goes wrong. */
    public double spoilK() {
        return spoilK;
    }

    /** What this charge is being cooked for, for the sentence on the panel. */
    public String product() {
        return product;
    }

    /** What "you got it right" looks like, beyond simply getting the product. */
    public String idealNote() {
        return idealNote;
    }

    /** What going wrong looks like, for the sentence on the panel. */
    public String spoilage() {
        return spoilage;
    }

    /**
     * How wide the safe band is between complete reaction and spoiling, in kelvin.
     *
     * <p>Worth being able to ask, because it is the honest measure of how much attention a
     * process demands — and the chlorate window is a fraction of the water one, which is the
     * whole reason feeding chlorate at the rock setting is a mistake rather than an
     * inefficiency.
     */
    public double safeBandK() {
        return spoilK - completeK;
    }

    /**
     * How far inside the safe band above completion the mirror aims, once it stopped being a
     * player's job.
     *
     * <p>0 would sit right at {@link #completeK()} — fragile, since any dip in sun drops back
     * into {@link #onsetK()}-to-{@link #completeK()} territory and the batch stalls PARTIAL. 1
     * would sit right at {@link #spoilK()} — the trap this whole mechanic used to exist to warn
     * against, now with nobody at the wheel to feel it coming. 0.4 lands the same side of
     * {@code RetortAdvice}'s own 0.3-share EXCESS boundary as {@code IDEAL}, so a fully
     * automatic mirror reads exactly as good on the panel as a careful player used to.
     */
    private static final double FOCUS_SAFETY_MARGIN = 0.4;

    /**
     * The kelvin the mirror aims for when this process is loaded — see
     * {@link SolarConcentrator#focusFor}, which turns this into a concentration for whatever
     * sun is actually on the dish right now.
     */
    public double targetK() {
        return completeK + FOCUS_SAFETY_MARGIN * safeBandK();
    }
}
