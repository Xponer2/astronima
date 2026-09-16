package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Container-screen chrome — a monochrome phosphor terminal, the same language
 * {@link Instruments} draws the suit HUD in.
 *
 * <p>This used to deliberately match vanilla's own light steel bevel, on the reasoning that a
 * container screen sits directly above the player's beige inventory and should speak the game's
 * own grammar rather than the fiction's. Reported back directly, of both this and the HUD kit at
 * once: <em>"худ вообще не поменялся мне он пиздец как не нравится тупо ии слоп"</em> — flat,
 * gradient-shaded, forgettable. The fix is the same one {@link Instruments} took: this mod's own
 * machines are not modern appliances, they are hard-wired industrial gear old enough to still use
 * a monochrome tube for a readout, and every screen in the mod should look like it, machine panel
 * and visor alike, rather than one looking like vanilla and the other like a HUD.
 *
 * <p>Square bezel, black glass, one phosphor colour lit or dim, scanlines
 * ({@link HudPaint#scanlines}) — no gradients, because a real tube does not have any.
 */
public final class MachineFrame {
    /** The screen's own glass: near-black, the same tint {@link Instruments} uses. */
    private static final int BODY = 0xFF060C08;
    private static final int BEVEL_LIGHT = 0xFF34382F;
    private static final int BEVEL_DARK = 0xFF0A0B0A;
    private static final int BORDER = 0xFF17191A;

    /** Inset wells: a dead segment's own colour, the same one {@link Instruments}'s unlit
     *  bargraph cells use, so a slot and a gauge read as the same material. */
    private static final int WELL = 0xFF12291A;
    private static final int WELL_SHADOW = 0xFF0A1811;
    private static final int WELL_LIGHT = 0xFF2F7A44;

    /** Product slots get a lit floor, feed slots keep the dead one. */
    private static final int PRODUCT_FLOOR = 0xFF1F4A2C;
    /** The role watermark: visible on an empty slot, gone under an item. */
    private static final int ARROW = 0xFF2F7A44;

    /** Phosphor green, the same value {@link HudScale#COLOR_VALUE}/{@code COLOR_LABEL} use. */
    public static final int TEXT = 0xFF3EFF6E;
    public static final int TEXT_DIM = 0xFF2F7A44;

    /** The same green/amber/red alarm colours {@link HudScale} reads a vital sign against. */
    public static final int GOOD = 0xFF3EFF6E;
    public static final int WARN = 0xFFFFB000;
    public static final int BAD = 0xFFFF4433;
    /** Cyan-green: the second trace colour, for a control rather than a reading. */
    public static final int ACCENT = 0xFF3EF0D8;

    /**
     * The panel: a square plastic bezel around black glass, scanlined.
     *
     * <p>No chamfer, no gradient body — see the class doc for why a rounded, shaded panel was
     * exactly the look this replaced.
     */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BORDER);
        graphics.fill(x, y, x + width - 1, y + 1, BEVEL_LIGHT);
        graphics.fill(x, y, x + 1, y + height - 1, BEVEL_LIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, BEVEL_DARK);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, BEVEL_DARK);

        int gx = x + 2;
        int gy = y + 2;
        int gw = width - 4;
        int gh = height - 4;
        graphics.fill(gx, gy, gx + gw, gy + gh, BODY);
        HudPaint.scanlines(graphics, gx, gy, gw, gh);
    }

    /** An inset well, for a slot or a gauge track: a dim outline around the dead cell. */
    public static void well(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, WELL_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, WELL_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, WELL);
    }

    /** The 18×18 well vanilla draws behind an inventory slot. */
    public static void slot(GuiGraphicsExtractor graphics, int slotX, int slotY) {
        well(graphics, slotX - 1, slotY - 1, 18, 18);
    }

    /**
     * A feed slot: the ordinary inset well with a faint down-arrow watermark — things go
     * <em>into</em> this one. The watermark sits behind whatever item is in the slot, so
     * it reads on an empty slot and disappears under a full one, exactly when it is and
     * is not needed.
     */
    public static void feedSlot(GuiGraphicsExtractor graphics, int slotX, int slotY) {
        slot(graphics, slotX, slotY);
        arrow(graphics, slotX, slotY, true);
    }

    /**
     * A product slot: a visibly brighter well with an up-arrow — things come <em>out</em>.
     * The lighter floor is the second channel, readable at a glance before the arrow is.
     */
    public static void productSlot(GuiGraphicsExtractor graphics, int slotX, int slotY) {
        well(graphics, slotX - 1, slotY - 1, 18, 18);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, PRODUCT_FLOOR);
        arrow(graphics, slotX, slotY, false);
    }

    /** The watermark: a stem and a stepped head, dim enough to sit under an item. */
    private static void arrow(GuiGraphicsExtractor graphics, int slotX, int slotY, boolean down) {
        int cx = slotX + 8;
        int cy = slotY + 8;
        if (down) {
            graphics.fill(cx - 1, cy - 4, cx + 1, cy + 1, ARROW); // stem
            graphics.fill(cx - 3, cy + 1, cx + 3, cy + 2, ARROW); // head, widest row
            graphics.fill(cx - 2, cy + 2, cx + 2, cy + 3, ARROW);
            graphics.fill(cx - 1, cy + 3, cx + 1, cy + 4, ARROW); // tip
        } else {
            graphics.fill(cx - 1, cy - 4, cx + 1, cy - 3, ARROW); // tip
            graphics.fill(cx - 2, cy - 3, cx + 2, cy - 2, ARROW);
            graphics.fill(cx - 3, cy - 2, cx + 3, cy - 1, ARROW); // head, widest row
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 4, ARROW); // stem
        }
    }

    /**
     * A filled bar in a well.
     *
     * <p>Solid rather than gradient-shaded. Vanilla's progress arrows and flame icons
     * are flat, and a gradient here is one of the tells that a panel was not drawn to
     * match the game.
     */
    public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                           float fraction, int colour) {
        well(graphics, x, y, width, height);
        int filled = Math.round((width - 2) * Math.clamp(fraction, 0f, 1f));
        if (filled > 0) {
            // Flat: a lit phosphor segment is one brightness, not a shaded one.
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, colour);
        }
    }

    /**
     * A bar with a target band painted into its track, and a needle at the reading.
     *
     * <p>For a machine whose right answer is a <em>window</em> rather than a direction. A
     * plain fill bar can only say "more" or "less", which is exactly the wrong grammar for
     * a control that is wrong at both ends — the player would have no way to know they had
     * gone past the answer except by ruining a batch. Painting the band into the track makes
     * the target a place on the instrument, so it can be aimed at rather than remembered.
     *
     * @param from    start of the good band, 0..1 of the track
     * @param to      end of it
     * @param danger  where the band stops being merely wasteful and starts destroying
     * @param reading where the needle sits, 0..1
     */
    public static void window(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              float from, float to, float danger, float reading, int colour) {
        well(graphics, x, y, width, height);
        int track = width - 2;

        // The band first, so the fill and the needle sit on top of it: a translucent phosphor
        // wash over the dead glass, not an overbright fill — the reading and the needle still
        // have to read clearly on top of it.
        int bandFrom = x + 1 + Math.round(track * Math.clamp(from, 0f, 1f));
        int bandTo = x + 1 + Math.round(track * Math.clamp(to, 0f, 1f));
        graphics.fill(bandFrom, y + 1, Math.max(bandTo, bandFrom + 1), y + height - 1,
                HudPaint.withAlpha(GOOD, 0x50));

        // Past the danger mark the track itself reads as hot, so overshooting looks wrong
        // before anything is lost rather than after.
        int dangerAt = x + 1 + Math.round(track * Math.clamp(danger, 0f, 1f));
        graphics.fill(dangerAt, y + 1, x + 1 + track, y + height - 1, HudPaint.withAlpha(BAD, 0x50));

        int filled = Math.round(track * Math.clamp(reading, 0f, 1f));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, colour);
        }
        // The needle: one bright white column at the reading, so an exact value is legible
        // whatever colour the fill and the band underneath it happen to be.
        int needle = Math.clamp(x + 1 + filled, x + 1, x + width - 2);
        graphics.fill(needle, y, needle + 1, y + height, 0xFFFFFFFF);
    }

    /** A raised control handle, bevelled the same way as the panel. */
    public static void knob(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BORDER);
        graphics.fill(x, y, x + width - 1, y + height - 1, BEVEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, BEVEL_DARK);
        graphics.fill(x + 1, y + 1, x + width - 2, y + height - 2, BODY);
    }

    /**
     * A one-pixel border, for highlighting a control the pointer is over.
     *
     * <p>Hover feedback is the plainest signal a UI has for <em>this can be clicked</em>, and a
     * control with none reads as decoration — which is exactly how the airlock terminals read
     * before this (PLAN rule 23). Drawn as an outline rather than a fill so it frames whatever
     * symbol is already inside the control without hiding it.
     */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y, x + 1, y + height, colour);
        graphics.fill(x + width - 1, y, x + width, y + height, colour);
    }

    /** A groove separating regions of the panel: a dim phosphor line, not a bevel — a CRT
     *  screen has no physical ridge to catch light on. */
    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, WELL_LIGHT);
    }

    /**
     * Label text, fitted into the room it was given.
     *
     * <p><strong>The width is not optional and used to not exist.</strong> These two methods took
     * an x, a y and a colour for their whole lives, so every machine panel in the mod was drawing
     * unbounded strings through a function that looked like it was in charge of layout. It was
     * fine in English and cut off in Russian, which is the failure mode of every unbounded string
     * ever written.
     *
     * @param maxWidth pixels available. Text longer than this is cut with an ellipsis rather than
     *                 allowed to run over whatever is beside it — for a label, being short is
     *                 better than being on top of the next thing.
     */
    public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                             int maxWidth) {
        graphics.text(font, fit(font, text, maxWidth), x, y, TEXT_DIM, false);
    }

    public static void value(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                             int maxWidth, int colour) {
        graphics.text(font, fit(font, text, maxWidth), x, y, colour, false);
    }

    /**
     * Text drawn over the world rather than on a panel, so it keeps its drop shadow.
     *
     * <p>A separate method rather than a boolean, because the choice is not a flag: on a panel a
     * shadow is noise, and over the world without one a pale string vanishes against regolith.
     * Two names make the call sites say which surface they are on.
     */
    public static void overlay(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                               int maxWidth, int colour) {
        graphics.text(font, fit(font, text, maxWidth), x, y, colour, true);
    }

    public static void overlay(GuiGraphicsExtractor graphics, Font font,
                               net.minecraft.network.chat.Component text, int x, int y,
                               int maxWidth, int colour) {
        overlay(graphics, font, text.getString(), x, y, maxWidth, colour);
    }

    /** The same, for text that has already been translated into a component. */
    public static void value(GuiGraphicsExtractor graphics, Font font,
                             net.minecraft.network.chat.Component text, int x, int y,
                             int maxWidth, int colour) {
        value(graphics, font, text.getString(), x, y, maxWidth, colour);
    }

    public static void label(GuiGraphicsExtractor graphics, Font font,
                             net.minecraft.network.chat.Component text, int x, int y,
                             int maxWidth) {
        label(graphics, font, text.getString(), x, y, maxWidth);
    }

    /**
     * A sentence that is allowed to take as many lines as it needs.
     *
     * <p>The other honest answer to a string that does not fit: where a label should be cut, a
     * sentence should wrap. Returns the y just past the last line so a caller can stack blocks
     * without knowing how tall each will be.
     */
    public static int wrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                              int maxWidth, int lineHeight, int colour) {
        return WrappedText.draw(graphics, font, text, x, y, maxWidth, lineHeight, colour);
    }

    /**
     * Cuts text to a width, with an ellipsis when it had to.
     *
     * <p>The ellipsis matters: a silently truncated string reads as a shorter string, and a player
     * comparing two panels has no way to know one of them is lying to them by omission.
     */
    public static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(net.minecraft.network.chat.Component.literal(text))
                <= maxWidth) {
            return text;
        }
        String dots = "...";
        int room = Math.max(0, maxWidth - font.width(
                net.minecraft.network.chat.Component.literal(dots)));
        return font.plainSubstrByWidth(text, room) + dots;
    }

    /** Same bands as the instrument kit, and now the same colours too. */
    public static int colourFor(float fraction) {
        if (fraction >= 0.6f) {
            return GOOD;
        }
        return fraction >= 0.3f ? WARN : BAD;
    }

    private MachineFrame() {}
}
