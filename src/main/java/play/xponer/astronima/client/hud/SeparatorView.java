package play.xponer.astronima.client.hud;

/**
 * The separator's own picture: a drum, a feed falling onto it, and two streams leaving it.
 *
 * <h2>The trade is a shape, so draw the shape</h2>
 * Turn the field up and the drum catches more — and carries more rock round with it. Two numbers in
 * tension used to say that; a drum with a wide catch arc and a dirty concentrate stream says it
 * without being read.
 *
 * <p>Minecraft-free: how far round the drum a particle is carried is arithmetic with a right
 * answer, and rule 25 keeps that out of the paint code.
 */
public final class SeparatorView {

    public static final int WIDTH = 160;
    // Must clear drawCalibration's status lamp+text row (control().y() + 4, one text row
    // tall) with room to spare before the next row starts. See ForgeView's own note and
    // PanelLayoutTest.
    public static final int HEIGHT = 76;

    /** The feed, arriving from above. */
    public static Layout.Box feed() {
        return new Layout.Box("feed", 2, 0, 40, 16);
    }

    /** The drum itself. */
    public static Layout.Box drum() {
        return new Layout.Box("drum", 8, 16, 40, 40);
    }

    /** What the magnet carried round: the concentrate. */
    public static Layout.Box concentrate() {
        return new Layout.Box("concentrate", 52, 20, 50, 36);
    }

    /** What fell straight past: the tailings. */
    public static Layout.Box tailings() {
        return new Layout.Box("tailings", 106, 20, 52, 36);
    }

    /**
     * The control, under the drum whose field it sets.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 6, 60, 44, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("separator", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {feed(), drum(), concentrate(), tailings(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How far round the drum the field holds a particle before it lets go, in radians from the top.
     *
     * <p>A stronger field holds things further round — that is the whole mechanism, and it is why a
     * strong field catches rock as well: anything the magnet can hold at all gets carried past the
     * point where gravity would have dropped it.
     */
    public static double catchArc(double field) {
        return Math.clamp(field, 0, 1) * Math.PI * 0.9;
    }

    /**
     * How dirty the caught stream looks, 0..1.
     *
     * <p>Rises with the field, because the same pull that catches more mineral catches more of the
     * rock it is stuck to. The picture and the CLEAN gauge are two views of one fact, and they come
     * from the same number so they cannot disagree.
     */
    public static double contamination(double field, double grade) {
        return Math.clamp(1 - grade, 0, 1) * (0.4 + 0.6 * Math.clamp(field, 0, 1));
    }

    private SeparatorView() {}
}
