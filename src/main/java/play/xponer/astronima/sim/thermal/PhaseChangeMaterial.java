package play.xponer.astronima.sim.thermal;

/**
 * How a block of paraffin wax answers a room's own temperature swing: not by warming or cooling
 * with it, but by melting or freezing at a fixed point while the swing is absorbed as latent heat
 * instead. See {@code design/phase-change-blocks.md}.
 *
 * <h2>Why this is a correction, not a second heat balance</h2>
 * {@link HeatBalance} already decides what a room's temperature becomes this tick. This class
 * does not re-derive that — it takes the room's own already-stepped temperature and, if that
 * temperature crossed the melt point this tick, un-crosses it: the energy that pushed it past is
 * exactly the energy {@link #step} converts into {@code meltFraction} instead, using the room's
 * own real capacity to translate a temperature difference back into joules. Nothing here invents
 * a second, competing heat model.
 *
 * <p>Minecraft-free (rule 1): temperatures, a fraction and a capacity in; the same two, corrected,
 * out.
 */
public final class PhaseChangeMaterial {

    /**
     * Fully refined paraffin wax's real melting point, K (58 °C) — the commonly cited figure for
     * that grade; bulk paraffin generally melts 46-68 °C depending on refinement.
     */
    public static final double MELT_POINT_K = 331.15;

    /** Paraffin's real heat of fusion, J/kg — the standard cited bulk figure, not tuned. */
    public static final double LATENT_HEAT_J_PER_KG = 200_000.0;

    /**
     * One blended specific heat for solid and liquid paraffin, J/(kg*K) — real solid and liquid
     * Cp differ by under 10%, and nothing this block does would read any differently either way,
     * the same kind of cut this mod already names for Sabatier's token catalyst.
     */
    public static final double SPECIFIC_HEAT_J_PER_KG_K = 2100.0;

    /** Paraffin's real bulk density, kg/m^3 — stated for scale, not load-bearing on any formula
     *  below. */
    public static final double DENSITY_KG_PER_M3 = 900.0;

    /**
     * A stated assumption, not a measured in-game quantity — the same honesty {@code
     * HeatPipeTransfer.CROSS_SECTION_M2} already uses for its own bore. About 2.2 litres of wax
     * in a sealed cartridge, chosen for the melt timescale it produces against one worked
     * machine's own heat (design/phase-change-blocks.md §5), not for any in-game measurement.
     */
    public static final double MASS_PER_BLOCK_KG = 2.0;

    /** The room's temperature and this block's own melt state, both corrected for one tick. */
    public record Step(double temperatureK, double meltFraction) {}

    /**
     * One tick's correction.
     *
     * @param temperatureK  the room's own temperature, already stepped by {@link HeatBalance}
     *                      this tick
     * @param meltFraction  0 (fully solid) .. 1 (fully liquid), before this tick
     * @param massKg        how much paraffin this block holds
     * @param capacityJPerK the room's own real heat capacity, the same figure {@link HeatBalance}
     *                      used to produce {@code temperatureK} in the first place
     */
    public static Step step(double temperatureK, double meltFraction, double massKg,
                            double capacityJPerK) {
        double latentCapacityJ = massKg * LATENT_HEAT_J_PER_KG;
        if (temperatureK > MELT_POINT_K && meltFraction < 1.0) {
            double overshootJ = (temperatureK - MELT_POINT_K) * capacityJPerK;
            double neededToFullyMeltJ = (1.0 - meltFraction) * latentCapacityJ;
            if (overshootJ <= neededToFullyMeltJ) {
                return new Step(MELT_POINT_K, meltFraction + overshootJ / latentCapacityJ);
            }
            double leftoverJ = overshootJ - neededToFullyMeltJ;
            return new Step(MELT_POINT_K + leftoverJ / capacityJPerK, 1.0);
        }
        if (temperatureK < MELT_POINT_K && meltFraction > 0.0) {
            double deficitJ = (MELT_POINT_K - temperatureK) * capacityJPerK;
            double neededToFullyFreezeJ = meltFraction * latentCapacityJ;
            if (deficitJ <= neededToFullyFreezeJ) {
                return new Step(MELT_POINT_K, meltFraction - deficitJ / latentCapacityJ);
            }
            double leftoverJ = deficitJ - neededToFullyFreezeJ;
            return new Step(MELT_POINT_K - leftoverJ / capacityJPerK, 0.0);
        }
        return new Step(temperatureK, meltFraction);
    }

    private PhaseChangeMaterial() {}
}
