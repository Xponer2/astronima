package play.xponer.astronima.sim;

import java.util.EnumMap;

/**
 * A quantity of mixed gas, tracked as moles per species.
 *
 * <p>All pressure math is the ideal gas law {@code P = nRT/V}. A mixture knows nothing
 * about where it is; volume and temperature are supplied by the container (see
 * {@link RoomState}). Convention throughout the sim: 1 block = 1 m³, pressure in kPa,
 * temperature in kelvin.
 */
public final class GasMixture {
    /** Universal gas constant, J/(mol·K). */
    public static final double R = 8.314462618;

    /** Standard sea-level pressure, kPa. */
    public static final double EARTH_PRESSURE_KPA = 101.325;

    /** Below this many moles a species is treated as absent (avoids float dust). */
    private static final double EPSILON_MOL = 1.0e-9;

    private final EnumMap<Gas, Double> moles = new EnumMap<>(Gas.class);

    /** Earth-like air (21 % O2, 79 % N2) filling {@code volumeM3} at the given conditions. */
    public static GasMixture earthAir(double volumeM3, double pressureKPa, double temperatureK) {
        return earthAirMoles(totalMolesFor(pressureKPa, volumeM3, temperatureK));
    }

    /**
     * Earth-like air (21 % O2, 79 % N2) totalling {@code totalMol} moles — the same real split
     * {@link #earthAir} uses, factored out so a machine that injects a specific molar amount
     * (rather than filling a volume to a pressure) shares the one real composition rather than
     * re-deriving it (design/halogens.md §30, the cleanroom controller's own blower).
     */
    public static GasMixture earthAirMoles(double totalMol) {
        GasMixture mix = new GasMixture();
        mix.add(Gas.OXYGEN, Math.max(0.0, totalMol) * 0.21);
        mix.add(Gas.NITROGEN, Math.max(0.0, totalMol) * 0.79);
        return mix;
    }

    /** Moles of ideal gas that exert {@code pressureKPa} in {@code volumeM3} at {@code temperatureK}. */
    public static double totalMolesFor(double pressureKPa, double volumeM3, double temperatureK) {
        return pressureKPa * 1000.0 * volumeM3 / (R * temperatureK);
    }

    public double get(Gas gas) {
        return moles.getOrDefault(gas, 0.0);
    }

    public void add(Gas gas, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative gas amount: " + amount + " mol of " + gas);
        }
        if (amount == 0) {
            return;
        }
        // Every positive amount is kept, however small — dropping "dust" here would
        // silently destroy mass during near-equilibrium exchange.
        moles.merge(gas, amount, Double::sum);
    }

    /**
     * Removes up to {@code amount} moles of one species.
     *
     * @return the amount actually removed (less than requested if the species runs out)
     */
    public double remove(Gas gas, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative gas amount: " + amount + " mol of " + gas);
        }
        double present = get(gas);
        double removed = Math.min(present, amount);
        double left = present - removed;
        if (left < EPSILON_MOL) {
            moles.remove(gas);
            removed = present;
        } else {
            moles.put(gas, left);
        }
        return removed;
    }

    /** Removes the same fraction of every species; returns the extracted gas. */
    public GasMixture extractFraction(double fraction) {
        if (fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("fraction out of [0,1]: " + fraction);
        }
        GasMixture out = new GasMixture();
        for (Gas gas : Gas.values()) {
            double amount = get(gas) * fraction;
            if (amount >= EPSILON_MOL) {
                remove(gas, amount);
                out.add(gas, amount);
            }
        }
        return out;
    }

    public void addAll(GasMixture other) {
        for (Gas gas : Gas.values()) {
            add(gas, other.get(gas));
        }
    }

    public double totalMoles() {
        double sum = 0;
        for (double v : moles.values()) {
            sum += v;
        }
        return sum;
    }

    public boolean isEmpty() {
        return totalMoles() < EPSILON_MOL;
    }

    public double pressureKPa(double volumeM3, double temperatureK) {
        return totalMoles() * R * temperatureK / volumeM3 / 1000.0;
    }

    public double partialPressureKPa(Gas gas, double volumeM3, double temperatureK) {
        return get(gas) * R * temperatureK / volumeM3 / 1000.0;
    }

    /** Mole fraction of {@code gas} in this mixture, 0 when empty. */
    public double fraction(Gas gas) {
        double total = totalMoles();
        return total < EPSILON_MOL ? 0 : get(gas) / total;
    }

    public GasMixture copy() {
        GasMixture out = new GasMixture();
        out.moles.putAll(moles);
        return out;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GasMixture{");
        moles.forEach((gas, n) -> sb.append(gas.symbol()).append('=').append(String.format("%.3f", n)).append("mol "));
        return sb.append('}').toString();
    }
}
