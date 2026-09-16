package play.xponer.astronima.sim.wire;

/**
 * How high a trace rides above its surface — flat, except exactly where it crosses another.
 *
 * <p><strong>This replaces standing each colour off the wall by a fixed amount, which was wrong
 * in two ways at once.</strong> It made a second colour hover a pixel clear of the floor along
 * its whole length, and — worse — a hovering trace stayed hovering when it turned a corner, so it
 * hung in the air off the edge of the step. Reported from play: <em>"если этот цвет который
 * сверху начать ставить под угловую стенку, он так и останется на один пиксель висеть"</em>.
 *
 * <p>The fix is the one real cable management uses: <strong>everything lies flat, and a crossing
 * steps over.</strong> Where two traces want the same pixel, one stays down and the other lifts
 * by its own thickness — one pixel, which is exactly the clearance needed and no more.
 *
 * <p><strong>No ramp, deliberately.</strong> The first version approached each crossing over
 * three pixels, and at this scale that did not read as a bridge — it read as a staircase, which
 * is both uglier and a lie about what wire does. A conductor crossing another is bent over it at
 * the point of contact and is flat either side. One pixel, at the pixel that needs it.
 *
 * <p>A trace crossing two others at one pixel lifts by two, and so on, so a bundle stacks in the
 * order its colours demand rather than fighting for one height.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class TraceElevation {

    /**
     * How far a trace lifts to clear one beneath it, in pixels.
     *
     * <p>One — a wire is one pixel thick, so one pixel is the whole of the clearance required.
     * Anything more would be a gantry rather than a crossing.
     */
    public static final int HOP_PIXELS = 1;

    /**
     * Height at one pixel, in pixels above the surface.
     *
     * @param rank the <em>demand</em> at this pixel: how many other traces this one must clear
     *             here. Zero — the ordinary case, everywhere that is not a crossing — is flat.
     */
    public static int heightAt(int u, int v, java.util.function.IntBinaryOperator rank) {
        if (!FaceBasis.onGrid(u) || !FaceBasis.onGrid(v)) {
            return 0;
        }
        return Math.max(rank.applyAsInt(u, v), 0) * HOP_PIXELS;
    }

    /** The same height in blocks, which is what a renderer wants. */
    public static double blocksAt(int u, int v, java.util.function.IntBinaryOperator rank) {
        return heightAt(u, v, rank) / (double) FaceBasis.GRID;
    }

    private TraceElevation() {}
}
