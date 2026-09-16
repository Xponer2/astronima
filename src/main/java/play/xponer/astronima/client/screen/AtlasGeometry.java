package play.xponer.astronima.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure geometry for the atlas chart (design/astra-atlas-redesign.md §3, §4, §9) — deliberately
 * free of Minecraft, LDLib2 and even {@code org.joml} types so it can be exercised by the plain
 * JUnit runner: any class that touches those fails to load under the {@code test} task with
 * {@code NoClassDefFoundError} even for calling an unrelated pure method (confirmed building this
 * leaf) — the same MC-classpath gap {@code ControlWiringTest}/{@code CodexBlocksWiringTest} work
 * around for MC-heavy classes by scraping source text instead; this class sidesteps it entirely by
 * never importing anything that trips it. {@link AtlasUi} is the sole production caller.
 */
public final class AtlasGeometry {

    /** A projected chart position, in real GUI pixels within the card. */
    public record Pixel(double x, double y) {}

    /**
     * The exact mapping {@code AtlasUi.pixelCenter} needs: a normalised chart position
     * {@code (u, v)} (both in {@code [0, 1]} — {@code SkyProjection}'s own output shape) onto real
     * pixels within a panel of {@code panelWidth} x {@code panelHeight}, inset by {@code inset}
     * pixels on every edge so a projected {@code 0} or {@code 1} lands on the panel's own drawable
     * edge rather than under the card's padding.
     */
    public static Pixel projectedPixel(double u, double v, double panelWidth, double panelHeight, double inset) {
        double drawableWidth = panelWidth - 2.0 * inset;
        double drawableHeight = panelHeight - 2.0 * inset;
        return new Pixel(inset + u * drawableWidth, inset + v * drawableHeight);
    }

    /** One short straight run of a claim's stroke (design/astra-atlas-redesign.md §4: "12 short
     *  segments... each given a small perpendicular offset"). The wander offset itself is
     *  {@link AtlasMotion}'s job; this is just the segment's own resting endpoints. */
    public record Segment(double x1, double y1, double x2, double y2) {
        public double length() {
            double dx = x2 - x1;
            double dy = y2 - y1;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    /**
     * {@code segmentCount} segments spanning exactly between {@code (ax,ay)} and {@code (bx,by)},
     * inset by {@code iconRadius} at each end so the first segment's first point and the last
     * segment's last point touch the icons' own rims rather than their bare centres — the exact
     * arithmetic design/astra-research.md §6a.3's original complaint ("a bare rotated rectangle
     * floating independent of the icons it touches") was about, and design/astra-atlas-redesign.md
     * §10's own first mutation ("drop the half-icon-radius inset") exists to guard against
     * regressing.
     *
     * <p><strong>Degenerate case, named in §9:</strong> a claim whose two anchors project to the
     * same pixel (real at the chart's poles, where every longitude maps to one point) has no
     * defined direction. Rather than dividing by a zero length, every returned segment collapses
     * to that single point — a length-zero stroke draws nothing, which is honest, not a crash.
     */
    public static List<Segment> strokeSegments(double ax, double ay, double bx, double by,
                                                double iconRadius, int segmentCount) {
        if (segmentCount < 1) {
            throw new IllegalArgumentException("segmentCount must be at least 1, was " + segmentCount);
        }
        double dx = bx - ax;
        double dy = by - ay;
        double length = Math.sqrt(dx * dx + dy * dy);

        double startX;
        double startY;
        double endX;
        double endY;
        if (length < 1.0e-6) {
            // Coincident anchors: no direction exists. Collapse the whole stroke to that one
            // point instead of dividing by zero.
            startX = ax;
            startY = ay;
            endX = ax;
            endY = ay;
        } else {
            double ux = dx / length;
            double uy = dy / length;
            // Never inset past the midpoint, so two icons closer together than 2x their radius
            // still produce a valid (if short) stroke rather than one whose ends have crossed.
            double inset = Math.min(iconRadius, length / 2.0);
            startX = ax + ux * inset;
            startY = ay + uy * inset;
            endX = bx - ux * inset;
            endY = by - uy * inset;
        }

        List<Segment> segments = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            double t0 = (double) i / segmentCount;
            double t1 = (double) (i + 1) / segmentCount;
            segments.add(new Segment(
                    lerp(startX, endX, t0), lerp(startY, endY, t0),
                    lerp(startX, endX, t1), lerp(startY, endY, t1)));
        }
        return segments;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * The claim stroke's own box, anchored at its own start ({@code anchorX,anchorY} is the
     * inset "A" endpoint {@link #strokeSegments} already computes), sized and rotated to reach
     * {@code (bx,by)}'s own inset endpoint.
     *
     * <p><strong>Found live, in play: the opening animation and the resting frame used to
     * disagree about their own pivot.</strong> {@code AtlasUi} used to lay this box out centred
     * on the segment's midpoint (correct only if the box is then rotated about its own centre)
     * but the opening animation rotated it about its own left-middle edge instead (needed so a
     * scale-x-from-zero grows the stroke visibly from A toward B) — two different pivots given
     * the same centre-anchored layout, so the two settled at different final rotated positions
     * whenever the pair was not perfectly horizontal. Real placements almost never are.
     *
     * <p>The fix is this method: anchor the box at A itself and always rotate about that same
     * point (the box's own left-middle). A scale-x animation pivoted there already grows toward
     * B correctly, and the resting frame, rotated about the identical point, lands on the exact
     * same pixels — one geometric fact instead of two that happened to agree by accident.
     */
    public record StrokeBox(double anchorX, double anchorY, double width, double angleDegrees) {}

    public static StrokeBox strokeBox(double ax, double ay, double bx, double by,
                                      double iconRadius, int segmentCount) {
        List<Segment> segments = strokeSegments(ax, ay, bx, by, iconRadius, segmentCount);
        Segment first = segments.get(0);
        Segment last = segments.get(segments.size() - 1);
        double dx = last.x2() - first.x1();
        double dy = last.y2() - first.y1();
        double width = Math.max(Math.sqrt(dx * dx + dy * dy), 0.01);
        double angleDegrees = Math.toDegrees(Math.atan2(dy, dx));
        return new StrokeBox(first.x1(), first.y1(), width, angleDegrees);
    }

    /** One point on a claim's curled stroke path (round 4 - "неоновая молния которая
     *  изгибается... либо линия магии которая закручивается"), with its own cumulative
     *  arc-length fraction from 0 (the very start, at A's own curl) to 1 (the very end, at B's
     *  own curl) - what a travelling pulse and a segment-count reveal both need, since neither
     *  "fraction of box width" nor "fraction of point count" places a point correctly once the
     *  path is not straight and its segments are not equal length. */
    public record CurlPoint(double x, double y, double arcFraction) {}

    private static final int CURL_STEPS = 11;
    private static final double CURL_TURNS = 2.1;
    private static final double CURL_RADIUS_FRACTION_OF_DISTANCE = 0.16;
    private static final double CURL_RADIUS_MAX_PX = 40.0;
    private static final int CROSS_DEPTH = 5;
    private static final double CROSS_SPREAD_FRACTION_OF_DISTANCE = 0.05;
    private static final double CROSS_SPREAD_DECAY = 0.58;

    /**
     * A curl-crossing-curl path between two icons' own centres, replacing {@link
     * #strokeSegments}'s straight run for the same two anchors: a spiral curl at A shrinking to
     * exactly {@code (ax,ay)}, a jagged (midpoint-displacement) crossing to exactly
     * {@code (bx,by)}, then a curl at B growing back out from there. Deterministic per {@code
     * seed} — the same claim always draws the same shape, no per-frame reflicker (that is what
     * {@link play.xponer.astronima.client.screen.AtlasMotion}'s own wander/pulse functions are
     * for instead).
     *
     * <p><strong>Curl radius scales with the real distance between the two icons, capped both
     * ways</strong> ({@link #CURL_RADIUS_FRACTION_OF_DISTANCE}, {@link #CURL_RADIUS_MAX_PX}):
     * never so large on a short pair that the two curls collide, never so large on a far pair
     * that one curl dwarfs the whole chart. The curl's own tightest point is exactly the icon's
     * own centre — geometrically converging there, not stopping short of it — which is safe only
     * because the icon itself always draws after (on top of) this stroke; z-order, not geometry,
     * is what keeps the icon legible, the same way a real cable never worries about drawing
     * "through" the socket it plugs into.
     *
     * <p>Degenerate case, same rule {@link #strokeSegments} already keeps: coincident anchors
     * collapse to that single point rather than dividing by zero.
     */
    public static List<CurlPoint> curledStrokePoints(double ax, double ay, double bx, double by,
                                                      double iconRadius, long seed) {
        double dx = bx - ax;
        double dy = by - ay;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 1.0e-6) {
            return List.of(new CurlPoint(ax, ay, 0.0), new CurlPoint(ax, ay, 1.0));
        }

        Random rnd = new Random(seed);
        double curlRadius = Math.min(distance * CURL_RADIUS_FRACTION_OF_DISTANCE, CURL_RADIUS_MAX_PX);
        // Never let the two curls' own reach exceed most of the distance between them - leaves
        // real room for the crossing between whatever is left, however close the pair is.
        curlRadius = Math.min(curlRadius, distance * 0.32);

        List<double[]> raw = new ArrayList<>();
        appendCurl(raw, ax, ay, bx, by, curlRadius, 1, rnd);
        appendCross(raw, ax, ay, bx, by, distance * CROSS_SPREAD_FRACTION_OF_DISTANCE, rnd);
        appendCurl(raw, bx, by, ax, ay, curlRadius, -1, rnd);
        reverseTail(raw, ax, ay, bx, by, curlRadius);

        double totalLength = 0.0;
        double[] cumulative = new double[raw.size()];
        for (int i = 1; i < raw.size(); i++) {
            double segDx = raw.get(i)[0] - raw.get(i - 1)[0];
            double segDy = raw.get(i)[1] - raw.get(i - 1)[1];
            totalLength += Math.sqrt(segDx * segDx + segDy * segDy);
            cumulative[i] = totalLength;
        }
        List<CurlPoint> points = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            double fraction = totalLength < 1.0e-6 ? 0.0 : cumulative[i] / totalLength;
            points.add(new CurlPoint(raw.get(i)[0], raw.get(i)[1], fraction));
        }
        return points;
    }

    /** How many jagged strands {@link #braidedStrokeStrands} overlaps, and each one's own
     *  spread relative to the widest (round 4's own correction, matching the reviewed artifact
     *  directly: "посмотри как эта линия сделана в артефакте... сделай так же" — the artifact's
     *  own {@code drawLightning} braided the crossing from several overlapping jagged strands at
     *  decreasing spread, {@code 22, 19, 16, 13}, which is exactly these four ratios of the
     *  widest). A single strand at real depth read as a clean zigzag, not chaos; several
     *  overlapping ones, each independently random, are what actually reads as a tangle. */
    private static final double[] BRAID_SPREAD_RATIOS = {1.0, 0.8636, 0.7273, 0.5909};

    /**
     * Several overlapping jagged (midpoint-displacement) strands between two icons' own rims —
     * {@link #curledStrokePoints} without the spiral curls, round 4's own correction: "оставить
     * только линию... не добавлять вихри" (keep only the line, do not add the curls). Insets
     * from each icon's real centre by {@code iconRadius}, the same rim-touching rule {@link
     * #strokeSegments} already uses, rather than converging on the bare centre the way the
     * curled version safely could (that safety depended on the curl's own decoration covering
     * the join; a plain braid has none, so it goes back to landing on the rim instead).
     * Deterministic per {@code seed} - same claim, same shape, every frame; only {@link
     * play.xponer.astronima.client.screen.AtlasMotion}'s own wander/pulse animate it.
     */
    public static List<List<CurlPoint>> braidedStrokeStrands(double ax, double ay, double bx, double by,
                                                              double iconRadius, long seed) {
        double dx = bx - ax;
        double dy = by - ay;
        double distance = Math.sqrt(dx * dx + dy * dy);
        List<List<CurlPoint>> strands = new ArrayList<>(BRAID_SPREAD_RATIOS.length);
        if (distance < 1.0e-6) {
            List<CurlPoint> single = List.of(new CurlPoint(ax, ay, 0.0), new CurlPoint(ax, ay, 1.0));
            for (int s = 0; s < BRAID_SPREAD_RATIOS.length; s++) {
                strands.add(single);
            }
            return strands;
        }
        double ux = dx / distance;
        double uy = dy / distance;
        double inset = Math.min(iconRadius, distance / 2.0);
        double startX = ax + ux * inset;
        double startY = ay + uy * inset;
        double endX = bx - ux * inset;
        double endY = by - uy * inset;
        double baseSpread = distance * CROSS_SPREAD_FRACTION_OF_DISTANCE;

        for (int s = 0; s < BRAID_SPREAD_RATIOS.length; s++) {
            // A distinct, deterministic seed per strand - the same seed reused for every strand
            // would make them re-trace nearly the same random walk instead of genuinely
            // different ones, which is what a braid actually needs to read as several strands
            // rather than one thick one.
            Random rnd = new Random(seed * 1_000_003L + s);
            List<double[]> raw = new ArrayList<>(List.of(new double[] {startX, startY}));
            appendCross(raw, startX, startY, endX, endY, baseSpread * BRAID_SPREAD_RATIOS[s], rnd);
            strands.add(withArcFractions(raw));
        }
        return strands;
    }

    /** Shared by {@link #curledStrokePoints} and {@link #jaggedStrokePoints}: turns a raw point
     *  list into {@link CurlPoint}s carrying their own cumulative arc-length fraction. */
    private static List<CurlPoint> withArcFractions(List<double[]> raw) {
        double totalLength = 0.0;
        double[] cumulative = new double[raw.size()];
        for (int i = 1; i < raw.size(); i++) {
            double segDx = raw.get(i)[0] - raw.get(i - 1)[0];
            double segDy = raw.get(i)[1] - raw.get(i - 1)[1];
            totalLength += Math.sqrt(segDx * segDx + segDy * segDy);
            cumulative[i] = totalLength;
        }
        List<CurlPoint> points = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            double fraction = totalLength < 1.0e-6 ? 0.0 : cumulative[i] / totalLength;
            points.add(new CurlPoint(raw.get(i)[0], raw.get(i)[1], fraction));
        }
        return points;
    }

    /** A spiral centred on {@code (cx,cy)} (an icon's own centre), oriented toward {@code
     *  (towardX,towardY)}, shrinking from {@link #CURL_RADIUS_MAX_PX}-capped {@code radius} down
     *  to exactly 0 at the centre — {@code windingDirection} is {@code +1} or {@code -1} so the
     *  two curls at either end of one claim spiral opposite ways, matching the reference's own
     *  mirrored pair rather than two curls that look identical rotated. */
    private static void appendCurl(List<double[]> out, double cx, double cy, double towardX, double towardY,
                                   double radius, int windingDirection, Random rnd) {
        double dirAngle = Math.atan2(towardY - cy, towardX - cx);
        for (int i = 0; i <= CURL_STEPS; i++) {
            double t = (double) i / CURL_STEPS;
            double angle = dirAngle + windingDirection * CURL_TURNS * 2.0 * Math.PI * t;
            double jitter = 1.0 + (rnd.nextDouble() - 0.5) * 0.12;
            double r = radius * (1.0 - t) * jitter;
            out.add(new double[] {cx + Math.cos(angle) * r, cy + Math.sin(angle) * r});
        }
    }

    /** The jagged crossing between the two icons' own centres — real midpoint displacement, not
     *  a hand-authored zigzag, so the shape is a different, still-organic-looking claim to claim
     *  rather than one fixed silhouette. Spread scales with the real distance so a short pair's
     *  crossing does not overshoot itself the way a fixed pixel spread would. */
    private static void appendCross(List<double[]> out, double ax, double ay, double bx, double by,
                                    double initialSpread, Random rnd) {
        List<double[]> pts = new ArrayList<>(List.of(new double[] {ax, ay}, new double[] {bx, by}));
        double spread = initialSpread;
        for (int depth = 0; depth < CROSS_DEPTH; depth++) {
            List<double[]> next = new ArrayList<>(List.of(pts.get(0)));
            for (int i = 0; i < pts.size() - 1; i++) {
                double[] p0 = pts.get(i);
                double[] p1 = pts.get(i + 1);
                double mx = (p0[0] + p1[0]) / 2.0;
                double my = (p0[1] + p1[1]) / 2.0;
                double segDx = p1[0] - p0[0];
                double segDy = p1[1] - p0[1];
                double len = Math.max(Math.sqrt(segDx * segDx + segDy * segDy), 1.0e-6);
                double nx = -segDy / len;
                double ny = segDx / len;
                double offset = (rnd.nextDouble() * 2.0 - 1.0) * spread;
                next.add(new double[] {mx + nx * offset, my + ny * offset});
                next.add(p1);
            }
            pts = next;
            spread *= CROSS_SPREAD_DECAY;
        }
        // First and last points here are A and B themselves, already the join points the two
        // curls converge on - skip the very first (curlA's own appendCurl already ended there)
        // and keep the rest, including the final B, for appendCurl(b...) to continue from.
        for (int i = 1; i < pts.size(); i++) {
            out.add(pts.get(i));
        }
    }

    /** {@code appendCurl} for B is built to shrink toward B from an outward point, same as A's -
     *  but it needs to run in the opposite time direction (grow outward from B, since the path
     *  arrives at B and must leave it again) - simplest expressed as building it the same way as
     *  A's and reversing just that tail in place, rather than a second, subtly-different curl
     *  function to keep in sync with the first. */
    private static void reverseTail(List<double[]> raw, double ax, double ay, double bx, double by,
                                    double curlRadius) {
        int tailStart = raw.size() - (CURL_STEPS + 1);
        List<double[]> tail = new ArrayList<>(raw.subList(tailStart, raw.size()));
        java.util.Collections.reverse(tail);
        for (int i = 0; i < tail.size(); i++) {
            raw.set(tailStart + i, tail.get(i));
        }
    }

    private AtlasGeometry() {}
}
