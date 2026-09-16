package play.xponer.astronima.client.hud;

/**
 * The electrolysis cell's own picture: a molten pool, an electrode dipped into it, and the
 * metal it plates out.
 *
 * <h2>What the machine actually does, drawn</h2>
 * There is no dial to draw — {@code design/electrolysis.md}'s whole point is that this cell
 * has none — so the picture carries the one thing worth reading at a glance: <em>which
 * electrode is in, and what it is worth</em>. The pool glows the same whatever is installed
 * (it is always molten regolith); the rod dipped into it is what changes, and that is what
 * this view actually varies from call to call.
 *
 * <p>Minecraft-free (rule 25): where the electrode sits and how many bubbles rise is
 * arithmetic with a right answer, not a texture choice.
 */
public final class ElectrolysisCellView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 70;

    /** The molten pool itself. */
    public static Layout.Box pool() {
        return new Layout.Box("pool", 6, 14, 76, 40);
    }

    /**
     * The electrode's own reserved strip, above the pool.
     *
     * <p>Only as tall as the part that never overlaps the pool's own reserved box — the rod
     * itself is drawn reaching down into the melt in {@code MachineBody}, past this
     * rectangle's bottom edge, the same way a knob is allowed to overhang its own control box
     * elsewhere in this panel kit. The layout scan checks reserved regions, not pixels.
     */
    public static Layout.Box electrode() {
        return new Layout.Box("electrode", 34, 0, 20, 14);
    }

    /** Where recovered metal is shown collecting, off to the side of the pool. */
    public static Layout.Box cathode() {
        return new Layout.Box("cathode", 92, 8, 62, 46);
    }

    /** No control: the target is set by which electrode is installed, not by anything drawn
     * here. Declared anyway, per the layout scan's own rule, and left empty. */
    public static Layout.Box control() {
        return new Layout.Box("control", 2, HEIGHT - 12, 156, 10);
    }

    public static Layout plan() {
        Layout layout = new Layout("electrolysis_cell", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {pool(), electrode(), cathode(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How many bubbles the pool shows rising this frame, 0..8.
     *
     * <p>Straight off the batch's own progress fraction, so a cell just started shows a calm
     * pool and one running hard shows it genuinely working — the same "no invisible
     * animation" rule the retort's beam and the refiner's column already keep.
     */
    public static int bubbleCount(double workFraction) {
        return (int) Math.round(Math.clamp(workFraction, 0, 1) * 8);
    }

    /** Where one bubble sits in the pool this tick, given a slow, deterministic drift upward. */
    public static double bubbleRise(int index, long ticks) {
        double phase = ((ticks * 0.6 + index * 37) % 100.0) / 100.0;
        return phase;
    }

    private ElectrolysisCellView() {}
}
