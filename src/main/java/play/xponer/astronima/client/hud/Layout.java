package play.xponer.astronima.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * A page's boxes, declared once, so nothing can be drawn on top of anything else.
 *
 * <h2>Why this exists (rule 27)</h2>
 * Every screen in this mod positioned itself with literals at the call site: a slot at
 * {@code (147, y)}, a title at {@code (26, 7)}, notes starting wherever the chart happened to end.
 * Nothing anywhere knew what those numbers added up to, so the only way to find out that two things
 * shared a rectangle was to look at it — and {@code ProcessingMenu} already carries a comment about
 * readouts having been drawn straight through the "Inventory" label, which is that discovery being
 * made the expensive way.
 *
 * <p>Worse, the arithmetic ran off the page with nothing to stop it. Output slots stepped twenty
 * pixels each with no bound on a 144-pixel page, so a recipe with more products than anybody had
 * tried put a slot below the bottom edge; and the notes underneath grew downward past it.
 *
 * <p>So a page <strong>reserves</strong> its boxes here and the drawing code asks where they are.
 * Two things cannot claim the same rectangle, because the reservation refuses — and a test walks
 * every page of every machine and proves it, which no amount of looking at a screenshot can.
 *
 * <p>Minecraft-free on purpose, like {@link PanelLayout}: a rectangle is arithmetic, and arithmetic
 * belongs where a unit test can reach it (rule 25 — a screen may draw, but it must not decide).
 */
public final class Layout {

    /** One reserved rectangle, in the page's own coordinates. */
    public record Box(String name, int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean overlaps(Box other) {
            return x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }
    }

    /** What went wrong with a page, in words a person can act on. */
    public record Fault(String what) { }

    private final String page;
    private final int width;
    private final int height;
    private final List<Box> boxes = new ArrayList<>();
    private final List<Fault> faults = new ArrayList<>();

    public Layout(String page, int width, int height) {
        this.page = page;
        this.width = width;
        this.height = height;
    }

    /**
     * Claims a rectangle, and records a fault rather than throwing if it cannot have it.
     *
     * <p>Recording rather than throwing, because a screen that crashes mid-frame is worse than one
     * that draws badly — and because the point is for a <em>test</em> to read the whole list at
     * once rather than for a player to meet the first problem.
     *
     * @return the box, always, so the caller can draw with it either way
     */
    public Box reserve(String name, int x, int y, int width, int height) {
        Box box = new Box(name, x, y, width, height);
        if (x < 0 || y < 0 || box.right() > this.width || box.bottom() > this.height) {
            faults.add(new Fault(page + ": " + name + " at " + x + "," + y + " " + width + "x"
                    + height + " runs off a " + this.width + "x" + this.height + " page"));
        }
        for (Box other : boxes) {
            if (box.overlaps(other)) {
                faults.add(new Fault(page + ": " + name + " sits on top of " + other.name()
                        + " - " + describe(box) + " overlaps " + describe(other)));
            }
        }
        boxes.add(box);
        return box;
    }

    /** Room left below everything claimed so far — what a growing block of text may use. */
    public int roomBelow(int y) {
        return Math.max(0, height - y);
    }

    /** Whether that many lines of this height fit under {@code y} without leaving the page. */
    public boolean fits(int y, int lines, int lineHeight) {
        return y + lines * lineHeight <= height;
    }

    public List<Box> boxes() {
        return List.copyOf(boxes);
    }

    public List<Fault> faults() {
        return List.copyOf(faults);
    }

    public String page() {
        return page;
    }

    private static String describe(Box box) {
        return "(" + box.x() + "," + box.y() + " " + box.width() + "x" + box.height() + ")";
    }
}
