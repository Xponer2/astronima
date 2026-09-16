package play.xponer.astronima.client.hud;

/**
 * Where the pieces of the retort's own picture sit, and how bright the beam is.
 *
 * <h2>Why a machine gets its own picture</h2>
 * Every processing machine wore the same panel: a slot, a bar, a dial and some rows. It read as one
 * machine with seven skins, and worse, it hid what each one <em>is</em>. A retort is a mirror
 * throwing sunlight at a vessel — that is a thing you can draw, and drawn it says more in one glance
 * than four rows of text.
 *
 * <p>Minecraft-free, so rule 27 can prove the parts do not overlap and rule 25 keeps the arithmetic
 * out of the screen (a beam whose brightness is decided in the paint code is a beam nobody can
 * check).
 */
public final class RetortView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 82;

    /** The dish, on the left, aimed right. */
    public static Layout.Box mirror() {
        return new Layout.Box("mirror", 2, 6, 30, 50);
    }

    /** The beam it throws, between the mirror and the vessel. */
    public static Layout.Box beam() {
        return new Layout.Box("beam", 34, 22, 46, 18);
    }

    /** The vessel it heats. */
    public static Layout.Box vessel() {
        return new Layout.Box("vessel", 82, 8, 34, 46);
    }

    /** The thermometer beside it, which is the one number that decides everything. */
    public static Layout.Box scale() {
        return new Layout.Box("scale", 120, 8, 12, 46);
    }

    /** What is coming off, drawn leaving the top of the vessel. */
    public static Layout.Box vapour() {
        return new Layout.Box("vapour", 134, 8, 24, 46);
    }

    /**
     * The control, under the vessel, beside the window it has to sit inside.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 80, 64, 54, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("retort", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {mirror(), beam(), vessel(), scale(), vapour(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How bright the beam is, 0..1.
     *
     * <p>Sunlight times focus, because both are real inputs and the player needs to tell them
     * apart: a dark beam at full focus is night, and a dark beam at noon is a dish pointing
     * nowhere. Multiplying them and drawing one beam would confuse the two — so the beam's
     * <em>width</em> is the focus and its <em>brightness</em> is the sun.
     */
    public static double beamBrightness(double sunlight) {
        return Math.clamp(sunlight, 0, 1);
    }

    /** How wide the beam is, 0..1 — the focus, tightened to a line at full. */
    public static double beamSpread(double focus) {
        return 1 - Math.clamp(focus, 0, 1) * 0.82;
    }

    /**
     * Where the temperature sits on the thermometer, 0 at the bottom.
     *
     * <p>Against the process window rather than against a maximum, for the reason
     * {@code design/indicators.md} gives: past the window hotter is actively worse, and a scale
     * that kept filling would be praising the mistake.
     */
    public static double mercury(double temperatureK, double onsetK, double spoilK) {
        double from = onsetK - (spoilK - onsetK) * 0.35;
        double to = spoilK + (spoilK - onsetK) * 0.2;
        return Math.clamp((temperatureK - from) / Math.max(1, to - from), 0, 1);
    }

    private RetortView() {}
}
