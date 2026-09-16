package play.xponer.astronima.sim.wire;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a trace can go from one pixel — the routing graph the whole tier is laid on.
 *
 * <p>There is a coarser question — <em>can these two surfaces carry a connection at all</em> —
 * and this answers the fine one: <em>this pixel, on this face, to which pixel next.</em> The
 * pathfinder walks it, the ghost preview draws what it found, and the network flood-fill agrees
 * with both because all three ask this same method. The coarse rule survives as
 * {@code SurfaceAdjacency} in the test sources, where it serves as an independent second opinion
 * this is checked against.
 *
 * <h2>Four steps, and three ways off the edge</h2>
 * Inside a face a trace moves like anything on a grid — one pixel along {@code u} or {@code v}.
 * At the boundary it must leave the face, and there are exactly three ways to do that:
 *
 * <table>
 *   <tr><td><strong>Straight on</strong></td><td>the next cell's matching face; the coordinate
 *       along travel wraps to the far edge</td></tr>
 *   <tr><td><strong>Inside corner</strong></td><td>up the wall of this same cell</td></tr>
 *   <tr><td><strong>Outside corner</strong></td><td>over the lip of this trace's own support
 *       block and down its side</td></tr>
 * </table>
 *
 * <p><strong>The coordinate along the shared edge is preserved through all three.</strong> That
 * is what makes a run stay on its own lane when it turns a corner instead of sliding sideways —
 * and it is the single most fiddly thing here, because a face may measure that direction
 * backwards, in which case the value has to be mirrored ({@link FaceBasis#transfer}).
 *
 * <p>Every one of these is a <em>candidate</em>. Whether a support block exists, whether the
 * pixel is already taken and whether the colours match are all questions for the world; this is
 * pure geometry.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class PixelGeometry {

    /** Every pixel a trace here could continue to, whether or not anything is there. */
    public static List<WirePixel> neighbours(WirePixel from) {
        List<WirePixel> found = new ArrayList<>(12);
        for (Face direction : Face.values()) {
            if (!FaceBasis.inPlane(from.face(), direction)) {
                continue;
            }
            if (!from.atEdge(direction)) {
                found.add(from.step(direction));
                continue;
            }
            found.add(straightOn(from, direction));
            found.add(corner(from, direction, true));
            found.add(corner(from, direction, false));
        }
        return found;
    }

    /** Onto the next cell's matching face, at the same place across the seam. */
    public static WirePixel straightOn(WirePixel from, Face direction) {
        Face face = from.face();
        boolean alongU = FaceBasis.isUAxis(face, direction);
        // Travelling off one edge arrives at the opposite one, so a straight run reads as
        // continuous across the seam rather than jumping back to where it started.
        int wrapped = from.coordinateAlong(direction) == 0 ? FaceBasis.GRID - 1 : 0;
        int u = alongU ? wrapped : from.u();
        int v = alongU ? from.v() : wrapped;
        return new WirePixel(from.x() + direction.dx(), from.y() + direction.dy(),
                from.z() + direction.dz(), face, u, v);
    }

    /**
     * Around a corner — into this cell's own wall, or over the edge of the support block.
     *
     * <p>Both are the same construction with two things swapped: which cell the trace lands in,
     * and which end of the new face it arrives at. Written once for that reason: two copies of
     * this arithmetic would be two chances to mirror a coordinate the wrong way, and the symptom
     * would be a trace that silently reappears on the far side of a wall.
     *
     * @param inside true for the inside of the corner, false for wrapping the outside
     */
    public static WirePixel corner(WirePixel from, Face direction, boolean inside) {
        Face face = from.face();
        Face target = inside ? direction : direction.opposite();
        Face edgeAxis = from.edgeAxis(direction);
        int preserved = from.coordinateAcross(direction);

        // The trace arrives at whichever end of the new face lies against the face it left.
        Face other = FaceBasis.isUAxis(target, edgeAxis)
                ? FaceBasis.vAxis(target) : FaceBasis.uAxis(target);
        boolean towardsOldFace = other == face;
        int arrival = inside == towardsOldFace ? FaceBasis.GRID - 1 : 0;

        int cellX = from.x();
        int cellY = from.y();
        int cellZ = from.z();
        if (!inside) {
            // Outside: the trace leaves this cell entirely, wrapping the support block it is
            // stapled to — one step along travel, one step out along the old face's normal.
            cellX += direction.dx() + face.dx();
            cellY += direction.dy() + face.dy();
            cellZ += direction.dz() + face.dz();
        }

        int mapped = FaceBasis.transfer(target, edgeAxis, preserved);
        return FaceBasis.isUAxis(target, edgeAxis)
                ? new WirePixel(cellX, cellY, cellZ, target, mapped, arrival)
                : new WirePixel(cellX, cellY, cellZ, target, arrival, mapped);
    }

    private PixelGeometry() {}
}
