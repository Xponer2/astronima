package play.xponer.astronima.client.hud;

/**
 * The cold forge's own picture: a press, a workpiece under it, and the two properties crossing.
 *
 * <h2>Two numbers moving against each other is a graph, not a pair of bars</h2>
 * Hardness is what you are buying and ductility is what you spend to buy it. Two gauges said that
 * and made you compare them; <strong>one plot with both lines on it puts the crossing in front of
 * you</strong>, and the crossing is the whole lesson — there is a point where another blow costs
 * more than it gains, and you should be able to see yourself approaching it.
 *
 * <p>Real, too: working metal cold moves dislocations until they tangle, which is exactly why it
 * gets harder and more brittle at the same time and why a meteoric tool blunts instead of
 * shattering.
 *
 * <p>Minecraft-free (rule 25).
 */
public final class ForgeView {

    public static final int WIDTH = 160;
    // Must clear drawCalibration's status lamp+text row (control().y() + 4, one text row
    // tall) with room to spare before the next row starts - a shorter value here does not
    // shrink the control, it shrinks the gap MachineBody leaves before drawing the next
    // row, which is how "Set true" and "Still soft - keep working it" once ended up printed
    // on top of each other. See PanelLayoutTest.
    public static final int HEIGHT = 76;

    /** The ram, which comes down as far as the blow strength says. */
    public static Layout.Box ram() {
        return new Layout.Box("ram", 8, 0, 34, 26);
    }

    /** The workpiece it lands on, squatter every time. */
    public static Layout.Box piece() {
        return new Layout.Box("piece", 4, 28, 42, 18);
    }

    /** The chamber gauge: a forge in vacuum has nothing to push against. */
    public static Layout.Box chamber() {
        return new Layout.Box("chamber", 4, 48, 42, 10);
    }

    /** The plot both properties are drawn on. */
    public static Layout.Box plot() {
        return new Layout.Box("plot", 52, 4, 104, 54);
    }

    /**
     * The control, under the ram it drops.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 6, 60, 40, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("forge", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {ram(), piece(), chamber(), plot(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How far down the ram sits, 0 at the top of its travel.
     *
     * <p>The blow strength, directly: a heavy blow is a ram that comes further down, and there is
     * no reason to dress that up. It is the one part of this machine that is honestly just the
     * dial.
     */
    public static double ramTravel(double blow) {
        return Math.clamp(blow, 0, 1);
    }

    /**
     * How squat the workpiece has become, 0..1.
     *
     * <p>From the hardness rather than from the blow, because a piece that has been worked is a
     * piece that has <em>already</em> been squashed — drawing it from the control would show the
     * next blow's effect before it happened.
     */
    public static double squash(double hardness) {
        return Math.clamp(hardness, 0, 1);
    }

    /**
     * Whether another blow is worth taking.
     *
     * <p>True while hardness is still climbing faster than ductility is being spent. Past the
     * crossing every blow buys less than it costs, and the plot should be able to say so before the
     * piece cracks rather than after.
     */
    public static boolean worthAnotherBlow(double hardness, double ductility) {
        return ductility > 0.25 && hardness < 0.95;
    }

    /**
     * Where this piece has got to along the plot, 0 fresh and 1 worked out.
     *
     * <p>Named for what it is after the first attempt called it "the crossing" and computed a
     * ratio that moved <em>backwards</em> as the piece hardened. The lines cross wherever the
     * curves cross — that is a property of the metal, not of this piece. What the marker shows is
     * how far along its own working the piece has come, which is the hardness it has gained plus
     * the give it has spent.
     */
    public static double workedTo(double hardness, double ductility) {
        return Math.clamp((Math.clamp(hardness, 0, 1) + (1 - Math.clamp(ductility, 0, 1))) / 2,
                0, 1);
    }

    private ForgeView() {}
}
