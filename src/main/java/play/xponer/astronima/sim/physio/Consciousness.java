package play.xponer.astronima.sim.physio;

import play.xponer.astronima.sim.O2Status;

/**
 * How long you stay useful once the air stops being enough.
 *
 * <p>A person deprived of oxygen does not fail immediately. They run on stored oxygen —
 * in the lungs, dissolved in blood, bound to haemoglobin — and the number that matters
 * is not how long those stores last but the <strong>time of useful consciousness</strong>:
 * how long you can still do something about it. Aviation documents this precisely,
 * because it has to.
 *
 * <pre>
 *   5 500 m   20-30 minutes        13 000 m    9-12 seconds
 *   7 600 m    3-5 minutes         vacuum      9-12 seconds
 *  10 000 m    1-2 minutes
 * </pre>
 *
 * <p>The vacuum figure is <em>circulation time</em> — the few seconds for
 * already-oxygenated blood to reach the brain. It cannot be extended by holding your
 * breath, and in vacuum holding your breath is actively fatal.
 *
 * <p>Modelling this turns suffocation from a state change into an interval you can
 * watch and act inside, which is both more accurate and more frightening than dying
 * between two ticks.
 */
public final class Consciousness {
    /** Reserve is a fraction of full body oxygen stores. */
    public static final double FULL = 1.0;

    /** Thresholds between graded states, as fractions of the reserve. */
    public static final double HYPOXIC_BELOW = 0.60;
    public static final double GREYOUT_BELOW = 0.25;
    public static final double UNCONSCIOUS_BELOW = 0.08;

    /**
     * Seconds of useful consciousness in hard vacuum, from full reserve.
     *
     * <p>Circulation time. Everything else in the model is scaled so this figure comes
     * out right, because it is the one that is least negotiable.
     */
    public static final double VACUUM_TUC_SECONDS = 11.0;

    /**
     * Partial pressure at which the reserve neither drains nor refills.
     *
     * <p>Slightly below comfortable: at the very bottom of the breathable band you are
     * holding station rather than recovering, which is why a marginal room feels
     * marginal instead of safe.
     */
    public static final double NEUTRAL_PPO2_KPA = 16.0;

    /**
     * Seconds of useful consciousness at half of neutral oxygen — 7 600 m, where the
     * table says 3–5 minutes. The second anchor the curve is fitted through.
     */
    public static final double HALF_NEUTRAL_TUC_SECONDS = 230.0;

    /**
     * Steepness of the fall-off.
     *
     * <p>Time of useful consciousness is not linear in oxygen — it collapses. Between
     * 5 500 m and 13 000 m the ambient pressure falls by a factor of three while the
     * time falls by a factor of about a hundred and fifty. A cube law through the two
     * anchors reproduces the whole documented table within about a factor of two, which
     * is as close as a single expression gets to data that spans that range.
     */
    private static final double FALLOFF_EXPONENT = 3.0;

    /** Reoxygenation is slower than desaturation, so a near miss costs something. */
    private static final double RECOVERY_FACTOR = 0.45;

    /** Graded states, each with its own cue in play. */
    public enum State {
        /** Nothing wrong. */
        NORMAL,
        /** Judgement and stamina degrading; audible breathing. */
        HYPOXIC,
        /** Vision narrowing, movement and mining slowed. */
        GREYOUT,
        /**
         * Collapsed and unable to act, but <em>not dead</em> — someone who gets you to
         * air, or a suit that resumes supplying, still saves you.
         */
        UNCONSCIOUS
    }

    public static State classify(double reserve) {
        if (reserve < UNCONSCIOUS_BELOW) {
            return State.UNCONSCIOUS;
        }
        if (reserve < GREYOUT_BELOW) {
            return State.GREYOUT;
        }
        return reserve < HYPOXIC_BELOW ? State.HYPOXIC : State.NORMAL;
    }

    /**
     * Fraction of reserve consumed per second at a given inspired oxygen pressure.
     *
     * <p>Derived from the time of useful consciousness rather than the other way round,
     * because the time is the thing that is actually measured. Everything else follows
     * from {@link #usefulConsciousnessSeconds}.
     */
    public static double drainPerSecond(double ppO2KPa, double activityFactor) {
        double seconds = usefulConsciousnessSeconds(ppO2KPa);
        if (Double.isInfinite(seconds)) {
            return 0;
        }
        return (FULL - GREYOUT_BELOW) / seconds * Math.max(0.5, activityFactor);
    }

    /**
     * Advances the reserve one step.
     *
     * @param activityFactor metabolic multiplier: ~1 at rest, ~3 working hard. Real
     *                       metabolic rate swings this much, which is why standing
     *                       still is a genuine tactic when supply is short.
     */
    public static double step(double reserve, double ppO2KPa, double activityFactor,
                              double dtSeconds) {
        double drain = drainPerSecond(ppO2KPa, activityFactor);
        if (drain > 0) {
            return Math.clamp(reserve - drain * dtSeconds, 0.0, FULL);
        }
        // Breathing air better than neutral refills the stores, but not as fast as bad
        // air empties them.
        double surplus = (ppO2KPa - NEUTRAL_PPO2_KPA) / NEUTRAL_PPO2_KPA;
        double recovery = (FULL - UNCONSCIOUS_BELOW) / VACUUM_TUC_SECONDS
                * RECOVERY_FACTOR * Math.min(1.0, surplus);
        return Math.clamp(reserve + recovery * dtSeconds, 0.0, FULL);
    }

    /**
     * Regulator pressure: what a working suit delivers regardless of the room.
     *
     * <p>Sea-level equivalent rather than the room's own oxygen, because that is what a
     * regulator is for. A suit that is supplying makes the outside irrelevant; a suit
     * that is not is simply not in the loop.
     */
    public static final double REGULATOR_PPO2_KPA = 21.0;

    /**
     * What the wearer is actually breathing, which is the only question the reserve asks
     * about the outside world.
     *
     * <p>Pulled out of the tick so the three cases can be stated once and tested: the
     * suit is feeding you, you are in air of some quality, or you are in vacuum. The
     * middle case is the one that used to be conflated with the others.
     *
     * @param inEnclosure false means no enclosure at all, which is hard vacuum
     */
    public static double inspiredPpO2(boolean suitSupplying, boolean inEnclosure,
                                      double roomPpO2KPa) {
        if (suitSupplying) {
            return REGULATOR_PPO2_KPA;
        }
        return inEnclosure ? Math.max(0, roomPpO2KPa) : 0.0;
    }

    /**
     * Seconds of useful consciousness from a full reserve at a given pressure.
     *
     * <p>Exists so the aviation figures can be asserted directly in a test rather than
     * approximated by simulating and hoping.
     */
    public static double usefulConsciousnessSeconds(double ppO2KPa) {
        if (ppO2KPa >= NEUTRAL_PPO2_KPA) {
            return Double.POSITIVE_INFINITY;
        }
        double oxygen = Math.max(0, ppO2KPa);
        // Ratio of what you have to what you are missing: zero in vacuum, and rising
        // without bound as the air approaches breathable.
        double ratio = oxygen / (NEUTRAL_PPO2_KPA - oxygen);
        return VACUUM_TUC_SECONDS
                + HALF_NEUTRAL_TUC_SECONDS * Math.pow(ratio, FALLOFF_EXPONENT);
    }

    /** How dark the screen goes: nothing until greyout, total once unconscious. */
    public static float veilStrength(double reserve) {
        if (reserve >= GREYOUT_BELOW) {
            return 0f;
        }
        double span = GREYOUT_BELOW - UNCONSCIOUS_BELOW;
        return (float) Math.clamp((GREYOUT_BELOW - reserve) / span, 0.0, 1.0);
    }

    /** Cross-check: the reserve model must agree with the room-level oxygen bands. */
    public static boolean agreesWith(O2Status status, double reserve) {
        boolean roomSaysBad = status != O2Status.NORMAL && status != O2Status.OXYGEN_TOXICITY;
        return !roomSaysBad || reserve < FULL;
    }

    private Consciousness() {}
}
