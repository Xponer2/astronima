package play.xponer.astronima.client.hud;

/**
 * Turning a cursor position into a setting, and back.
 *
 * <h2>Why this is its own class</h2>
 * A slider is three pieces of arithmetic that must agree: where the bar is drawn, where the mouse
 * counts as being on it, and what a given cursor position means. They lived as three separate
 * literals in the screen, and the moment the control moved into each machine's picture two of them
 * followed and the third did not.
 *
 * <p>The result was reported exactly as it behaves: <em>"I drag right, the cursor goes past the
 * edge of the dial, and the knob moves very slowly."</em> The bar was 56 pixels wide and the
 * mapping still divided by 160, so the knob moved at a third of the hand — and the value ran out
 * long after the cursor had left the control.
 *
 * <p>Having it here means the three cannot disagree again, and means the mapping is arithmetic a
 * unit test can reach rather than something that needs a mouse and an eye (rule 25).
 */
public final class Slider {

    /**
     * The setting a cursor at {@code mouseX} means, for a bar starting at {@code left}.
     *
     * <p>Clamped, so dragging past either end pins rather than wraps or refuses. A slider that
     * stops responding when the cursor leaves it is one a player fights.
     */
    public static double valueFor(double mouseX, int left, int width) {
        if (width <= 0) {
            return 0;
        }
        return Math.clamp((mouseX - left) / width, 0.0, 1.0);
    }

    /**
     * Where the knob's left edge goes for a setting.
     *
     * <p>The inverse of {@link #valueFor}, near enough that grabbing the knob does not make it
     * jump: the handle is {@link #KNOB_WIDTH} wide, so its centre is what tracks the value.
     */
    public static int knobX(double value, int left, int width) {
        int travel = Math.max(0, width - KNOB_WIDTH);
        return left + (int) Math.round(Math.clamp(value, 0.0, 1.0) * travel);
    }

    public static final int KNOB_WIDTH = 5;

    /**
     * How much slack a grab has around the bar.
     *
     * <p>A five-pixel-tall bar is a hard thing to hit with a mouse, and missing it feels like the
     * control is broken rather than like the hand was. Generous on purpose.
     */
    public static final int GRAB_MARGIN = 4;

    /** Whether a cursor is close enough to the bar to be grabbing it. */
    public static boolean isOver(double mouseX, double mouseY, int left, int top, int width,
                                 int height) {
        return mouseX >= left - GRAB_MARGIN && mouseX <= left + width + GRAB_MARGIN
                && mouseY >= top - GRAB_MARGIN && mouseY <= top + height + GRAB_MARGIN;
    }

    private Slider() {}
}
