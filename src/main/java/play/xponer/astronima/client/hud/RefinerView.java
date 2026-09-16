package play.xponer.astronima.client.hud;

import play.xponer.astronima.sim.metal.Carbonyl;

/**
 * The carbonyl refiner's own picture: a column, gas going up it, and metal coming back out.
 *
 * <h2>The Mond process is a picture of two temperatures</h2>
 * Carbon monoxide over warm nickel makes nickel carbonyl — a <em>gas</em>. Carry that gas somewhere
 * hotter and it gives the nickel straight back, pure. Iron does the same thing at a different
 * temperature, and that gap is the entire separation: it is not a melt, it is a metal that
 * volunteers to walk out as a vapour while the other one stays put.
 *
 * <p>Drawn, that is one column with a cool bottom and a hot top, and a dial that says which of the
 * two ends the vessel is actually at. A percentage could never show that you are between them and
 * therefore doing neither.
 *
 * <p>Minecraft-free (rule 25).
 */
public final class RefinerView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 76;

    /** The charge at the bottom, where the gas picks the nickel up. */
    public static Layout.Box charge() {
        return new Layout.Box("charge", 4, 34, 44, 22);
    }

    /** The column between them. */
    public static Layout.Box column() {
        return new Layout.Box("column", 12, 4, 28, 30);
    }

    /** The hot end, where it puts the nickel back down. */
    public static Layout.Box deposit() {
        return new Layout.Box("deposit", 52, 4, 48, 30);
    }

    /** The temperature scale, marked with both thresholds. */
    public static Layout.Box scale() {
        return new Layout.Box("scale", 104, 2, 54, 54);
    }

    /**
     * The control, under the temperature scale, which is what it is.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 102, 58, 56, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("refiner", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {charge(), column(), deposit(), scale(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /** Where a temperature sits on the scale, with both thresholds inside the travel. */
    public static double onScale(double temperatureK) {
        double from = Carbonyl.FORMING_K - 40;
        double to = Carbonyl.DECOMPOSING_COMPLETE_K + 40;
        return Math.clamp((temperatureK - from) / (to - from), 0, 1);
    }

    /**
     * How much gas is being made, 0..1 — the bottom half of the process.
     *
     * <p>Straight from the model, so the column cannot show a flow the vessel is not producing.
     */
    public static double lifting(double temperatureK) {
        return Math.clamp(Carbonyl.formingFraction(temperatureK), 0, 1);
    }

    /** How much is being put back down, 0..1 — the top half. */
    public static double depositing(double temperatureK) {
        return Math.clamp(Carbonyl.decomposingFraction(temperatureK), 0, 1);
    }

    /**
     * Whether the vessel is making gas and giving nothing back.
     *
     * <p>The mistake this machine actually allows, and the first version of this method described a
     * different one that <strong>cannot happen</strong>: I assumed forming fell away above its
     * threshold, so a vessel could be too warm to lift and too cool to lay down. It does not — once
     * it is forming, it keeps forming. A test caught me inventing a hazard the model does not have.
     *
     * <p>What is real is worse and more interesting: between the two thresholds the column
     * <em>is</em> carrying nickel up and nothing is bringing it back down, so the carbonyl simply
     * accumulates. Everything looks busy and no metal appears. A single progress figure would hide
     * that completely.
     */
    public static boolean isStranded(double temperatureK) {
        Carbonyl.Stage stage = Carbonyl.stageAt(temperatureK);
        return stage == Carbonyl.Stage.FORMING || stage == Carbonyl.Stage.HOLDING;
    }

    private RefinerView() {}
}
