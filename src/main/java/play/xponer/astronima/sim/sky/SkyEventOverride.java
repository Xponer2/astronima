package play.xponer.astronima.sim.sky;

/**
 * A debug-only override for the sky's own scheduled events (design/sky.md §S5.1) — lets
 * {@code /astronima sky flare <intensity>} / {@code /astronima sky occultation <depth>} force a
 * specific value for testing, without waiting out the real 18000-30000 tick schedule.
 *
 * <p>Deliberately a same-process static, not synced or persisted: this is the debug-command
 * rule's own "set and read its state" tool, not a real simulation value another player needs to
 * see — the same shape {@code /astronima} debug commands take throughout this mod, applied to a
 * value with no natural single "room" or block position to attach to (a flare and a transit are
 * whole-sky events, not local state).
 */
public final class SkyEventOverride {

    private static volatile Double flareIntensity;
    private static volatile Double occultationDepth;
    private static volatile Long passingBodyForceStart;
    private static volatile Long cometForceStart;
    private static volatile Long supernovaForceStart;

    public static void setFlareIntensity(double intensity) {
        flareIntensity = intensity;
    }

    public static void clearFlareOverride() {
        flareIntensity = null;
    }

    public static void setOccultationDepth(double depth) {
        occultationDepth = depth;
    }

    public static void clearOccultationOverride() {
        occultationDepth = null;
    }

    /** The flare intensity to actually use right now: the debug override if one is set, the real
     * schedule otherwise. Both the renderer and (once a v0.79 hazard exists to read it) any
     * mechanical consumer resolve through this one method, so a forced test value is never
     * visible in one place and not another. */
    public static double resolveFlareIntensity(long gameTimeTicks, long seed) {
        Double override = flareIntensity;
        return override != null ? override : FlareSchedule.intensityAt(gameTimeTicks, seed);
    }

    /** The occultation depth to actually use right now — same resolution rule as {@link
     * #resolveFlareIntensity}, read by both the renderer's own transiting body and {@code
     * SkyExposure#sunlightAt}, so a forced test value dims the retort exactly as it dims the
     * picture. */
    public static double resolveOccultationDepth(long gameTimeTicks, long seed) {
        Double override = occultationDepth;
        return override != null ? override : OccultationSchedule.depthAt(gameTimeTicks, seed);
    }

    /** Forces a passing-body pass to start right now, real {@link PassingBodySchedule#DURATION_TICKS}
     * window and all — unlike the flare/occultation overrides (a held value), a pass is a one-shot
     * event with real start-to-end motion, so the debug command records only when it began and lets
     * {@link #resolvePassingBodyActiveStart} run out its own real duration exactly like a natural
     * one would. */
    public static void triggerPassingBodyNow(long gameTimeTicks) {
        passingBodyForceStart = gameTimeTicks;
    }

    public static void clearPassingBodyOverride() {
        passingBodyForceStart = null;
    }

    /** The active pass's own start tick right now, or {@code -1} if none is active — a debug-forced
     * pass takes priority while its own window is still running, then falls back to the real
     * schedule on its own once that window elapses (no explicit clear needed for it to expire). */
    public static long resolvePassingBodyActiveStart(long gameTimeTicks, long seed) {
        Long forced = passingBodyForceStart;
        if (forced != null) {
            long elapsed = gameTimeTicks - forced;
            if (elapsed >= 0 && elapsed < PassingBodySchedule.DURATION_TICKS) {
                return forced;
            }
        }
        return PassingBodySchedule.activeEventStart(gameTimeTicks, seed);
    }

    /** Forces a comet apparition to start right now — but unlike {@link #triggerPassingBodyNow},
     * this does not run out the real schedule's own real two-week {@link
     * CometSchedule#DURATION_TICKS}: nobody tests a mechanic by watching for a fortnight. It runs
     * the identical ramp/hold/decay shape compressed into {@link CometSchedule#PREVIEW_DURATION_TICKS}
     * instead (see {@link #isCometPreviewActive}), the same reasoning {@code OccultationSchedule}'s
     * own debug preview sweep already established for a different event. */
    public static void triggerCometNow(long gameTimeTicks) {
        cometForceStart = gameTimeTicks;
    }

    public static void clearCometOverride() {
        cometForceStart = null;
    }

    /** The active apparition's own start tick right now, or {@code -1} if none is active — a
     * debug-forced preview takes priority while its own (compressed) window is still running,
     * then falls back to the real schedule on its own once that window elapses. */
    public static long resolveCometActiveStart(long gameTimeTicks, long seed) {
        Long forced = cometForceStart;
        if (forced != null) {
            long elapsed = gameTimeTicks - forced;
            if (elapsed >= 0 && elapsed < CometSchedule.PREVIEW_DURATION_TICKS) {
                return forced;
            }
        }
        return CometSchedule.activeEventStart(gameTimeTicks, seed);
    }

    /** Whether the tick returned by {@link #resolveCometActiveStart} right now is the debug
     * preview (and therefore needs {@link CometSchedule#previewGrowthForElapsed}, not {@link
     * CometSchedule#growthForElapsedTicks}) rather than a real apparition. */
    public static boolean isCometPreviewActive(long gameTimeTicks) {
        Long forced = cometForceStart;
        if (forced == null) {
            return false;
        }
        long elapsed = gameTimeTicks - forced;
        return elapsed >= 0 && elapsed < CometSchedule.PREVIEW_DURATION_TICKS;
    }

    /** Same reasoning as {@link #triggerCometNow}: the real schedule's own {@link
     * SupernovaSchedule#DURATION_TICKS} is over six real months, not a realistic thing to watch
     * run to completion. Plays back the identical rise/plateau/decline shape compressed into
     * {@link SupernovaSchedule#PREVIEW_DURATION_TICKS} instead. */
    public static void triggerSupernovaNow(long gameTimeTicks) {
        supernovaForceStart = gameTimeTicks;
    }

    public static void clearSupernovaOverride() {
        supernovaForceStart = null;
    }

    public static long resolveSupernovaActiveStart(long gameTimeTicks, long seed) {
        Long forced = supernovaForceStart;
        if (forced != null) {
            long elapsed = gameTimeTicks - forced;
            if (elapsed >= 0 && elapsed < SupernovaSchedule.PREVIEW_DURATION_TICKS) {
                return forced;
            }
        }
        return SupernovaSchedule.activeEventStart(gameTimeTicks, seed);
    }

    public static boolean isSupernovaPreviewActive(long gameTimeTicks) {
        Long forced = supernovaForceStart;
        if (forced == null) {
            return false;
        }
        long elapsed = gameTimeTicks - forced;
        return elapsed >= 0 && elapsed < SupernovaSchedule.PREVIEW_DURATION_TICKS;
    }

    private SkyEventOverride() {}
}
