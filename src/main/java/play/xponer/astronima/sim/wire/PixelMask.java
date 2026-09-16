package play.xponer.astronima.sim.wire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which pixels of one face carry a trace — 256 bits, and nothing else.
 *
 * <p><strong>A bitmask rather than a list of pixels</strong>, because a metre of trace is sixteen
 * pixels and a twenty-block run would otherwise be three hundred stored records. This is thirty-two
 * bytes per face-and-colour that carries anything, whatever shape it is: a straight run, a
 * meander and a full block of solid copper all cost exactly the same to store, sync and load.
 *
 * <p>Immutable. Every edit returns a new mask, so a chunk's wire data can be swapped as a value
 * and never half-changed under a reader — which matters because the renderer walks it on the
 * client thread while the server is editing its own copy.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class PixelMask {

    /** Sixteen by sixteen, packed a row per quarter-word. */
    public static final int WORDS = 4;

    private static final PixelMask EMPTY = new PixelMask(new long[WORDS]);

    private final long[] bits;

    private PixelMask(long[] bits) {
        this.bits = bits;
    }

    public static PixelMask empty() {
        return EMPTY;
    }

    /** Rebuilds a mask from stored words; short or long input is rejected rather than padded. */
    public static PixelMask of(long[] words) {
        if (words.length != WORDS) {
            throw new IllegalArgumentException("a face mask is " + WORDS + " words, got "
                    + words.length);
        }
        return new PixelMask(words.clone());
    }

    public long[] words() {
        return bits.clone();
    }

    private static int index(int u, int v) {
        if (!FaceBasis.onGrid(u) || !FaceBasis.onGrid(v)) {
            throw new IllegalArgumentException("pixel " + u + "," + v + " is off the face");
        }
        return v * FaceBasis.GRID + u;
    }

    public boolean has(int u, int v) {
        int bit = index(u, v);
        return (bits[bit >>> 6] & (1L << (bit & 63))) != 0;
    }

    public PixelMask with(int u, int v) {
        int bit = index(u, v);
        long[] next = bits.clone();
        next[bit >>> 6] |= 1L << (bit & 63);
        return new PixelMask(next);
    }

    public PixelMask without(int u, int v) {
        int bit = index(u, v);
        long[] next = bits.clone();
        next[bit >>> 6] &= ~(1L << (bit & 63));
        return new PixelMask(next);
    }

    public boolean isEmpty() {
        for (long word : bits) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    /** How much wire this face holds — what it costs to lay and what it returns when pulled. */
    public int count() {
        int total = 0;
        for (long word : bits) {
            total += Long.bitCount(word);
        }
        return total;
    }

    /** Every occupied pixel as {@code u + v * 16}, ascending — a stable order (rule 19). */
    public List<Integer> occupied() {
        List<Integer> found = new ArrayList<>(count());
        for (int word = 0; word < WORDS; word++) {
            long remaining = bits[word];
            while (remaining != 0) {
                int bit = Long.numberOfTrailingZeros(remaining);
                found.add(word * 64 + bit);
                remaining &= remaining - 1;
            }
        }
        return found;
    }

    public static int uOf(int packed) {
        return packed % FaceBasis.GRID;
    }

    public static int vOf(int packed) {
        return packed / FaceBasis.GRID;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PixelMask mask && Arrays.equals(bits, mask.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        return "PixelMask[" + count() + " pixels]";
    }
}
