package play.xponer.astronima.sim.wire;

/**
 * One pixel of wire: a cell, a face of it, and where on that face's 16×16 grid the trace sits.
 *
 * <p>This is the address the electrical tier is actually built on. A wire is not a cable that
 * occupies a block and it is not one cable per surface — it is a <strong>trace</strong>, placed
 * to the pixel, so a single wall can carry a dozen of them side by side exactly where the player
 * routed each one.
 *
 * <p>Which <em>surface</em> a trace is on is the same idea one level coarser, and lives with the
 * surface-adjacency oracle in the test sources — nothing in the running game needs it, because
 * the pixel graph answers every runtime question.
 *
 * <p>Minecraft-free (rule 1).
 *
 * @param u across the face along {@link FaceBasis#uAxis}, 0..15
 * @param v across the face along {@link FaceBasis#vAxis}, 0..15
 */
public record WirePixel(int x, int y, int z, Face face, int u, int v) {

    public WirePixel {
        if (!FaceBasis.onGrid(u) || !FaceBasis.onGrid(v)) {
            throw new IllegalArgumentException("pixel " + u + "," + v + " is off the face");
        }
    }

    /** The same face, one pixel along, without leaving it — or empty at the edge. */
    public WirePixel step(Face direction) {
        boolean alongU = FaceBasis.isUAxis(face, direction);
        int sign = (alongU ? FaceBasis.uAxis(face) : FaceBasis.vAxis(face)) == direction ? 1 : -1;
        int nextU = alongU ? u + sign : u;
        int nextV = alongU ? v : v + sign;
        return new WirePixel(x, y, z, face, nextU, nextV);
    }

    /** True when a step that way would leave this face. */
    public boolean atEdge(Face direction) {
        boolean alongU = FaceBasis.isUAxis(face, direction);
        int sign = (alongU ? FaceBasis.uAxis(face) : FaceBasis.vAxis(face)) == direction ? 1 : -1;
        int coordinate = alongU ? u : v;
        return sign > 0 ? coordinate == FaceBasis.GRID - 1 : coordinate == 0;
    }

    /** The coordinate that runs along the given in-plane direction. */
    public int coordinateAlong(Face direction) {
        return FaceBasis.isUAxis(face, direction) ? u : v;
    }

    /**
     * The coordinate that survives a turn in this direction — the one along the shared edge.
     *
     * <p>Turning a corner keeps a trace at the same position <em>across</em> the edge it crosses,
     * which is the coordinate measured perpendicular to the direction of travel.
     */
    public int coordinateAcross(Face direction) {
        return FaceBasis.isUAxis(face, direction) ? v : u;
    }

    /** The in-plane axis perpendicular to a direction of travel — the shared edge's direction. */
    public Face edgeAxis(Face direction) {
        return FaceBasis.isUAxis(face, direction)
                ? FaceBasis.vAxis(face) : FaceBasis.uAxis(face);
    }
}
