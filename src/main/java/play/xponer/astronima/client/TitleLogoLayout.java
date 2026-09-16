package play.xponer.astronima.client;

/**
 * The title screen's logo size, as a function of screen width — shared by
 * {@code TitleScreenLogoMixin} (which draws the logo) and {@code SplashAnchorMixin} (which moves
 * vanilla's splash-text anchor to sit at the logo's actual right edge instead of the fixed offset
 * it was tuned for around vanilla's old, constant-width logo). Centralized because those two
 * numbers drifting apart is exactly the bug this class exists to prevent: the splash text
 * rendering over top of, or oddly far from, whatever size the logo turned out to be.
 */
public final class TitleLogoLayout {

    /** The source artwork's own pixel size — {@link #heightFor} scales from this. */
    public static final int LOGO_TEXTURE_WIDTH = 2026;
    public static final int LOGO_TEXTURE_HEIGHT = 289;

    private static final int MIN_WIDTH = 260;
    private static final int MAX_WIDTH = 640;
    private static final float WIDTH_FRACTION = 0.55F;

    public static int widthFor(int screenWidth) {
        int width = Math.round(screenWidth * WIDTH_FRACTION);
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }

    public static int heightFor(int width) {
        return Math.round(width * (float) LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
    }

    private TitleLogoLayout() {}
}
