package play.xponer.astronima.sim.ore;

/**
 * A worldgen probability that defends its own minimum total, instead of being hand-tuned once
 * against whatever the body's size happened to be that day.
 *
 * <p>The asteroid became a bounded body ({@code design/asteroid-body.md}), which turned every
 * ore's total supply from unbounded into a real, computable number — see
 * {@code design/asteroid-body-resources.md}. Checked against the real numbers, none of the four
 * ores currently need boosting at today's radius and thresholds; the point of this class is what
 * happens the day someone changes one of those without remembering to re-check the arithmetic.
 * A per-block probability that only defends a floor when the geometry actually calls for it
 * cannot silently regress into scarcity the way a bare literal can.
 *
 * <p>Minecraft-free (rule 1): volumes and probabilities in, a probability out.
 */
public final class ResourceFloor {

    /**
     * The per-block probability that guarantees at least {@code minimumTotalCount} expected
     * instances across {@code bandVolumeBlocks} blocks, never lower than {@code baseProbability}.
     *
     * <p>{@code max}, not a replacement: the base probability is a real design choice about
     * density and pacing (rule 41), and this only ever raises it, never lowers a probability
     * that was already generous enough on its own.
     */
    public static double probabilityForFloor(double bandVolumeBlocks, double minimumTotalCount,
                                             double baseProbability) {
        if (bandVolumeBlocks <= 0) {
            return baseProbability;
        }
        return Math.max(baseProbability, minimumTotalCount / bandVolumeBlocks);
    }

    private ResourceFloor() {}
}
