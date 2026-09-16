package play.xponer.astronima.client.hud;

/**
 * A named place a floating readout can stand.
 *
 * <p>Every panel in this mod used to work out its own position with literals, which is why two of
 * them could land on each other and the only fix anybody reached for was making one refuse to draw.
 * A place is not a coordinate: it is a coordinate <em>once the screen and the panel are measured</em>,
 * so the same declaration works on any window.
 *
 * <p>Minecraft-free (rule 25).
 */
public enum HudPlace {

    /**
     * Just below the crosshair — where the eye already is.
     *
     * <p>The best place on the screen and therefore the contested one. Never <em>on</em> the
     * crosshair: a readout that covers the thing you are aiming with has stopped being an
     * instrument.
     */
    UNDER_CROSSHAIR,

    /** Above the hotbar, centred. Out of the way, still central. */
    ABOVE_HOTBAR,

    /** Right of the crosshair, at eye height. Reads without moving your eyes far. */
    RIGHT_OF_CROSSHAIR,

    /** Left of the crosshair. The other half of the same idea. */
    LEFT_OF_CROSSHAIR,

    /** Top right corner, where a status board belongs and nothing urgent should be. */
    TOP_RIGHT,

    /** Top left corner. */
    TOP_LEFT,

    /**
     * Directly above the crosshair.
     *
     * <p>Added when reservations started taking real space: with the oxygen strip, the suit board,
     * the analyzer and another mod's tooltip all holding rectangles, six places was thin enough
     * that a readout could end up on a second page for want of anywhere to stand — and a second
     * page is the answer of last resort, not a normal Tuesday.
     */
    ABOVE_CROSSHAIR,

    /** Low and to the right, clear of the hotbar. */
    LOWER_RIGHT,

    /** Low and to the left. */
    LOWER_LEFT,

    /** Against the right edge, half way down. */
    MID_RIGHT,

    /** Against the left edge, half way down. */
    MID_LEFT;

    /** Margin kept from every screen edge. */
    public static final int EDGE = 4;

    /** How far from the crosshair a panel starts, so the aiming point stays clear. */
    public static final int CLEARANCE = 12;

    /** Room left along the bottom for the hotbar and its trimmings. */
    public static final int HOTBAR_ROOM = 46;

    /**
     * Where a panel of this size stands here, on a screen of this size.
     *
     * <p>Clamped to the screen at the end, always. A place that resolves off the edge on a small
     * window is a readout that is simply gone, and the player has no way to know it was meant to
     * be there.
     */
    public Rect resolve(int screenWidth, int screenHeight, int width, int height) {
        int midX = screenWidth / 2;
        int midY = screenHeight / 2;
        int x;
        int y;
        switch (this) {
            case UNDER_CROSSHAIR -> {
                x = midX - width / 2;
                y = midY + CLEARANCE;
            }
            case ABOVE_HOTBAR -> {
                x = midX - width / 2;
                y = screenHeight - HOTBAR_ROOM - height;
            }
            // Side places sit ABOVE the crosshair line, not level with it. Level looks right and
            // is wrong: a panel under the crosshair is centred and wide, so it reaches well past
            // the crosshair to both sides, and anything at eye height beside it lands on its
            // corner. A test found this by placing a displaced panel in the hotbar corner when a
            // side place was supposedly free.
            case RIGHT_OF_CROSSHAIR -> {
                x = midX + CLEARANCE;
                y = midY - CLEARANCE - height;
            }
            case LEFT_OF_CROSSHAIR -> {
                x = midX - CLEARANCE - width;
                y = midY - CLEARANCE - height;
            }
            case ABOVE_CROSSHAIR -> {
                x = midX - width / 2;
                y = midY - CLEARANCE - height;
            }
            case LOWER_RIGHT -> {
                x = screenWidth - EDGE - width;
                y = screenHeight - HOTBAR_ROOM - height;
            }
            case LOWER_LEFT -> {
                x = EDGE;
                y = screenHeight - HOTBAR_ROOM - height;
            }
            case MID_RIGHT -> {
                x = screenWidth - EDGE - width;
                y = midY - height / 2;
            }
            case MID_LEFT -> {
                x = EDGE;
                y = midY - height / 2;
            }
            case TOP_RIGHT -> {
                x = screenWidth - EDGE - width;
                y = EDGE;
            }
            default -> {
                x = EDGE;
                y = EDGE;
            }
        }
        return new Rect(
                Math.clamp(x, EDGE, Math.max(EDGE, screenWidth - EDGE - width)),
                Math.clamp(y, EDGE, Math.max(EDGE, screenHeight - EDGE - height)),
                width, height);
    }

    /** A rectangle on the screen. */
    public record Rect(int x, int y, int width, int height) {
        public boolean overlaps(Rect other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }

        public boolean isInside(int screenWidth, int screenHeight) {
            return x >= 0 && y >= 0
                    && x + width <= screenWidth && y + height <= screenHeight;
        }
    }
}
