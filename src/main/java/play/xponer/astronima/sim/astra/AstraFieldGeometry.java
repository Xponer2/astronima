package play.xponer.astronima.sim.astra;

/**
 * The astra field's baseline density at a real position — design/astra-field-live.md §1, the
 * "world geometry" half {@link AstraField}'s own class doc names as deliberately not its job.
 * Minecraft-free (rule 1): takes {@code shellCoordinate}, the same normalised 0-at-centre,
 * 1-at-skin radial coordinate {@link play.xponer.astronima.sim.world.AsteroidBody#shellCoordinate}
 * already provides for ore banding, so a caller reduces a real {@code BlockPos} into a scalar
 * exactly once, the same way {@code AsteroidChunkGenerator} already does for ore (rule 46 — one
 * radial model, read from two consumers, not two).
 *
 * <h2>Where the two real inputs actually come from</h2>
 * <ul>
 *   <li><strong>Radial distance from the core.</strong> design/asteroid-body.md §5: "the field's
 *       gradient is radial, not vertical" — {@code AsteroidBody.shellCoordinate(r, y)} is exactly
 *       that gradient's own coordinate, already built for ore and reused here rather than
 *       re-derived, because the core sits at that same body's centre
 *       (design/astra-core.md §2.4).</li>
 *   <li><strong>Solar phase.</strong> design/astra-core.md §2.2: "core output tracks solar
 *       activity." The only real solar-activity signal this mod has today is
 *       {@link play.xponer.astronima.sim.sky.FlareSchedule#intensityAt} — there is no separate,
 *       slower solar-minimum/maximum cycle built (checked directly, rule 2, before writing this:
 *       {@code sim/sky} has no such class) — so "solar phase" here honestly means "current flare
 *       intensity," not a system that does not exist yet. A real MC-side caller must read it
     *       through {@link play.xponer.astronima.sim.sky.SkyEventOverride#resolveFlareIntensity},
     *       never {@code FlareSchedule} directly, per that class's own doc comment.</li>
 * </ul>
 *
 * <h2>The curve's shape, per design/astra-field-live.md §1.2 (rule 41)</h2>
 * Imperceptible at the rim ({@code shellCoordinate = 1}), rising sharply and non-linearly toward
 * the centre rather than ramping evenly — {@link #RADIAL_FALLOFF_EXPONENT} is what makes most of
 * the traversable rock read as "working depth" instead of splitting the range evenly between
 * imperceptible and rich. Not measured, and not meant to read as measured: a first-pass number,
 * exactly like {@link AstraField}'s own {@code SWEEP_FRACTION}, tuned by playtest at the leaf that
 * builds the first real instrument to read it.
 */
public final class AstraFieldGeometry {

    /** Higher = the field stays low for more of the body's radius and rises later, closer to the
     *  centre. Chosen, not measured — see the class doc's rule-41 note. */
    private static final double RADIAL_FALLOFF_EXPONENT = 3.0;

    /** How much a flare at full intensity multiplies the baseline on top of — "flares are both
     *  feed and threat" (design/astra-core.md §2.2) needs the feed half to actually move the
     *  reading, not just the threat half. A first-pass number, not measured. */
    private static final double FLARE_DENSITY_BOOST = 1.0;

    private AstraFieldGeometry() {}

    /**
     * The field's baseline density at a position described by {@code shellCoordinate} (0 at the
     * core, 1 at and past the rim — {@link play.xponer.astronima.sim.world.AsteroidBody#isInside}
     * governs whether a real position is even inside the body at all; a caller past the rim should
     * pass {@code 1.0}, never extrapolate past it) and {@code flareIntensity01} (0 outside any
     * flare, ramping to 1 at a flare's peak — {@link
     * play.xponer.astronima.sim.sky.FlareSchedule#intensityAt}).
     *
     * <p>Both inputs are clamped to their real range before use, so a caller passing a
     * slightly-out-of-range value from floating-point slop never produces a density outside what
     * the shape itself allows for that input, other than the flare boost's own deliberate headroom
     * above 1.0 at the core during a flare peak — a real spike, not a bug.
     */
    public static double baselineDensity(double shellCoordinate, double flareIntensity01) {
        double clampedShell = clamp01(shellCoordinate);
        double clampedFlare = clamp01(flareIntensity01);
        double radial = Math.pow(1.0 - clampedShell, RADIAL_FALLOFF_EXPONENT);
        return radial * (1.0 + FLARE_DENSITY_BOOST * clampedFlare);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
