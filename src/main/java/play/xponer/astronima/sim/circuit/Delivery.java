package play.xponer.astronima.sim.circuit;

/**
 * What arrives at the far end of a run, and what the run kept.
 *
 * <p>This is where the tier's arithmetic finally bites. Power is moved at a voltage; the current
 * that implies is {@code I = P/V}; and the run turns {@code I²R} of it into heat. So the same
 * energy sent down the same wire costs wildly different amounts depending on a number the player
 * chooses — and the loss goes as the <strong>square</strong> of the current, which is why halving
 * it quarters the waste.
 *
 * <p>Replaces {@code Transmission}'s flat one per cent per block, which {@code design/power.md}
 * itself flags as <em>"a game decision instead of a law"</em>. A percentage cannot tell iron from
 * aluminium, a thin wire from a thick one, or a short run from a long one. Ohm can, and the
 * player can read every term of it off the coil's own panel before they lay a metre.
 *
 * <p><strong>Conservation is the invariant.</strong> Whatever is sent either arrives or becomes
 * heat — never neither. A method that returned only the delivered half would make losing the
 * remainder easy to do by accident, which is the exact failure this tier exists to avoid.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Delivery {

    /**
     * The working voltage of an ordinary machine bus, V.
     *
     * <p>Forty-eight, because fifty volts DC is the widely used ceiling for wiring a person may
     * touch without special measures — so this is the highest bus that is still <em>safe</em>,
     * and it is exactly the trade the tier is about. Going higher cuts the loss fourfold for each
     * doubling and starts asking for insulation and discipline instead.
     */
    public static final double BUS_VOLTS = 48.0;

    /**
     * How a run splits what it is given.
     *
     * @param deliveredJ what reaches the far end
     * @param heatJ      what the conductor kept, which must go into the world somewhere
     */
    public record Split(double deliveredJ, double heatJ) {
        public double offeredJ() {
            return deliveredJ + heatJ;
        }

        /** 0..1, for a readout that wants to say how good a run is. */
        public double efficiency() {
            double offered = offeredJ();
            return offered <= 0 ? 1.0 : deliveredJ / offered;
        }
    }

    /**
     * Sends {@code joules} over {@code seconds} down a run of {@code resistanceOhms} at
     * {@code volts}.
     *
     * <p>Time is a parameter because {@code I²R} is a <em>rate</em>: the same joules pushed
     * through in half the time is twice the current and four times the loss. Taking the interval
     * rather than assuming one is what keeps that honest when a machine ticks at a different
     * cadence from a generator.
     *
     * <p>A run that would lose more than it carries delivers nothing and turns all of it to heat,
     * rather than going negative. That is not a fudge: it is what a badly undersized run does —
     * the load cannot pull its rated power at all, and everything the source makes ends up warming
     * the wire.
     */
    public static Split send(double joules, double seconds, double resistanceOhms, double volts) {
        double sent = Math.max(joules, 0);
        if (sent <= 0 || seconds <= 0 || resistanceOhms <= 0 || volts <= 0) {
            return new Split(sent, 0);
        }
        double watts = sent / seconds;
        double amps = watts / volts;
        double lossWatts = amps * amps * resistanceOhms;
        double lossJ = Math.min(lossWatts * seconds, sent);
        return new Split(sent - lossJ, lossJ);
    }

    /**
     * The current a run carries at this power and voltage, A — what an ampacity check is against.
     */
    public static double amps(double watts, double volts) {
        return volts <= 0 ? 0 : Math.max(watts, 0) / volts;
    }

    private Delivery() {}
}
