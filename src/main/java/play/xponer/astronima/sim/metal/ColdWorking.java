package play.xponer.astronima.sim.metal;

/**
 * Working metal without heat, which is the only way it can be worked out here.
 *
 * <h2>Cold welding</h2>
 * On Earth two clean pieces of metal pressed together do not join, because every metal
 * surface carries an oxide layer within microseconds of meeting air and that layer is
 * what keeps them apart. <strong>In vacuum there is no oxide layer</strong>, and clean
 * surfaces brought into contact simply bond — the atoms cannot tell which side they
 * belong to. It is a real spacecraft engineering hazard rather than a curiosity, and
 * the reason deployment mechanisms fly with dry-film coatings.
 *
 * <p>So loose grains can be pressed into a solid billet with nothing but force,
 * provided the work is done outside. That is the asteroid giving something back: no
 * fire, but hard vacuum.
 *
 * <h2>Work hardening</h2>
 * Hammering deforms the grain structure and multiplies dislocations. Those
 * dislocations obstruct each other, so the metal gets <em>harder</em> and at the same
 * time <em>less ductile</em> — it can absorb less further deformation before it
 * fractures. Every smith has known the consequence: cold work has a limit, past which
 * the piece must be annealed, and annealing needs heat there is none of here.
 *
 * <p>That gives a genuine optimum with failure on both sides, which is the mechanic:
 * stop too early and the tool is soft, too late and it cracks.
 */
public final class ColdWorking {
    /** Hardness of freshly consolidated metal, before any working. */
    public static final double ANNEALED_HARDNESS = 0.15;

    /** Ductility a fresh billet starts with — how much working it can absorb. */
    public static final double FRESH_DUCTILITY = 1.0;

    /** Below this remaining ductility the next blow risks cracking the piece. */
    public static final double CRACK_RISK_DUCTILITY = 0.2;

    /** Hardness a tool head needs to be worth fitting to a haft. */
    public static final double USABLE_HARDNESS = 0.55;

    /** Typical nickel fraction of ordinary chondritic metal. */
    public static final double BASE_NICKEL = 0.07;

    /** Nickel fraction from a metal-rich seam: harder, tougher, less forgiving. */
    public static final double SEAM_NICKEL = 0.17;

    /**
     * How much a blow raises hardness, before diminishing returns.
     *
     * <p>Nickel raises the work-hardening rate: a higher-nickel alloy strengthens
     * faster and therefore runs out of ductility sooner, which is exactly the trade
     * that makes seam metal better <em>and</em> harder to get right.
     */
    public static double hardeningRate(double nickelFraction) {
        return 0.055 * (1.0 + nickelFraction * 3.0);
    }

    /**
     * Advances one blow.
     *
     * @param blowStrength 0..1 — a light tap or a heavy strike
     * @return the state after the blow
     */
    /** What a crack costs a finished head, as a fraction of the quality it would have had. */
    public static final double CRACKED_QUALITY_PENALTY = 0.55;

    public static Piece strike(Piece piece, double blowStrength, double nickelFraction) {
        if (piece.cracked()) {
            return piece;
        }
        double strength = Math.clamp(blowStrength, 0.05, 1.0);

        // Hardening saturates: each blow buys less than the last, because the
        // dislocations already there are what obstruct the new ones.
        double headroom = 1.0 - piece.hardness();
        double gain = hardeningRate(nickelFraction) * strength * headroom;

        // Ductility is spent faster by heavy blows than by the hardness they buy —
        // which is why a fast job is a worse job, not merely a quicker one.
        double spent = strength * strength * 0.09 * (1.0 + nickelFraction * 2.0);
        double ductility = piece.ductility() - spent;

        if (ductility <= 0) {
            // Out of ductility mid-blow: the piece fractures.
            return new Piece(piece.hardness(), 0, true);
        }
        return new Piece(Math.min(1.0, piece.hardness() + gain), ductility, false);
    }

    /** A billet being worked. */
    public record Piece(double hardness, double ductility, boolean cracked) {
        public static Piece fresh() {
            return new Piece(ANNEALED_HARDNESS, FRESH_DUCTILITY, false);
        }

        /** True once the piece is hard enough to serve as a tool head. */
        public boolean isUsable() {
            return hardness >= USABLE_HARDNESS;
        }

        /**
         * True when this head is worth having rather than merely salvageable.
         *
         * <p>A cracked piece still makes a tool — it is metal, and it holds a haft —
         * but it will chip in use. Throwing it away instead would cost the player a
         * whole billet for one blow too many, which teaches nothing that living with
         * the bad tool does not teach better.
         */
        public boolean isSound() {
            return !cracked && hardness >= USABLE_HARDNESS;
        }

        /** True when the next heavy blow is likely to be the last one. */
        public boolean isRisky() {
            return !cracked && ductility <= CRACK_RISK_DUCTILITY;
        }
    }

    /**
     * What the smith should do next, in words rather than numbers.
     *
     * <p>"Hardness 0.42, ductility 0.55" is precise and means nothing to someone who
     * has not read a metallurgy text. The verdict is a band of the same numbers, so it
     * can never disagree with the gauges beside it.
     */
    public enum Verdict {
        SOFT("Still soft - keep working it"),
        READY("Hard enough to use - stopping now is safe"),
        RISKY("Hard, but nearly out of give - one more may crack it"),
        CRACKED("Cracked - the metal is spent");

        private final String advice;

        Verdict(String advice) {
            this.advice = advice;
        }

        public String advice() {
            return advice;
        }
    }

    public static Verdict verdict(Piece piece) {
        if (piece.cracked()) {
            return Verdict.CRACKED;
        }
        if (piece.isRisky()) {
            return Verdict.RISKY;
        }
        return piece.isUsable() ? Verdict.READY : Verdict.SOFT;
    }

    /**
     * Quality of a finished head, 0..1, from how well it was worked.
     *
     * <p>Rewards hardness but also rewards <em>leaving something in reserve</em>: a
     * head taken right to the edge of fracture is hard and fragile, and will not last
     * as long as one stopped a few blows earlier. There is no way to score full marks
     * by simply hitting it more.
     */
    public static double quality(Piece piece) {
        double hardnessScore = Math.clamp(
                (piece.hardness() - USABLE_HARDNESS) / (1.0 - USABLE_HARDNESS), 0.0, 1.0);
        double reserveScore = Math.clamp(piece.ductility() / 0.4, 0.0, 1.0);
        double score = Math.clamp(0.35 + 0.45 * hardnessScore + 0.2 * reserveScore, 0.0, 1.0);
        // A cracked piece is hard — that is how it cracked — but the crack is where it
        // will fail, so it is worth markedly less than the same hardness sound.
        return piece.cracked() ? score * CRACKED_QUALITY_PENALTY : score;
    }

    private ColdWorking() {}
}
