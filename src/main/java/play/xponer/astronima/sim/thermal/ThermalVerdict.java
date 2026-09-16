package play.xponer.astronima.sim.thermal;

/**
 * Turns a room's heat balance into the sentence the player needs, which is almost never the
 * temperature.
 *
 * <p><strong>A room at 280 K and falling is a different situation from a room at 280 K and
 * steady, and only one of them needs acting on.</strong> That is the whole reason this class
 * exists: the analyzer has shown a temperature since the first tier, and a temperature alone
 * cannot distinguish "cold because it is a store room" from "cold because it is on its way to
 * −60 °C and you are standing in it".
 *
 * <p>And the second half — <em>which lever</em>. Cold has three answers in this game (bury it,
 * insulate it, be in there working) and they are not interchangeable: an insulated habitat
 * open to the sky is still losing kilowatts, and a buried bare-plate one with nobody in it is
 * losing them slower and still losing them. So the verdict names the term that currently
 * dominates, rather than saying "cold" and leaving the player to try things.
 *
 * <p>Minecraft-free (rule 1): watts and fractions in, a sentence out.
 */
public final class ThermalVerdict {

    /**
     * Where the temperature is going.
     *
     * <p>Three states rather than a signed number, because the decision the player makes is
     * three-way: leave it, act on it, or stop adding heat.
     */
    public enum Trend { FALLING, STEADY, RISING }

    /**
     * How close supply and loss must be to count as holding, as a fraction of the loss.
     *
     * <p>Proportional rather than a fixed wattage: a hundred watts either way is noise in a
     * hangar shedding twelve kilowatts and is the entire budget of a well-insulated cupboard.
     * A fixed threshold would make one of those two read as permanently steady and the other
     * as permanently unstable.
     */
    public static final double STEADY_BAND = 0.05;

    /** Below this a room is cold enough to be worth saying so about, K (about 5 °C). */
    public static final double COLD_K = 278.15;

    /** The share of a shell that counts as "mostly", for naming the dominant term. */
    private static final double DOMINANT = 0.34;

    public record Reading(Trend trend, String advice) {
        /** True when the room is holding its own — the state the player is aiming for. */
        public boolean isGood() {
            return trend != Trend.FALLING;
        }
    }

    /**
     * Reads the room.
     *
     * @param temperatureK      what it is at now
     * @param lossWatts         radiated plus conducted, at this temperature
     * @param supplyWatts       crew and worked machines putting heat back
     * @param skyFraction       share of the shell facing vacuum
     * @param insulatedFraction share of the shell built out of insulated plate
     */
    public static Reading read(double temperatureK, double lossWatts, double supplyWatts,
                               double skyFraction, double insulatedFraction) {
        Trend trend = trendOf(lossWatts, supplyWatts);
        return new Reading(trend,
                advice(trend, temperatureK, skyFraction, insulatedFraction, supplyWatts));
    }

    private static Trend trendOf(double lossWatts, double supplyWatts) {
        double band = Math.max(Math.abs(lossWatts) * STEADY_BAND, 1.0);
        double net = supplyWatts - lossWatts;
        if (net > band) {
            return Trend.RISING;
        }
        return net < -band ? Trend.FALLING : Trend.STEADY;
    }

    private static String advice(Trend trend, double temperatureK, double skyFraction,
                                 double insulatedFraction, double supplyWatts) {
        if (trend == Trend.RISING) {
            return "Warming — more heat going in than the hull sheds";
        }
        if (trend == Trend.STEADY) {
            return temperatureK < COLD_K
                    ? "Steady, but cold — it will stay here until something changes"
                    : "Holding — what you are doing in here pays for what the hull sheds";
        }
        // Falling, so name the lever with the most left in it. Order matters: radiating to
        // open sky dwarfs everything else, so telling someone to insulate a shell that is
        // half exposed would be true, useless, and expensive.
        if (skyFraction > DOMINANT) {
            return "Falling — much of this shell faces open sky. Bury it";
        }
        if (insulatedFraction < DOMINANT) {
            return "Falling — bare plate against cold rock. Insulate it";
        }
        if (supplyWatts <= 0) {
            return "Falling — covered and insulated, but nothing is putting heat in";
        }
        return "Falling — covered and insulated, and still shedding more than you make";
    }

    private ThermalVerdict() {}
}
