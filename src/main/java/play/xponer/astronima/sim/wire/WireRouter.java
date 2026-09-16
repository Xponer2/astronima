package play.xponer.astronima.sim.wire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Working out where a trace should go — the thing the ghost preview draws.
 *
 * <p>The player anchors one end, points at the other, and a translucent route appears as they
 * move. <strong>Two modes, because a router that always knows best is automation and the player
 * is supposed to be building this themselves:</strong>
 *
 * <table>
 *   <tr><td>{@link Mode#PATHFIND}</td><td>A* finds a way round whatever is in the way. What you
 *       want for the long haul across a base you have already built.</td></tr>
 *   <tr><td>{@link Mode#STRAIGHT}</td><td>Straight legs, turning only when an axis is finished,
 *       and <strong>failing rather than detouring</strong>. What you want when the tidiness of
 *       the run is the point and a helpful diversion would ruin it.</td></tr>
 * </table>
 *
 * <p>That second mode is not a lesser version of the first. A router that quietly went around an
 * obstacle when the player asked for a straight line would be overruling them; refusing, and
 * showing where it stopped, leaves the decision where it belongs.
 *
 * <p>Minecraft-free (rule 1): the world appears only as {@link Space}, one question about one
 * pixel. That is what lets the server re-run the identical search the client previewed, rather
 * than trusting a path off the network (rule 13).
 */
public final class WireRouter {

    /** What the world will allow, asked one pixel at a time. */
    @FunctionalInterface
    public interface Space {
        /** True when a trace may occupy this pixel: something to fasten to, and nothing there. */
        boolean canOccupy(WirePixel pixel);

        /**
         * Extra cost for going through here — zero for an ordinary bare pixel.
         *
         * <p><strong>A route must not make electrical connections nobody asked for.</strong> The
         * player asked to get from one point to another; brushing an unrelated run of the same
         * colour on the way is a side effect they never chose and cannot see, and two conductors
         * touching really are one circuit. Reported as <em>"если два провода одинакового цвета
         * рядом провести, они соединяются, даже если ты не ставил соединение"</em> — which is
         * physically correct and was still the router's decision rather than theirs.
         *
         * <p>A cost rather than a refusal, because a corridor may genuinely have room for only one
         * lane. The router will detour several pixels to keep a gap and will still get there when
         * there is no gap to be had — and the ghost shows what it settled for either way.
         */
        default int penalty(WirePixel pixel) {
            return 0;
        }
    }

    public enum Mode {
        /** Go around obstacles. */
        PATHFIND,
        /** Go straight, and stop if that is impossible. */
        STRAIGHT
    }

    /** How many pixels a single search may examine before giving up. */
    public static final int DEFAULT_BUDGET = 20_000;

    /**
     * The trace from one pixel to another, or empty when there is no route.
     *
     * <p>Includes both ends, so the result is exactly the set of pixels that would be laid.
     */
    public static Optional<List<WirePixel>> route(WirePixel from, WirePixel to, Space space,
                                                  Mode mode, int budget) {
        if (from.equals(to)) {
            return space.canOccupy(from) ? Optional.of(List.of(from)) : Optional.empty();
        }
        if (!space.canOccupy(from) || !space.canOccupy(to)) {
            return Optional.empty();
        }
        return mode == Mode.STRAIGHT ? straight(from, to, space, budget)
                : pathfind(from, to, space, budget);
    }

    public static Optional<List<WirePixel>> route(WirePixel from, WirePixel to, Space space,
                                                  Mode mode) {
        return route(from, to, space, mode, DEFAULT_BUDGET);
    }

    // ---- A* --------------------------------------------------------------------

    private static Optional<List<WirePixel>> pathfind(WirePixel from, WirePixel to, Space space,
                                                      int budget) {
        Map<WirePixel, WirePixel> cameFrom = new HashMap<>();
        Map<WirePixel, Integer> cost = new HashMap<>();
        // Ordered by estimate first and then by a stable tiebreak: two routes of equal length
        // must not depend on hash order, or the ghost would flicker between them as the player
        // moves and the server could disagree with the client about what was previewed
        // (PLAN rule 19).
        PriorityQueue<WirePixel> open = new PriorityQueue<>(
                Comparator.<WirePixel>comparingInt(pixel ->
                                cost.getOrDefault(pixel, Integer.MAX_VALUE) + estimate(pixel, to))
                        .thenComparing(WireRouter::tiebreak));
        cost.put(from, 0);
        open.add(from);
        int examined = 0;

        while (!open.isEmpty() && examined++ < budget) {
            WirePixel current = open.poll();
            if (current.equals(to)) {
                return Optional.of(rebuild(cameFrom, current));
            }
            int soFar = cost.getOrDefault(current, Integer.MAX_VALUE);
            for (WirePixel neighbour : PixelGeometry.neighbours(current)) {
                if (!space.canOccupy(neighbour)) {
                    continue;
                }
                // One per step, plus whatever the world thinks that particular pixel costs. The
                // heuristic stays admissible because a penalty only ever *raises* a real cost.
                int next = soFar + 1 + space.penalty(neighbour);
                if (next < cost.getOrDefault(neighbour, Integer.MAX_VALUE)) {
                    cost.put(neighbour, next);
                    cameFrom.put(neighbour, current);
                    open.add(neighbour);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Straight-line distance in pixels, which never overestimates.
     *
     * <p>Admissible because every step moves the trace one pixel through space at most — so the
     * search cannot be talked out of a route that exists.
     */
    private static int estimate(WirePixel from, WirePixel to) {
        double[] a = position(from);
        double[] b = position(to);
        return (int) (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]));
    }

    /** Where a pixel physically is, in pixel units, so distances mean something. */
    public static double[] position(WirePixel pixel) {
        Face face = pixel.face();
        Face uAxis = FaceBasis.uAxis(face);
        Face vAxis = FaceBasis.vAxis(face);
        double du = pixel.u() + 0.5 - FaceBasis.GRID / 2.0;
        double dv = pixel.v() + 0.5 - FaceBasis.GRID / 2.0;
        double half = FaceBasis.GRID / 2.0;
        return new double[] {
                pixel.x() * FaceBasis.GRID + half + uAxis.dx() * du + vAxis.dx() * dv
                        + face.dx() * half,
                pixel.y() * FaceBasis.GRID + half + uAxis.dy() * du + vAxis.dy() * dv
                        + face.dy() * half,
                pixel.z() * FaceBasis.GRID + half + uAxis.dz() * du + vAxis.dz() * dv
                        + face.dz() * half};
    }

    /** A total order on pixels, so equal-cost routes resolve the same way every time. */
    private static long tiebreak(WirePixel pixel) {
        return ((long) pixel.x() << 40) ^ ((long) pixel.y() << 28) ^ ((long) pixel.z() << 16)
                ^ ((long) pixel.face().ordinal() << 12) ^ (pixel.u() << 6) ^ pixel.v();
    }

    private static List<WirePixel> rebuild(Map<WirePixel, WirePixel> cameFrom, WirePixel end) {
        List<WirePixel> path = new ArrayList<>();
        WirePixel at = end;
        while (at != null) {
            path.add(at);
            at = cameFrom.get(at);
        }
        Collections.reverse(path);
        return path;
    }

    // ---- straight legs ---------------------------------------------------------

    /**
     * Straight legs only: close the biggest gap first, turn when it is closed, refuse when
     * blocked.
     *
     * <p>Greedy on purpose. The player asked for a straight run, so a search that explored
     * alternatives would be answering a question they did not ask — and the useful failure is
     * <em>"it stops here"</em>, which tells them exactly which block to move.
     */
    private static Optional<List<WirePixel>> straight(WirePixel from, WirePixel to, Space space,
                                                      int budget) {
        List<WirePixel> path = new ArrayList<>();
        Set<WirePixel> seen = new HashSet<>();
        WirePixel at = from;
        path.add(at);
        seen.add(at);

        for (int step = 0; step < budget; step++) {
            if (at.equals(to)) {
                return Optional.of(path);
            }
            WirePixel best = null;
            double bestGap = gap(at, to);
            for (WirePixel candidate : PixelGeometry.neighbours(at)) {
                if (seen.contains(candidate) || !space.canOccupy(candidate)) {
                    continue;
                }
                double candidateGap = gap(candidate, to);
                if (candidateGap < bestGap) {
                    bestGap = candidateGap;
                    best = candidate;
                }
            }
            if (best == null) {
                return Optional.empty(); // stopped, and that is the answer
            }
            at = best;
            path.add(at);
            seen.add(at);
        }
        return Optional.empty();
    }

    private static double gap(WirePixel from, WirePixel to) {
        double[] a = position(from);
        double[] b = position(to);
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    /** Every pixel a laid route would occupy, for costing it before it is committed. */
    public static int wireCost(List<WirePixel> route) {
        return route == null ? 0 : route.size();
    }

    private WireRouter() {}
}
