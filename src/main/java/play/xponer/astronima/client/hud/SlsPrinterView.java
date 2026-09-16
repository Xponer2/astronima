package play.xponer.astronima.client.hud;

import play.xponer.astronima.sim.metal.LaserSintering;

/**
 * The printer's own picture: the process plane itself, laser power across one axis and scan
 * speed across the other — the first machine picture in the mod that <em>is</em> the control
 * rather than something drawn beside one ({@code design/sls.md} §2).
 *
 * <h2>Why a heatmap and not a bar</h2>
 * Every other machine's picture explains a line: a window on a scale, a band a drum has to sit
 * in. This one cannot, because the good result here is a <em>pocket in a plane</em> — the same
 * energy density reached fast or slow prints a different part, so collapsing either axis to
 * summarise the other would draw exactly the lie rule 8 exists to forbid. The grid samples
 * {@link LaserSintering#regime} at every cell it draws, so the picture cannot drift from the
 * physics it is reporting.
 *
 * <p>Minecraft-free (rule 25): which cell of the grid a screen pixel falls into, and which real
 * (power, speed) pair a cell's centre stands for, are both arithmetic with one right answer.
 */
public final class SlsPrinterView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 100;

    /** Cells across the plane, one axis each. Coarse enough to be cheap, fine enough to read. */
    public static final int COLS = 32;
    public static final int ROWS = 24;

    /** The plane itself: power left-to-right, speed top-to-bottom. */
    public static Layout.Box plane() {
        return new Layout.Box("plane", 6, 4, 96, 72);
    }

    /** Regime label, soundness and energy density, beside the plane. */
    public static Layout.Box readout() {
        return new Layout.Box("readout", 108, 4, 46, 72);
    }

    /**
     * No separate control: the plane above <em>is</em> the control, dragged directly rather than
     * read off a bar underneath it. Declared anyway, per the layout scan's own rule, and left
     * empty — the same convention {@code ElectrolysisCellView#control()} already uses for a
     * machine whose real control lives somewhere other than this strip.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 2, HEIGHT - 12, 156, 10);
    }

    public static Layout plan() {
        Layout layout = new Layout("sls_printer", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {plane(), readout(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /** The real power a cell's centre, column {@code col} of {@link #COLS}, stands for. */
    public static double powerAt(int col) {
        double fraction = (col + 0.5) / COLS;
        return LaserSintering.MIN_POWER_W
                + fraction * (LaserSintering.MAX_POWER_W - LaserSintering.MIN_POWER_W);
    }

    /** The real scan speed a cell's centre, row {@code row} of {@link #ROWS}, stands for. */
    public static double speedAt(int row) {
        double fraction = (row + 0.5) / ROWS;
        return LaserSintering.MIN_SPEED_MMS
                + fraction * (LaserSintering.MAX_SPEED_MMS - LaserSintering.MIN_SPEED_MMS);
    }

    /** Which column of the grid a real power value falls into. */
    public static int colFor(double powerW) {
        double fraction = (powerW - LaserSintering.MIN_POWER_W)
                / (LaserSintering.MAX_POWER_W - LaserSintering.MIN_POWER_W);
        return (int) Math.clamp(Math.floor(fraction * COLS), 0, COLS - 1);
    }

    /** Which row of the grid a real scan speed falls into. */
    public static int rowFor(double speedMmS) {
        double fraction = (speedMmS - LaserSintering.MIN_SPEED_MMS)
                / (LaserSintering.MAX_SPEED_MMS - LaserSintering.MIN_SPEED_MMS);
        return (int) Math.clamp(Math.floor(fraction * ROWS), 0, ROWS - 1);
    }

    private SlsPrinterView() {}
}
