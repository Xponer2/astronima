package play.xponer.astronima.wire;

import net.minecraft.core.Direction;
import play.xponer.astronima.sim.wire.Face;

/**
 * The seam between Minecraft's directions and the wire geometry's own faces.
 *
 * <p>Both mappings are <strong>exhaustive switches on purpose</strong>. This is where tested,
 * world-free geometry meets the untested world, and a switch that stops covering every case
 * <em>fails to compile</em> rather than failing to connect. A lookup table would simply have been
 * silently wrong — and wrong here means a trace that turns the wrong way at a corner, which looks
 * like nothing at all until a circuit does not conduct.
 */
public final class Faces {

    public static Face of(Direction direction) {
        return switch (direction) {
            case DOWN -> Face.DOWN;
            case UP -> Face.UP;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case EAST -> Face.EAST;
        };
    }

    public static Direction of(Face face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    private Faces() {}
}
