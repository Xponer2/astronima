package play.xponer.astronima.sim.optics;

/**
 * Whether a raycast result means "the sky is genuinely visible from here" — the one place that
 * decision is made, so the telescope's real occlusion and any later mechanic asking the same real
 * question (rule 46) read the identical answer. The raycast itself needs a real {@code Level}
 * (rule 50's honest risk) and lives at its own call site; this is only the decision once a result
 * exists, kept MC-free so it is testable against a fabricated result rather than only a real world.
 */
public final class LineOfSight {

    /**
     * @param rayHitSomething whether the probe found a solid block before reaching open sky
     * @param weatherClear    whether the sky itself is currently unobstructed (an existing,
     *                        already-computed flag — this method never re-derives weather)
     */
    public static boolean isClear(boolean rayHitSomething, boolean weatherClear) {
        return !rayHitSomething && weatherClear;
    }

    private LineOfSight() {}
}
