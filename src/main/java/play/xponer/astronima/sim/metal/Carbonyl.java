package play.xponer.astronima.sim.metal;

/**
 * The Mond process: metal that walks into a gas and walks back out pure.
 *
 * <p>1890, and still how the purest nickel in the world is made. Nickel and carbon monoxide
 * combine at around 50 °C into nickel tetracarbonyl — a gas — and that gas falls apart again at
 * around 230 °C, putting the metal down and <strong>handing the carbon monoxide back</strong>.
 *
 * <h2>The carrier is not a fuel, and that is the whole idea</h2>
 * Carbon monoxide has been a pure hazard for this entire game: thresholds, a dose model, an
 * ailment that names it, and no use whatever. Here it becomes a <em>tool</em>, and specifically
 * one you need once and keep — it goes in, carries the metal across, and comes back out. What
 * the player actually spends is heat, time and attention.
 *
 * <p><strong>Iron does not follow.</strong> Iron pentacarbonyl needs far higher pressure to
 * form, so at these conditions the nickel leaves and the iron stays behind. That is why the
 * process exists industrially and why this is a <em>separator</em> rather than a purifier.
 *
 * <h2>And the gap between the two temperatures is the hazard</h2>
 * Held too cool to decompose but warm enough to form, the machine makes nickel carbonyl and
 * never takes it apart — and nickel carbonyl is one of the most toxic industrial substances
 * there is, dangerous at parts per million and notorious for a delayed onset. Getting the
 * temperature wrong does not waste a batch; it fills the room with the worst poison in the game.
 *
 * <p>Minecraft-free (rule 1): a temperature and a charge in, moles out.
 */
public final class Carbonyl {

    /**
     * Where nickel begins to leave with the gas, K — about 50 °C.
     *
     * <p>Startlingly low, and that is real: the Mond process runs near room temperature, which
     * is exactly what made it revolutionary and exactly what makes it dangerous.
     */
    public static final double FORMING_K = 323.15;

    /** Fully forming by here, K — about 80 °C. */
    public static final double FORMING_COMPLETE_K = 353.15;

    /**
     * Where the carbonyl starts falling apart again, K — about 180 °C.
     *
     * <p>The <strong>gap</strong> between {@link #FORMING_COMPLETE_K} and this is the whole
     * hazard: a machine parked in it forms the poison and never destroys it.
     */
    public static final double DECOMPOSING_K = 453.15;

    /** Decomposing completely by here, K — about 230 °C, the industrial figure. */
    public static final double DECOMPOSING_COMPLETE_K = 503.15;

    /** Moles of carbon monoxide bound per mole of nickel: Ni + 4 CO ⇌ Ni(CO)₄. */
    public static final double CO_PER_NICKEL = 4.0;

    /** What the machine is doing at a given temperature. */
    public enum Stage {
        /** Too cool for anything to happen. */
        COLD,
        /** Nickel is leaving the charge and entering the gas. */
        FORMING,
        /**
         * Warm enough to have formed carbonyl and too cool to break it down.
         *
         * <p>The failure state, and it is not a stall: the machine is working perfectly and
         * filling with the most poisonous thing in the game.
         */
        HOLDING,
        /** The carbonyl is falling apart and putting pure nickel down. */
        DECOMPOSING
    }

    public static Stage stageAt(double temperatureK) {
        if (temperatureK < FORMING_K) {
            return Stage.COLD;
        }
        if (temperatureK < FORMING_COMPLETE_K) {
            return Stage.FORMING;
        }
        return temperatureK < DECOMPOSING_K ? Stage.HOLDING : Stage.DECOMPOSING;
    }

    /**
     * How much of the charge's nickel is carried into the gas at this temperature, 0..1.
     *
     * <p>Ramped rather than switched, so the player is hunting a band and not a threshold —
     * and so a machine slightly too cool produces a little rather than nothing, which is the
     * legible failure.
     */
    public static double formingFraction(double temperatureK) {
        if (temperatureK < FORMING_K) {
            return 0;
        }
        if (temperatureK >= FORMING_COMPLETE_K) {
            return 1;
        }
        return (temperatureK - FORMING_K) / (FORMING_COMPLETE_K - FORMING_K);
    }

    /** How much carbonyl is broken back down at this temperature, 0..1. */
    public static double decomposingFraction(double temperatureK) {
        if (temperatureK < DECOMPOSING_K) {
            return 0;
        }
        if (temperatureK >= DECOMPOSING_COMPLETE_K) {
            return 1;
        }
        return (temperatureK - DECOMPOSING_K) / (DECOMPOSING_COMPLETE_K - DECOMPOSING_K);
    }

    /**
     * One step of a charge sitting at this temperature.
     *
     * <p><strong>{@code rate} throttles forming, not decomposing.</strong> Nickel walking into
     * the gas is a real diffusion-limited process — the metal takes real ticks to dissolve into
     * the carrier, which is what {@code rate} models. Once the gas is already formed and hot
     * enough to break down, there is nothing left to diffuse through: gas-phase decomposition is
     * fast, and holding it back by the same per-tick throttle as forming built up a real,
     * previously undetected "bubble" of held carbonyl at the one setpoint
     * {@code CarbonylRefinerBlockEntity} actually runs at — where forming and decomposing are
     * both fully saturated from the very first tick of a charge, not reached in two separate
     * phases the way this class's own two-phase unit test happened to exercise it. Sequential
     * first-order kinetics with equal rate constants peaks the intermediate species at a real,
     * non-negligible fraction of the total (about 1/e of the whole charge, here), and every mole
     * riding in that peak pays {@code CarbonylRefinerBlockEntity.LEAK_FRACTION} per tick it
     * spends there — roughly 9% of an entire charge's nickel and carbon monoxide, gone for
     * good, under textbook-correct operation that never once parks in the forming/decomposing
     * gap. That directly contradicted this class's and {@code setpointK()}'s own documented
     * claim that correct operation "never accumulates" — the claim was right about intent, the
     * code was not living up to it. Uncoupling decomposition from {@code rate} (it still ramps
     * smoothly with {@link #decomposingFraction}, from 0 at {@link #DECOMPOSING_K} to 1 at
     * {@link #DECOMPOSING_COMPLETE_K} — gradual for the same reason forming is, just not
     * additionally throttled once it is fully underway) empties whatever formed that same tick
     * before more can build up, matching "comes back down as pure metal the very next tick" for
     * real rather than in name only.
     *
     * @param nickelInCharge  moles of nickel still in the solid
     * @param carbonylHeld    moles of nickel currently riding in the gas
     * @param availableCo     moles of carbon monoxide free to bind
     * @param rate            fraction of the possible movement this step's forming performs
     */
    public static Step step(double nickelInCharge, double carbonylHeld, double availableCo,
                            double temperatureK, double rate) {
        double forming = formingFraction(temperatureK) * Math.clamp(rate, 0, 1);
        double breaking = decomposingFraction(temperatureK);

        // Limited by the carrier as well as by the metal: four moles of CO per mole of nickel,
        // and with none free nothing moves however hot it is.
        double carried = Math.min(Math.max(nickelInCharge, 0) * forming,
                Math.max(availableCo, 0) / CO_PER_NICKEL);
        double released = Math.max(carbonylHeld, 0) * breaking;

        return new Step(carried, released,
                carried * CO_PER_NICKEL, released * CO_PER_NICKEL);
    }

    /**
     * What one step moved.
     *
     * @param nickelCarried moles of nickel that left the solid for the gas
     * @param nickelPutDown moles of nickel the gas gave back as pure metal
     * @param coBound       carbon monoxide taken up by the carrying
     * @param coReleased    carbon monoxide handed back by the putting down
     */
    public record Step(double nickelCarried, double nickelPutDown,
                       double coBound, double coReleased) {
        /** Net carbon monoxide the carrier gained or lost this step. */
        public double netCo() {
            return coReleased - coBound;
        }
    }

    private Carbonyl() {}
}
