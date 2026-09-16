package play.xponer.astronima.sim.circuit;

/**
 * What a fuse or a breaker on a given run gives up at.
 *
 * <h2>One rating protected nothing but one gauge</h2>
 * Protection was a flat twelve amps, quoted against 4 mm² iron. That is a real figure for a
 * standard run and <strong>decoration on anything thinner</strong>: signal wire gives up at 2.9 A in
 * vacuum, so a fuse waiting for twelve is a fuse that watches the conductor burn. And the previous
 * slices made that reachable rather than theoretical — a single machine five metres down signal wire
 * pulls 4.7 A, which cooks the run while the fuse sits there.
 *
 * <h2>Not four fuses — one, sized by the conductor it is spliced into</h2>
 * The obvious fix is a fuse per gauge, and rule 8 forbids it: a 2.3 A fuse and an 11 A fuse are the
 * same idea with a different number, and three item ids for that is three chances to pick up the
 * wrong one for no lesson at all.
 *
 * <p>So the rating comes from <strong>the run</strong>. That is also what a fuse physically is — a
 * deliberately weak length of the same conductor — and it puts the decision where this tier already
 * puts every other one: on the gauge and the metal the player chose.
 *
 * <p>It buys a mistake worth making, too. A run is rated by its <em>thinnest</em> conductor, exactly
 * as it is already resisted by its worst metal, so splicing one pixel of signal wire into a busbar
 * trunk drops the whole trunk's protection to 2.3 A and the trunk trips under a load it used to
 * carry. One pixel of the wrong wire ruining a run is the same lesson the resistance already
 * teaches, and now the protection teaches it too.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Protection {

    /**
     * How much of the conductor's own limit protection is set at.
     *
     * <p>Four fifths, which is not invented: it is the continuous-load rule real wiring codes use,
     * for the real reason that an ampacity is a steady-state figure and a circuit that sits at
     * exactly its limit has no margin for a warm day or a second machine.
     *
     * <p>It very nearly reproduces the twelve amps this shipped with — 13.7 A of standard iron at
     * four fifths is 11.0 A — and the small change is worth more than the round number was, because
     * eleven <em>follows from</em> something while twelve was chosen. Both sit where the lesson
     * needs them: above the 10.4 A two machines draw and below the 15.6 A of three.
     */
    public static final double CONTINUOUS_FRACTION = 0.8;

    /**
     * What protection on this conductor gives up at, in amps.
     *
     * <p>Measured in <strong>vacuum</strong>, deliberately, and that is a safety argument rather
     * than an oversight. A fuse cannot know whether the stretch of run it is protecting is indoors:
     * the same conductor carries two thirds more in air, so rating it for air would leave every
     * length that crosses a vacuum bay unprotected — and a run's exposed stretch is exactly where it
     * fails (§2.1).
     */
    public static double ratingAmps(Conductor metre) {
        return Cooling.inVacuum(metre, 0).ratingAmps() * CONTINUOUS_FRACTION;
    }

    /** The same, for the thinnest gauge and worst metal anywhere on a run — its weakest link. */
    public static double ratingAmps(WireGauge gauge, ConductorMaterial metal) {
        return ratingAmps(gauge.metre(metal));
    }

    private Protection() {}
}
