package play.xponer.astronima.client.screen;

import java.util.Locale;

/**
 * Abbreviates a count of four or more digits into a short "1k"/"12k"/"1.5M" form for display -
 * see {@code design/data-cells.md} §26. Never touches the real, underlying number: only the
 * text drawn in a slot's corner is shortened, the same way vanilla's own item-count text is
 * just a {@code String} handed to the font renderer.
 */
public final class CompactCount {

    private static final String[] SUFFIXES = {"", "k", "M", "B"};

    private CompactCount() {}

    public static String format(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        double value = count;
        int magnitude = 0;
        while (value >= 1000.0 && magnitude < SUFFIXES.length - 1) {
            value /= 1000.0;
            magnitude++;
        }
        String digits = value < 10.0
                ? trimTrailingZero(String.format(Locale.ROOT, "%.1f", value))
                : String.valueOf((long) value);
        return digits + SUFFIXES[magnitude];
    }

    private static String trimTrailingZero(String formatted) {
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }
}
