package play.xponer.astronima.sim.power;

import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * Burning fuel for electricity, and mostly getting heat.
 *
 * <p><strong>A generator is a stove that also makes power.</strong> A small engine converts
 * about a quarter of its fuel's energy into work and loses the rest as heat — which is not a
 * balance decision but the reason cogeneration exists on Earth, and the reason a real habitat
 * treats its generator as a heater with a useful by-product.
 *
 * <p>That puts this tier's law at its most extreme. A cable gives up one per cent per block; an
 * engine gives up three quarters of everything. So <em>where</em> a generator goes is a decision
 * at ten times the stakes of where a cable goes — in a cold habitat it answers two problems at
 * once, and in a warm one it is the thing that cooks you.
 *
 * <h2>Why this is not {@code Flammability} with a wire on it (rule 8)</h2>
 * They ask different questions. {@code Flammability} asks <em>will this room explode</em>, which
 * depends on the mixture sitting between its limits. An engine asks <em>is there fuel and
 * oxidiser to draw</em>, because it mixes its own charge at the ratio it wants: a room far too
 * rich to ignite runs an engine perfectly well. That difference is real, and it is why an engine
 * is safe to operate in an atmosphere that would kill you.
 *
 * <p>Minecraft-free (rule 1): moles and seconds in, watts and moles out.
 */
public final class CombustionEngine {


    /**
     * Fraction of the fuel's energy that comes out as electricity.
     *
     * <p>A quarter, which is an ordinary small reciprocating engine or a modest turbine. Large
     * combined-cycle plant reaches nearly sixty per cent, and nothing on this asteroid is large
     * combined-cycle plant.
     */
    public static final double THERMAL_EFFICIENCY = 0.25;

    /** Lower heating value of methane, J/mol. Matches {@code Flammability}'s figure. */
    public static final double METHANE_J_PER_MOL = 890_000.0;

    /**
     * Fuel drawn at full output, mol/s.
     *
     * <p><strong>Derived from what it has to replace, not chosen.</strong> The generator exists
     * because solar makes nothing at night and a cell holds fifteen minutes, so the one thing it
     * must be able to do is run a machine — and the rate that does that falls straight out of
     * the heating value and the efficiency. Typed as a round number instead, the first draft
     * came to 244.75 W against a machine's 250 and was an ornament by five watts.
     */
    public static final double FUEL_MOL_PER_SECOND =
            HeatBalance.WORKED_MACHINE_W / (METHANE_J_PER_MOL * THERMAL_EFFICIENCY);

    /** Moles of oxygen burnt per mole of methane: CH₄ + 2 O₂ → CO₂ + 2 H₂O. */
    public static final double O2_PER_FUEL = 2.0;

    /** Moles of carbon dioxide returned per mole of methane. */
    public static final double CO2_PER_FUEL = 1.0;

    /** Moles of water vapour returned per mole of methane. */
    public static final double WATER_PER_FUEL = 2.0;

    /** What it makes at full output, W. */
    public static final double RATED_WATTS =
            FUEL_MOL_PER_SECOND * METHANE_J_PER_MOL * THERMAL_EFFICIENCY;

    /**
     * What one run of the engine did.
     *
     * @param fuelMol     methane consumed
     * @param oxygenMol   oxygen consumed with it
     * @param co2Mol      carbon dioxide returned
     * @param waterMol    water vapour returned
     * @param electricalJ energy delivered as electricity
     * @param heatJ       energy delivered as heat, which is most of it
     */
    public record Burn(double fuelMol, double oxygenMol, double co2Mol, double waterMol,
                       double electricalJ, double heatJ) {
        public boolean ran() {
            return fuelMol > 0;
        }
    }

    private static final Burn NOTHING = new Burn(0, 0, 0, 0, 0, 0);

    /**
     * Burns for {@code seconds}, taking no more than the room can actually supply.
     *
     * <p><strong>Throttles rather than refusing.</strong> An engine short of fuel or air runs
     * slowly; one that stopped dead at 99 % of its charge would make the difference between
     * enough methane and nearly enough a cliff, when the honest answer — and the legible one —
     * is a generator that is audibly struggling.
     *
     * @param availableFuelMol   methane in the room
     * @param availableOxygenMol oxygen in the room
     */
    public static Burn burn(double availableFuelMol, double availableOxygenMol, double seconds) {
        double wanted = FUEL_MOL_PER_SECOND * Math.max(seconds, 0);
        double byFuel = Math.min(wanted, Math.max(availableFuelMol, 0));
        // And by whichever of the two runs out first: an engine with fuel and no air is as
        // stopped as one with air and no fuel, and the player needs to be told which.
        double byAir = Math.max(availableOxygenMol, 0) / O2_PER_FUEL;
        double burnt = Math.min(byFuel, byAir);
        if (burnt <= 0) {
            return NOTHING;
        }
        double energy = burnt * METHANE_J_PER_MOL;
        return new Burn(burnt, burnt * O2_PER_FUEL, burnt * CO2_PER_FUEL, burnt * WATER_PER_FUEL,
                energy * THERMAL_EFFICIENCY, energy * (1 - THERMAL_EFFICIENCY));
    }

    /**
     * Why it is not running, when it is not.
     *
     * <p>Two faults that look identical from outside the machine and need opposite responses:
     * pipe more methane in, or get more air in. Rule 18 — the error path has to name which.
     */
    public enum Stall {
        RUNNING,
        NO_FUEL,
        NO_AIR,
        /**
         * Packed solid with its own soot.
         *
         * <p>Its own state rather than a flavour of the other two, because the fix is in a
         * different category: no fuel and no air are both plumbing, and this is a shovel. A
         * machine reporting "stopped" for all three sends the player to check pipes that are
         * perfectly fine (rule 18).
         */
        FOULED
    }

    public static Stall diagnose(double availableFuelMol, double availableOxygenMol) {
        if (availableFuelMol <= 0) {
            return Stall.NO_FUEL;
        }
        return availableOxygenMol < O2_PER_FUEL ? Stall.NO_AIR : Stall.RUNNING;
    }

    private CombustionEngine() {}
}
