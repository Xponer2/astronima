package play.xponer.astronima.sim.sky;

/**
 * When a solar flare is happening (design/sky.md §S5.1) — a pure function of game time and a
 * seed, with no Minecraft dependency, so "periodic and learnable, never two flares back to back"
 * (design/sky.md §7's own test-plan language) is something a test can hold this class to directly
 * rather than trusting a screenshot.
 *
 * <p>A flare's start time in cycle {@code n} is {@code n * PERIOD_TICKS + jitter(n, seed)}: close
 * to regular, never exactly, and identical for every player and session given the same seed —
 * real solar activity is not metronomic, but it is not patternless either.
 */
public final class FlareSchedule {

    /** Roughly how often a flare occurs — not exact, see {@link #JITTER_TICKS}. */
    public static final long PERIOD_TICKS = 30_000L;

    /** How far a flare's actual start can drift from its cycle's own regular slot, in either
     * direction. Chosen well under half of {@link #PERIOD_TICKS} specifically so two flares can
     * never land close enough together to overlap — see {@link #DURATION_TICKS}. */
    public static final long JITTER_TICKS = 9_000L;

    public static final long RAMP_TICKS = 200L;
    public static final long HOLD_TICKS = 400L;
    public static final long DECAY_TICKS = 600L;

    /** A flare's own total visible lifetime, ramp through decay. */
    public static final long DURATION_TICKS = RAMP_TICKS + HOLD_TICKS + DECAY_TICKS;

    /**
     * Current flare intensity, {@code 0.0} outside any flare window, ramping {@code 0 -> 1 -> 0}
     * through one otherwise. Checks the current cycle and both neighbours, since jitter can push
     * a flare's real start earlier or later than the cycle boundary its own index would suggest.
     */
    public static double intensityAt(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        double best = 0.0;
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            long start = flareStartForCycle(cycle, seed);
            long elapsed = gameTimeTicks - start;
            if (elapsed >= 0 && elapsed < DURATION_TICKS) {
                best = Math.max(best, intensityForElapsed(elapsed));
            }
        }
        return best;
    }

    private static double intensityForElapsed(long elapsed) {
        if (elapsed < RAMP_TICKS) {
            return elapsed / (double) RAMP_TICKS;
        }
        if (elapsed < RAMP_TICKS + HOLD_TICKS) {
            return 1.0;
        }
        long decayElapsed = elapsed - RAMP_TICKS - HOLD_TICKS;
        return Math.max(0.0, 1.0 - decayElapsed / (double) DECAY_TICKS);
    }

    /** The real tick a given cycle's own flare starts at — exposed (not just used internally) so
     * a test can hold the back-to-back guarantee directly against real start times. */
    public static long flareStartForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** The next flare's own start tick, strictly after {@code afterTick} — a "time until next
     * flare" readout (the debug-command rule's own "read its state") without waiting real
     * minutes to confirm the schedule is actually advancing. Three cycles ahead is always enough:
     * {@link #JITTER_TICKS} is well under half of {@link #PERIOD_TICKS}, so even the earliest
     * possible next-cycle start cannot also fall at or before {@code afterTick} two cycles running. */
    public static long nextFlareStart(long afterTick, long seed) {
        long cycleIndex = Math.floorDiv(afterTick, PERIOD_TICKS);
        for (long cycle = cycleIndex; cycle <= cycleIndex + 2; cycle++) {
            long start = flareStartForCycle(cycle, seed);
            if (start > afterTick) {
                return start;
            }
        }
        return flareStartForCycle(cycleIndex + 2, seed);
    }

    private static long jitterForCycle(long cycleIndex, long seed) {
        long h = mix(cycleIndex ^ (seed * 0x9E3779B97F4A7C15L));
        double unit = (h >>> 11) * (1.0 / (1L << 53));
        return Math.round((unit * 2.0 - 1.0) * JITTER_TICKS);
    }

    /** SplitMix64's own finaliser — a small, well-known, well-mixed 64-bit hash, not a home-grown
     * one with untested distribution properties. */
    private static long mix(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private FlareSchedule() {}
}
