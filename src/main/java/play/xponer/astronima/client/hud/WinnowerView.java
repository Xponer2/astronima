package play.xponer.astronima.client.hud;

/**
 * The winnowing table's own picture: a column of gas with the feed falling through it.
 *
 * <h2>What the machine actually does, drawn</h2>
 * Gas goes up, grains fall down, and whether a grain reaches the bottom depends on whether the
 * stream can lift it. Dense metal falls through; light rock is carried over the lip. That is
 * <strong>elutriation</strong>, and it is a picture of two populations crossing in mid-air rather
 * than a percentage.
 *
 * <p>It also makes the pressure dependency visible in a way a number never did. In a thin room
 * there is barely a stream at all, so nothing separates — and the picture shows a column with
 * almost nothing in it instead of a gauge reading low.
 *
 * <p>Minecraft-free (rule 25): where a grain ends up is arithmetic with a right answer.
 */
public final class WinnowerView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 72;

    /** The feed, tipped in at the top. */
    public static Layout.Box feed() {
        return new Layout.Box("feed", 2, 0, 44, 12);
    }

    /** The column the gas rises through. */
    public static Layout.Box column() {
        return new Layout.Box("column", 8, 12, 48, 44);
    }

    /** Carried over the lip: the light fraction. */
    public static Layout.Box light() {
        return new Layout.Box("light", 60, 12, 46, 44);
    }

    /** Fell through: the dense fraction, which is the one you want. */
    public static Layout.Box dense() {
        return new Layout.Box("dense", 110, 12, 48, 44);
    }

    /**
     * Where the sizing bar goes.
     *
     * <p>The winnowing table has no control — the bar reports what the grind decided, and a handle
     * a player cannot move is worse than none. It still needs a declared home: it used to be
     * placed just past the bottom of the picture by a literal that no layout knew about, so it was
     * drawn straight across the advice line and the scan had nothing to check.
     */
    public static Layout.Box control() {
        return new Layout.Box("sizing bar", 2, 58, 156, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("winnower", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {feed(), column(), light(), dense(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How strong the stream looks, 0..1.
     *
     * <p>Pressure, not the dial: the dial sizes the cut and the room supplies the gas. Below the
     * pressure a stream needs there is nothing to separate with, and the column should look empty
     * rather than merely reading low — <em>"bring it inside"</em> is a different instruction from
     * <em>"turn the dial"</em>.
     */
    public static double streamStrength(double pressureKPa, double minimumKPa) {
        if (pressureKPa <= 0) {
            return 0;
        }
        return Math.clamp(pressureKPa / Math.max(1, minimumKPa * 4), 0, 1);
    }

    /**
     * How far apart the two populations end up, 0..1.
     *
     * <p>Straight from the model's own sharpness, so the picture cannot claim a cleaner split than
     * the machine achieved. At zero the two clouds sit on top of each other, which is exactly what
     * a bad cut looks like on a real table.
     */
    public static double spread(double sharpness) {
        return Math.clamp(sharpness, 0, 1);
    }

    /**
     * Where one grain of a given lift lands across the picture, 0 at the column and 1 over the lip.
     *
     * <p>A grain the stream cannot lift stays at zero however sharp the cut is; a grain it lifts
     * easily goes further the sharper the separation. That difference <em>is</em> the separation.
     */
    public static double landing(double lift, double sharpness) {
        double carried = Math.clamp(lift, 0, 1);
        return carried * (0.35 + 0.65 * Math.clamp(sharpness, 0, 1));
    }

    private WinnowerView() {}
}
