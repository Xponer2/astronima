package play.xponer.astronima.sim.rad;

/**
 * Real gamma dose-rate physics and accumulation (design/radiation.md §1) — gamma only, see that
 * document's §0 for why a neutron channel is not here: nothing in this mod produces one yet.
 *
 * <p>Minecraft-free (rule 1): every method here is a pure function of distances, intensities and
 * a dose value, so the whole model is checkable against a hand-worked value. The one thing this
 * class does <em>not</em> do is decide whether a line of sight is actually clear — that walks
 * real blocks and lives in {@link play.xponer.astronima.atmosphere.Shielding}, which calls back
 * into the pure functions here once it has answered that one Minecraft-touching question.
 */
public final class RadiationDose {

    /** An unshielded RTG's reference dose rate at one block, Sv/h — an industrial-source-at-
     * close-range order of magnitude, falling off from there by the real inverse-square law. */
    public static final double RTG_REFERENCE_SV_PER_H_AT_1_BLOCK = 0.6;

    /** A severe solar particle event's dose rate at full intensity, unshielded, Sv/h — the real
     * order of magnitude estimated for an unshielded observer in deep space during a large SPE
     * (the sun is effectively infinitely distant, so this has no inverse-square term). */
    public static final double FLARE_PEAK_SV_PER_H = 4.0;

    /** How fast accumulated dose falls away with nothing hurting the player, Sv/s. Real marrow
     * recovery from sub-lethal exposure takes real days, not the ~20 minutes {@code GasToxicity}
     * clears CO in — sized so clearing the 0.25 Sv "first symptoms" threshold alone takes about a
     * day of real time with no further exposure. */
    public static final double RECOVERY_SV_PER_S = 0.25 / (24.0 * 3600.0);

    /** Real acute radiation syndrome (ARS) thresholds, Sv — not tuned numbers. */
    public enum Severity {
        NONE, MILD, SEVERE, CRITICAL;

        public static Severity classify(double doseSv) {
            if (doseSv < 0.25) {
                return NONE;
            }
            if (doseSv < 1.0) {
                return MILD;
            }
            if (doseSv < 6.0) {
                return SEVERE;
            }
            return CRITICAL;
        }
    }

    /**
     * A point source's dose rate at {@code distanceBlocks}, Sv/h — the real inverse-square law,
     * or zero if anything solid stands on the line between source and observer (design/
     * radiation.md §1.1: at this model's reference gamma energy a single ordinary block is
     * already dozens of half-value-layers, so shielding is legitimately binary here).
     */
    public static double fromPointSource(double referenceSvPerHAt1Block, double distanceBlocks,
                                          boolean lineOfSightClear) {
        if (!lineOfSightClear || distanceBlocks <= 0) {
            return lineOfSightClear ? referenceSvPerHAt1Block : 0.0;
        }
        return referenceSvPerHAt1Block / (distanceBlocks * distanceBlocks);
    }

    /**
     * A flare's dose rate, Sv/h — scales with intensity, and only reaches a position with a
     * genuinely clear view of the sky (no inverse-square term: the sun is not a nearby point).
     */
    public static double fromFlare(double flareIntensity01, boolean hasClearSky) {
        if (!hasClearSky) {
            return 0.0;
        }
        return FLARE_PEAK_SV_PER_H * Math.clamp(flareIntensity01, 0.0, 1.0);
    }

    /**
     * Advances accumulated body dose, Sv, by {@code dtSeconds} at {@code totalRateSvPerH} —
     * never negative.
     */
    public static double stepDose(double doseSv, double totalRateSvPerH, double dtSeconds) {
        double next = doseSv + (totalRateSvPerH / 3600.0) * dtSeconds - RECOVERY_SV_PER_S * dtSeconds;
        return Math.max(0.0, next);
    }

    private RadiationDose() {}
}
