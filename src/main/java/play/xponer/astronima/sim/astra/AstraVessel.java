package play.xponer.astronima.sim.astra;

/**
 * A held charge of astra, leaking over time — design/astra-core.md §5: "it leaks. A vessel loses
 * astra over time; you cannot bank it for years." Minecraft-free (rule 1), and deliberately a
 * separate class from {@link AstraField}: the ground refills *toward its own baseline* (diffusion
 * from richer surroundings); a held charge decays *toward zero* (nothing refills a vessel), which
 * is a genuinely different shape, not a special case of the same formula.
 *
 * <h2>The number, and what it is not</h2>
 * {@link #LEAK_TIME_CONSTANT_S} is a first-pass rule-41 number, exactly like {@code AstraField}'s
 * own {@code SWEEP_FRACTION}: not measured, chosen to state a real player experience —
 * design/astra-precipitation.md §1.2's own "race" only exists if a vessel left too long genuinely
 * loses something you would have wanted, which means the leak has to be fast enough to matter
 * inside one play session and slow enough that a short errand does not gut it.
 */
public final class AstraVessel {

    /** Seconds for a held charge to decay to about a third of itself (one time constant of
     *  exponential decay) — five real minutes, so leaving a full collector aside "for a bit"
     *  costs something noticeable without punishing a single short trip. */
    private static final double LEAK_TIME_CONSTANT_S = 300.0;

    private AstraVessel() {}

    /**
     * What a held charge of {@code amount} decays to after {@code elapsedSeconds} of leaking.
     * Exponential, never negative, never below zero, and — unlike {@link
     * AstraField#refilled} — approaching zero rather than any baseline, because nothing refills a
     * vessel.
     */
    public static double afterLeak(double amount, double elapsedSeconds) {
        if (amount <= 0.0) {
            return 0.0;
        }
        if (elapsedSeconds <= 0.0) {
            return amount;
        }
        return amount * Math.exp(-elapsedSeconds / LEAK_TIME_CONSTANT_S);
    }
}
