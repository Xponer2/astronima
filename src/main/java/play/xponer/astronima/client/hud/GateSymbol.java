package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import play.xponer.astronima.sim.logic.Gate;

/**
 * The gates, drawn as the shapes an engineer already knows.
 *
 * <p><strong>A grey box with the letters "NAND" in it is a label, not a symbol.</strong> The
 * distinction matters here more than it usually does: a plate holds up to eight of these and the
 * whole point of a schematic is that you read the <em>shape</em> of a circuit at a glance rather
 * than spelling out every part. Nobody reads a real schematic word by word, and the reason is that
 * the flat-backed D, the pointed shield and the little bubble are faster than any font.
 *
 * <p>So: the ANSI/IEEE distinctive shapes. AND is a D — flat back, round nose. OR is a shield —
 * concave back, pointed nose. XOR is an OR with a second arc behind it. NOT is a triangle. And
 * <strong>the bubble is the inversion</strong>, in exactly the place it is on paper, which is what
 * makes NAND read as "AND, but not" instead of as a different four-letter word.
 *
 * <p>Drawn per row of pixels from the curve's own arithmetic rather than from a sprite, so the
 * symbols stay sharp at whatever size the panel gives them and there is no texture to keep in step
 * with the palette.
 */
public final class GateSymbol {

    /** The chip body: dark, so the pale silkscreen outline reads against it. */
    private static final int BODY = 0xFF1B2A25;
    private static final int BODY_LIT = 0xFF24382F;

    /** Silkscreen: the printed outline on a real board is off-white, never pure white. */
    private static final int EDGE = 0xFFD8E2DC;
    private static final int EDGE_DIM = 0xFF7E8F87;

    /**
     * Draws one gate's symbol filling the given box.
     *
     * @param highlighted the pointer is over it, or it is otherwise the one being talked about
     */
    public static void draw(GuiGraphicsExtractor graphics, Gate gate, int x, int y, int width,
                            int height, boolean highlighted) {
        int body = highlighted ? BODY_LIT : BODY;
        int edge = highlighted ? EDGE : EDGE_DIM;
        // The bubble eats into the right-hand end, so the body stops short of it.
        int bubble = inverting(gate) ? BUBBLE : 0;
        int span = width - bubble;

        for (int row = 0; row < height; row++) {
            int[] extent = rowOf(gate, row, height, span);
            if (extent == null) {
                continue;
            }
            int left = x + extent[0];
            int right = x + extent[1];
            if (right <= left) {
                continue;
            }
            graphics.fill(left, y + row, right, y + row + 1, body);
            // One pixel of outline at each end of the row, which is what turns a stack of bars
            // into a drawn shape.
            graphics.fill(left, y + row, left + 1, y + row + 1, edge);
            graphics.fill(right - 1, y + row, right, y + row + 1, edge);
        }
        // Cap the flat back and the flat top/bottom of the D, which rows alone cannot outline.
        capEnds(graphics, gate, x, y, height, span, edge);

        if (bubble > 0) {
            circle(graphics, x + span, y + height / 2 - bubble / 2, bubble, body, edge);
        }
    }

    /** How wide the inversion bubble is. Three pixels reads at panel scale; two does not. */
    private static final int BUBBLE = 5;

    /**
     * The horizontal extent of one row of the symbol.
     *
     * <p>{@code t} runs 0 at the middle of the shape to 1 at its top and bottom edge, which is the
     * parameter every one of these curves is expressed in.
     */
    private static int[] rowOf(Gate gate, int row, int height, int span) {
        double half = (height - 1) / 2.0;
        double t = Math.min(1.0, Math.abs(row - half) / Math.max(1.0, half));

        return switch (gate) {
            // A flat back and a semicircular nose: the D.
            case AND, NAND -> {
                int straight = span * 55 / 100;
                int nose = span - straight;
                yield new int[] {0, straight + (int) Math.round(nose * Math.sqrt(1 - t * t))};
            }
            // A concave back and a pointed nose: the shield.
            case OR, NOR -> {
                int dish = span * 22 / 100;
                int left = (int) Math.round(dish * (1 - t * t));
                int straight = span * 45 / 100;
                int nose = span - straight;
                yield new int[] {left, straight + (int) Math.round(nose * Math.sqrt(1 - t * t))};
            }
            // The same shield, moved right to leave room for its second back arc.
            case XOR -> {
                int dish = span * 22 / 100;
                int offset = 4;
                int left = offset + (int) Math.round(dish * (1 - t * t));
                int straight = span * 45 / 100;
                int nose = span - straight;
                yield new int[] {left, straight + (int) Math.round(nose * Math.sqrt(1 - t * t))};
            }
            // A triangle, point to the right.
            case NOT -> new int[] {0, (int) Math.round(span * (1 - t))};
        };
    }

    /** The straight edges the row loop cannot draw: the flat back, and XOR's second arc. */
    private static void capEnds(GuiGraphicsExtractor graphics, Gate gate, int x, int y, int height,
                                int span, int edge) {
        switch (gate) {
            case AND, NAND, NOT -> graphics.fill(x, y, x + 1, y + height, edge);
            case OR, NOR -> { }
            case XOR -> {
                // The extra back arc, three pixels clear of the body — the whole of what makes an
                // XOR an XOR on paper.
                double half = (height - 1) / 2.0;
                for (int row = 0; row < height; row++) {
                    double t = Math.min(1.0, Math.abs(row - half) / Math.max(1.0, half));
                    int at = x + (int) Math.round(span * 22 / 100.0 * (1 - t * t));
                    graphics.fill(at, y + row, at + 1, y + row + 1, edge);
                }
            }
        }
        if (gate == Gate.AND || gate == Gate.NAND) {
            // The D's flat top and bottom, which the nose curve leaves open.
            int straight = span * 55 / 100;
            graphics.fill(x, y, x + straight, y + 1, edge);
            graphics.fill(x, y + height - 1, x + straight, y + height, edge);
        }
    }

    /** A small outlined disc — the inversion bubble, and nothing else in this kit needs one. */
    private static void circle(GuiGraphicsExtractor graphics, int x, int y, int size, int fill,
                               int edge) {
        double radius = size / 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - radius;
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            if (half <= 0) {
                continue;
            }
            int left = x + (int) Math.round(radius) - half;
            int right = x + (int) Math.round(radius) + half;
            graphics.fill(left, y + row, right, y + row + 1, fill);
            graphics.fill(left, y + row, left + 1, y + row + 1, edge);
            graphics.fill(right - 1, y + row, right, y + row + 1, edge);
        }
    }

    private static boolean inverting(Gate gate) {
        return gate == Gate.NAND || gate == Gate.NOR || gate == Gate.NOT;
    }

    private GateSymbol() {}
}
