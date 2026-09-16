package play.xponer.astronima.client.hud;

/**
 * Where everything sits on the hand bench.
 *
 * <p>Free of {@code Screen}, and that is not tidiness: a class extending {@code Screen} cannot be
 * loaded by a unit test, so geometry that lives inside one is geometry checked by looking at it.
 * The same lesson the JEI page taught — rule 27 only bites where the arithmetic is reachable.
 */
public final class DishLayout {

    public static final int WIDTH = 300;
    public static final int HEIGHT = 190;
    public static final int MARGIN = 8;

    /** The plate itself, big and round, because a circle reads as glassware and nothing else does. */
    public static Layout.Box plate() {
        return new Layout.Box("plate", MARGIN, MARGIN, 120, 120);
    }

    /** Everything read off it: the label, and what the key still wants. */
    public static Layout.Box readout() {
        return new Layout.Box("readout", 136, MARGIN, WIDTH - 136 - MARGIN, 120);
    }

    /** The operations, along the bottom, with the pour bar under them. */
    public static Layout.Box tools() {
        return new Layout.Box("tools", MARGIN, 134, WIDTH - MARGIN * 2, HEIGHT - 134 - MARGIN);
    }

    public static Layout plan() {
        Layout layout = new Layout("dish", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {plate(), readout(), tools()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    private DishLayout() {}
}
