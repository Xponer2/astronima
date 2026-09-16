package play.xponer.astronima.client.hud;

import play.xponer.astronima.sim.metal.CentrifugalBed;
import play.xponer.astronima.sim.metal.FluidizedBedControl;

/**
 * The centrifugal bed's own picture: a drum seen end-on, and the window you are trying to sit in.
 *
 * <h2>The whole machine is one window that moves</h2>
 * Spin too slow and the fixed hydrogen flow carries the charge out of the exhaust; spin too fast
 * and the artificial gravity pins it to the wall so the gas channels past a dead bed. In between
 * it boils. Two opposite failures bracketing one good band — and the band's <em>position</em>
 * depends on how finely the ore was ground, because both threshold velocities scale with grain
 * size.
 *
 * <p>That is why this machine cannot be drawn as a percentage. "63%" cannot say that you are on
 * the wrong side of a boundary, and it certainly cannot say that the boundary moved when you
 * changed feed. A bar with the window drawn <em>on</em> it, sliding as the grind changes, says
 * both at a glance — and it teaches the thing the design document calls the lesson: there is no
 * better grind here, only a match between how fine you ground and how fast you spin.
 *
 * <p>The window edges are found by bisection rather than by inverting the correlations. Wen &amp;
 * Yu and Haider &amp; Levenspiel do not invert in closed form, and a search that asks the model
 * itself cannot drift away from it the way a hand-derived inverse would.
 *
 * <p>Minecraft-free (rule 25).
 */
public final class FluidBedView {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 68;

    /** How finely the window edges are searched for, in dial units. Sub-pixel on a 150 px bar. */
    private static final double DIAL_PRECISION = 1.0 / 512;

    /** The drum, seen down its axis. */
    public static Layout.Box drum() {
        return new Layout.Box("drum", 4, 2, 46, 46);
    }

    /** What is happening in it, said in words beside it. */
    public static Layout.Box state() {
        return new Layout.Box("state", 54, 2, 104, 14);
    }

    /** The spin bar, with the good window marked on it. */
    public static Layout.Box window() {
        return new Layout.Box("window", 54, 20, 104, 16);
    }

    /** The two numbers under the bar: the speed asked for, and the gravity it makes. */
    public static Layout.Box figures() {
        return new Layout.Box("figures", 54, 38, 104, 12);
    }

    /** The declared layout, so nothing here can be drawn over anything else (rule 27). */
    /**
     * The control, directly under the spin window it has to land in.
     *
     * <p>Every machine used to put its dial in the same place, fourteen pixels above the readouts,
     * whatever the machine was. That is a strip of chrome rather than an instrument, and it was
     * fairly called lazy. A control belongs <em>on</em> the thing it drives, so a player reaches
     * for the jaws to change the jaws.
     */
    public static Layout.Box control() {
        return new Layout.Box("control", 54, 52, 104, 14);
    }

    public static Layout plan() {
        Layout layout = new Layout("fluidbed", WIDTH, HEIGHT);
        for (Layout.Box box : new Layout.Box[] {drum(), state(), window(), figures(), control()}) {
            layout.reserve(box.name(), box.x(), box.y(), box.width(), box.height());
        }
        return layout;
    }

    /**
     * Where the good band starts and ends on the dial, for this grind.
     *
     * <p>Returns {@code [low, high]} in dial units. An empty window — a grind this drum cannot
     * fluidize at any speed it can reach — comes back as {@code low >= high}, which is a real
     * answer and not an error: it means "regrind, do not keep turning the knob".
     */
    public static double[] window(double feedMicrons) {
        boolean anyBoiling = false;
        for (double s = 0; s <= 1.0; s += 1.0 / 64) {
            if (regimeAtDial(feedMicrons, s) == CentrifugalBed.Regime.BOILING) {
                anyBoiling = true;
                break;
            }
        }
        if (!anyBoiling) {
            return new double[] {1.0, 0.0};
        }
        return new double[] {
                edge(feedMicrons, CentrifugalBed.Regime.BLOWING_OUT),
                edge(feedMicrons, CentrifugalBed.Regime.PACKED)};
    }

    /**
     * The dial position where the bed stops being in {@code failure} and starts boiling.
     *
     * <p>Regime is monotonic in spin — slow blows out, fast packs — so a bisection between a dial
     * position known to fail that way and one known to boil converges on the boundary.
     */
    private static double edge(double feedMicrons, CentrifugalBed.Regime failure) {
        double failing = failure == CentrifugalBed.Regime.BLOWING_OUT ? 0.0 : 1.0;
        double boiling = failure == CentrifugalBed.Regime.BLOWING_OUT ? 1.0 : 0.0;
        if (regimeAtDial(feedMicrons, failing) != failure) {
            return failing; // it never fails that way: the window runs to the end of the dial
        }
        while (Math.abs(boiling - failing) > DIAL_PRECISION) {
            double middle = (failing + boiling) / 2;
            if (regimeAtDial(feedMicrons, middle) == failure) {
                failing = middle;
            } else {
                boiling = middle;
            }
        }
        return (failing + boiling) / 2;
    }

    private static CentrifugalBed.Regime regimeAtDial(double feedMicrons, double dial) {
        return CentrifugalBed.regimeAt(feedMicrons, FluidizedBedControl.rpmFor(dial));
    }

    /**
     * How far up the drum wall the bed is standing, 0..1.
     *
     * <p>A packed bed is a thin dense ring hard against the wall; a boiling one stands off it and
     * churns; an entrained one is a thin cloud reaching the middle on its way out. So this is not
     * "progress" — it is where the solids physically are, and each of the three failures looks
     * different without reading a word.
     */
    public static double bedDepth(double feedMicrons, double rpm) {
        return switch (CentrifugalBed.regimeAt(feedMicrons, rpm)) {
            case PACKED -> 0.16;
            case BOILING -> 0.30 + 0.45 * CentrifugalBed.contactQuality(feedMicrons, rpm);
            case BLOWING_OUT -> 1.0;
        };
    }

    /**
     * How violently to shake the bed particles, in pixels.
     *
     * <p>Packed is still — that is exactly what has gone wrong with it, and a picture that keeps
     * jittering a packed bed would be lying about the one thing the player needs to see.
     */
    public static double agitation(double feedMicrons, double rpm) {
        return CentrifugalBed.regimeAt(feedMicrons, rpm) == CentrifugalBed.Regime.PACKED
                ? 0.0 : 1.0 + 2.0 * CentrifugalBed.contactQuality(feedMicrons, rpm);
    }

    /** How fast the drum wall appears to turn, in radians per tick, for the wall markings. */
    public static double wallSpin(double rpm) {
        return rpm / FluidizedBedControl.MAX_RPM * 0.35;
    }

    /**
     * How much of the charge is on its way out of the exhaust, 0..1 — drawn as the plume.
     *
     * <p>Straight from the model's own entrainment term, so the plume cannot disagree with the
     * losses the machine is actually taking.
     */
    public static double losing(double feedMicrons, double rpm) {
        return CentrifugalBed.entrainmentSeverity(feedMicrons, rpm);
    }

    private FluidBedView() {}
}
