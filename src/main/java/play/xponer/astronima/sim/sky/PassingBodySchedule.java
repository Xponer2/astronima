package play.xponer.astronima.sim.sky;

import play.xponer.astronima.sim.optics.SkyRotation;

/**
 * When a companion body drifts across the sky (design/sky.md §3's "passing body" row) — the same
 * periodic-and-learnable, Minecraft-free shape as {@link FlareSchedule}, {@link
 * OccultationSchedule} and {@link ImpactSchedule}, plus a real per-event start and end direction
 * so the body crosses a different path each time rather than always retracing the same line.
 *
 * <p>Real content: a belt genuinely has more than one body in it, and one occasionally passing
 * near enough to be seen — visibly tumbling, since irregular small bodies are not spherical and
 * do not present the same face to an observer as they turn — is exactly what "belt density" looks
 * like from the ground, the design's own words for this row.
 */
public final class PassingBodySchedule {

    /** Rarer than the other three events — a genuine encounter close enough to be visible is the
     * least common of the four, matching real relative object densities in a belt. */
    public static final long PERIOD_TICKS = 50_000L;

    public static final long JITTER_TICKS = 15_000L;

    /** Two real seconds. The first fix cut this from 600 ticks (thirty seconds, imperceptibly
     * slow — "тупо крутящийся шарик который стоит на одном месте", just a spinning ball standing
     * in one place) to 100 (five seconds); reported back again as still "слишком больше и
     * некрасиво... ещё быстрее" (too big and ugly, even faster) — a genuinely close flyby reads as
     * a quick streak, not a five-second glide, so this went down again. */
    public static final long DURATION_TICKS = 40L;

    /** How far above or below the local horizon a pass can appear. Real close flybys are
     * typically seen low — rising into view and setting again — not swinging through the zenith,
     * and keeping every pass within this band also keeps it far more likely to cross somewhere
     * near a player's ordinary eye-line instead of requiring them to already be looking straight
     * up. Deliberately not tied to the player's live camera direction: every other event in this
     * file is a pure function of world time and seed specifically so two players see the identical
     * sky at the identical world time (design/sky.md §7's own test-plan requirement) — coupling
     * this to one player's momentary look direction would break that guarantee for whichever
     * player is not looking where the trigger fired. */
    public static final double MAX_ELEVATION_DEGREES = 25.0;

    /** The guaranteed azimuthal separation between {@link #startDirection} and {@link
     * #endDirection} — a real, substantial horizontal sweep every pass, never a coin flip that two
     * independent random points might land close together. (The true angular distance between the
     * two directions can read slightly smaller than this at maximum elevation, since a fixed
     * azimuth step covers less great-circle distance off the horizon than on it — see
     * {@code PassingBodyScheduleTest} for the exact worst-case bound.) */
    public static final double MIN_SWEEP_DEGREES = 70.0;

    public static final double MAX_SWEEP_DEGREES = 140.0;

    public static long eventStartForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** The start tick of the currently active pass, or {@code -1} if none is active. */
    public static long activeEventStart(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            long start = eventStartForCycle(cycle, seed);
            long elapsed = gameTimeTicks - start;
            if (elapsed >= 0 && elapsed < DURATION_TICKS) {
                return start;
            }
        }
        return -1L;
    }

    /** The next pass's own start tick, strictly after {@code afterTick}. */
    public static long nextEventStart(long afterTick, long seed) {
        long cycleIndex = Math.floorDiv(afterTick, PERIOD_TICKS);
        for (long cycle = cycleIndex; cycle <= cycleIndex + 2; cycle++) {
            long start = eventStartForCycle(cycle, seed);
            if (start > afterTick) {
                return start;
            }
        }
        return eventStartForCycle(cycleIndex + 2, seed);
    }

    /** Which cycle a given active start tick belongs to — the inverse of {@link
     * #eventStartForCycle}, needed so a caller holding only the start tick (from {@link
     * #activeEventStart}) can still ask {@link #startDirection}/{@link #endDirection} for that
     * same event's own path.
     *
     * <p><strong>The fallback used to collapse every debug-forced trigger onto the same path.</strong>
     * {@code /astronima sky passing now} (see {@link SkyEventOverride#triggerPassingBodyNow})
     * records the raw tick it was called on, which essentially never matches a real scheduled
     * cycle's own start tick — so this method used to fall through to {@code
     * floorDiv(eventStart, PERIOD_TICKS)}, and every trigger fired within the same ~50000-tick
     * window (an entire ordinary testing session, easily) produced the identical cycle number and
     * therefore the identical path. Reported back exactly that way: "несколько раз ввёл команду...
     * спавнился всегда на одном и том-же месте" (entered the command several times, it always
     * spawned in the same place). Falling back to {@code eventStart} itself instead gives every
     * distinct trigger tick its own distinct hash input — it is not a "real" cycle number, but
     * {@link #startDirection}/{@link #endDirection} only ever use this value for deterministic
     * hashing, never for anything that requires it to be a true cycle index.
     */
    public static long cycleForEventStart(long eventStart, long seed) {
        long approxCycle = Math.floorDiv(eventStart, PERIOD_TICKS);
        for (long cycle = approxCycle - 1; cycle <= approxCycle + 1; cycle++) {
            if (eventStartForCycle(cycle, seed) == eventStart) {
                return cycle;
            }
        }
        return eventStart;
    }

    /** The fixed direction a given cycle's own pass starts from — azimuth uniform around the full
     * horizon, elevation confined to {@code [-MAX_ELEVATION_DEGREES, MAX_ELEVATION_DEGREES]} (see
     * that constant's own comment for why), both deterministic per cycle and seed so the body does
     * not always cross the same path. */
    public static SkyRotation.Vec3 startDirection(long cycleIndex, long seed) {
        double azimuth = fraction(cycleIndex, seed, 1) * 2.0 * Math.PI;
        double elevation = elevationRadians(cycleIndex, seed);
        return horizonDirection(azimuth, elevation);
    }

    /** The fixed direction that same pass ends at — {@link #startDirection}'s own azimuth, swept
     * by a guaranteed {@code [MIN_SWEEP_DEGREES, MAX_SWEEP_DEGREES]} step in a random direction,
     * at the identical elevation, so the whole pass reads as a real horizontal crossing rather
     * than a point that could happen to land almost back where it started. */
    public static SkyRotation.Vec3 endDirection(long cycleIndex, long seed) {
        double startAzimuth = fraction(cycleIndex, seed, 1) * 2.0 * Math.PI;
        double elevation = elevationRadians(cycleIndex, seed);
        double sweepDegrees = MIN_SWEEP_DEGREES
                + fraction(cycleIndex, seed, 3) * (MAX_SWEEP_DEGREES - MIN_SWEEP_DEGREES);
        double sign = fraction(cycleIndex, seed, 4) < 0.5 ? -1.0 : 1.0;
        double endAzimuth = startAzimuth + sign * Math.toRadians(sweepDegrees);
        return horizonDirection(endAzimuth, elevation);
    }

    private static double elevationRadians(long cycleIndex, long seed) {
        return Math.toRadians((fraction(cycleIndex, seed, 2) * 2.0 - 1.0) * MAX_ELEVATION_DEGREES);
    }

    /** Standard azimuth/elevation-to-direction conversion, {@code y} up — the same "up" every
     * other fixed sky-space direction in this file already assumes. */
    private static SkyRotation.Vec3 horizonDirection(double azimuth, double elevation) {
        double horizontalRadius = Math.cos(elevation);
        return new SkyRotation.Vec3(
                horizontalRadius * Math.cos(azimuth), Math.sin(elevation), horizontalRadius * Math.sin(azimuth));
    }

    /** A deterministic {@code [0, 1)} draw for cycle {@code cycleIndex}, distinguished from every
     * other draw the same cycle needs by {@code salt} — one small hash helper backing {@link
     * #startDirection}, {@link #endDirection} and {@link #jitterForCycle} alike, rather than a
     * differently-shaped one-off for each. */
    private static double fraction(long cycleIndex, long seed, long salt) {
        long h = mix((cycleIndex * 97 + salt * 131) ^ (seed * 0x9E3779B97F4A7C15L));
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static long jitterForCycle(long cycleIndex, long seed) {
        long h = mix(cycleIndex ^ (seed * 0x9E3779B97F4A7C15L));
        double unit = (h >>> 11) * (1.0 / (1L << 53));
        return Math.round((unit * 2.0 - 1.0) * JITTER_TICKS);
    }

    /** SplitMix64's own finaliser — see {@link FlareSchedule}'s identical choice for why. */
    private static long mix(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private PassingBodySchedule() {}
}
