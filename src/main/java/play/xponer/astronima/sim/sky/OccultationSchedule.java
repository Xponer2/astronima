package play.xponer.astronima.sim.sky;

/**
 * When another body is transiting the Sun (design/sky.md §3's occultation row) — a pure function
 * of game time and a seed, the same shape as {@link FlareSchedule} and for the same reason:
 * "brief, sharp partial darkness" has to be something a test can hold this class to directly.
 *
 * <p>Real content, not an arbitrary dimming: the design's own point is that this is a *mechanical*
 * event — {@code SolarRetortBlockEntity.sunlight()} genuinely drops while a transit is under way,
 * the sky changing a machine, predictably, if the player is watching for it.
 */
public final class OccultationSchedule {

    /** More frequent than {@link FlareSchedule}'s own period — a belt has more small transiting
     * bodies than it has flare-producing activity. */
    public static final long PERIOD_TICKS = 18_000L;

    public static final long JITTER_TICKS = 5_000L;

    public static final long RAMP_TICKS = 100L;
    public static final long HOLD_TICKS = 200L;
    public static final long DECAY_TICKS = 100L;

    /** A transit's own total visible lifetime — short on purpose ("brief, sharp"), a fifth of a
     * flare's own duration. */
    public static final long DURATION_TICKS = RAMP_TICKS + HOLD_TICKS + DECAY_TICKS;

    /** A transiting body does not have to fully eclipse the Sun to read as a real transit —
     * capped short of 1.0 so this always stays *partial* darkness, matching the design's own
     * wording, never a total blackout. */
    public static final double MAX_DEPTH = 0.75;

    /**
     * Current occultation depth — the fraction of sunlight blocked, {@code 0.0} outside any
     * transit window, ramping {@code 0 -> MAX_DEPTH -> 0} through one otherwise.
     */
    public static double depthAt(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        double best = 0.0;
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            long start = transitStartForCycle(cycle, seed);
            long elapsed = gameTimeTicks - start;
            if (elapsed >= 0 && elapsed < DURATION_TICKS) {
                best = Math.max(best, MAX_DEPTH * depthForElapsed(elapsed));
            }
        }
        return best;
    }

    private static double depthForElapsed(long elapsed) {
        if (elapsed < RAMP_TICKS) {
            return elapsed / (double) RAMP_TICKS;
        }
        if (elapsed < RAMP_TICKS + HOLD_TICKS) {
            return 1.0;
        }
        long decayElapsed = elapsed - RAMP_TICKS - HOLD_TICKS;
        return Math.max(0.0, 1.0 - decayElapsed / (double) DECAY_TICKS);
    }

    /** The real tick a given cycle's own transit starts at — exposed so a test can hold the
     * back-to-back guarantee directly against real start times. */
    public static long transitStartForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** The start tick of the currently active transit, or {@code -1} if none is active — the
     * position data a renderer needs (sweeping the transiting body across the Sun) that the depth
     * curve alone does not expose, without duplicating {@link #depthAt}'s own neighbour-cycle
     * search a second time. */
    public static long activeTransitStart(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            long start = transitStartForCycle(cycle, seed);
            long elapsed = gameTimeTicks - start;
            if (elapsed >= 0 && elapsed < DURATION_TICKS) {
                return start;
            }
        }
        return -1L;
    }

    /** The next transit's own start tick, strictly after {@code afterTick} — the same "read its
     * state without waiting real minutes" readout {@link FlareSchedule#nextFlareStart} gives. */
    public static long nextTransitStart(long afterTick, long seed) {
        long cycleIndex = Math.floorDiv(afterTick, PERIOD_TICKS);
        for (long cycle = cycleIndex; cycle <= cycleIndex + 2; cycle++) {
            long start = transitStartForCycle(cycle, seed);
            if (start > afterTick) {
                return start;
            }
        }
        return transitStartForCycle(cycleIndex + 2, seed);
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

    private OccultationSchedule() {}
}
