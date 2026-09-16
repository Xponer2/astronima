package play.xponer.astronima.sim;

/**
 * Room acoustics. Real spacecraft are loud — fans, pumps, and compressors run
 * around the clock, and chronic noise above ~60 dB measurably degrades sleep and
 * recovery (the ISS has strict acoustic limits for exactly this reason).
 *
 * <p>Decibels are logarithmic, so sources don't add linearly: two 55 dB machines
 * make 58 dB, not 110. Point sources also spread spherically, losing 6 dB per
 * doubling of distance, so where you put your bunk matters as much as how many
 * machines you run. Sealed walls block sound entirely (and vacuum carries none).
 */
public final class Acoustics {
    /** Above this sound level, rest and natural healing stop. */
    public static final double REST_BLOCKED_ABOVE_DB = 60.0;

    /** Distance at which machine ratings are quoted, metres. */
    public static final double REFERENCE_DISTANCE_M = 1.0;

    /**
     * Sound level of a point source heard at {@code distanceM}: spherical spreading
     * costs {@code 20·log10(d/d0)} dB — 6 dB per doubling.
     */
    public static double attenuatedDb(double sourceDb, double distanceM) {
        double distance = Math.max(REFERENCE_DISTANCE_M, distanceM);
        return sourceDb - 20.0 * Math.log10(distance / REFERENCE_DISTANCE_M);
    }

    /** Combines source levels in dB: {@code 10·log10(Σ 10^(L/10))}. */
    public static double combineDb(double... levelsDb) {
        double sum = 0;
        for (double level : levelsDb) {
            sum += Math.pow(10, level / 10.0);
        }
        return sum <= 0 ? 0 : 10.0 * Math.log10(sum);
    }

    public static boolean blocksRest(double levelDb) {
        return levelDb > REST_BLOCKED_ABOVE_DB;
    }

    /** Below this pressure, the medium can no longer carry sound to a listener's ear. */
    public static final double INAUDIBLE_BELOW_KPA = 1.0;

    /**
     * Fraction of full volume a sound is heard at, given the pressure of the medium around the
     * <em>listener's</em> head — {@link GasMixture#EARTH_PRESSURE_KPA} and above carries a
     * source at full volume, {@link #INAUDIBLE_BELOW_KPA} and below carries none, and pressure
     * between the two interpolates linearly.
     *
     * <p>Not a rigorous transmission-loss model — real acoustic impedance in a gas scales with
     * density, and a proper treatment would differ by frequency and geometry. This is the
     * honest simplification (design/presentation.md §4.1): one stated, testable curve in the
     * units a player can already read off the gas analyzer (rule 41), enough to make "the air
     * is thin" and "there is no air" sound like what they are. What this does not model —
     * structure-borne sound conducted through a solid path to the listener — is deliberately
     * out of scope; see the same section for why.
     */
    public static double pressureVolumeFactor(double pressureKPa) {
        if (pressureKPa <= INAUDIBLE_BELOW_KPA) {
            return 0.0;
        }
        if (pressureKPa >= GasMixture.EARTH_PRESSURE_KPA) {
            return 1.0;
        }
        return (pressureKPa - INAUDIBLE_BELOW_KPA)
                / (GasMixture.EARTH_PRESSURE_KPA - INAUDIBLE_BELOW_KPA);
    }

    private Acoustics() {}
}
