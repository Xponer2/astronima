package play.xponer.astronima.sim;

/**
 * Gas movement between volumes.
 *
 * <p>Flow toward equilibrium is modeled per species as exponential decay with a
 * conductance constant (fraction of the remaining imbalance closed per second) —
 * unconditionally stable at any tick rate and mass-conserving by construction.
 */
public final class GasFlow {
    /**
     * Exchanges gas between two rooms through an opening until partial pressures
     * equalize. Each species flows down its own partial-pressure gradient, so two
     * rooms at equal total pressure but different composition still mix (diffusion).
     *
     * @param conductancePerSecond fraction of the remaining imbalance closed per second;
     *                             large values (≥ 5) behave like an open doorway, small
     *                             values (~0.02) like leakage around an unsealed door
     */
    public static void equalize(RoomState a, RoomState b, double conductancePerSecond, double dtSeconds) {
        double f = 1.0 - Math.exp(-conductancePerSecond * dtSeconds);
        // Capacity: moles held per kPa of pressure at the room's temperature (C = V/RT).
        double capA = a.volumeM3() * 1000.0 / (GasMixture.R * a.temperatureK());
        double capB = b.volumeM3() * 1000.0 / (GasMixture.R * b.temperatureK());
        double capTotal = capA + capB;
        for (Gas gas : Gas.values()) {
            double nA = a.gases().get(gas);
            double nB = b.gases().get(gas);
            double total = nA + nB;
            if (total <= 0) {
                continue;
            }
            double nAEquilibrium = total * capA / capTotal;
            double delta = (nAEquilibrium - nA) * f;
            if (delta > 0) {
                double moved = b.removeGas(gas, delta);
                a.addGasAt(gas, moved, b.temperatureK());
            } else if (delta < 0) {
                double moved = a.removeGas(gas, -delta);
                b.addGasAt(gas, moved, a.temperatureK());
            }
        }
    }

    /**
     * Bleeds a room into hard vacuum (breach, open outer door, deliberate vent).
     * The escaping gas is simply lost to space.
     *
     * @return the mixture that escaped this step
     */
    public static GasMixture ventToVacuum(RoomState room, double conductancePerSecond, double dtSeconds) {
        double f = 1.0 - Math.exp(-conductancePerSecond * dtSeconds);
        return room.gases().extractFraction(f);
    }

    private GasFlow() {}
}
