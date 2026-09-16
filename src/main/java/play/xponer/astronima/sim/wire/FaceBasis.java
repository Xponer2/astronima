package play.xponer.astronima.sim.wire;

/**
 * The two in-plane axes of a block face, so a pixel on it has coordinates.
 *
 * <p>A wire is a <strong>one-pixel trace on the 16×16 grid of a face</strong> (design/electrical.md
 * §4.2), which means every face needs an agreed {@code u} and {@code v}. Agreed is the operative
 * word: the axes are what makes a trace crossing from one cell to the next land on the pixel it
 * left from, and two faces disagreeing about which way {@code u} runs would put a kink in every
 * seam.
 *
 * <p>Chosen by a single rule rather than a table of special cases — <em>{@code u} is the first
 * of X, Z, Y that is not the face's own normal, and {@code v} is the second</em> — so the answer
 * is derivable rather than remembered, and there is no entry to get wrong.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class FaceBasis {

    /** Pixels across a face. Sixteen, like everything else in this game. */
    public static final int GRID = 16;

    /** The face's first in-plane axis. */
    public static Face uAxis(Face face) {
        return switch (face) {
            case DOWN, UP -> Face.EAST;      // a floor: u runs east
            case NORTH, SOUTH -> Face.EAST;  // a north wall: u runs east
            case WEST, EAST -> Face.SOUTH;   // a west wall: u runs south
        };
    }

    /** The face's second in-plane axis, perpendicular to the first. */
    public static Face vAxis(Face face) {
        return switch (face) {
            case DOWN, UP -> Face.SOUTH;     // a floor: v runs south
            case NORTH, SOUTH -> Face.UP;    // a wall: v runs up
            case WEST, EAST -> Face.UP;
        };
    }

    /** True when this direction lies in the face's plane rather than through it. */
    public static boolean inPlane(Face face, Face direction) {
        return face.perpendicularTo(direction);
    }

    /**
     * Where a coordinate lands on another face, given which way that face's axes run.
     *
     * <p>When a trace turns a corner the coordinate <em>along the shared edge</em> carries over —
     * but the new face may measure that same direction backwards, in which case the coordinate
     * has to be mirrored. Getting this wrong does not break anything visibly; it puts the trace
     * on the wrong pixel of the new face, so a run that looked straight arrives somewhere else.
     *
     * @param along the world direction the shared edge runs in
     * @param value the coordinate along it on the face being left
     * @return the same position, measured the way {@code target} measures it
     */
    public static int transfer(Face target, Face along, int value) {
        if (uAxis(target) == along || vAxis(target) == along) {
            return value;
        }
        if (uAxis(target) == along.opposite() || vAxis(target) == along.opposite()) {
            return GRID - 1 - value;
        }
        throw new IllegalArgumentException(along + " does not lie in the plane of " + target);
    }

    /** Whether {@code along} is that face's u axis (true) or its v axis (false). */
    public static boolean isUAxis(Face target, Face along) {
        if (uAxis(target) == along || uAxis(target) == along.opposite()) {
            return true;
        }
        if (vAxis(target) == along || vAxis(target) == along.opposite()) {
            return false;
        }
        throw new IllegalArgumentException(along + " does not lie in the plane of " + target);
    }

    public static boolean onGrid(int coordinate) {
        return coordinate >= 0 && coordinate < GRID;
    }

    private FaceBasis() {}
}
