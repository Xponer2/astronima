package play.xponer.astronima.sim.astra;

/**
 * A collector's own held astra, on the stack — design/astra-precipitation.md §1.2: how much, and
 * as of when, so a caller can compute what is really left there right now via {@link
 * AstraVessel#afterLeak}, the same lazy way {@code AstraFieldStorage} computes a cell's real
 * density — never ticked, only ever recomputed on read.
 *
 * <p>Minecraft-free (rule 1) — deliberately pure data, no behaviour: the persistence codec lives
 * in {@code registry/ModDataComponents} (mirroring {@code ToolHead}'s own split), and the leak
 * itself is computed by callers through {@link AstraVessel} directly rather than a convenience
 * method here, so that model keeps a real consumer outside {@code sim/astra} (rule 13) instead of
 * one that never leaves this package.
 */
public record AstraCharge(float amount, long lastUpdateGameTime) {

    public static final AstraCharge EMPTY = new AstraCharge(0.0F, 0L);
}
