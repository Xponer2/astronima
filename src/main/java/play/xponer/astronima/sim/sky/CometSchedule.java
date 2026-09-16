package play.xponer.astronima.sim.sky;

import play.xponer.astronima.sim.optics.SkyRotation;

/**
 * When a comet is visible (design/sky.md §3's own "comet" row) — the same periodic-and-learnable,
 * Minecraft-free shape as {@link FlareSchedule}, {@link OccultationSchedule}, {@link
 * ImpactSchedule} and {@link PassingBodySchedule}, but at a genuinely different scale: this event
 * is the design's own "Calendar" row, appearing over real days, holding for real days, and fading
 * over real weeks — the slow arc is the actual content, not incidental pacing.
 *
 * <p>Real content: a comet is real content once it approaches close enough to the Sun for solar
 * heating to sublimate its ices, growing a coma and a tail that always points directly away from
 * the Sun — the design's own callout: "the detail almost everyone gets wrong" is trailing it
 * behind the comet's own direction of travel instead. This class only owns *when* a comet is
 * visible and *how strongly*; the tail's own real direction is computed in {@code
 * AsteroidSkyRenderer#drawComet} from the Sun's actual current position, not here, since that
 * needs the live Sun angle this Minecraft-free class deliberately has no access to.
 */
public final class CometSchedule {

    /** Roughly every 62 real Minecraft days — rarer than any of the four faster sky events,
     * matching how comets bright enough to see with the naked eye are themselves a comparatively
     * rare treat, not a weekly occurrence. */
    public static final long PERIOD_TICKS = 1_500_000L;

    public static final long JITTER_TICKS = 400_000L;

    /** Two real days growing toward peak brightness as the comet nears the Sun. */
    public static final long RAMP_TICKS = 48_000L;

    /** Four real days near peak. */
    public static final long HOLD_TICKS = 96_000L;

    /** Eight real days fading as it recedes — real comets fade slower than they brighten, the
     * tail shrinking as outgassing drops off. */
    public static final long DECAY_TICKS = 192_000L;

    /** A little over two real weeks, start to finish. */
    public static final long DURATION_TICKS = RAMP_TICKS + HOLD_TICKS + DECAY_TICKS;

    /** A debug-forced preview cannot realistically run out its own real two-week arc — nobody
     * tests by watching for a fortnight. {@link SkyEventOverride#triggerCometNow} plays back the
     * identical ramp/hold/decay *shape* (via {@link #previewGrowthForElapsed}) compressed into
     * twenty real seconds instead, the same reasoning {@code OccultationSchedule}'s own debug
     * preview sweep already established for a different event. */
    public static final long PREVIEW_RAMP_TICKS = 100L;

    public static final long PREVIEW_HOLD_TICKS = 200L;

    public static final long PREVIEW_DECAY_TICKS = 100L;

    public static final long PREVIEW_DURATION_TICKS = PREVIEW_RAMP_TICKS + PREVIEW_HOLD_TICKS + PREVIEW_DECAY_TICKS;

    public static long eventStartForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** Current growth/brightness, {@code 0.0} outside any comet's own window, ramping
     * {@code 0 -> 1 -> 0} through one otherwise — checks the current cycle and both neighbours,
     * since jitter can push a comet's real start earlier or later than its cycle index alone
     * would suggest. */
    public static double growthAt(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        double best = 0.0;
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            long start = eventStartForCycle(cycle, seed);
            long elapsed = gameTimeTicks - start;
            if (elapsed >= 0 && elapsed < DURATION_TICKS) {
                best = Math.max(best, growthForElapsedTicks(elapsed));
            }
        }
        return best;
    }

    /** The real schedule's own ramp/hold/decay shape, exposed directly so a caller already
     * holding a known active start tick (real or debug-forced) can compute growth without
     * re-deriving it through {@link #growthAt}'s own cycle search. */
    public static double growthForElapsedTicks(long elapsed) {
        return rampHoldDecayShape(elapsed, RAMP_TICKS, HOLD_TICKS, DECAY_TICKS);
    }

    /** The compressed preview shape — see {@link #PREVIEW_DURATION_TICKS}'s own comment. */
    public static double previewGrowthForElapsed(long elapsed) {
        return rampHoldDecayShape(elapsed, PREVIEW_RAMP_TICKS, PREVIEW_HOLD_TICKS, PREVIEW_DECAY_TICKS);
    }

    private static double rampHoldDecayShape(long elapsed, long ramp, long hold, long decay) {
        if (elapsed < ramp) {
            return elapsed / (double) ramp;
        }
        if (elapsed < ramp + hold) {
            return 1.0;
        }
        long decayElapsed = elapsed - ramp - hold;
        return Math.max(0.0, 1.0 - decayElapsed / (double) decay);
    }

    /** The start tick of the currently active apparition, or {@code -1} if none is active. */
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

    /** The next apparition's own start tick, strictly after {@code afterTick}. */
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
     * #eventStartForCycle}. Falls back to the raw {@code eventStart} tick itself (not {@code
     * floorDiv(eventStart, PERIOD_TICKS)}) when no real cycle produced it, matching {@link
     * PassingBodySchedule#cycleForEventStart}'s own fix: a debug-forced trigger's own tick almost
     * never matches a real cycle, and bucketing every forced trigger within the same
     * ~1500000-tick window onto one identical cycle number is exactly the "always the same
     * comet" bug that fix was built to avoid. */
    public static long cycleForEventStart(long eventStart, long seed) {
        long approxCycle = Math.floorDiv(eventStart, PERIOD_TICKS);
        for (long cycle = approxCycle - 1; cycle <= approxCycle + 1; cycle++) {
            if (eventStartForCycle(cycle, seed) == eventStart) {
                return cycle;
            }
        }
        return eventStart;
    }

    /** How far a comet travels against the fixed stars over one whole apparition. Real content:
     * a comet genuinely does move against the background sky across an apparition — that proper
     * motion is exactly how comets were found and tracked before photography — unlike a nebula or
     * a galaxy, which are fixed for any human timescale. The first cut of this event left the
     * comet stationary and named that as a simplification; it was reported back as the obvious
     * result, "комета постоянно на месте стоит не двигается" (the comet just stands there and
     * doesn't move), so the simplification is now removed rather than defended. */
    public static final double MIN_TRAVEL_DEGREES = 40.0;

    public static final double MAX_TRAVEL_DEGREES = 90.0;

    /** Where a given cycle's own comet first appears — full-sphere placement (Marsaglia's method,
     * {@code StarField}'s own established technique, rule 46), unlike {@link
     * PassingBodySchedule}'s own horizon band: this marker sits in the star field's own fixed
     * frame and wheels past every player repeatedly as the asteroid spins many times over a
     * single multi-day apparition, so there is no risk of an unlucky placement staying hidden the
     * way a two-second pass would be. */
    public static SkyRotation.Vec3 startDirection(long cycleIndex, long seed) {
        return uniformSphereDirection(cycleIndex, seed, 5);
    }

    /** Where that same comet has travelled to by the end of its apparition — {@link
     * #startDirection} rotated by a guaranteed {@code [MIN_TRAVEL_DEGREES, MAX_TRAVEL_DEGREES]}
     * about a real perpendicular axis (Rodrigues' formula, {@link SkyRotation#rotate} — the
     * identical operation the asteroid's own spin already uses, rule 46), never a second
     * independent point that could land back where it started. */
    public static SkyRotation.Vec3 endDirection(long cycleIndex, long seed) {
        SkyRotation.Vec3 start = startDirection(cycleIndex, seed);
        SkyRotation.Vec3 axis = travelAxis(cycleIndex, seed, start);
        double travelDegrees = MIN_TRAVEL_DEGREES
                + fraction(cycleIndex, seed, 8) * (MAX_TRAVEL_DEGREES - MIN_TRAVEL_DEGREES);
        return SkyRotation.rotate(start, axis, Math.toRadians(travelDegrees));
    }

    /** Where the comet is right now, {@code progress} of the way through its own apparition — a
     * straight interpolation between the two endpoints, renormalised back onto the sphere. */
    public static SkyRotation.Vec3 directionAtProgress(long cycleIndex, long seed, double progress) {
        double clamped = Math.clamp(progress, 0.0, 1.0);
        SkyRotation.Vec3 start = startDirection(cycleIndex, seed);
        SkyRotation.Vec3 end = endDirection(cycleIndex, seed);
        return new SkyRotation.Vec3(
                start.x() + (end.x() - start.x()) * clamped,
                start.y() + (end.y() - start.y()) * clamped,
                start.z() + (end.z() - start.z()) * clamped).normalize();
    }

    /** A unit vector perpendicular to {@code start} — Gram-Schmidt against a second random draw,
     * the same construction {@link PassingBodySchedule} established for the same need. */
    private static SkyRotation.Vec3 travelAxis(long cycleIndex, long seed, SkyRotation.Vec3 start) {
        SkyRotation.Vec3 raw = uniformSphereDirection(cycleIndex, seed, 6);
        SkyRotation.Vec3 perp = rejectAlong(raw, start);
        if (length(perp) < 1.0e-6) {
            SkyRotation.Vec3 fallback = Math.abs(start.x()) < 0.9
                    ? new SkyRotation.Vec3(1.0, 0.0, 0.0)
                    : new SkyRotation.Vec3(0.0, 1.0, 0.0);
            perp = rejectAlong(fallback, start);
        }
        double len = length(perp);
        return new SkyRotation.Vec3(perp.x() / len, perp.y() / len, perp.z() / len);
    }

    private static SkyRotation.Vec3 rejectAlong(SkyRotation.Vec3 v, SkyRotation.Vec3 along) {
        double projection = v.dot(along);
        return new SkyRotation.Vec3(
                v.x() - along.x() * projection, v.y() - along.y() * projection, v.z() - along.z() * projection);
    }

    private static double length(SkyRotation.Vec3 v) {
        return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
    }

    private static double fraction(long cycleIndex, long seed, long salt) {
        long h = mix((cycleIndex * 97 + salt * 131) ^ (seed * 0x9E3779B97F4A7C15L));
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static SkyRotation.Vec3 uniformSphereDirection(long cycleIndex, long seed, int salt) {
        long h1 = mix((cycleIndex * 31 + salt * 97L) ^ (seed * 0x9E3779B97F4A7C15L));
        long h2 = mix(h1 ^ 0x2545F4914F6CDD1DL);
        double u1 = (h1 >>> 11) * (1.0 / (1L << 53));
        double u2 = (h2 >>> 11) * (1.0 / (1L << 53));
        double z = u1 * 2.0 - 1.0;
        double theta = u2 * 2.0 * Math.PI;
        double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new SkyRotation.Vec3(r * Math.cos(theta), z, r * Math.sin(theta));
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

    private CometSchedule() {}
}
