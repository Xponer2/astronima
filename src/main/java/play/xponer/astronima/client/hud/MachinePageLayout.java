package play.xponer.astronima.client.hud;

/**
 * Where everything sits on a machine's documentation page.
 *
 * <h2>Rule 27, and the two faults that prompted it</h2>
 * The page used to position itself with literals at each call site — a slot at {@code (147, y)}, a
 * title at {@code (26, 7)}, notes starting wherever the chart happened to end. Nothing anywhere
 * knew what those numbers added up to, so two things were wrong in plain sight and no test could
 * see either:
 *
 * <ul>
 *   <li>the product column stepped twenty pixels each with <strong>no bound on a 144-pixel
 *       page</strong>, so a machine's fourth product lands below the bottom edge;</li>
 *   <li>the title was drawn at full length from x=26 <strong>straight into the product column</strong>
 *       at x=147.</li>
 * </ul>
 *
 * <p>Deliberately free of JEI, and that is not tidiness: JEI's API is {@code compileOnly}, so
 * anything that touches it is invisible to every unit test in the build. Geometry that cannot be
 * tested is geometry that gets checked by looking at it, which is how both faults survived.
 */
public final class MachinePageLayout {

    public static final int WIDTH = 168;
    public static final int HEIGHT = 144;

    public static final int SLOT = 18;
    public static final int NOTE_LINE = 9;

    public static final int CHART_X = 26;
    public static final int CHART_Y = 24;
    public static final int CHART_W = 114;
    public static final int CHART_H = 46;

    public static final int TITLE_X = 26;
    public static final int TITLE_Y = 7;

    private static final int OUT_X = 147;
    private static final int OUT_STEP = 20;
    private static final int OUT_TOP = 4;

    /**
     * How wide a title may be before it reaches the products.
     *
     * <p>The fourth fault the guard found, and it only appears once the product column wraps: with
     * eight products the second column starts at x=127 and the title ran to x=144. Same rule as the
     * notes — stop at the leftmost thing that shares your rows — which is the rule the whole page
     * should have had from the start instead of four literals that happened to agree.
     */
    public static int titleWidth(int outputs) {
        return Math.min(OUT_X, leftmostBeside(outputs, TITLE_Y, TITLE_Y + 9)) - TITLE_X - 3;
    }

    /** The nearest product column that shares any of these rows, or the page's right edge. */
    private static int leftmostBeside(int outputs, int top, int bottom) {
        int leftmost = WIDTH;
        for (int i = 0; i < outputs; i++) {
            if (outputY(i) < bottom && top < outputY(i) + SLOT) {
                leftmost = Math.min(leftmost, outputX(i));
            }
        }
        return leftmost;
    }

    /** Where the notes begin, under the chart. */
    public static int noteTop() {
        return CHART_Y + CHART_H + 16;
    }

    /**
     * How wide the notes may run before they reach the products.
     *
     * <p>The third fault the guard found on its first run, and the one nobody would have looked
     * for: the notes were the full width of the page, so from the fifth product down they ran
     * underneath the product column. Two products is what the machines have today, which is exactly
     * why it had never been seen.
     *
     * <p>Bounded by the leftmost column that actually has something beside the notes, so a page
     * with two products keeps its full width and a page with eight gives up what it must.
     */
    public static int noteWidth(int outputs) {
        return Math.min(WIDTH - 8, leftmostBeside(outputs, noteTop(), HEIGHT) - 4 - 3);
    }

    /**
     * Where the nth product sits.
     *
     * <p>Wrapped into a second column once the first would leave the page, rather than marching off
     * the bottom. Two columns is the honest answer at this size: three products is a real machine —
     * the fluidized bed — and a fourth is one recipe away.
     */
    public static int outputY(int index) {
        return OUT_TOP + (index % perColumn()) * OUT_STEP;
    }

    public static int outputX(int index) {
        return OUT_X - (index / perColumn()) * OUT_STEP;
    }

    private static int perColumn() {
        return Math.max(1, (HEIGHT - OUT_TOP) / OUT_STEP);
    }

    /** Every box on one page, as data, so a test can walk it. */
    public static Layout plan(String title, int outputs) {
        Layout layout = new Layout(title, WIDTH, HEIGHT);
        layout.reserve("input slot", 3, 4, SLOT, SLOT);
        layout.reserve("title", TITLE_X, TITLE_Y, titleWidth(outputs), 9);
        layout.reserve("chart", CHART_X, CHART_Y, CHART_W, CHART_H);
        for (int i = 0; i < outputs; i++) {
            layout.reserve("output " + (i + 1), outputX(i), outputY(i), SLOT, SLOT);
        }
        layout.reserve("notes", 4, noteTop(), noteWidth(outputs), layout.roomBelow(noteTop()));
        return layout;
    }

    private MachinePageLayout() {}
}
