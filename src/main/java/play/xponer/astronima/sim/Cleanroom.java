package play.xponer.astronima.sim;

/**
 * A tracked room-cleanliness quantity (design/halogens.md §30-32, Part C3) — no existing
 * analogue: every other room quantity in this mod (gas, temperature) is a physical fact about the
 * volume itself, while this one is a certification, only ever produced by the one controller that
 * measures it. Minecraft-free (rule 1); every method here is a pure function of a fraction, a
 * pressure, or a capacity in real moles.
 *
 * <p>{@link #isPositivelyPressurized} substitutes an absolute pressure threshold for the real,
 * far smaller room-vs-corridor differential a real cleanroom is specified against (§31) — this
 * simulation has no existing room-vs-room comparison to reuse, and the threshold is self-verifying
 * for the same real property anyway: {@code Atmosphere.exchangeThroughLeaks} already bleeds any
 * room with a real leak back toward its neighbour's pressure every tick, so sustained overpressure
 * is only achievable in a room that is genuinely well-sealed.
 */
public final class Cleanroom {

    /** Real margin above standard atmosphere this design treats as "positively pressurized" — see
     *  §31 for why an absolute threshold substitutes for the real, much smaller (~10-15 Pa)
     *  room-vs-corridor differential a real cleanroom spec actually names. */
    public static final double POSITIVE_PRESSURE_MARGIN_KPA = 5.0;

    public static final double TARGET_PRESSURE_KPA =
            GasMixture.EARTH_PRESSURE_KPA + POSITIVE_PRESSURE_MARGIN_KPA;

    /** Full climb 0->1 in about 20 real minutes, sealed and pressurized — a real cleanroom
     *  industry recovery-time figure, not a tuned one (§31). */
    public static final double RISE_PER_SECOND = 1.0 / (20.0 * 60.0);

    /** Full loss 1->0 in about 60 real seconds when the room is not sealed at all — real
     *  door-breach particle studies show excursions this fast (§31). */
    public static final double BREACH_DECAY_PER_SECOND = 1.0 / 60.0;

    /** Full loss 1->0 in about a real hour when sealed but not actively filtered/pressurized — an
     *  order of magnitude slower than a breach, the same three-timescale honesty
     *  {@code GasToxicity}'s own acute-vs-accumulated split already keeps (§31). */
    public static final double IDLE_DECAY_PER_SECOND = 1.0 / 3600.0;

    /** A designer's choice in this mod's own molar convention (no real "cubic metres of air"
     *  service-life figure is universal across HEPA models) — sized the same way
     *  {@code Scrubber.CARTRIDGE_CAPACITY_MOL} is: roughly a session's worth of continuous use. */
    public static final double HEPA_CAPACITY_MOL = 500.0;

    /** The blower's real throughput, mol/s. */
    public static final double AIR_INJECTION_MOL_PER_S = 0.5;

    /** 5% of capacity — the same "well before mathematically zero" reasoning
     *  {@code Scrubber.SWAP_THRESHOLD_MOL} already uses, scaled to this capacity. */
    public static final double SWAP_THRESHOLD_MOL = HEPA_CAPACITY_MOL * 0.05;

    /** True once a room's own pressure clears the real margin above standard atmosphere. */
    public static boolean isPositivelyPressurized(double roomPressureKPa) {
        return roomPressureKPa >= TARGET_PRESSURE_KPA;
    }

    /**
     * One step of the room's own certification, picking exactly one of the three real rates
     * §31 names. Clamped 0..1.
     */
    public static double stepCleanliness(double cleanliness, boolean sealed,
                                         boolean positivelyPressurized, double dtSeconds) {
        double rate;
        if (!sealed) {
            rate = -BREACH_DECAY_PER_SECOND;
        } else if (!positivelyPressurized) {
            rate = -IDLE_DECAY_PER_SECOND;
        } else {
            rate = RISE_PER_SECOND;
        }
        return Math.clamp(cleanliness + rate * dtSeconds, 0.0, 1.0);
    }

    /** Real moles the blower wants to push in {@code dtSeconds} at full throughput. */
    public static double airToInjectMol(double dtSeconds) {
        return AIR_INJECTION_MOL_PER_S * dtSeconds;
    }

    /** Filter remaining, 0..1 — what the gauge draws. */
    public static double chargeFraction(double capacityLeftMol) {
        return Math.clamp(capacityLeftMol / HEPA_CAPACITY_MOL, 0.0, 1.0);
    }

    /** True when the filter is spent enough that swapping it is the right move. */
    public static boolean needsSwap(double capacityLeftMol) {
        return capacityLeftMol <= SWAP_THRESHOLD_MOL;
    }

    private Cleanroom() {}
}
