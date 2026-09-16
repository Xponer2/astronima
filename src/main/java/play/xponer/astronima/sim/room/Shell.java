package play.xponer.astronima.sim.room;

/**
 * What a room's boundary is made of and what it faces, counted in faces rather than blocks.
 *
 * <p>This is the geometry the thermal balance needs and nothing else in the mod has: how
 * much of the enclosure looks at vacuum, how much is pressed against rock, and how much of
 * it is insulated. A habitat loses heat through its surface, so a surface is what has to be
 * measured — and the unit is the <strong>face</strong>, because that is what has area. One
 * shell block between two rooms is two faces; one block in a corner may be three.
 *
 * <p><strong>Why looking outward one cell is the whole rule.</strong> A boundary face is
 * <em>buried</em> when the cell immediately past the shell is solid, and <em>exposed</em>
 * otherwise. That is cheap, it is computed during the walk the scanner already does, and it
 * gets the two cases the design turns on exactly right: a hull plate on the surface has
 * vacuum behind it, and a hull plate cut into the asteroid has rock behind it.
 *
 * <p>Its one inaccuracy is worth stating and not worth fixing: a <strong>two-block-thick
 * wall reads as buried</strong>, because the cell past the first plate is the second plate.
 * A double wall genuinely is better insulated than a single one, so the cheap rule and the
 * physics agree about the answer even though they disagree about the reason.
 */
public record Shell(int exposedFaces, int buriedFaces, int insulatedFaces, int paintedFaces) {

    public static final Shell NONE = new Shell(0, 0, 0, 0);

    /** Every boundary face, however it is made up. */
    public int totalFaces() {
        return exposedFaces + buriedFaces;
    }

    /**
     * The share of the enclosure that can radiate to the sky, 0..1.
     *
     * <p>Feeds the fourth-power term. A fully buried room has none of it, which is why
     * digging in is the first answer to cold.
     */
    public double skyFraction() {
        return fraction(exposedFaces);
    }

    /** The share pressed against rock, 0..1 — the linear, much smaller loss. */
    public double buriedFraction() {
        return fraction(buriedFaces);
    }

    /** The share built out of insulated plate, 0..1. */
    public double insulatedFraction() {
        return fraction(insulatedFaces);
    }

    /**
     * The share built out of albedo-painted plate, 0..1.
     *
     * <p>Independent of {@link #insulatedFraction()} the same way the two are independent
     * properties of a wall in the first place (design/albedo-paint.md §3) — a face can be
     * painted, insulated, both, or neither, and this counts only the first.
     */
    public double paintedFraction() {
        return fraction(paintedFaces);
    }

    private double fraction(int faces) {
        int total = totalFaces();
        return total <= 0 ? 0 : (double) faces / total;
    }

    /** A shell with one more face of the given kind, for accumulating during a scan. */
    public Shell plusFace(boolean buried, boolean insulated, boolean painted) {
        return new Shell(exposedFaces + (buried ? 0 : 1), buriedFaces + (buried ? 1 : 0),
                insulatedFaces + (insulated ? 1 : 0), paintedFaces + (painted ? 1 : 0));
    }
}
