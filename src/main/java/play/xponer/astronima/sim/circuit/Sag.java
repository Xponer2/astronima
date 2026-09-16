package play.xponer.astronima.sim.circuit;

/**
 * What is left of the bus voltage by the time it reaches the far end, and what that costs.
 *
 * <h2>A full battery was hiding the whole mechanic</h2>
 * {@code design/electrical.md} §5.1 promises that <em>"voltage sags under load, and a machine at the
 * end of a long thin run undervolts and slows"</em>. It did not. A machine worked out what the run
 * would waste, asked the battery for <strong>that much extra</strong>, and ran at full speed — so a
 * hundred metres of signal wire to a crusher cost nothing but charge, and only when the battery ran
 * dry did anything happen at all.
 *
 * <p>That is not what a wire does. A wire cannot be persuaded to carry more by wanting it more: the
 * source has a fixed voltage, the line and the load divide it between them, and what the load gets
 * is whatever share of it the load's own resistance won.
 *
 * <h2>The load is a resistance, which is a named simplification</h2>
 * A machine here is modelled as the resistance it would present at its rated power and the nominal
 * bus — {@code R = V²/P}. Then it is an ordinary divider:
 *
 * <pre>
 *   I      = V / (R_line + R_load)
 *   V_load = I · R_load
 *   P_load = I² · R_load = P_rated · (R_load / (R_line + R_load))²
 * </pre>
 *
 * <p><strong>Constant impedance rather than constant power</strong>, and the difference matters. A
 * constant-power load pulls <em>more</em> current as the voltage falls, which on a bad enough run
 * runs away to infinity and has no solution at all — real equipment behaves that way and real
 * equipment also trips out. A resistive load degrades smoothly, which is what a player needs from
 * a mechanic they are supposed to diagnose. It is exact for a heater and approximate for a motor,
 * and that is the approximation, stated (§9).
 *
 * <p>The square is what makes it a real decision. Halve the voltage at the machine and it does a
 * <strong>quarter</strong> of the work, so a run that is merely a bit too thin is a nuisance and one
 * that is twice too thin is a machine that has stopped.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Sag {

    /**
     * What a load of this rated power looks like to the line, in ohms.
     *
     * <p>Its resistance at the nominal bus, which is the definition of a rating: a 250 W machine on
     * a 48 V bus is 9.2 Ω, and that is the number the line is competing against.
     */
    public static double loadOhms(double ratedWatts, double volts) {
        if (!(ratedWatts > 0) || !(volts > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        return volts * volts / ratedWatts;
    }

    /** The current that actually flows, in amps. */
    public static double amps(double lineOhms, double ratedWatts, double volts) {
        double load = loadOhms(ratedWatts, volts);
        if (!Double.isFinite(load)) {
            return 0;
        }
        return volts / (load + Math.max(0, lineOhms));
    }

    /**
     * How much of its rated power the load actually receives, 0..1.
     *
     * <p>The square of the voltage it kept. This is the number a machine's work rate scales by, and
     * the reason a long thin run is a decision rather than a tax.
     */
    public static double fraction(double lineOhms, double ratedWatts, double volts) {
        double load = loadOhms(ratedWatts, volts);
        if (!Double.isFinite(load) || load <= 0) {
            return 0;
        }
        double kept = load / (load + Math.max(0, lineOhms));
        return kept * kept;
    }

    /** The voltage that reaches the machine — what an instrument at the far end would read. */
    public static double voltsAt(double lineOhms, double ratedWatts, double volts) {
        double load = loadOhms(ratedWatts, volts);
        if (!Double.isFinite(load)) {
            return volts;
        }
        return amps(lineOhms, ratedWatts, volts) * load;
    }

    /**
     * What the source has to supply to sit at that operating point, in watts.
     *
     * <p><strong>Less than the rating, not more.</strong> That is the counter-intuitive half and it
     * is correct: an undervolted machine draws <em>less</em> current, so a badly wired base does not
     * drain its batteries faster — it just gets nothing done. The old model had it the other way
     * round, which taught exactly the wrong lesson about what a bad run costs.
     */
    public static double drawWatts(double lineOhms, double ratedWatts, double volts) {
        return volts * amps(lineOhms, ratedWatts, volts);
    }

    private Sag() {}
}
