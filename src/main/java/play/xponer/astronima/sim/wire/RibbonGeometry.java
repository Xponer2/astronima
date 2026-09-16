package play.xponer.astronima.sim.wire;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turning one routed leg into N parallel ones — the geometry a bus is built on.
 * {@code design/bus.md} §1.1/§1.2/§3.1 is the design this follows.
 *
 * <h2>Replay, never re-search</h2>
 * Lane 0's route is whatever {@code WireRouter} already found. Every other lane is walked by
 * <strong>replaying lane 0's exact sequence of moves</strong> — the same {@code step}/{@code
 * straightOn}/{@code corner} calls {@link PixelGeometry} already exposes — starting from a pixel
 * offset {@link #LANE_PITCH} pixels across the direction of lane 0's own first move.
 *
 * <p>This is not an optimisation over searching N times independently; it is the thing that makes
 * "N lanes never cross" true <em>by construction</em> rather than by luck. An independent search per
 * lane can send two lanes round opposite sides of the same obstacle — same start, same end, and
 * still crossed in the middle. A replayed path cannot, because every lane takes the identical
 * sequence of turns lane 0 took; only the constant offset carried alongside it differs.
 *
 * <h2>Why a corner can still run a lane off the grid, even though the seed fit</h2>
 * {@link WirePixel#coordinateAcross} is exactly the value a corner or a straight-on crossing
 * preserves — {@link FaceBasis#transfer} either returns it unchanged or mirrors it
 * ({@code GRID - 1 - value}), and both of those are bijections on a value that was already on the
 * grid, so a corner or straight-on move can never itself construct an invalid pixel. An ordinary
 * interior {@code step()} can, though: a corner can hand the offset to a face where it is now the
 * <em>along</em> coordinate rather than the across one, and a lane that started with room to spare
 * can arrive at that face already near its edge — safe for lane 0, but not for a lane fourteen
 * pixels further out. A real ribbon has exactly this problem turning a tight inside corner; this is
 * not an implementation gap to route around, so every step is attempted, not assumed safe, and any
 * lane that cannot be built at all refuses the whole set (§3.1's own rule, applied one layer down).
 *
 * <p>Minecraft-free (rule 1): nothing here touches the world. Whether a derived pixel is actually
 * free to lay is the caller's question, exactly as it already is for a single trace.
 */
public final class RibbonGeometry {

    /**
     * Two pixels apart, never one — the same spacing every multi-pad part in the set already uses
     * for its own pads ({@code design/bus.md} §1.2), because a pad's own rule
     * (never adjacent to another pad, or it is a short) applies just as hard to a lane of a bus.
     */
    public static final int LANE_PITCH = 2;

    private enum Kind { STEP, STRAIGHT_ON, CORNER_INSIDE, CORNER_OUTSIDE }

    private record Move(Face direction, Kind kind) { }

    /**
     * Lane 0's route, replayed {@code laneCount - 1} more times from parallel starting points.
     *
     * @param lane0     lane 0's already-routed path, both ends included, as {@link WireRouter}
     *                  returns it
     * @param laneCount how many parallel lanes to derive, lane 0 included
     * @return one route per lane, lane 0 identical to the input, in ascending lane order — or
     *         empty when no offset direction seats every lane on the starting pixel's own face
     */
    public static Optional<List<List<WirePixel>>> laneRoutes(List<WirePixel> lane0, int laneCount) {
        if (laneCount < 1) {
            throw new IllegalArgumentException("a ribbon needs at least one lane");
        }
        if (lane0.isEmpty()) {
            return Optional.empty();
        }
        if (lane0.size() == 1) {
            // A leg of length zero (from == to) is refused before routing ever runs — WireRouter
            // itself returns a single-pixel result only for that case, and there is no direction
            // of travel to offset across, so a ribbon cannot be seated here either.
            return laneCount == 1 ? Optional.of(List.of(new ArrayList<>(lane0))) : Optional.empty();
        }

        List<Move> moves = new ArrayList<>(lane0.size() - 1);
        for (int i = 1; i < lane0.size(); i++) {
            moves.add(classify(lane0.get(i - 1), lane0.get(i)));
        }

        WirePixel seed = lane0.get(0);
        boolean acrossIsV = FaceBasis.isUAxis(seed.face(), moves.get(0).direction());
        int sign = seatingSign(seed, acrossIsV, laneCount);
        if (sign == 0) {
            return Optional.empty();
        }

        List<List<WirePixel>> lanes = new ArrayList<>(laneCount);
        try {
            for (int lane = 0; lane < laneCount; lane++) {
                WirePixel start = offset(seed, acrossIsV, sign * lane * LANE_PITCH);
                List<WirePixel> route = new ArrayList<>(lane0.size());
                route.add(start);
                WirePixel current = start;
                for (Move move : moves) {
                    current = apply(move, current);
                    route.add(current);
                }
                lanes.add(route);
            }
        } catch (IllegalArgumentException offGrid) {
            // Some lane ran off the edge of a face partway through — a corner handed its offset
            // to what is now the along coordinate, and this particular lane had no room left on
            // it. Refuse the whole set rather than lay the lanes that did fit; a five-lane ribbon
            // where eight were asked for is a wiring mistake nobody would see (§3.1).
            return Optional.empty();
        }
        return Optional.of(lanes);
    }

    /**
     * Which way to fan the lanes out so every one of them lands on the grid: {@code +1} if
     * offsetting upward seats them all, {@code -1} if only downward does, {@code 0} if neither —
     * the caller's cue to refuse rather than seat some lanes off the edge of the face.
     */
    private static int seatingSign(WirePixel seed, boolean acrossIsV, int laneCount) {
        int base = acrossIsV ? seed.v() : seed.u();
        int reach = (laneCount - 1) * LANE_PITCH;
        if (base + reach < FaceBasis.GRID) {
            return 1;
        }
        if (base - reach >= 0) {
            return -1;
        }
        return 0;
    }

    private static WirePixel offset(WirePixel from, boolean acrossIsV, int delta) {
        return acrossIsV
                ? new WirePixel(from.x(), from.y(), from.z(), from.face(), from.u(), from.v() + delta)
                : new WirePixel(from.x(), from.y(), from.z(), from.face(), from.u() + delta, from.v());
    }

    /** Which of {@link PixelGeometry}'s own moves turned {@code from} into {@code to}. */
    private static Move classify(WirePixel from, WirePixel to) {
        for (Face direction : Face.values()) {
            if (!FaceBasis.inPlane(from.face(), direction)) {
                continue;
            }
            if (!from.atEdge(direction)) {
                if (from.step(direction).equals(to)) {
                    return new Move(direction, Kind.STEP);
                }
                continue;
            }
            if (PixelGeometry.straightOn(from, direction).equals(to)) {
                return new Move(direction, Kind.STRAIGHT_ON);
            }
            if (PixelGeometry.corner(from, direction, true).equals(to)) {
                return new Move(direction, Kind.CORNER_INSIDE);
            }
            if (PixelGeometry.corner(from, direction, false).equals(to)) {
                return new Move(direction, Kind.CORNER_OUTSIDE);
            }
        }
        throw new IllegalArgumentException(to + " is not a neighbour of " + from
                + " — lane0 was not a real adjacency chain");
    }

    private static WirePixel apply(Move move, WirePixel from) {
        return switch (move.kind()) {
            case STEP -> from.step(move.direction());
            case STRAIGHT_ON -> PixelGeometry.straightOn(from, move.direction());
            case CORNER_INSIDE -> PixelGeometry.corner(from, move.direction(), true);
            case CORNER_OUTSIDE -> PixelGeometry.corner(from, move.direction(), false);
        };
    }

    private RibbonGeometry() {}
}
