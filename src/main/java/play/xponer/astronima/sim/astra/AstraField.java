package play.xponer.astronima.sim.astra;

/**
 * design/astra-core.md §4.1's whole mechanic, made real: extraction depletes the local field,
 * and a depleted (burnt) volume refills only by diffusion from its own surroundings — never
 * instantly, never on a fixed schedule. This is the first piece of the astra-core.md frame to
 * become code; the field's own world geometry (the depth gradient, per-chunk storage, the
 * collector item/block) is a separate, larger leaf and deliberately not this one's job — the
 * same "land the model before the block that owns it" shape {@code sim/magic/Coherence} was
 * given before the coherence meter existed (rule 13).
 *
 * <h2>What the constants are, and are not</h2>
 * Not measured — astra-core.md §3 says so explicitly: "No numbers here on purpose (rule 41):
 * the compression ratio, the depth curve, the refill rate and the cycle period are all player
 * experiences before they are constants." What is fixed here is only the <strong>shape</strong>
 * the mechanic takes, which astra-core.md does commit to:
 * <ul>
 *   <li>Extraction depletes <strong>multiplicatively</strong>, not by a flat amount — a sweep of
 *       a rich site draws more astra in absolute terms than the same sweep of a poor one, which
 *       is what "concentrating" a thin field actually means, and it is why a swept site can
 *       never go negative: its own current density is the hard ceiling on what one sweep can
 *       take.</li>
 *   <li>Refill is exponential toward the surrounding baseline, and <strong>faster where the
 *       baseline itself is richer</strong> (§3: "diffusion... faster where the surroundings are
 *       richer (so: faster deeper)") — not a single flat time constant everywhere.</li>
 * </ul>
 * {@link #SWEEP_FRACTION} and {@link #REFILL_TIME_CONSTANT_AT_FULL_RICHNESS_S} are first-pass
 * numbers (rule 41) — real decisions for whichever leaf plan builds the collector and the depth
 * curve, not settled here.
 */
public final class AstraField {

    /** How large one sweep's draw is, as a fraction of whatever density is there right now. */
    private static final double SWEEP_FRACTION = 0.6;

    /** Seconds for a fully-depleted volume to refill about two-thirds of the way back to its own
     *  baseline, at baseline richness 1.0. Richer baselines refill faster than this; poorer ones
     *  slower — see {@link #refillTimeConstantSeconds}. */
    private static final double REFILL_TIME_CONSTANT_AT_FULL_RICHNESS_S = 600.0;

    /** Below this fraction of baseline, a volume reads "burnt" (astra-core.md §4.1's own word) —
     *  not exactly zero, since diffusion approaches its baseline asymptotically and never
     *  technically finishes. */
    private static final double BURNT_THRESHOLD_FRACTION = 0.1;

    private AstraField() {}

    /** The density left behind after one sweep — {@link #SWEEP_FRACTION} of whatever was there
     *  leaves with the player, and the ground keeps the rest. Never negative: a volume already
     *  at zero stays at zero, it does not owe the next sweep anything. */
    public static double densityAfterSweep(double currentDensity) {
        if (currentDensity <= 0.0) {
            return 0.0;
        }
        return currentDensity * (1.0 - SWEEP_FRACTION);
    }

    /** What one sweep actually collects, in the same units as density — exactly what the ground
     *  lost, so a caller never has to keep {@link #densityAfterSweep} and this in sync by hand. */
    public static double swept(double currentDensity) {
        return currentDensity - densityAfterSweep(currentDensity);
    }

    /** How long a volume takes to refill toward {@code baselineDensity} — a real diffusion
     *  property (a richer baseline pulls harder on its own depleted neighbour), not a flat
     *  constant everywhere. {@link Double#POSITIVE_INFINITY} at a zero baseline: a burnt site
     *  next to an equally burnt one never refills either, because there is nothing to diffuse
     *  from. */
    public static double refillTimeConstantSeconds(double baselineDensity) {
        if (baselineDensity <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return REFILL_TIME_CONSTANT_AT_FULL_RICHNESS_S / baselineDensity;
    }

    /** {@code currentDensity} after {@code elapsedSeconds} of diffusion refill toward
     *  {@code baselineDensity} — an exponential approach that never overshoots past the
     *  baseline and never drops below where it started (refill only ever adds; {@link
     *  #densityAfterSweep} is the only thing that removes). */
    public static double refilled(double currentDensity, double baselineDensity, double elapsedSeconds) {
        if (elapsedSeconds <= 0.0 || currentDensity >= baselineDensity) {
            return currentDensity;
        }
        double timeConstant = refillTimeConstantSeconds(baselineDensity);
        if (Double.isInfinite(timeConstant)) {
            return currentDensity;
        }
        double progress = 1.0 - Math.exp(-elapsedSeconds / timeConstant);
        return currentDensity + (baselineDensity - currentDensity) * progress;
    }

    /** Whether a volume reads "burnt" right now — usable astra effectively gone, per {@link
     *  #BURNT_THRESHOLD_FRACTION} of its own baseline. A volume with no baseline at all (nothing
     *  to diffuse from) is not "burnt", it simply never had anything — a different, honester
     *  state a caller should read from the baseline itself. */
    public static boolean isBurnt(double currentDensity, double baselineDensity) {
        return baselineDensity > 0.0 && currentDensity < baselineDensity * BURNT_THRESHOLD_FRACTION;
    }
}
