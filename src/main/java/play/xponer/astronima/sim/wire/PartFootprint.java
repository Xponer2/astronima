package play.xponer.astronima.sim.wire;

/**
 * Where a face-mounted part's pixels land, once it has been turned.
 *
 * <p>A part lies on a block face in the same plane as the traces, occupying a rectangle of the
 * 16×16 pixel grid. It can be turned through four quarter-turns, and every question that follows
 * from that — which pixels are covered, where each pad ends up, whether it still fits on the face —
 * is arithmetic on two integers and a rotation.
 *
 * <p><strong>Which is why it lives here.</strong> Rotation is exactly the kind of thing that is
 * "obviously right" until a part on a ceiling has its inputs on the wrong side, and by then the
 * only way to check is to place one and look. Sixteen combinations of rotation and corner is a
 * small enough space to test <em>completely</em>, which is what {@code PixelGeometry} does for the
 * connection cases next door and for the same reason.
 *
 * <p>The turn is <strong>clockwise in the face's own (u, v) basis</strong>. That is a choice, not a
 * law, and it is stated because the alternative differs only by which way a wrench appears to turn
 * a part — invisible in the code and immediately obvious in the world.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class PartFootprint {

    /** How many distinct turns a part has. */
    public static final int ROTATIONS = 4;

    /** The extent along u after turning, given the part's own width and height. */
    public static int spanU(int width, int height, int rotation) {
        return odd(rotation) ? height : width;
    }

    /** The extent along v after turning. */
    public static int spanV(int width, int height, int rotation) {
        return odd(rotation) ? width : height;
    }

    /**
     * Where a point of the part's own grid ends up on the face, measured from the part's corner.
     *
     * @param localU across the part as it is drawn on paper, 0 at its left
     * @param localV down the part as it is drawn on paper, 0 at its top
     */
    public static int offsetU(int width, int height, int rotation, int localU, int localV) {
        return switch (Math.floorMod(rotation, ROTATIONS)) {
            case 1 -> height - 1 - localV;
            case 2 -> width - 1 - localU;
            case 3 -> localV;
            default -> localU;
        };
    }

    /** @see #offsetU */
    public static int offsetV(int width, int height, int rotation, int localU, int localV) {
        return switch (Math.floorMod(rotation, ROTATIONS)) {
            case 1 -> localU;
            case 2 -> height - 1 - localV;
            case 3 -> width - 1 - localU;
            default -> localV;
        };
    }

    /**
     * Whether a part of this size, turned this way, fits on the face with its corner there.
     *
     * <p>A part half off the edge of a face is not a part that wraps around the corner — the wire
     * layer's three connection cases are about <em>traces</em> crossing an edge, and a rigid
     * component does not bend. Refusing at placement is the only honest answer.
     */
    public static boolean fits(int originU, int originV, int width, int height, int rotation) {
        return originU >= 0 && originV >= 0
                && originU + spanU(width, height, rotation) <= FaceBasis.GRID
                && originV + spanV(width, height, rotation) <= FaceBasis.GRID;
    }

    /**
     * The corner a part should take so that it is centred on the pixel the player aimed at,
     * clamped so it stays on the face.
     *
     * <p>Clamped rather than refused: a face is sixteen pixels across and a five-pixel part
     * aimed near an edge is somebody trying to put it near that edge, not somebody making a
     * mistake. {@code WireAim} exists because pixel-precise aim is ten times finer than this game
     * asks for anywhere else.
     */
    public static int corner(int aimed, int span) {
        return Math.clamp(aimed - span / 2, 0, FaceBasis.GRID - span);
    }

    /**
     * The turn that points the part's output the way the player is looking.
     *
     * <p>The gate <em>blocks</em> this replaces did the same thing — <em>"faces away from the
     * player, so the output points where they were looking, which is the direction they are about
     * to run the wire"</em> — and it is worth keeping because it means the common case needs no
     * adjustment at all. The wrench turns it when the guess is wrong.
     *
     * <p>The part's output sits at the far end of its own {@code +u}, and a turn maps that to one
     * of the four in-plane directions. So the answer is whichever of those four the player's look
     * vector, flattened into the face, points most nearly along.
     */
    public static int rotationFor(Face face, double lookX, double lookY, double lookZ) {
        Face uAxis = FaceBasis.uAxis(face);
        Face vAxis = FaceBasis.vAxis(face);
        double alongU = lookX * uAxis.dx() + lookY * uAxis.dy() + lookZ * uAxis.dz();
        double alongV = lookX * vAxis.dx() + lookY * vAxis.dy() + lookZ * vAxis.dz();
        // The four turns move the output by (+u), (+v), (-u), (-v) in that order.
        double[] score = {alongU, alongV, -alongU, -alongV};
        int best = 0;
        for (int i = 1; i < ROTATIONS; i++) {
            if (score[i] > score[best]) {
                best = i;
            }
        }
        return best;
    }

    private static boolean odd(int rotation) {
        return Math.floorMod(rotation, ROTATIONS) % 2 == 1;
    }

    private PartFootprint() {}
}
