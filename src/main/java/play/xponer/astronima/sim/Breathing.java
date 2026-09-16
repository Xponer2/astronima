package play.xponer.astronima.sim;

/**
 * Human gas exchange with the surrounding room.
 *
 * <p>Rates come from NASA's life-support baseline (BVAD): an active crew member
 * consumes ~0.84 kg (≈26 mol) of O2 per day. A gameplay multiplier compresses the
 * timeline (see {@code Config.METABOLISM_SCALE}); the model itself stays real, so
 * doubling room volume really does double survival time.
 */
public final class Breathing {
    /** Real O2 consumption of one active human: ~26.3 mol/day. */
    public static final double O2_CONSUMPTION_MOL_PER_S = 3.04e-4;

    /** Respiratory quotient: moles of CO2 exhaled per mole of O2 consumed (mixed diet ≈ 0.85). */
    public static final double RESPIRATORY_QUOTIENT = 0.85;

    /** Exhaled breath is water-saturated: ~0.36 kg/day of vapor ≈ 0.76 mol H2O per mol O2. */
    public static final double EXHALED_WATER_PER_O2 = 0.76;

    /**
     * One breathing step against {@code room}.
     *
     * @return moles of O2 actually obtained; the caller compares this against demand to
     *         detect that the room ran out mid-step (treat the shortfall as suffocation)
     */
    public static double breathe(RoomState room, double dtSeconds, double metabolismScale) {
        double demand = O2_CONSUMPTION_MOL_PER_S * metabolismScale * dtSeconds;
        double consumed = room.removeGas(Gas.OXYGEN, demand);
        if (consumed > 0) {
            // Exhaled gas leaves at body temperature (310 K).
            room.addGasAt(Gas.CARBON_DIOXIDE, consumed * RESPIRATORY_QUOTIENT, 310.0);
            room.addGasAt(Gas.WATER_VAPOR, consumed * EXHALED_WATER_PER_O2, 310.0);
        }
        return consumed;
    }

    private Breathing() {}
}
