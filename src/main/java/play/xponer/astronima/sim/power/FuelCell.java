package play.xponer.astronima.sim.power;

import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * Hydrogen and oxygen straight to electricity, with water as the only exhaust.
 *
 * <p>The opposite of {@link CombustionEngine} in every way that matters, which is why there are
 * two of them rather than one with a fuel switch (rule 8). An engine burns, so it is limited by
 * the Carnot cycle and throws away three quarters of its fuel as heat. <strong>A fuel cell does
 * not burn</strong> — it takes the reaction apart across a membrane and collects the electrons
 * on the way, so it is not a heat engine and not bound by that limit.
 *
 * <h2>The number that matters is not efficiency</h2>
 * It is <strong>oxygen per megajoule</strong>. Methane wants 9.0 mol of O₂ per MJ of
 * electricity; hydrogen wants <strong>2.9</strong>. After the oxygen tier that is the currency
 * that hurts, so the fuel cell is not "better power" — it is cheaper power in the only unit the
 * player is genuinely short of.
 *
 * <p>And its exhaust is water, which the dehumidifier already bottles, rather than carbon
 * dioxide, which the scrubber has to eat with a finite cartridge.
 *
 * <p>Minecraft-free (rule 1): moles and seconds in, watts and moles out.
 */
public final class FuelCell {

    /**
     * Fraction of the fuel's energy that comes out as electricity.
     *
     * <p>Sixty per cent, which is an ordinary PEM cell and about two and a half times a small
     * engine. <strong>It is high because a fuel cell is not a heat engine</strong>: there is no
     * combustion, so there is no Carnot ceiling to squeeze it under. That is the physical reason
     * for the number rather than a tier bonus.
     */
    public static final double EFFICIENCY = 0.60;

    /** Lower heating value of hydrogen, J/mol. Matches {@code Flammability}'s figure. */
    public static final double HYDROGEN_J_PER_MOL = 286_000.0;

    /** Moles of oxygen per mole of hydrogen: H₂ + ½O₂ → H₂O. */
    public static final double O2_PER_FUEL = 0.5;

    /** Moles of water vapour returned per mole of hydrogen. */
    public static final double WATER_PER_FUEL = 1.0;

    /**
     * Fuel drawn at full output, mol/s — derived from the machine it has to run.
     *
     * <p>Same reasoning as the engine's: the one thing a power source must be able to do is run
     * a machine, and the rate that does it falls out of the heating value and the efficiency
     * rather than being chosen. Typing a round number instead is how the generator's first draft
     * came out five watts short of useful.
     */
    public static final double FUEL_MOL_PER_SECOND =
            HeatBalance.WORKED_MACHINE_W / (HYDROGEN_J_PER_MOL * EFFICIENCY);

    /** What it makes at full output, W. */
    public static final double RATED_WATTS =
            FUEL_MOL_PER_SECOND * HYDROGEN_J_PER_MOL * EFFICIENCY;

    /** What one run of the cell did. */
    public record Run(double fuelMol, double oxygenMol, double waterMol,
                      double electricalJ, double heatJ) {
        public boolean ran() {
            return fuelMol > 0;
        }
    }

    private static final Run NOTHING = new Run(0, 0, 0, 0, 0);

    /**
     * Runs for {@code seconds}, taking no more than the vessels can supply.
     *
     * <p>Throttles rather than refusing, for the same reason the engine does: a cell that
     * stopped dead at the last mole would make the difference between enough hydrogen and
     * nearly enough a cliff, when the legible answer is a cell delivering less.
     */
    public static Run run(double availableFuelMol, double availableOxygenMol, double seconds) {
        double wanted = FUEL_MOL_PER_SECOND * Math.max(seconds, 0);
        double byFuel = Math.min(wanted, Math.max(availableFuelMol, 0));
        double byOxygen = Math.max(availableOxygenMol, 0) / O2_PER_FUEL;
        double used = Math.min(byFuel, byOxygen);
        if (used <= 0) {
            return NOTHING;
        }
        double energy = used * HYDROGEN_J_PER_MOL;
        return new Run(used, used * O2_PER_FUEL, used * WATER_PER_FUEL,
                energy * EFFICIENCY, energy * (1 - EFFICIENCY));
    }

    /**
     * Why it is not running, when it is not.
     *
     * <p>Out of hydrogen and out of oxygen are <em>completely different journeys</em> — one is a
     * trip into the deep rock for a rare pocket, the other is a valve on a tank you already
     * have. Rule 18: a block that says only "stopped" sends the player on the wrong one.
     */
    public enum Stall { RUNNING, NO_HYDROGEN, NO_OXYGEN }

    public static Stall diagnose(double availableFuelMol, double availableOxygenMol) {
        if (availableFuelMol <= 0) {
            return Stall.NO_HYDROGEN;
        }
        return availableOxygenMol < O2_PER_FUEL ? Stall.NO_OXYGEN : Stall.RUNNING;
    }

    /**
     * Moles of oxygen spent per megajoule of electricity — the comparison that decides which
     * generator you want.
     */
    public static double oxygenPerMegajoule(double o2PerFuel, double jPerMol, double efficiency) {
        return o2PerFuel / (jPerMol * efficiency) * 1_000_000.0;
    }

    private FuelCell() {}
}
