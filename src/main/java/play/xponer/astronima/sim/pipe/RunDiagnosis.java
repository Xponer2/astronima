package play.xponer.astronima.sim.pipe;

import org.jspecify.annotations.Nullable;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

import java.util.List;

/**
 * What is wrong with a run of pipe, in the order a player should go and fix it.
 *
 * <p>Every other part of the plumbing has an instrument on it: the tank has a pressure
 * gauge, the pump says running or stalled, the valve shows its bore. The <em>run</em> has
 * nothing, and the run is what people actually get wrong — a line that goes nowhere, a line
 * with a shut valve in the middle of it, a line joined to only one room. All of those look
 * identical from outside: a pipe, doing nothing.
 *
 * <p>Reported over and over from play in exactly that shape — <em>"the pump does not
 * understand the valve"</em>, <em>"you cannot tell when it is shut"</em>, <em>"nothing
 * happens"</em>. Each time the plumbing was doing precisely what it was built to do and
 * there was no way to see it.
 *
 * <p>Minecraft-free (rule 1) because the interesting part is the <em>ordering</em>: a run
 * can have three things wrong at once, and telling the player about the least important one
 * sends them to the wrong block. One verdict, chosen by what to fix first, provable without
 * a world.
 */
public final class RunDiagnosis {

    /** What a surveyed run is doing, worst-first. */
    public enum Verdict {
        /**
         * A valve on the line is shut. Nothing beyond it is even reachable.
         *
         * <p>First, always: it is the one fault that is entirely invisible from the pipe
         * you are looking at, and the one that most often has a player rebuilding a line
         * that was already correct.
         */
        SHUT_VALVE,
        /**
         * The run reaches nothing worth reaching — fewer than two volumes and no pump.
         *
         * <p>A pipe joined to one room and nothing else is the commonest first build, and
         * it does nothing at all: gas has nowhere to go.
         */
        GOES_NOWHERE,
        /** A valve is part open, so the line is deliberately throttled. Not a fault. */
        THROTTLED,
        /** Connected, able to carry gas, with nothing currently pushing. */
        IDLE,
        /** Connected and carrying. */
        FLOWING;

        public boolean isFault() {
            return this == SHUT_VALVE || this == GOES_NOWHERE;
        }
    }

    /**
     * What a survey of the world found on one run.
     *
     * @param pipes         how many pipe/valve blocks are on it
     * @param volumes       how many rooms and tanks it joins — the thing that decides
     *                      whether gas has anywhere to go
     * @param tanks         how many of those volumes are tanks
     * @param valveOpen     the tightest valve's opening, 0 shut .. 1 wide
     * @param servedByPump  a pump draws from or feeds this run, so one volume is enough
     * @param moving        something is actually pushing gas along it right now
     * @param dominantGas   the most abundant gas pooled across every volume this run joins,
     *                      or null when none of them hold any gas at all — D3's own
     *                      "colour-coded contents" half of the presentation, reported in words
     *                      rather than a world-space colour since the survey is already text
     *                      (design/wrench.md §4)
     */
    public record Survey(int pipes, int volumes, int tanks, double valveOpen,
                         boolean servedByPump, boolean moving, @Nullable Gas dominantGas) {}

    /** How far open a valve has to be before it stops being worth mentioning. */
    public static final double THROTTLE_NOTICE = 0.99;

    /**
     * The one thing to tell the player about this run.
     *
     * <p>Order is the whole design: a shut valve outranks everything because nothing past it
     * exists; a run that goes nowhere outranks a throttle because throttling a line that
     * reaches nothing is not the problem. Only once neither is true does the reading become
     * about what the run is doing rather than what is wrong with it.
     */
    public static Verdict verdict(Survey survey) {
        if (survey.valveOpen() <= 0) {
            return Verdict.SHUT_VALVE;
        }
        // A pump only needs one volume on the side it draws from; a passive run needs two,
        // because gas moves between volumes and one volume is a dead end by definition.
        int needed = survey.servedByPump() ? 1 : 2;
        if (survey.volumes() < needed) {
            return Verdict.GOES_NOWHERE;
        }
        if (survey.moving()) {
            return Verdict.FLOWING;
        }
        return survey.valveOpen() < THROTTLE_NOTICE ? Verdict.THROTTLED : Verdict.IDLE;
    }

    /**
     * The verdict said out loud, in the words the player needs.
     *
     * <p>Each fault names <em>what to do</em>, not what is true. "shut valve on this line"
     * is a fact; a player who reads it still has to work out that the answer is to go and
     * open it. The throttle line carries the number because a part-open valve passes far
     * less than its bore suggests — a quarter-open valve is nearer a hundredth of the flow —
     * and that surprise is the whole reason the valve is interesting.
     */
    public static String label(Survey survey) {
        Verdict verdict = verdict(survey);
        String contents = survey.dominantGas() == null ? "" : " (" + survey.dominantGas().symbol() + ")";
        return switch (verdict) {
            case SHUT_VALVE -> "shut valve on this line - open it";
            case GOES_NOWHERE -> survey.servedByPump()
                    ? "reaches no room or tank - add a port or a tank"
                    : "joins only " + survey.volumes()
                            + " volume - a line needs two ends, or a pump";
            case THROTTLED -> String.format("throttled to %.0f%% flow",
                    Valve.flowFraction(survey.valveOpen()) * 100) + contents;
            case IDLE -> "open, " + survey.volumes() + " volumes - nothing pushing" + contents;
            case FLOWING -> "carrying gas, " + survey.volumes() + " volumes" + contents;
        };
    }

    /**
     * The most abundant gas pooled across a run's own volumes — D3's own "colour-coded
     * contents" half (design/wrench.md §4), reported in words through this same survey rather
     * than the still-not-built world-space colouring. Lives here, not in {@link
     * play.xponer.astronima.pipe.PipeSurvey}, so the pooling itself stays Minecraft-free and
     * testable without a world (rule 1) — that class also touches real blocks, and a class
     * that does forces even its Minecraft-free methods to resolve against Minecraft at load
     * time, which a plain unit test's classpath does not have.
     */
    public static @Nullable Gas dominantGas(List<RoomState> rooms) {
        Gas best = null;
        double bestMoles = 0;
        for (RoomState room : rooms) {
            for (Gas gas : Gas.values()) {
                double moles = room.gases().get(gas);
                if (moles > bestMoles) {
                    bestMoles = moles;
                    best = gas;
                }
            }
        }
        return best;
    }

    private RunDiagnosis() {}
}
