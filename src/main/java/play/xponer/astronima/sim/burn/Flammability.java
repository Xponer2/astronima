package play.xponer.astronima.sim.burn;

import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

import java.util.List;
import java.util.Map;

/**
 * Combustion chemistry for room atmospheres.
 *
 * <p>A mixture ignites only when some fuel sits within its real flammability window
 * (LEL–UEL, by mole fraction) <em>and</em> enough oxygen is present (ppO2 > 12 kPa —
 * below that, flames starve regardless of fuel). Burning consumes fuel and O2
 * stoichiometrically, releases the real heats of combustion, and leaves CO2 and
 * water vapor — a fire in a sealed room smothers itself.
 */
public final class Flammability {
    /** Below this oxygen partial pressure nothing sustains a flame. */
    public static final double MIN_O2_KPA = 12.0;

    /** Rough molar heat capacity used for the post-burn temperature spike, J/(mol·K). */
    private static final double CV_J_PER_MOL_K = 21.0;
    /** Flame products can't push the gas past this (energy beyond it is structural). */
    private static final double MAX_FLAME_TEMP_K = 3000.0;

    /**
     * @param o2PerMol       moles of O2 consumed per mole of fuel
     * @param energyJPerMol  lower heating value, J per mole of fuel
     * @param products       product moles per mole of fuel
     */
    public record Fuel(Gas gas, double lelFraction, double uelFraction,
                       double o2PerMol, double energyJPerMol, Map<Gas, Double> products) {}

    public static final List<Fuel> FUELS = List.of(
            new Fuel(Gas.METHANE, 0.05, 0.15, 2.0, 890_000,
                    Map.of(Gas.CARBON_DIOXIDE, 1.0, Gas.WATER_VAPOR, 2.0)),
            new Fuel(Gas.HYDROGEN, 0.04, 0.75, 0.5, 286_000,
                    Map.of(Gas.WATER_VAPOR, 1.0)),
            new Fuel(Gas.CARBON_MONOXIDE, 0.125, 0.74, 0.5, 283_000,
                    Map.of(Gas.CARBON_DIOXIDE, 1.0)));

    /** Why a mixture will or won't take a spark — the analyzer's explanation. */
    public enum IgnitionStatus {
        /** Nothing combustible present. */
        NO_FUEL,
        /** Fuel is present but there is not enough oxygen to sustain a flame. */
        NO_OXYGEN,
        /** Fuel is too dilute to propagate a flame (below its lower explosive limit). */
        TOO_LEAN,
        /** Fuel is too concentrated — it displaces the oxygen it would need. */
        TOO_RICH,
        /** Fuel, oxygen, and mixing are all in range: a spark sets this off. */
        EXPLOSIVE
    }

    /**
     * Diagnoses the mixture. Order matters: with fuel present but no oxygen, the
     * missing oxygen is the fact the player needs, not the fuel concentration.
     */
    public static IgnitionStatus assess(RoomState room) {
        Fuel richest = null;
        double richestFraction = 0;
        for (Fuel fuel : FUELS) {
            double fraction = room.gases().fraction(fuel.gas());
            if (fraction > richestFraction) {
                richestFraction = fraction;
                richest = fuel;
            }
        }
        if (richest == null || richestFraction <= 0.001) {
            return IgnitionStatus.NO_FUEL;
        }
        if (room.partialPressureKPa(Gas.OXYGEN) <= MIN_O2_KPA) {
            return IgnitionStatus.NO_OXYGEN;
        }
        if (richestFraction < richest.lelFraction()) {
            return IgnitionStatus.TOO_LEAN;
        }
        if (richestFraction > richest.uelFraction()) {
            return IgnitionStatus.TOO_RICH;
        }
        return IgnitionStatus.EXPLOSIVE;
    }

    /** True when a spark or flame in this room would ignite it. */
    public static boolean ignitable(RoomState room) {
        if (room.partialPressureKPa(Gas.OXYGEN) <= MIN_O2_KPA) {
            return false;
        }
        for (Fuel fuel : FUELS) {
            double fraction = room.gases().fraction(fuel.gas());
            if (fraction >= fuel.lelFraction() && fraction <= fuel.uelFraction()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Burns every in-window fuel against the available oxygen, updating the room's
     * contents and temperature.
     *
     * @return total energy released in joules (0 when nothing ignited)
     */
    public static double burn(RoomState room) {
        if (!ignitable(room)) {
            return 0.0;
        }
        double energy = 0.0;
        for (Fuel fuel : FUELS) {
            double fraction = room.gases().fraction(fuel.gas());
            if (fraction < fuel.lelFraction() || fraction > fuel.uelFraction()) {
                continue;
            }
            double fuelMoles = room.gases().get(fuel.gas());
            double o2Moles = room.gases().get(Gas.OXYGEN);
            double burnable = Math.min(fuelMoles, o2Moles / fuel.o2PerMol());
            if (burnable <= 0) {
                continue;
            }
            room.removeGas(fuel.gas(), burnable);
            room.removeGas(Gas.OXYGEN, burnable * fuel.o2PerMol());
            for (Map.Entry<Gas, Double> product : fuel.products().entrySet()) {
                // Products appear at the current room temp; the explicit spike below
                // handles heating, keeping energy accounting in one place.
                room.addGasAt(product.getKey(), burnable * product.getValue(), room.temperatureK());
            }
            energy += burnable * fuel.energyJPerMol();
        }
        if (energy > 0) {
            double totalMoles = Math.max(1.0, room.gases().totalMoles());
            double spike = energy / (totalMoles * CV_J_PER_MOL_K);
            room.setTemperatureK(Math.min(MAX_FLAME_TEMP_K, room.temperatureK() + spike));
        }
        return energy;
    }

    private Flammability() {}
}
