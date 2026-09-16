package play.xponer.astronima.client.hud;

/**
 * Where everything is on the biomonitor: three columns and a body diagram between them.
 *
 * <p>Declared so a scan can read it. This screen used to position itself with literals in the
 * drawing code, so nothing could tell whether two things overlapped — the only way to find out was
 * to open the game, which is how <em>"icons overlap"</em> gets reported instead of caught. The
 * screen reads these constants rather than owning them, so the declaration cannot describe a
 * layout the drawing does not use (rule 27).
 */
public final class BiomonitorLayout {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;
    public static final int ROW_HEIGHT = 11;
    public static final int BODY_WIDTH = 60;

    public static final int SYSTEMS_X = 8;
    public static final int BODY_X = 118;
    public static final int CONDITIONS_X = 190;

    /** Where the two accumulating loads sit, along the bottom. */
    public static final int LOWER_Y = HEIGHT - 30;

    public static Layout plan() {
        Layout layout = new Layout("biomonitor", WIDTH, HEIGHT);
        layout.reserve("title", 8, 7, WIDTH - 16, 12);
        layout.reserve("systems", SYSTEMS_X, 24, BODY_X - SYSTEMS_X - 8, LOWER_Y - 26);
        layout.reserve("body", BODY_X, 26, BODY_WIDTH, LOWER_Y - 28);
        layout.reserve("conditions", CONDITIONS_X, 24, WIDTH - CONDITIONS_X - 8, LOWER_Y - 26);
        layout.reserve("contamination", SYSTEMS_X, LOWER_Y, 150, 28);
        layout.reserve("lasting damage", CONDITIONS_X, LOWER_Y, WIDTH - CONDITIONS_X - 8, 28);
        return layout;
    }

    private BiomonitorLayout() {}
}
