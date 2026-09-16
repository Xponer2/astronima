package play.xponer.astronima.sim.sky;

/**
 * When a meteoroid strikes the surface (design/sky.md §3's impact-flash row) — a pure function
 * of game time and a seed, the same periodic-and-learnable shape as {@link FlareSchedule} and
 * {@link OccultationSchedule}, but a one-tick pulse rather than a held event: a real impact is
 * instantaneous — a flash and a burst of ejecta — not something with a duration to ramp through.
 *
 * <p>Real content, and the actual point of this event (design/sky.md's own words): *no meteor
 * streak*. There is no air here to burn an incoming body, so nothing crosses the sky first —
 * the flash is the whole event, on the ground, without warning. Every player expects a shooting
 * star; getting a silent flash instead is the one sky event whose entire job is to teach vacuum
 * in a single look.
 */
public final class ImpactSchedule {

    /** More frequent than {@link FlareSchedule} or {@link OccultationSchedule} — small impacts
     * are the most common event a real airless surface actually sees. */
    public static final long PERIOD_TICKS = 4_000L;

    public static final long JITTER_TICKS = 1_500L;

    /** The real tick a given cycle's own impact lands on. */
    public static long impactTickForCycle(long cycleIndex, long seed) {
        return cycleIndex * PERIOD_TICKS + jitterForCycle(cycleIndex, seed);
    }

    /** True on exactly the tick an impact fires — checked against the current cycle and its
     * neighbours, since jitter can push an impact's real tick across what would otherwise be its
     * own cycle boundary. */
    public static boolean firesAt(long gameTimeTicks, long seed) {
        long cycleIndex = Math.floorDiv(gameTimeTicks, PERIOD_TICKS);
        for (long cycle = cycleIndex - 1; cycle <= cycleIndex + 1; cycle++) {
            if (impactTickForCycle(cycle, seed) == gameTimeTicks) {
                return true;
            }
        }
        return false;
    }

    /** The next impact's own tick, strictly after {@code afterTick} — the same "read its state
     * without waiting real minutes" readout {@link FlareSchedule#nextFlareStart} gives. */
    public static long nextImpactTick(long afterTick, long seed) {
        long cycleIndex = Math.floorDiv(afterTick, PERIOD_TICKS);
        for (long cycle = cycleIndex; cycle <= cycleIndex + 2; cycle++) {
            long tick = impactTickForCycle(cycle, seed);
            if (tick > afterTick) {
                return tick;
            }
        }
        return impactTickForCycle(cycleIndex + 2, seed);
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

    private ImpactSchedule() {}
}
