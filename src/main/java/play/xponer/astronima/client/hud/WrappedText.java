package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Text that cannot run off the panel it is drawn on.
 *
 * <p>This exists because the same bug has now been fixed three times in three places —
 * the machine screens, the biomonitor, the JEI pages — each time by hand, each time
 * only where it had been noticed. Minecraft's font is variable width, so the only way
 * to know where a line ends is to ask the font, and any code that draws a sentence
 * without asking is one long word away from spilling over an edge.
 *
 * <p>Having it once means the next panel gets it for free, and means the fix is a
 * property of the drawing rather than of the strings. Shortening the strings would have
 * worked until someone translated them.
 */
public final class WrappedText {
    /** Splits text into lines that each fit within {@code maxWidth}. */
    public static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            // A single word can still be wider than the whole column - long compound words are
            // routine in Russian technical/medical translations even where the English original
            // fit on one line easily. Splitting only on spaces then draws that one word as-is,
            // past the edge, exactly like the unwrapped bug this class exists to fix once for
            // everyone - so a word gets the same hard, width-driven break a line does.
            while (font.width(Component.literal(word)) > maxWidth) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                String head = font.plainSubstrByWidth(word, maxWidth);
                if (head.isEmpty()) {
                    // maxWidth cannot fit even one character; nothing further to split.
                    break;
                }
                lines.add(head);
                word = word.substring(head.length());
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(Component.literal(candidate)) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Draws wrapped text and returns the y just past the last line.
     *
     * <p>Returning the next y rather than taking a line count is what lets a caller
     * stack blocks of text without knowing in advance how tall each will be — which is
     * the thing that makes overflow impossible rather than merely unlikely.
     */
    public static int draw(GuiGraphicsExtractor graphics, Font font, String text,
                           int x, int y, int maxWidth, int lineHeight, int colour) {
        int row = y;
        for (String line : wrap(font, text, maxWidth)) {
            graphics.text(font, line, x, row, colour, false);
            row += lineHeight;
        }
        return row;
    }

    /** Height a block of text will occupy once wrapped. */
    public static int height(Font font, String text, int maxWidth, int lineHeight) {
        return wrap(font, text, maxWidth).size() * lineHeight;
    }

    private WrappedText() {}
}
