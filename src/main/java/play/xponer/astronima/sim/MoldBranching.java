package play.xponer.astronima.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A colony's branching shape — computed live, from a seed, never baked to a file.
 *
 * <p>Reported live: a flat coverage-density texture on a single carpet-shaped face read as a
 * stain, not a colony ("wtf is carpet-shaped model"), and a fixed small set of baked
 * stage-models still read as pre-made ("текстура должна быть полностью процедурной а не
 * заранее сделаной"). Real mold, lichen and coral all get their organic look from a branching
 * structure that is different every time, not a flat coating repeated from a palette — so this
 * produces one, on demand, from nothing but a seed: no model file, no texture file, nothing on
 * disk at all. {@code MoldBlockEntityRenderer} derives the seed from the block's own world
 * position, so no two colonies in the world are the same shape, and the same position always
 * regrows the same way (deterministic, not merely random).
 *
 * <p>Every segment is a thin, <strong>axis-aligned</strong> box: each step of the walk moves
 * along exactly one of X/Y/Z, never diagonally, so no segment ever needs a rotation to look
 * right — the same reason vanilla's own chorus plant reads as an organic branching structure
 * using nothing but plain axis-aligned boxes at varying positions and sizes.
 *
 * <p><strong>Growth is a prefix, not a replacement</strong> (design/mold-growth.md §3c): every
 * age shares the same seed and walks the exact same sequence of random choices, and only the
 * element budget differs — so age 1's shape is not a different roll of the dice, it is exactly
 * age 0's shape with more growth appended, the same way a real colony does not discard what it
 * already built to grow further.
 *
 * <p>Minecraft-free (rule 1): a {@code long} seed in, a list of boxes out.
 */
public final class MoldBranching {

    /** One thin twig, in model-pixel units (0..16 per axis, y=0 the attached surface).
     *  {@code shade} (0..1) is this twig's own colour variation, drawn from the same walk as the
     *  geometry rather than sampled from any texture (design/mold-growth.md §3c) — the renderer
     *  only maps it to an actual colour, it does not decide it. */
    public record Segment(float loX, float loY, float loZ, float hiX, float hiY, float hiZ,
                           float shade) { }

    private record Direction(int dx, int dy, int dz, int weight) { }

    // Horizontal directions dominate - a colony creeps across a surface; "up" is rare and
    // short, a raised tuft rather than a tower.
    private static final List<Direction> DIRECTIONS = List.of(
            new Direction(1, 0, 0, 3), new Direction(-1, 0, 0, 3),
            new Direction(0, 0, 1, 3), new Direction(0, 0, -1, 3),
            new Direction(0, 1, 0, 1));

    private static final int TUFT_COUNT = 5;
    private static final int BRANCH_DEPTH = 6;

    /** One element budget per {@code AGE_3} value - the "sparse fleck to dense colony" story,
     *  now told in geometry instead of (or alongside) coverage density. */
    private static final int[] ELEMENT_BUDGET = {6, 14, 26, 45};

    public static final int MAX_AGE = ELEMENT_BUDGET.length - 1;

    private static final float MAX_HEIGHT = 6.0f;
    private static final float MARGIN = 0.5f;

    /** How close to an edge a biased tuft starts - close enough to visibly reach the seam, not
     *  so close it clips into whatever the neighbour drew there. */
    private static final float EDGE_BAND = 2.5f;

    /**
     * Which of this colony's four in-plane edges have a real neighbour to reach toward -
     * <strong>any</strong> mold block there, not only one on the same surface, because the
     * corner where a floor meets a wall is exactly where continuity matters most (design/
     * mold-growth.md §3d: reported live as looking like two unrelated colonies, one "growing
     * either on the floor or on the wall", never both at the seam between them). {@code
     * MoldBlockEntity} is the one Minecraft-side enough to ask the world which edges qualify;
     * this stays a plain four-flag fact so the algorithm itself stays Minecraft-free.
     */
    public record EdgeBias(boolean negX, boolean posX, boolean negZ, boolean posZ) {
        public static final EdgeBias NONE = new EdgeBias(false, false, false, false);

        private List<float[]> ranges() {
            List<float[]> ranges = new ArrayList<>(4);
            if (posX) {
                ranges.add(new float[] {16 - MARGIN - EDGE_BAND, 16 - MARGIN, 3.0f, 13.0f});
            }
            if (negX) {
                ranges.add(new float[] {MARGIN, MARGIN + EDGE_BAND, 3.0f, 13.0f});
            }
            if (posZ) {
                ranges.add(new float[] {3.0f, 13.0f, 16 - MARGIN - EDGE_BAND, 16 - MARGIN});
            }
            if (negZ) {
                ranges.add(new float[] {3.0f, 13.0f, MARGIN, MARGIN + EDGE_BAND});
            }
            return ranges;
        }
    }

    /** This age's shape for this seed - always a prefix of every higher age's shape, for a fixed
     *  {@code bias} (changing which edges are biased is a genuinely different growth direction,
     *  not just more time passed, so it is not required to extend anything). */
    public static List<Segment> forAge(long seed, int age, EdgeBias bias) {
        int budget = ELEMENT_BUDGET[Math.max(0, Math.min(MAX_AGE, age))];
        Random rng = new Random(seed);
        List<Segment> segments = new ArrayList<>(budget);
        List<float[]> biasedRanges = bias.ranges();
        int tuft = 0;
        for (; tuft < biasedRanges.size() && tuft < TUFT_COUNT && segments.size() < budget; tuft++) {
            float[] range = biasedRanges.get(tuft);
            float startX = range[0] + rng.nextFloat() * (range[1] - range[0]);
            float startZ = range[2] + rng.nextFloat() * (range[3] - range[2]);
            growTuft(rng, startX, 0.0f, startZ, BRANCH_DEPTH, segments, budget);
        }
        for (; tuft < TUFT_COUNT && segments.size() < budget; tuft++) {
            float startX = 3.0f + rng.nextFloat() * 10.0f;
            float startZ = 3.0f + rng.nextFloat() * 10.0f;
            growTuft(rng, startX, 0.0f, startZ, BRANCH_DEPTH, segments, budget);
        }
        return segments;
    }

    public static List<Segment> forAge(long seed, int age) {
        return forAge(seed, age, EdgeBias.NONE);
    }

    private record Walker(float x, float y, float z, Direction last, boolean isRoot, int budget) { }

    private static void growTuft(Random rng, float startX, float startY, float startZ,
                                  int depth, List<Segment> segments, int maxElements) {
        Deque<Walker> stack = new ArrayDeque<>();
        stack.push(new Walker(startX, startY, startZ, null, true, depth));
        while (!stack.isEmpty() && segments.size() < maxElements) {
            Walker walker = stack.pop();
            if (walker.budget() <= 0) {
                continue;
            }
            // A tuft's very first step is always horizontal: real growth spreads outward along
            // the surface before it rises, so the base always reads as rooted in the surface
            // rather than a stray twig starting mid-air (reported live as "может расти в
            // воздухе"). Only the root step is constrained; children may still climb.
            Direction direction = pickDirection(rng, walker.last(), !walker.isRoot());
            float length = 1.0f + rng.nextFloat() * 1.5f;
            float endX = clamp(walker.x() + direction.dx() * length, MARGIN, 16 - MARGIN);
            float endY = clamp(walker.y() + direction.dy() * length, 0.0f, MAX_HEIGHT);
            float endZ = clamp(walker.z() + direction.dz() * length, MARGIN, 16 - MARGIN);
            float thickness = 0.7f + rng.nextFloat() * 0.6f;
            float shade = rng.nextFloat();
            segments.add(segment(walker.x(), walker.y(), walker.z(), endX, endY, endZ, thickness, shade));
            int children = rng.nextFloat() < 0.5f ? 1 : 2;
            for (int i = 0; i < children && segments.size() < maxElements; i++) {
                stack.push(new Walker(endX, endY, endZ, direction, false, walker.budget() - 1));
            }
        }
    }

    private static Direction pickDirection(Random rng, Direction last, boolean allowUp) {
        Direction excluded = last == null ? null
                : new Direction(-last.dx(), -last.dy(), -last.dz(), 0);
        int total = 0;
        for (Direction candidate : DIRECTIONS) {
            if (!allowUp && candidate.dy() != 0) {
                continue;
            }
            if (excluded == null || candidate.dx() != excluded.dx() || candidate.dy() != excluded.dy()
                    || candidate.dz() != excluded.dz()) {
                total += candidate.weight();
            }
        }
        float roll = rng.nextFloat() * total;
        float upTo = 0f;
        for (Direction candidate : DIRECTIONS) {
            if (!allowUp && candidate.dy() != 0) {
                continue;
            }
            if (excluded != null && candidate.dx() == excluded.dx() && candidate.dy() == excluded.dy()
                    && candidate.dz() == excluded.dz()) {
                continue;
            }
            upTo += candidate.weight();
            if (roll <= upTo) {
                return candidate;
            }
        }
        for (int i = DIRECTIONS.size() - 1; i >= 0; i--) {
            if (allowUp || DIRECTIONS.get(i).dy() == 0) {
                return DIRECTIONS.get(i);
            }
        }
        throw new IllegalStateException("no horizontal direction available");
    }

    /** One thin axis-aligned box from (x0,y0,z0) to (x1,y1,z1) - a step only ever moves along
     *  one axis, so exactly one axis carries the real length and the other two get padded to
     *  {@code thickness} around the shared coordinate. */
    private static Segment segment(float x0, float y0, float z0, float x1, float y1, float z1,
                                    float thickness, float shade) {
        float half = thickness / 2f;
        float[] lo = new float[3];
        float[] hi = new float[3];
        float[] a = {x0, y0, z0};
        float[] b = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(a[i] - b[i]) < 1e-6f) {
                lo[i] = a[i] - half;
                hi[i] = a[i] + half;
            } else {
                lo[i] = Math.min(a[i], b[i]);
                hi[i] = Math.max(a[i], b[i]);
            }
        }
        // Thickness padding is centred on the walk's own coordinate, which the walker already
        // clamped only to its own centreline margin - not to the padded edge. A twig ending
        // right at that margin still pushes its thickness half past 0 or 16 (loY going through
        // the floor was the first instance of this; any axis can hit it), so every axis gets
        // clamped here, after padding, rather than trusting the walker's own margin to have
        // been wide enough.
        for (int i = 0; i < 3; i++) {
            if (lo[i] < 0.0f) {
                hi[i] -= lo[i];
                lo[i] = 0.0f;
            }
            if (hi[i] > 16.0f) {
                lo[i] -= hi[i] - 16.0f;
                hi[i] = 16.0f;
            }
        }
        return new Segment(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], shade);
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private MoldBranching() {}
}
