package play.xponer.astronima.sim.metal;

import play.xponer.astronima.sim.machine.WorkState;

/**
 * The fluidized-bed reactor's control surface, MC-free (rule 1): the one dial the player trims and
 * the regime it reads back as.
 *
 * <p>Kept out of the block entity so it can be unit-tested without a running game — the block just
 * delegates. Two decisions live here: how the 0..1 dial maps to a drum speed, and how the
 * {@link CentrifugalBed.Regime} that speed produces becomes the {@link WorkState} the player sees
 * on the block. The physics itself is {@link CentrifugalBed}; this is the machine's interface to it.
 */
public final class FluidizedBedControl {

    /** Slowest and fastest the drum will turn, rpm — the ends of the dial. */
    public static final double MIN_RPM = 1.0;
    public static final double MAX_RPM = 1000.0;

    /**
     * Ilmenite in one crushed-ilmenite item, in moles — sized so a clean batch gives a stack of
     * iron powder and titania.
     */
    public static final double CHARGE_ILMENITE_MOL = 8.0;

    /** Fraction of the possible movement one reduction step performs. */
    public static final double STEP_RATE = 0.05;

    /**
     * Geometric map from the 0..1 dial to a drum speed.
     *
     * <p>Geometric, not linear, for the same reason the crusher's is: the fluidization window
     * spans a wide ratio of speeds and slides with the grind, so a linear dial would spend most of
     * its travel in speeds no grind ever wants. The ends span {@link #MIN_RPM}..{@link #MAX_RPM},
     * chosen so the good band for a grain-size feed sits in the middle of the dial with a failure
     * at each end — verified against the real regime velocities in {@code FluidizedBedTest}.
     */
    public static double rpmFor(double dial) {
        double t = Math.clamp(dial, 0.0, 1.0);
        return MIN_RPM * Math.pow(MAX_RPM / MIN_RPM, t);
    }

    /** Bisection iterations per edge — comfortably past double precision on a 0..1 range. */
    private static final int BISECTION_STEPS = 60;

    /**
     * The dial setting whose spin sits in the middle of the boiling band for this grind — the
     * machine's own answer to "match the spin to the grind", no longer a player's job.
     *
     * <p>Bisection rather than a closed form: {@link CentrifugalBed}'s two bracketing velocities
     * come from the Wen &amp; Yu / Haider &amp; Levenspiel correlations, which have no algebraic
     * inverse. Both move strictly with spin for a fixed grind — more artificial gravity always
     * needs more flow to either lift or entrain a grain of a given size — so the regime a dial
     * produces walks {@link CentrifugalBed.Regime#BLOWING_OUT} → {@link CentrifugalBed.Regime#BOILING}
     * → {@link CentrifugalBed.Regime#PACKED} exactly once as the dial sweeps 0..1, and a bisection
     * on each edge finds the band to machine precision in a few dozen steps — cheap enough to run
     * fresh whenever the feed's grind is asked about, rather than cached and invalidated.
     *
     * <p>If a grind has no boiling band at all within the drum's range (too fine or too coarse for
     * any spin to match), the two edges cross and this returns whatever point they converge on —
     * no worse than a player who could not find one either.
     */
    public static double matchedDialFor(double particleSizeMicrons) {
        // Edge A: smallest dial where the bed has stopped blowing out.
        double aLo = 0.0;
        double aHi = 1.0;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (aLo + aHi) / 2.0;
            if (CentrifugalBed.regimeAt(particleSizeMicrons, rpmFor(mid))
                    == CentrifugalBed.Regime.BLOWING_OUT) {
                aLo = mid;
            } else {
                aHi = mid;
            }
        }
        // Edge B: largest dial where the bed is not yet packed.
        double bLo = 0.0;
        double bHi = 1.0;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (bLo + bHi) / 2.0;
            if (CentrifugalBed.regimeAt(particleSizeMicrons, rpmFor(mid))
                    == CentrifugalBed.Regime.PACKED) {
                bHi = mid;
            } else {
                bLo = mid;
            }
        }
        return Math.clamp((aHi + bLo) / 2.0, 0.0, 1.0);
    }

    /**
     * The regime the player reads on the block, in the order they can act on it.
     *
     * <p>A slot stall (no feed, no room) is passed straight through — the spin is irrelevant with
     * nothing in the drum. Otherwise: no hydrogen is its own stall ahead of the spin, because the
     * remedy is a supply not the dial; then the two spin failures, {@link WorkState#PACKED} and
     * {@link WorkState#BLOWING_OUT}; and a boiling bed is just working.
     *
     * @param general     the generic stall from the base machine (STARVED / BLOCKED / CRANKING / CREEPING)
     * @param hasHydrogen the room holds hydrogen to reduce with
     * @param regime      the bed's fluidization regime at the current grind and spin
     */
    public static WorkState workStateFor(WorkState general, boolean hasHydrogen,
                                         CentrifugalBed.Regime regime) {
        if (!general.isWorking()) {
            return general;
        }
        if (!hasHydrogen) {
            return WorkState.NO_REAGENT;
        }
        return switch (regime) {
            case PACKED -> WorkState.PACKED;
            case BLOWING_OUT -> WorkState.BLOWING_OUT;
            case BOILING -> general;
        };
    }

    private FluidizedBedControl() {}
}
