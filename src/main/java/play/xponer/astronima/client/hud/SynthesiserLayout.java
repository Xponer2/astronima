package play.xponer.astronima.client.hud;

/**
 * Where everything is on the synthesiser: a list of drugs, one row each.
 *
 * <p>Declared so a scan can read it. This screen used to position itself with literals in the
 * drawing code, so nothing could tell whether two things overlapped — the only way to find out was
 * to open the game, which is how <em>"icons overlap"</em> gets reported instead of caught. The
 * screen reads these constants rather than owning them, so the declaration cannot describe a
 * layout the drawing does not use (rule 27).
 */
public final class SynthesiserLayout {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 190;

    public static final int ROW_X = 20;
    public static final int ROW_Y = 54;
    public static final int ROW_W = 136;
    public static final int ROW_H = 13;

    /**
     * How many rows the panel has room for.
     *
     * <p>Declared, so a fourth antibiotic cannot quietly draw its row through whatever is under
     * the list. The laboratory has three; the day it has four, this fails rather than overlaps.
     */
    public static final int ROWS = 3;

    /**
     * The player's own slots.
     *
     * <p>Declared because the bug being hunted is a control landing <strong>on</strong> them, and
     * a layout that stops at the machine's own furniture is perfectly happy with that. Taken from
     * the menu's real slot coordinates rather than guessed.
     */
    public static final int INVENTORY_LABEL_Y = 96;
    public static final int INVENTORY_Y = 108;
    public static final int HOTBAR_Y = 166;

    public static Layout plan() {
        Layout layout = new Layout("synthesiser", WIDTH, HEIGHT);
        layout.reserve("vial slot", 8, 20, 26, 26);
        layout.reserve("what it holds", 40, 22, WIDTH - 48, 22);
        for (int row = 0; row < ROWS; row++) {
            layout.reserve("drug " + (row + 1), ROW_X, ROW_Y + row * ROW_H, ROW_W, ROW_H - 1);
        }
        layout.reserve("inventory label", 8, INVENTORY_LABEL_Y, WIDTH - 16, 10);
        layout.reserve("inventory", 8, INVENTORY_Y, 9 * 18, 3 * 18);
        layout.reserve("hotbar", 8, HOTBAR_Y, 9 * 18, 18);
        return layout;
    }

    private SynthesiserLayout() {}
}
