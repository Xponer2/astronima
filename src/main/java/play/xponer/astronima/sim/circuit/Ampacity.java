package play.xponer.astronima.sim.circuit;

import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * How much current a wire may carry — which is a question about <em>cooling</em>, not about wire.
 *
 * <p>This is the asteroid's own contribution to electrical engineering, and it is the reason
 * this tier is not a reskin of every other mod's cable. <strong>A current rating is the current
 * at which heat in equals heat out.</strong> Every ampacity table on Earth is written for a wire
 * sitting in air, where most of "heat out" is convection. There is no air here. A conductor in
 * vacuum can only radiate, so the same wire carrying the same current runs far hotter — and its
 * safe current is about <strong>sixty per cent</strong> of the handbook figure.
 *
 * <p>That is the identical law {@code design/power.md} is built on — <em>"in vacuum there is no
 * convection, which is why the ISS's hard problem is cooling rather than power"</em> — applied
 * one level down, to the wire itself. It costs the player nothing new to learn and it inverts
 * the decision a third time:
 *
 * <table>
 *   <tr><th>Where the run is</th><th>What the heat does</th><th>So</th></tr>
 *   <tr><td>Inside a pressurised habitat</td>
 *       <td>air carries it off, <em>and it lands in the room</em></td>
 *       <td>high ampacity; an overloaded run is a <em>heater</em></td></tr>
 *   <tr><td>Outside, or in an evacuated chamber</td>
 *       <td>radiates to a 2.7 K sky and warms nothing</td>
 *       <td>~60 % of the ampacity; overload melts the insulation instead</td></tr>
 * </table>
 *
 * <p>A run through the workshop and the identical run across the surface are different
 * components. No other mod can say that, because no other mod knows where the air is — and this
 * one has known since v0.2.
 *
 * <p>Minecraft-free (rule 1); it borrows {@link HeatBalance#SIGMA} rather than keeping a second
 * copy of the Stefan-Boltzmann constant, because two copies of a physical constant is exactly
 * how a habitat and a wire end up disagreeing about radiation.
 */
public final class Ampacity {

    /**
     * The temperature the insulation gives up at, K.
     *
     * <p>400 K — about 127 °C — is an ordinary rating for a decent polymer insulation, and it is
     * the wire's real limit rather than the metal's: the copper in a burnt-out cable is almost
     * always fine, and the thing that failed was the plastic around it. Conductors that run bare
     * are limited by their own melting point instead ({@link ConductorMaterial#meltingPointK}).
     */
    public static final double INSULATION_LIMIT_K = 400.0;

    /**
     * How well a wire radiates, 0..1.
     *
     * <p>0.8 for a dull, dark, insulated surface. A bright bare metal would be nearer 0.1 and
     * would therefore be <em>worse</em> in vacuum, which is genuinely counter-intuitive and
     * genuinely true: shiny things cannot dump heat.
     */
    public static final double EMISSIVITY = 0.8;

    /**
     * Free-convection coefficient for a thin wire in still cabin air, W/m²K.
     *
     * <p>The one engineering approximation in this class, and it is named rather than hidden.
     * Real free convection depends on diameter and orientation through a Nusselt correlation;
     * for a millimetre-scale wire in still air the answer lands in the region of 10, and the
     * mechanic here needs the <em>ratio</em> between air and vacuum to be right rather than the
     * third significant figure of either.
     */
    public static final double AIR_CONVECTION_W_PER_M2K = 10.0;

    /**
     * Watts per metre a run can radiate away at a given temperature.
     *
     * <p>Stefan-Boltzmann, fourth power, against the surroundings. In hard vacuum this is the
     * whole of the cooling budget.
     */
    public static double radiatedWattsPerMetre(Conductor conductor, double wireK,
                                               double ambientK) {
        double hot = Math.max(wireK, 0);
        double cold = Math.max(ambientK, 0);
        return EMISSIVITY * HeatBalance.SIGMA * conductor.surfacePerMetreM2()
                * (Math.pow(hot, 4) - Math.pow(cold, 4));
    }

    /** Watts per metre carried off by air, which in vacuum is exactly zero. */
    public static double convectedWattsPerMetre(Conductor conductor, double wireK,
                                                double ambientK, double airFraction) {
        double air = Math.clamp(airFraction, 0.0, 1.0);
        return AIR_CONVECTION_W_PER_M2K * conductor.surfacePerMetreM2()
                * Math.max(wireK - ambientK, 0) * air;
    }

    /**
     * Everything the run can shed, W/m.
     *
     * @param airFraction how much atmosphere is around it, 0 in vacuum and 1 at cabin pressure
     */
    public static double coolingWattsPerMetre(Conductor conductor, double wireK, double ambientK,
                                              double airFraction) {
        return radiatedWattsPerMetre(conductor, wireK, ambientK)
                + convectedWattsPerMetre(conductor, wireK, ambientK, airFraction);
    }

    /**
     * The most current this run may carry before it cooks its own insulation, A.
     *
     * <p>Solved where {@code I²R = cooling}, <strong>with the resistance taken at the limit
     * temperature rather than at room temperature</strong>. That matters more than it looks: a
     * wire sitting at its limit is half again as resistive as a cold one, so using the cold
     * figure overstates the rating by around thirty per cent. The wire is hot — price it hot.
     */
    public static double limitAmps(Conductor conductor, double ambientK, double airFraction) {
        return limitAmps(conductor, ambientK, airFraction, INSULATION_LIMIT_K);
    }

    /** The same, for a run whose limit is something other than its insulation. */
    public static double limitAmps(Conductor conductor, double ambientK, double airFraction,
                                   double limitK) {
        if (limitK <= ambientK) {
            // Surroundings already at or above the limit: nothing can be carried, and saying
            // so is better than returning a number that pretends otherwise.
            return 0;
        }
        double cooling = coolingWattsPerMetre(conductor, limitK, ambientK, airFraction);
        double resistancePerMetre = conductor.resistancePerMetre(limitK);
        if (resistancePerMetre <= 0) {
            return Double.POSITIVE_INFINITY; // a superconductor has no limit of this kind
        }
        return Math.sqrt(Math.max(cooling, 0) / resistancePerMetre);
    }

    /**
     * The steady temperature a run settles at carrying this current, K.
     *
     * <p>What the meter shows and what makes the run glow. Solved by bisection because the
     * balance {@code I²R(T) = cooling(T)} has a fourth power on one side and a rising resistance
     * on the other, so there is no closed form — and the rising resistance is the runaway, which
     * is exactly the part that must not be approximated away.
     *
     * <p>Returns the melting point when no equilibrium exists below it: the honest answer to
     * "how hot does this get" for a wire that is destroying itself is *hot enough* (rule 18 —
     * the failure must be the loudest branch, not a silently clamped number).
     */
    public static double steadyTemperatureK(Conductor conductor, double amps, double ambientK,
                                            double airFraction) {
        double ceiling = conductor.material().meltingPointK();
        double low = ambientK;
        double high = ceiling;
        if (netWattsPerMetre(conductor, amps, high, ambientK, airFraction) > 0) {
            return ceiling; // cannot balance even at melting: it is on its way to failing
        }
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (low + high);
            if (netWattsPerMetre(conductor, amps, mid, ambientK, airFraction) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return 0.5 * (low + high);
    }

    /** Heat made minus heat shed, W/m — zero at the steady temperature. */
    private static double netWattsPerMetre(Conductor conductor, double amps, double wireK,
                                           double ambientK, double airFraction) {
        double made = amps * amps * conductor.resistancePerMetre(wireK);
        return made - coolingWattsPerMetre(conductor, wireK, ambientK, airFraction);
    }

    private Ampacity() {}
}
