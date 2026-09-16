package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The shared look of every panel in the mod: a monochrome phosphor terminal.
 *
 * <p>Replaced a gradient-shaded, chamfered "modern flat UI" body — reported directly as
 * <em>"худ вообще не поменялся мне он пиздец как не нравится тупо ии слоп"</em> — with the thing
 * this mod's own fiction actually is: a screen a suit or a machine built decades ago would have,
 * not a phone app. The reference is the Nostromo's own console, not a dashboard:
 *
 * <ul>
 *   <li><strong>One phosphor colour, flat.</strong> No gradients, no soft alpha glow — a pixel is
 *       either lit or it is not. Colour still carries meaning ({@link HudScale}'s green/amber/red),
 *       but within one colour there are no shades, because a real monochrome tube has none.</li>
 *   <li><strong>Scanlines</strong> ({@link HudPaint#scanlines}) — every other row darkened, the
 *       one texture every CRT has that no flat panel does.</li>
 *   <li><strong>A square bezel.</strong> Chamfered corners read as a rounded app icon; a real
 *       terminal's case is a rectangle.</li>
 *   <li><strong>Segments that show even when unlit</strong> — a dim outline where a lit block
 *       would go, the way a real seven-segment or VFD bargraph never hides its own dead cells.</li>
 *   <li><strong>Threshold markers</strong>, so a bar shows not just where you are but how close
 *       you are to the value that matters.</li>
 * </ul>
 *
 * <p>All the arithmetic lives in {@link HudPaint} and is unit-tested; this class only
 * turns it into pixels.
 */
public final class Instruments {
    /** Room a line of text has here: a gauge label has the gauge to sit beside. */
    private static final int TEXT_WIDTH = 120;

    public static final int SEGMENT_WIDTH = 3;
    public static final int SEGMENT_GAP = 1;

    /** The tube's own glass: near-black with the faintest green cast, never neutral grey. */
    private static final int GLASS = 0xFF060C08;
    /** The case around the glass — dark plastic, flat, no gradient. */
    private static final int BEZEL = 0xFF17191A;
    private static final int BEZEL_EDGE_LIGHT = 0xFF34382F;
    private static final int BEZEL_EDGE_DARK = 0xFF0A0B0A;
    /** A segment slot with nothing lit in it: the dead cell every real bargraph still shows. */
    private static final int SEGMENT_UNLIT = 0xFF12291A;

    /**
     * Draws a square-bezelled screen: dark plastic case, black glass, scanlines.
     *
     * @param accent colour of the identity rule along the top edge of the glass
     */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int accent) {
        // The case: flat, with a one-pixel catch-light on the top-left and shadow on the
        // bottom-right — a real moulded edge, not a gradient fill.
        graphics.fill(x, y, x + width, y + height, BEZEL);
        graphics.fill(x, y, x + width - 1, y + 1, BEZEL_EDGE_LIGHT);
        graphics.fill(x, y, x + 1, y + height - 1, BEZEL_EDGE_LIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, BEZEL_EDGE_DARK);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, BEZEL_EDGE_DARK);

        // The glass, inset two pixels into the case.
        int gx = x + 2;
        int gy = y + 2;
        int gw = width - 4;
        int gh = height - 4;
        graphics.fill(gx, gy, gx + gw, gy + gh, GLASS);
        HudPaint.scanlines(graphics, gx, gy, gw, gh);

        // The identity rule: one flat line, no faded second line under it — a CRT trace does
        // not glow into the row below on a pixel screen, so pretending it does was the tell.
        graphics.fill(gx + 2, gy, gx + gw - 2, gy + 1, accent);
    }

    /** A segment slot's dead cell, drawn whether or not it ends up lit. */
    public static void track(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int pitch = SEGMENT_WIDTH + SEGMENT_GAP;
        int segments = Math.max(1, width / pitch);
        for (int i = 0; i < segments; i++) {
            int sx = x + i * pitch;
            graphics.fill(sx, y, sx + SEGMENT_WIDTH, y + height, SEGMENT_UNLIT);
        }
    }

    /**
     * A segmented bar, every cell drawn lit or dead, with an optional threshold marker.
     *
     * @param markerAt pixels from the left for a threshold tick, or negative for none
     */
    public static void gauge(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             float fraction, int colour, int markerAt) {
        track(graphics, x, y, width, height);

        int pitch = SEGMENT_WIDTH + SEGMENT_GAP;
        int segments = Math.max(1, width / pitch);
        int lit = HudPaint.litSegments(fraction, segments);

        // Flat fill, not a gradient — a lit phosphor segment is one brightness, not a shaded one.
        for (int i = 0; i < lit; i++) {
            int sx = x + i * pitch;
            graphics.fill(sx, y, sx + SEGMENT_WIDTH, y + height, colour);
        }

        if (markerAt >= 0 && markerAt <= width) {
            graphics.fill(x + markerAt, y - 1, x + markerAt + 1, y + height + 1, 0xB0FFFFFF);
        }
    }

    /** Label in the dim column colour, value in the bright one — a fixed hierarchy. */
    public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        MachineFrame.overlay(graphics, font, text, x, y, TEXT_WIDTH, HudScale.COLOR_LABEL);
    }

    public static void value(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                             int colour) {
        MachineFrame.overlay(graphics, font, text, x, y, TEXT_WIDTH, colour);
    }

    private Instruments() {}
}
