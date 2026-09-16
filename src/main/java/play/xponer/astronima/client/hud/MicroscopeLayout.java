package play.xponer.astronima.client.hud;

/**
 * Where everything is on the microscope: the field, and the fine focus you have to work.
 *
 * <p>Declared so a scan can read it. This screen used to position itself with literals in the
 * drawing code, so nothing could tell whether two things overlapped — the only way to find out was
 * to open the game, which is how <em>"icons overlap"</em> gets reported instead of caught. The
 * screen reads these constants rather than owning them, so the declaration cannot describe a
 * layout the drawing does not use (rule 27).
 */
public final class MicroscopeLayout {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 200;

    public static final int FIELD_X = 48;
    public static final int FIELD_Y = 12;
    public static final int FIELD = 80;

    public static final int KNOB_X = 152;
    public static final int KNOB_Y = 12;
    public static final int KNOB_H = 80;

    /**
     * The player's own slots, from the menu's real coordinates rather than guessed.
     *
     * <p>Declared because the bug being hunted is something landing <strong>on</strong> them.
     */
    public static final int INVENTORY_LABEL_Y = 106;
    public static final int INVENTORY_Y = 118;
    public static final int HOTBAR_Y = 176;

    public static Layout plan() {
        Layout layout = new Layout("microscope", WIDTH, HEIGHT);
        layout.reserve("slide slot", 8, 30, 26, 26);
        layout.reserve("field", FIELD_X - 2, FIELD_Y - 2, FIELD + 4, FIELD + 4);
        layout.reserve("fine focus", KNOB_X - 4, KNOB_Y - 2, 16, KNOB_H + 4);
        layout.reserve("record", FIELD_X - 2, FIELD_Y + FIELD + 2, FIELD + 4, 10);
        layout.reserve("inventory label", 8, INVENTORY_LABEL_Y, WIDTH - 16, 10);
        layout.reserve("inventory", 8, INVENTORY_Y, 9 * 18, 3 * 18);
        layout.reserve("hotbar", 8, HOTBAR_Y, 9 * 18, 18);
        return layout;
    }

    private MicroscopeLayout() {}
}
