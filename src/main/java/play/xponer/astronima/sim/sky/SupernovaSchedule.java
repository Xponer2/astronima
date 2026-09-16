package play.xponer.astronima.sim.sky;

import play.xponer.astronima.sim.optics.SkyRotation;

/**
 * When a supernova is visible (design/sky.md §3's own "supernova" row) — the same
 * periodic-and-learnable, Minecraft-free shape as {@link CometSchedule}, and the rarest of every
 * event this file schedules: a naked-eye supernova is a once-in-a-lifetime real event, and this
 * row's own design weight is explicit about why — "Spectroscopy content: its spectrum changes over
 * time, so the instrument has something to say that a static target never could."
 *
 * <p>This class owns only *when* one is visible and *how bright* — real, monotonic light-curve
 * phases (rise, plateau, decline) matching a real Type II-P core-collapse supernova's own shape,
 * scaled to real day counts. {@link play.xponer.astronima.sim.magic.SupernovaSpectrum} owns *what
 * the instrument reads* while it is, keyed to this class's own {@link #RAMP_TICKS}/{@link
 * #HOLD_TICKS}/{@link #DECAY_TICKS} phase boundaries directly rather than a second, independently
 * tuned set that could quietly drift out of step with them.
 */
public final class SupernovaSchedule {

    /** Roughly every 500 real Minecraft days — the rarest event in this file by a wide margin,
     * matching how rare a naked-eye supernova genuinely is (a handful per recorded human history). */
    public static final long PERIOD_TICKS = 12_000_000L;

    public static final long JITTER_TICKS = 3_000_000L;

    /** Five real days rising to peak — a real core-collapse shock breakout and rise is this fast. */
    public static final long RAMP_TICKS = 120_000L;

    /** Sixty real days near peak — a real Type II-P's own "plateau" phase, where the recombining
     * hydrogen envelope holds the luminosity roughly steady even as the photosphere itself cools. */
    public static final long HOLD_TICKS = 1_440_000L;

    /** A hundred and twenty real days declining — the real nebular-phase tail, compressed from
     * what can genuinely run for months to years in reality. */
    public static final long DECAY_TICKS = 2_880_000L;

    /** A little over six real months, start to finish. */
    public static final long DURATION_TICKS = RAMP_TICKS + HOLD_TICKS + DECAY_TICKS;

    /** Same reasoning as {@link CometSchedule#PREVIEW_DURATION_TICKS}: nobody tests a mechanic by
     * watching for six real months. {@link SkyEventOverride#triggerSupernovaNow} plays back the
     * identical rise/plateau/decline shape compressed into this many real seconds instead. */
    public static final long PREVIEW_RAMP_TICKS = 100L;

    public static final long PREVIEW_HOLD_TICKS = 200L;

    public static final long PREVIEW_DECAY_TICKS = 100L;

    public static final long PREVIEW_DURATION_TICKS = PREVIEW_RAMP_TICKS + PREVIEW_HOLD_TICKS + PREVIEW_DECAY_TICKS;

    public static long eventStartForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** Current brightness/growth, {@code 0.0} outside any supernova's own window, ramping
     * {@code 0 -> 1 -> 0} through one otherwise. */
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

    public static double growthForElapsedTicks(long elapsed) {
        return rampHoldDecayShape(elapsed, RAMP_TICKS, HOLD_TICKS, DECAY_TICKS);
    }

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

    /** The start tick of the currently active supernova, or {@code -1} if none is active. */
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

    /** The next supernova's own start tick, strictly after {@code afterTick}. */
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

    /** Which cycle a given active start tick belongs to — falls back to the raw {@code
     * eventStart} tick itself (not {@code floorDiv(eventStart, PERIOD_TICKS)}) when no real cycle
     * produced it, the same fix {@link PassingBodySchedule#cycleForEventStart} and {@link
     * CometSchedule#cycleForEventStart} already made for the identical debug-trigger bug. */
    public static long cycleForEventStart(long eventStart, long seed) {
        long approxCycle = Math.floorDiv(eventStart, PERIOD_TICKS);
        for (long cycle = approxCycle - 1; cycle <= approxCycle + 1; cycle++) {
            if (eventStartForCycle(cycle, seed) == eventStart) {
                return cycle;
            }
        }
        return eventStart;
    }

    /** The fixed sky-space direction a given cycle's own supernova sits at — full-sphere
     * placement, the same reasoning as {@link CometSchedule#direction}: a months-long event wheels
     * past every player's own view many times over as the asteroid spins, so there is no risk of
     * an unlucky placement staying hidden. */
    public static SkyRotation.Vec3 direction(long cycleIndex, long seed) {
        long h1 = mix((cycleIndex * 31 + 7L * 97L) ^ (seed * 0x9E3779B97F4A7C15L));
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

    private SupernovaSchedule() {}
}
