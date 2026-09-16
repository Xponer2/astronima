package play.xponer.astronima.client.hud;

/**
 * Where everything is on the incubator: a door you look through, and the thermostat under it.
 *
 * <p>Declared so a scan can read it. This screen used to position itself with literals in the
 * drawing code, so nothing could tell whether two things overlapped — the only way to find out was
 * to open the game, which is how <em>"icons overlap"</em> gets reported instead of caught. The
 * screen reads these constants rather than owning them, so the declaration cannot describe a
 * layout the drawing does not use (rule 27).
 */
public final class IncubatorLayout {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 188;

    public static final int WINDOW_X = 92;
    public static final int WINDOW_Y = 20;
    public static final int WINDOW = 68;

    /**
     * The thermostat, in the column beside the door rather than across the whole panel.
     *
     * <p>It used to be 160 wide at y 76 — which ran it straight <em>through</em> the door window,
     * because the window is 68 pixels tall starting at 20 and ends at 88. Nobody could see that
     * from the code, and the first thing that noticed was the layout scan.
     */
    public static final int DIAL_X = 8;
    public static final int DIAL_Y = 70;
    public static final int DIAL_W = 80;

    /** Where the dish goes in. */
    public static final int SLOT_X = 44;
    public static final int SLOT_Y = 38;

    /** The rule under the readouts, above the player's inventory. */
    public static final int DIVIDER_Y = 92;

    /**
     * The player's own slots.
     *
     * <p>Declared because the bug being hunted is a control landing <strong>on</strong> them, and
     * a layout that stops at the machine's own furniture is perfectly happy with that. Taken from
     * the menu's real slot coordinates rather than guessed.
     */
    public static final int INVENTORY_LABEL_Y = 94;
    public static final int INVENTORY_Y = 106;
    public static final int HOTBAR_Y = 164;

    public static Layout plan() {
        Layout layout = new Layout("incubator", WIDTH, HEIGHT);
        layout.reserve("dish slot", SLOT_X - 8, SLOT_Y - 8, 34, 32);
        layout.reserve("door window", WINDOW_X - 1, WINDOW_Y - 1, WINDOW + 2, WINDOW + 2);
        // The knob overhangs the bar by three pixels top and bottom; the box owns that, or a
        // handle lands on whatever is above it with the scan perfectly happy.
        layout.reserve("thermostat", DIAL_X, DIAL_Y - 4, DIAL_W, 14);
        layout.reserve("readout", DIAL_X, DIAL_Y + 10, DIAL_W, 10);
        layout.reserve("inventory label", 8, INVENTORY_LABEL_Y, WIDTH - 16, 10);
        layout.reserve("inventory", 8, INVENTORY_Y, 9 * 18, 3 * 18);
        layout.reserve("hotbar", 8, HOTBAR_Y, 9 * 18, 18);
        return layout;
    }

    private IncubatorLayout() {}
}
