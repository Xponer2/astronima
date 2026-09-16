package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The P&amp;ID widget kit: process equipment drawn as a diagram (PLAN §0.5.1).
 *
 * <p>A machine with internal process state does not get a grid of slots — it gets the
 * picture an operator actually reads, which is the plant drawn in the order the fluid
 * moves through it, each item of equipment carrying its own state. That is what makes a
 * chain of blocks legible: you look at the line, and the break in it is where the problem
 * is.
 *
 * <p>Written after the airlock came back from play as *"nothing is comprehensible, it just
 * works blindly"*. The controller was binding to a chamber, a pump and a tank correctly and
 * showing none of it, so a wrong build could only be diagnosed by watching a cycle fail to
 * start. The fix is not more words: it is drawing the line so the gap is visible.
 *
 * <p>Deliberately in the machine-panel palette rather than the suit HUD's, for the reason
 * {@link MachineFrame} gives — a container screen belongs to the game's interface, not to
 * the fiction.
 */
public final class Schematic {
    /** Room a line of text has here: a schematic label. */
    private static final int TEXT_WIDTH = 120;

    /** Equipment that resolved and is doing its job. */
    public static final int LIVE = 0xFF2E7D4F;
    /** Equipment that resolved but is idle right now. */
    public static final int IDLE = 0xFF6E7476;
    /** The stage that is wrong — the one the player has to go and fix. */
    public static final int BROKEN = 0xFF9E3B32;
    /** Body of an equipment symbol. */
    private static final int SHELL = 0xFF8B9194;
    private static final int SHELL_DARK = 0xFF4E5254;

    /** How the diagram colours one stage. */
    public enum State { LIVE_STATE, IDLE_STATE, BROKEN_STATE;
        public int colour() {
            return switch (this) {
                case LIVE_STATE -> LIVE;
                case IDLE_STATE -> IDLE;
                case BROKEN_STATE -> BROKEN;
            };
        }
    }

    /**
     * A run of line between two items of equipment, with flow pips along it.
     *
     * <p>The pips march when {@code flowing}, which is the one piece of animation that
     * earns its place: a still diagram cannot distinguish "connected" from "moving", and
     * those are exactly the two states a player is trying to tell apart.
     */
    public static void line(GuiGraphicsExtractor graphics, int x, int y, int width,
                            int colour, boolean flowing, int animationTick) {
        graphics.fill(x, y + 1, x + width, y + 3, SHELL_DARK);
        graphics.fill(x, y + 1, x + width, y + 2, colour);
        if (!flowing) {
            return;
        }
        // Pips at a fixed spacing, offset by time: the line reads as carrying something.
        for (int i = (animationTick / 2) % 6; i < width - 1; i += 6) {
            graphics.fill(x + i, y, x + i + 2, y + 4, colour);
        }
    }

    /** A vessel: a tall rounded body with a fill level, for the chamber and the tank. */
    public static void vessel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              float fill, int colour) {
        graphics.fill(x, y, x + width, y + height, SHELL_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, SHELL);
        // Shoulders knocked off, so it reads as a pressure vessel and not a crate.
        graphics.fill(x, y, x + 2, y + 2, 0);
        graphics.fill(x + width - 2, y, x + width, y + 2, 0);
        graphics.fill(x, y + height - 2, x + 2, y + height, 0);
        graphics.fill(x + width - 2, y + height - 2, x + width, y + height, 0);

        int inner = height - 4;
        int filled = Math.round(inner * Math.clamp(fill, 0f, 1f));
        if (filled > 0) {
            graphics.fill(x + 2, y + height - 2 - filled, x + width - 2, y + height - 2, colour);
            graphics.fill(x + 2, y + height - 2 - filled, x + width - 2,
                    y + height - 1 - filled, HudPaint.shade(colour, 1.4f));
        }
    }

    /** A pump: a circle-ish body with a discharge nose, so it is not another box. */
    public static void pump(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
        graphics.fill(x + 1, y, x + size - 1, y + size, SHELL_DARK);
        graphics.fill(x, y + 1, x + size, y + size - 1, SHELL_DARK);
        graphics.fill(x + 2, y + 1, x + size - 2, y + size - 1, colour);
        graphics.fill(x + 1, y + 2, x + size - 1, y + size - 2, colour);
        // The nose: which way it pushes, the same thing the block itself says.
        graphics.fill(x + size - 1, y + size / 2 - 1, x + size + 3, y + size / 2 + 1, SHELL_DARK);
    }

    /** A door, drawn shut or open — the two states the interlock is all about. */
    public static void door(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            boolean shut, int colour) {
        graphics.fill(x, y, x + width, y + height, SHELL_DARK);
        if (shut) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, colour);
            graphics.fill(x + width / 2, y + 1, x + width / 2 + 1, y + height - 1, SHELL_DARK);
        } else {
            // Open: leaves pushed to the sides, a gap you can see through.
            graphics.fill(x + 1, y + 1, x + 3, y + height - 1, colour);
            graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, colour);
        }
    }

    /**
     * An empty terminal: a dashed outline where a device is meant to go.
     *
     * <p>Dashed rather than blank on purpose. A blank rectangle on a schematic reads as
     * something still loading, and a player will wait for it; a dashed one reads as a
     * socket, which is what it is — the panel is asking them for a device
     * (design/commissioning.md §C5).
     */
    public static void emptyTerminal(GuiGraphicsExtractor graphics, int x, int y,
                                     int width, int height) {
        for (int i = 0; i < width; i += 4) {
            int end = Math.min(i + 2, width);
            graphics.fill(x + i, y, x + end, y + 1, EMPTY);
            graphics.fill(x + i, y + height - 1, x + end, y + height, EMPTY);
        }
        for (int i = 0; i < height; i += 4) {
            int end = Math.min(i + 2, height);
            graphics.fill(x, y + i, x + 1, y + end, EMPTY);
            graphics.fill(x + width - 1, y + i, x + width, y + end, EMPTY);
        }
    }

    /** A terminal nobody has filled yet — deliberately not the same grey as "idle". */
    public static final int EMPTY = 0xFF9AA0A3;

    /** A caption under a symbol, centred on it. */
    public static void caption(GuiGraphicsExtractor graphics, Font font, String text,
                               int centreX, int y, int colour) {
        MachineFrame.overlay(graphics, font, text, centreX - font.width(text) / 2, y, TEXT_WIDTH, colour);
    }

    private Schematic() {}
}
