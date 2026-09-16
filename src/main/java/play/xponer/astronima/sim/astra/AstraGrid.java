package play.xponer.astronima.sim.astra;

/**
 * The astra field's real depletion grid — design/astra-extraction-loop.md §1.2: a "worked-over
 * volume" is a place, not a block, so depletion is tracked per cube-shaped cell rather than per
 * exact position. Minecraft-free (rule 1) and deliberately not built on {@code BlockPos.asLong}:
 * this mod's plain-JUnit source set has no Minecraft classes on its classpath at all (confirmed
 * directly against this project's own established finding for exactly this reason —
 * {@code WireChunk}'s equivalent test needed a GameTest instead), so a key scheme built on a
 * Minecraft type could never be proven here. This is its own, self-contained scheme: collision-free
 * for this scheme's own keys, not compatible with and never compared against {@code BlockPos}'s.
 */
public final class AstraGrid {

    /** A first-pass rule-41 number (design/astra-extraction-loop.md §1.2): big enough that
     *  stepping sideways to "reset" the field is obviously cheesing it, small enough that several
     *  cells fit inside one 80-160-block site (design/asteroid-body.md §5). Tuned by playtest. */
    public static final int CELL_SIZE_BLOCKS = 32;

    /** Comfortably larger in magnitude than any cell coordinate this mod's 480-block-wide body can
     *  ever produce, so every packed axis stays positive and two's-complement sign bits never
     *  enter the packing at all. */
    private static final long AXIS_OFFSET = 1_000_000L;
    private static final int BITS_PER_AXIS = 20;
    private static final long AXIS_MASK = (1L << BITS_PER_AXIS) - 1;

    private AstraGrid() {}

    /**
     * The grid cell containing a real block coordinate. {@link Math#floorDiv}, not {@code /}:
     * plain integer division truncates toward zero, which gives the cells straddling zero on
     * either axis the wrong width (asteroid-body.md §8's own "sample more than the ends"
     * discipline, applied to this leaf's own coordinate).
     */
    public static int cellCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_SIZE_BLOCKS);
    }

    /** A collision-free key for the cell containing {@code (x, y, z)}. */
    public static long cellKey(int x, int y, int z) {
        long cellX = cellCoordinate(x) + AXIS_OFFSET;
        long cellY = cellCoordinate(y) + AXIS_OFFSET;
        long cellZ = cellCoordinate(z) + AXIS_OFFSET;
        return ((cellX & AXIS_MASK) << (2 * BITS_PER_AXIS))
                | ((cellY & AXIS_MASK) << BITS_PER_AXIS)
                | (cellZ & AXIS_MASK);
    }
}
