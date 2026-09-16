package play.xponer.astronima.sim.astra;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A ritual's live draw — design/astra-core.md §5.1 (resolved): no stockpile, a direct draw on the
 * surrounding field for the ritual's own duration, depleting the ground the same way ordinary
 * extraction does, except live and at speed. Minecraft-free (rule 1): takes a caller-supplied list
 * of real tap points (one per arm — real {@code AstraFieldStorage} readings, however many the
 * built figure has), so the sim never assumes a shape for "how rich the site is" that only real
 * per-position data can actually answer.
 *
 * <h2>One step, not one leap — design/astra-ritual-grammar.md's own real block entity needs this</h2>
 * {@link #step} advances exactly {@link #STEP_SECONDS} and returns immediately — the shape a real
 * block entity ticking once per real second needs, so the player watches the burnt radius grow
 * rather than seeing a result appear instantly. {@link #run} is not a second implementation: it is
 * {@link #step} called in a loop, so the debug command's "what would this figure do" answer and a
 * real ritual's own tick can never quietly drift apart (rule 46).
 *
 * <h2>Why a shared pool over several tap points, not one point scaled up</h2>
 * design/astra-ritual-grammar.md §1: more arms reach more ground. The honest way to represent that
 * is literally more real positions contributing their own real density — not a single point's
 * density multiplied by an arm count, which would be inventing supply a real position was never
 * asked to hold. A richer figure is one whose arms actually reach richer ground, which is a real
 * placement decision, not a number this class manufactures.
 *
 * <h2>The numbers, first-pass per rule 41 — and a real finding, not hidden</h2>
 * {@link #DRAW_RATE_PER_ARM_PER_SECOND} and {@link #REQUIRED_DURATION_SECONDS} are chosen, not
 * measured. Checked directly rather than assumed: because both the draw rate and the tapped pool
 * scale with arm count together, a figure built over *uniformly* rich ground is roughly as hard to
 * sustain at any arm count — the real difficulty differentiation this design wants ("a bigger
 * figure is a harder bet") comes from arms actually reaching into ground that is not uniformly
 * rich (a neighbour already burnt, a reach toward the rim), which only real per-position wiring
 * (a later leaf) can supply. This class's own contract is narrower and still real: given whatever
 * the world actually reports at each tap point, does the pool sustain the demand for the required
 * duration.
 */
public final class AstraRitual {

    /** Astra drawn per second, per arm — a first-pass number, not measured. Small relative to a
     *  rich real-world baseline (~0.9 near the core) on purpose: a real altar's arms all reach
     *  into the same {@code AstraFieldStorage} grid cell (they are at most {@code
     *  AstraAltarBlockEntity.MAX_ARM_LENGTH} apart, far inside one cell), so unlike this class's
     *  own abstract per-arm-independent-pool tests, a real figure's arms compete for one shared
     *  reservoir rather than each drawing on their own — found by
     *  {@code astraAltarActivatesAndCompletesARealRitual} stalling at a genuinely rich site under
     *  the original 0.008. */
    private static final double DRAW_RATE_PER_ARM_PER_SECOND = 0.003;

    /** How long a ritual must sustain its draw to complete — a first-pass number, not measured. */
    private static final double REQUIRED_DURATION_SECONDS = 60.0;

    /** Simulation step size. Coarser than a real game tick on purpose: a real block entity calls
     *  {@link #step} on a cadence (not necessarily every tick), and this is the unit that cadence
     *  advances by. */
    private static final double STEP_SECONDS = 1.0;

    private AstraRitual() {}

    /** One arm's real tap point: what the field actually reads there right now, and what it
     *  would recover toward if left alone. */
    public record TapPoint(double density, double baselineDensity) {}

    /** Whether a ritual is still drawing, finished sustaining the full duration, or died under
     *  its own demand. */
    public enum Phase { RUNNING, COMPLETED, STALLED }

    /** A ritual's state between steps — everything {@link #step} needs to advance it once more,
     *  and everything a caller needs to know right now. {@code tapPoints} carries each point's
     *  live density forward from step to step; {@code armCount} is fixed for the run (a figure's
     *  own arm count does not change while it is running). */
    public record State(List<TapPoint> tapPoints, int armCount, double elapsedSeconds,
                         double totalDrawn, Phase phase) {

        public static State start(List<TapPoint> tapPoints, int armCount) {
            return new State(tapPoints, armCount, 0.0, 0.0, Phase.RUNNING);
        }
    }

    /** Astra drawn per second by a figure with this many arms. */
    public static double drawRatePerSecond(int armCount) {
        return DRAW_RATE_PER_ARM_PER_SECOND * Math.max(0, armCount);
    }

    /** How many real seconds one call to {@link #step} (or one real tap-point draw, for a caller
     *  driving {@link AstraFieldStorage} directly rather than this class's own in-memory pool)
     *  represents. */
    public static double stepSeconds() {
        return STEP_SECONDS;
    }

    /** How long a ritual must sustain its draw to complete — exposed so a real block entity
     *  ticking its own draw against {@link AstraFieldStorage} checks completion against the same
     *  number {@link #step} does, rather than a second copy of it (rule 46). */
    public static double requiredDurationSeconds() {
        return REQUIRED_DURATION_SECONDS;
    }

    /**
     * The proportional share of {@code totalToDraw} each of {@code values} gives up, or
     * {@code null} if the pool cannot cover it at all — the one piece of arithmetic both
     * {@link #step}'s own in-memory pool and a real block entity persisting each point through
     * {@code AstraFieldStorage.drawAt} need, factored out once so the two can never quietly
     * compute it two different ways (rule 46).
     */
    public static double @Nullable [] drawShares(double[] values, double totalToDraw) {
        double poolAvailable = sum(values);
        if (poolAvailable < totalToDraw) {
            return null;
        }
        double[] shares = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double share = poolAvailable > 0.0 ? values[i] / poolAvailable : 0.0;
            shares[i] = totalToDraw * share;
        }
        return shares;
    }

    /**
     * Advances {@code state} by exactly {@link #STEP_SECONDS}: draws proportionally from every
     * tap point by its own current share of the pool ({@link #drawShares}), then lets every tap
     * point refill independently toward its own baseline ({@link AstraField#refilled}) — a
     * richer point pulls its own recovery harder, exactly as the ground model already does
     * elsewhere. Idempotent once {@code state} is no longer {@link Phase#RUNNING}: calling
     * {@code step} again on a finished state returns it unchanged, so a caller ticking a block
     * entity does not need to guard every call with its own "is this still running" check.
     */
    public static State step(State state) {
        if (state.phase() != Phase.RUNNING) {
            return state;
        }

        int n = state.tapPoints().size();
        double[] density = new double[n];
        double[] baseline = new double[n];
        for (int i = 0; i < n; i++) {
            density[i] = state.tapPoints().get(i).density();
            baseline[i] = state.tapPoints().get(i).baselineDensity();
        }

        double drawThisStep = drawRatePerSecond(state.armCount()) * STEP_SECONDS;
        double[] shares = drawShares(density, drawThisStep);
        if (shares == null) {
            return new State(state.tapPoints(), state.armCount(), state.elapsedSeconds(),
                    state.totalDrawn(), Phase.STALLED);
        }

        List<TapPoint> next = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double afterDraw = density[i] - shares[i];
            next.add(new TapPoint(AstraField.refilled(afterDraw, baseline[i], STEP_SECONDS), baseline[i]));
        }

        double elapsed = state.elapsedSeconds() + STEP_SECONDS;
        double totalDrawn = state.totalDrawn() + drawThisStep;
        Phase phase = elapsed >= REQUIRED_DURATION_SECONDS ? Phase.COMPLETED : Phase.RUNNING;
        return new State(next, state.armCount(), elapsed, totalDrawn, phase);
    }

    /** What a ritual run actually did: whether it completed, how long it actually ran before
     *  stopping (equal to the required duration on completion), and the total astra it drew —
     *  the number {@link AstraPrecipitation} converts into a yield on completion. */
    public record Outcome(boolean completed, double elapsedSeconds, double totalDrawn) {}

    /**
     * Runs a ritual over {@code tapPoints} to its conclusion in one call — {@link #step}, looped,
     * for a caller (the debug command) that wants the answer immediately rather than one real
     * second at a time.
     */
    public static Outcome run(List<TapPoint> tapPoints, int armCount) {
        State state = State.start(tapPoints, armCount);
        while (state.phase() == Phase.RUNNING) {
            state = step(state);
        }
        return new Outcome(state.phase() == Phase.COMPLETED, state.elapsedSeconds(), state.totalDrawn());
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }
}
