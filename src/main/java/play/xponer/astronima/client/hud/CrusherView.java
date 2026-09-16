package play.xponer.astronima.client.hud;

import play.xponer.astronima.sim.ore.Comminution;

/**
 * The crusher's own picture: two jaws at their gap, and what comes out underneath.
 *
 * <h2>The trade is two things at once, so draw both</h2>
 * Narrow the jaws and more mineral is freed from the rock around it — and every batch costs more
 * cranking. Those move together and they are the whole machine. A row saying "412 um / frees 34% /
 * 18 work" contains that; a gap you can see closing, with the product below getting finer and the
 * effort bar filling, shows it.
 *
 * <h2>The grain is drawn, and that is the point</h2>
 * Liberation is not a mood: mineral sits in the rock in grains about 120 µm across, and crushing
 * frees it exactly when the fragments get smaller than the grains. Drawing the grain size as a line
 * the fragments have to cross under makes the whole mechanic self-evident, and it is the one number
 * a player would otherwise never meet.
 *
 * <p>Minecraft-free (rule 25).
 */
public final class CrusherView {

    public static final int WIDTH = 160;
    // Must clear drawCalibration's status lamp+text row (control().y() + 4, one text row
    // tall) with room to spare before the next row starts. See ForgeView's own note and
    // PanelLayoutTest.
    public static final int HEIGHT = 74;

    /** The fixed jaw, on the left. */
    public static Layout.Box fixedJaw() {
        return new Layout.Box("fixed jaw", 6, 0, 12, 34);
    }

    /** The swing jaw, which is the one the dial moves. */
    public static Layout.Box swingJaw() {
        return new Layout.Box("swing jaw", 44, 0, 12, 34);
    }

    /** What falls out, sized by the gap above it. */
    public static Layout.Box product() {
        return new Layout.Box("product", 2, 36, 58, 20);
    }

    /** The grain line: where liberation actually happens. */
    public static Layout.Box grainScale() {
        return new Layout.Box("grain scale", 64, 0, 54, 56);
    }

    /** What each batch costs to turn. */
    public static Layout.Box effort() {
        return new Layout.Box("effort", 122, 0, 36, 56);
    }

    /**
     * The control, under the jaws it moves, because that is the thing it moves.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 4, 58, 56, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("crusher", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {
                fixedJaw(), swingJaw(), product(), grainScale(), effort(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * How open the jaws are, 0..1, where 1 is the widest the machine goes.
     *
     * <p>Straight from the particle size the model produces rather than from the dial, so the
     * picture cannot claim a gap the crusher is not actually working at. Logarithmic, because the
     * size range is — two millimetres down to twenty microns is two decades, and a linear gap would
     * spend nine tenths of its travel in sizes nobody uses.
     */
    public static double jawGap(double setting) {
        double microns = Comminution.particleSizeMicrons(setting);
        double from = Math.log10(Comminution.FINEST_MICRONS);
        double to = Math.log10(Comminution.COARSEST_MICRONS);
        return Math.clamp((Math.log10(microns) - from) / (to - from), 0, 1);
    }

    /**
     * Where the grain size sits on the same scale, so the fragments can be seen crossing it.
     *
     * <p>Above the line, fragments are bigger than the grains and the mineral is still locked in
     * rock. Below it, it is free. That crossing <em>is</em> liberation, and it is why the curve
     * bends where it does.
     */
    public static double grainMark() {
        double from = Math.log10(Comminution.FINEST_MICRONS);
        double to = Math.log10(Comminution.COARSEST_MICRONS);
        return Math.clamp((Math.log10(Comminution.GRAIN_SIZE_MICRONS) - from) / (to - from), 0, 1);
    }

    /**
     * How much of the effort bar is filled, against the hardest setting the machine has.
     *
     * <p>Against its own worst case rather than against a fixed number, so the bar is full exactly
     * when the player has asked for everything the machine can do — which is the moment the
     * trade-off is at its sharpest and the moment they should be looking at it.
     */
    public static double effortFraction(int work, int hardest) {
        return Math.clamp(work / (double) Math.max(1, hardest), 0, 1);
    }

    private CrusherView() {}
}
