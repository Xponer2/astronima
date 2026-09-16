package play.xponer.astronima.sim;

/**
 * The physical state of one enclosed volume: its gas contents and temperature.
 *
 * <p>Owns the thermodynamics that {@link GasMixture} deliberately doesn't: incoming gas
 * mixes temperatures mole-weighted (approximating equal molar heat capacity for all
 * species — within ~25 % of reality for the gases we track, and stable to simulate).
 */
public final class RoomState {
    private final long id;
    private int volumeBlocks;
    private final GasMixture gases;
    private double temperatureK;

    public RoomState(long id, int volumeBlocks, GasMixture gases, double temperatureK) {
        if (volumeBlocks <= 0) {
            throw new IllegalArgumentException("room volume must be positive: " + volumeBlocks);
        }
        if (temperatureK <= 0) {
            throw new IllegalArgumentException("temperature must be positive: " + temperatureK);
        }
        this.id = id;
        this.volumeBlocks = volumeBlocks;
        this.gases = gases;
        this.temperatureK = temperatureK;
    }

    public long id() {
        return id;
    }

    public int volumeBlocks() {
        return volumeBlocks;
    }

    /** 1 block = 1 m³. */
    public double volumeM3() {
        return volumeBlocks;
    }

    public void setVolumeBlocks(int volumeBlocks) {
        if (volumeBlocks <= 0) {
            throw new IllegalArgumentException("room volume must be positive: " + volumeBlocks);
        }
        this.volumeBlocks = volumeBlocks;
    }

    public GasMixture gases() {
        return gases;
    }

    public double temperatureK() {
        return temperatureK;
    }

    public void setTemperatureK(double temperatureK) {
        if (temperatureK <= 0) {
            throw new IllegalArgumentException("temperature must be positive: " + temperatureK);
        }
        this.temperatureK = temperatureK;
    }

    public double pressureKPa() {
        return gases.pressureKPa(volumeM3(), temperatureK);
    }

    public double partialPressureKPa(Gas gas) {
        return gases.partialPressureKPa(gas, volumeM3(), temperatureK);
    }

    /**
     * Adds gas that arrives at {@code incomingTempK}, mixing room temperature
     * mole-weighted. Adding to a near-empty room adopts the incoming temperature.
     */
    public void addGasAt(Gas gas, double amount, double incomingTempK) {
        if (amount <= 0) {
            return;
        }
        double nOld = gases.totalMoles();
        this.temperatureK = mixedTemperature(nOld, temperatureK, amount, incomingTempK);
        gases.add(gas, amount);
    }

    /** Adds a whole mixture arriving at {@code incomingTempK} (same thermal rule as {@link #addGasAt}). */
    public void addMixtureAt(GasMixture incoming, double incomingTempK) {
        double nIn = incoming.totalMoles();
        if (nIn <= 0) {
            return;
        }
        double nOld = gases.totalMoles();
        this.temperatureK = mixedTemperature(nOld, temperatureK, nIn, incomingTempK);
        gases.addAll(incoming);
    }

    /** Removes gas without changing temperature (outflow carries its heat with it). */
    public double removeGas(Gas gas, double amount) {
        return gases.remove(gas, amount);
    }

    private static double mixedTemperature(double nA, double tA, double nB, double tB) {
        double total = nA + nB;
        return total <= 0 ? tA : (nA * tA + nB * tB) / total;
    }
}
