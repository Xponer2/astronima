package play.xponer.astronima.client.screen;

/**
 * Pure time-and-index-to-value functions for the atlas's motion layer
 * (design/astra-atlas-redesign.md §3, §4, §5) — every animated quantity that is not plain
 * geometry. Deliberately free of Minecraft/LDLib2/{@code org.joml} types for the same reason
 * {@link AtlasGeometry} is: the plain JUnit runner cannot load a class with those imports at all
 * (confirmed building this leaf).
 *
 * <p>{@link AtlasUi} calls these from inside a {@code DynamicTexture} supplier, feeding it
 * {@code System.nanoTime()} converted to seconds — never from a place that would trigger a
 * rebuild of the element tree (design/astra-atlas-redesign.md §1.1's rule: "Animate the texture a
 * supplier returns. Never rebuild the element tree to animate.").
 */
public final class AtlasMotion {

    private AtlasMotion() {}

    /** design/astra-atlas-redesign.md §5.1: node {@code nodeIndex} appears
     *  {@code nodeIndex * perNodeSeconds} after the opening sequence's node-reveal step starts.
     *  Deliberately not a constant — §10's own second mutation ("return a constant delay for
     *  every index") exists exactly to guard this: without the multiply, every node would appear
     *  in the same frame and the staggered assembly would read as one flat pop. */
    public static double staggerDelaySeconds(int nodeIndex, double perNodeSeconds) {
        return nodeIndex * perNodeSeconds;
    }

    /**
     * §3's {@code EMISSION} row: "the core glow breathes... ±{@code amplitude} brightness" around
     * a fully-lit baseline of {@code 1.0}. Continuous and periodic by construction (a sine), but
     * the raw value still reads as high as {@code 1 + amplitude} for a real slice of every cycle —
     * past what an already-fully-lit colour can represent. §10's third mutation ("remove the
     * clamp on breath amplitude") targets exactly this: without it, a brightness fraction above
     * {@code 1.0} later multiplied into a 0-255 colour channel overflows and wraps, so the glow
     * would visibly flicker dark at the very peak of its own "breath" once per cycle — the bug
     * design/astra-atlas-redesign.md §9 warns "only shows up once per cycle."
     */
    public static double breathingBrightnessFraction(double timeSeconds, double periodSeconds, double amplitude) {
        double phase = 2.0 * Math.PI * (timeSeconds / periodSeconds);
        double raw = 1.0 + amplitude * Math.sin(phase);
        return Math.clamp(raw, 0.0, 1.0);
    }

    /** §3's {@code REFLECTION} row: one star-fleck's own independent twinkle, naturally bounded to
     *  {@code [0, 1]} by construction — this one oscillates around its own midpoint rather than
     *  around a baseline already at the ceiling, so unlike {@link #breathingBrightnessFraction} it
     *  never needs a defensive clamp to stay legal. */
    public static double twinkleBrightnessFraction(double timeSeconds, double periodSeconds, double phaseOffsetRadians) {
        double phase = 2.0 * Math.PI * (timeSeconds / periodSeconds) + phaseOffsetRadians;
        return 0.5 + 0.5 * Math.sin(phase);
    }

    /** §3's {@code PLANETARY} row: the ring's own rotation, degrees, always in {@code [0, 360)} —
     *  in particular exactly {@code 0.0} (not {@code 360.0}) at a whole-period boundary. */
    public static double ringRotationDegrees(double timeSeconds, double periodSeconds) {
        double turns = timeSeconds / periodSeconds;
        double fraction = turns - Math.floor(turns);
        return fraction * 360.0;
    }

    /** §3's {@code GALAXY} row: "a slow shear... plus a steady bulge" — a skew angle in degrees,
     *  naturally bounded to {@code [-maxShearDegrees, maxShearDegrees]} by construction. */
    public static double galaxyShearDegrees(double timeSeconds, double periodSeconds, double maxShearDegrees) {
        double phase = 2.0 * Math.PI * (timeSeconds / periodSeconds);
        return maxShearDegrees * Math.sin(phase);
    }

    /** §4's held-claim row: the travelling pulse's own position along the stroke, {@code [0, 1)},
     *  restarting every {@code periodSeconds} — a sawtooth, not a sine, since the pulse runs one
     *  direction and starts over rather than bouncing back and forth. */
    public static double pulsePositionFraction(double timeSeconds, double periodSeconds) {
        double cycles = timeSeconds / periodSeconds;
        return cycles - Math.floor(cycles);
    }

    /** §4's held/outlined rows: one stroke segment's small perpendicular wander —
     *  {@code amplitude * sin(time * rate + index * phasePerSegment)} — bounded to
     *  {@code [-amplitude, amplitude]} by construction, and different for every
     *  {@code segmentIndex} at a given instant, which is the actual "переливается" ask: a drawn
     *  line that visibly wanders along its own length, not one rigid segment moving as a whole. */
    public static double segmentWanderOffset(double timeSeconds, int segmentIndex,
                                              double rate, double phasePerSegment, double amplitude) {
        return amplitude * Math.sin(timeSeconds * rate + segmentIndex * phasePerSegment);
    }

    /** §4's refuted-claim row: "frozen, with a single slow shudder every ~6s" — zero everywhere
     *  except a brief decaying wiggle right after each {@code periodSeconds} boundary, so the
     *  stroke reads as stopped rather than merely slow. Bounded to {@code [-amplitude, amplitude]}
     *  by construction: the envelope only ever shrinks the sine, never grows past it. */
    public static double refutedShudderOffset(double timeSeconds, double periodSeconds,
                                               double shudderDurationSeconds, double amplitude) {
        double cycles = timeSeconds / periodSeconds;
        double intoThisCycle = (cycles - Math.floor(cycles)) * periodSeconds;
        if (intoThisCycle >= shudderDurationSeconds) {
            return 0.0;
        }
        double envelope = 1.0 - intoThisCycle / shudderDurationSeconds; // 1 -> 0 across the shudder
        double wiggle = Math.sin(intoThisCycle / shudderDurationSeconds * Math.PI * 4.0);
        return amplitude * envelope * wiggle;
    }

    // ---- §5.4 payoff. Every function below takes elapsedSeconds since the instant an object was
    // just identified, and every one is clamped to its own duration, so a caller can keep reading
    // it forever after the moment passes without separately tracking "is this still playing" -
    // past its duration each one just reports its own settled resting value. Use stillPlaying
    // below to decide whether an overlay is worth building at all.

    /** §5.4 step 2: "outline -> lit over ~0.5s, overshooting to 1.25x scale before settling" — a
     *  symmetric bump from {@code 1.0} up to {@code overshootScale} and back down to exactly
     *  {@code 1.0} at {@code durationSeconds}, held at {@code 1.0} after. */
    public static double bloomScale(double elapsedSeconds, double durationSeconds, double overshootScale) {
        double t = Math.clamp(elapsedSeconds / durationSeconds, 0.0, 1.0);
        return 1.0 + (overshootScale - 1.0) * Math.sin(Math.PI * t);
    }

    /** §5.4 step 3: "a ring ripples outward... and fades" — {@code 0} (just born, invisible) to
     *  {@code 1} (fully expanded, fully faded) at {@code durationSeconds}, held there after. */
    public static double rippleProgress(double elapsedSeconds, double durationSeconds) {
        return Math.clamp(elapsedSeconds / durationSeconds, 0.0, 1.0);
    }

    /** §5.4 step 4: "every claim stroke touching that node charges from that end toward the
     *  other" — {@code 0} (nothing charged yet) to {@code 1} (fully charged) at
     *  {@code durationSeconds}, held there after. A caller multiplies this by a stroke's own
     *  segment count and floors it to get "how many segments, counted from the identified end,
     *  are lit right now." */
    public static double chargeFraction(double elapsedSeconds, double durationSeconds) {
        return Math.clamp(elapsedSeconds / durationSeconds, 0.0, 1.0);
    }

    /** §5.4 step 1: "the strip's matched line locks — its bar flares white, then settles to its
     *  own wavelength colour" — {@code 1} (fully white) decaying to {@code 0} (fully settled) by
     *  {@code durationSeconds}; an ease-out (the remaining fraction squared) so the flare snaps in
     *  and settles gradually rather than fading at a constant rate. */
    public static double stripFlashFraction(double elapsedSeconds, double durationSeconds) {
        double t = Math.clamp(elapsedSeconds / durationSeconds, 0.0, 1.0);
        double remaining = 1.0 - t;
        return remaining * remaining;
    }

    /** Whether a payoff-driven effect above is still worth drawing at all — {@code false} both
     *  before it starts and once it has fully settled, so a caller can skip building the extra
     *  overlay element rather than evaluate (and draw) an effectively-invisible one forever. */
    public static boolean stillPlaying(double elapsedSeconds, double durationSeconds) {
        return elapsedSeconds >= 0.0 && elapsedSeconds < durationSeconds;
    }
}
