package play.xponer.astronima.client.hud;

/**
 * Where a HUD element attaches to the screen, and the arithmetic for turning an
 * anchor plus an offset into a top-left corner.
 *
 * <p>Kept free of Minecraft and NeoForge types — {@link HudConfig} owns the config
 * spec, this owns the maths — so placement can be unit-tested without a game.
 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public int resolveX(int offsetX, int screenWidth, int elementWidth) {
        return switch (this) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - elementWidth - offsetX;
        };
    }

    public int resolveY(int offsetY, int screenHeight, int elementHeight) {
        return switch (this) {
            case TOP_LEFT, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - elementHeight - offsetY;
        };
    }
}
