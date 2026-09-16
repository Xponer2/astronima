package play.xponer.astronima.sim.wire;

/**
 * The six faces of a block-space cell, as the wire layer sees them.
 *
 * <p>Deliberately <em>not</em> Minecraft's {@code Direction}: rule 1 keeps {@code sim/} free of
 * the game, and the connection rules built on it are pure geometry that deserves to be
 * exhaustively unit-tested without a world. The block layer maps {@code Direction} onto this
 * with a switch, which the compiler checks — so the two cannot drift apart silently, and if
 * Minecraft ever grew a seventh direction the mapping would fail to build rather than fail to
 * connect.
 *
 * <p>The normal points <strong>from the cell toward the block that supports the wire</strong>.
 * A wire on {@link #DOWN} is lying on a floor: it is in the cell above the floor block, pressed
 * against that cell's bottom face.
 */
public enum Face {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Face(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    /** The face across the cell from this one. */
    public Face opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /**
     * True when the two faces meet at an edge rather than lying flat or facing off.
     *
     * <p>The one predicate both corner cases turn on: a wire can only bend where two surfaces
     * actually touch, which is neither the same plane nor the opposite one.
     */
    public boolean perpendicularTo(Face other) {
        return this != other && this != other.opposite();
    }

    /** Dot product of the two normals — 0 exactly when they are perpendicular. */
    public int dot(Face other) {
        return dx * other.dx + dy * other.dy + dz * other.dz;
    }
}
