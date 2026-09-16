package play.xponer.astronima.sim.metal;

/**
 * Selective laser sintering of a metal powder bed — the first machine in the mod whose control is
 * a <em>plane</em>, not a line.
 *
 * <p>A laser scans a bed of iron powder and fuses it into a solid part. What fuses it is the
 * volumetric energy density delivered to the powder,
 *
 * <pre>
 *   VED = P / (v · h · t)        P laser power (W), v scan speed (mm/s), h hatch, t layer
 * </pre>
 *
 * and the same VED reached two ways does <strong>not</strong> give the same part: high power run
 * fast and low power run slow can deliver identical energy per mm³ while one keyholes and the other
 * balls. So the player trims <em>two</em> dials — power and speed — and the good result is a pocket
 * in the (power, speed) plane, not a point on a line. That is the whole reason this machine is not
 * the solar retort with a laser (rule 8): its control has a second axis, and it is a real one.
 *
 * <h2>The three ways it goes wrong, on different axes</h2>
 * <ul>
 *   <li><strong>Too little energy</strong> (low power or high speed) → the powder never fully
 *       melts: {@link Regime#LACK_OF_FUSION} porosity, a weak part shot through with voids.</li>
 *   <li><strong>Too much energy</strong> (high power or low speed) → the melt pool vaporises and
 *       collapses: {@link Regime#KEYHOLING} porosity, a defective part.</li>
 *   <li><strong>Too fast, at any energy</strong> → the molten track beads up before it fuses to
 *       its neighbours (Plateau–Rayleigh): {@link Regime#BALLING}. A perfect VED scanned too fast
 *       is still ruined, which is exactly why VED alone cannot be the model.</li>
 * </ul>
 *
 * <p>Minecraft-free (rule 1): a power and a speed in, the part's soundness out.
 */
public final class LaserSintering {

    /** Hatch spacing, mm — the gap between adjacent laser tracks. Fixed; the player trims P and v. */
    public static final double HATCH_MM = 0.10;
    /** Layer thickness, mm — how deep each powder layer is. Fixed. */
    public static final double LAYER_MM = 0.03;

    /** Below this energy density the bed does not fully melt: lack-of-fusion porosity. J/mm³. */
    public static final double VED_LACK_OF_FUSION = 45.0;
    /** Above this the melt pool keyholes: vapour-cavity porosity. J/mm³. */
    public static final double VED_KEYHOLE = 110.0;

    /** Scan speed above which the molten track breaks into beads (balling), mm/s. */
    public static final double BALLING_SPEED_MMS = 1200.0;

    /** The real range either dial can be set across — the plane's own bounds. */
    public static final double MIN_POWER_W = 10.0;
    public static final double MAX_POWER_W = 1000.0;
    public static final double MIN_SPEED_MMS = 50.0;
    public static final double MAX_SPEED_MMS = 3000.0;

    /** The best relative density a sound part reaches — never quite unity, as in real LPBF. */
    public static final double FULL_DENSITY = 0.995;

    /** How sound a part must be to count as usable, 0..1. */
    public static final double SOUND_THRESHOLD = 0.95;

    /** Which corner of the process plane a (power, speed) pair lands in. */
    public enum Regime {
        /** Too little energy: unmelted voids, a weak part. */
        LACK_OF_FUSION,
        /** Energy in the window and the speed under the balling limit: a dense, sound part. */
        SOUND,
        /** Too much energy: vapour-cavity (keyhole) porosity, a defective part. */
        KEYHOLING,
        /** Scanned too fast: the track beads up and does not fuse to its neighbours. */
        BALLING
    }

    /** Volumetric energy density this power and speed deliver, J/mm³. */
    public static double energyDensity(double powerW, double speedMmS) {
        double v = Math.max(speedMmS, 1e-6);
        return Math.max(powerW, 0) / (v * HATCH_MM * LAYER_MM);
    }

    /**
     * How stable the melt track is, 0..1 — one below the balling speed, falling above it as the
     * track breaks into beads. Independent of the energy density, which is the point: this is the
     * second axis, and no amount of the right energy fixes a track scanned too fast.
     */
    public static double trackStability(double speedMmS) {
        if (speedMmS <= BALLING_SPEED_MMS) {
            return 1.0;
        }
        return Math.clamp(1.0 - (speedMmS - BALLING_SPEED_MMS) / BALLING_SPEED_MMS, 0.0, 1.0);
    }

    /**
     * Relative density of the fused metal, from the energy density alone — a hump: rising out of
     * lack-of-fusion below the window, near-full inside it, and falling into keyhole porosity above
     * it. This is the first axis; {@link #trackStability} is the second, and {@link #soundness}
     * combines them.
     */
    public static double relativeDensity(double powerW, double speedMmS) {
        double ved = energyDensity(powerW, speedMmS);
        if (ved < VED_LACK_OF_FUSION) {
            // Unmelted voids: the deeper below the threshold, the more porous, down to a floor.
            return FULL_DENSITY - (VED_LACK_OF_FUSION - ved) / VED_LACK_OF_FUSION * 0.35;
        }
        if (ved > VED_KEYHOLE) {
            // Keyhole porosity: milder than lack-of-fusion per unit, but a genuine defect.
            return Math.max(0.0, FULL_DENSITY - (ved - VED_KEYHOLE) / VED_KEYHOLE * 0.25);
        }
        return FULL_DENSITY;
    }

    /**
     * Overall soundness of the printed part, 0..1 — the density the energy bought, cut by how
     * stable the track was. A part needs the energy in the window <em>and</em> the speed under the
     * balling limit; either one wrong pulls this below {@link #SOUND_THRESHOLD}.
     */
    public static double soundness(double powerW, double speedMmS) {
        return relativeDensity(powerW, speedMmS) * trackStability(speedMmS);
    }

    /** True when the part would come out dense and fused — the pocket in the plane. */
    public static boolean isSound(double powerW, double speedMmS) {
        return soundness(powerW, speedMmS) >= SOUND_THRESHOLD;
    }

    /**
     * Which regime a (power, speed) pair is in.
     *
     * <p>Balling is checked first and reported whatever the energy: a track that beads up is
     * ruined even at a perfect VED, so it is the dominant defect and hiding it behind the energy
     * window would let a player chase a number that cannot save the part.
     */
    public static Regime regime(double powerW, double speedMmS) {
        if (trackStability(speedMmS) < SOUND_THRESHOLD) {
            return Regime.BALLING;
        }
        double ved = energyDensity(powerW, speedMmS);
        if (ved < VED_LACK_OF_FUSION) {
            return Regime.LACK_OF_FUSION;
        }
        if (ved > VED_KEYHOLE) {
            return Regime.KEYHOLING;
        }
        return Regime.SOUND;
    }

    private LaserSintering() {}
}
