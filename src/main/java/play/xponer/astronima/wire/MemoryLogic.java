package play.xponer.astronima.wire;

/**
 * What a {@link play.xponer.astronima.sim.logic.PartType#RAM} chip does with its pins: read live,
 * write once on the clock's rising edge. {@code design/memory-chips.md} is the design this
 * follows. Reused whole by {@link play.xponer.astronima.sim.logic.PartType#FRAMEBUFFER} — same
 * pins, same store; {@code design/display.md} covers the one thing that part adds on top: drawing
 * the same sixty-four bits as a picture ({@link #pixelAt}).
 *
 * <h2>Pin order, fixed by {@code PartType.ramPads()}</h2>
 * {@code a0..a3} (address), {@code d0..d3} (data in), {@code we}, {@code clk} — in that order,
 * because {@link play.xponer.astronima.sim.logic.PartType#inputs()} preserves declaration order
 * among the non-driving pads it filters to. Indices {@code 0..3}, {@code 4..7}, {@code 8}, {@code
 * 9} below are not a coincidence that needs re-deriving; they are that declaration order, named.
 *
 * <h2>Edge-triggered, not level-triggered, and this is the whole reason it is not one gate</h2>
 * A level-sensitive write — write continuously while {@code we} is high — has exactly the hazard
 * rule 63 already found in a bare cross-coupled latch: whatever drives {@code d0..d3} is very often
 * downstream of what this chip is about to write, and a transparent write turns that into the same
 * "no fixed point" instability. The fix is the same one a counter's own register already uses — act
 * once, at the instant the clock transitions, not for as long as it holds — applied here to a byte
 * array instead of a single flip-flop, because {@link play.xponer.astronima.sim.logic.Circuit}'s
 * gate model has no primitive that can hold one at all.
 */
public final class MemoryLogic {

    private static final int ADDRESS_BITS = 4;
    private static final int WE_INDEX = 8;
    private static final int CLK_INDEX = 9;

    /**
     * @param outputBits the addressed cell's contents after this refresh, live either way
     * @param memory     the chip's sixteen cells, possibly one written
     * @param clockHigh  {@code clk} as read this refresh — the caller stores this back as {@code
     *                   held}, so the next refresh can tell a rising edge from a held level
     */
    public record Result(int outputBits, long memory, boolean clockHigh) { }

    /**
     * @param inputs        this refresh's pin readings, in {@code PartType.RAM}'s own declared
     *                      order
     * @param memory        the chip's stored cells coming in
     * @param clockWasHigh  {@code held} as stored from the previous refresh — was {@code clk} high
     *                      last time this chip was asked
     */
    public static Result evaluate(boolean[] inputs, long memory, boolean clockWasHigh) {
        int address = bits(inputs, 0);
        boolean clockNowHigh = bit(inputs, CLK_INDEX);
        boolean risingEdge = clockNowHigh && !clockWasHigh;
        long next = memory;
        if (risingEdge && bit(inputs, WE_INDEX)) {
            int data = bits(inputs, ADDRESS_BITS);
            int shift = address * 4;
            next = (memory & ~(0xFL << shift)) | ((long) data << shift);
        }
        int q = (int) ((next >>> (address * 4)) & 0xF);
        return new Result(q, next, clockNowHigh);
    }

    private static boolean bit(boolean[] inputs, int index) {
        return index < inputs.length && inputs[index];
    }

    private static int bits(boolean[] inputs, int from) {
        int value = 0;
        for (int i = 0; i < ADDRESS_BITS; i++) {
            if (bit(inputs, from + i)) {
                value |= 1 << i;
            }
        }
        return value;
    }

    /**
     * Whether {@link play.xponer.astronima.sim.logic.PartType#FRAMEBUFFER} draws pixel
     * {@code (x, y)} lit, given its stored {@code memory} — bit {@code y * 8 + x}
     * (design/display.md §2.3). Address {@code N}'s nibble (bits {@code 4N..4N+3}) is therefore
     * exactly one half of row {@code N / 2} — the low nibble the left four pixels, the high
     * nibble the right four — which is why sixteen cells and an 8×8 grid are the matching pair
     * they are.
     *
     * <p>Pulled out of the renderer rather than left inline in it: a client-only renderer cannot
     * be reached by a gametest, so the one function that decides a pixel has to live somewhere a
     * test can call it directly, or a transposed layout or an off-by-one address would only ever
     * be caught by a player looking at a wrong picture.
     */
    public static boolean pixelAt(long memory, int x, int y) {
        if (x < 0 || x >= 8 || y < 0 || y >= 8) {
            return false;
        }
        int index = y * 8 + x;
        return (memory & (1L << index)) != 0;
    }

    private MemoryLogic() {}
}
