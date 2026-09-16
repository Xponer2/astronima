package play.xponer.astronima.sim.circuit;

/**
 * How long a wire takes to get hot, and how long it takes to cool down again.
 *
 * <h2>Why a wire is not allowed to fail instantly</h2>
 * {@link Ampacity} answers <em>where does this wire end up</em>; it says nothing about <em>when</em>.
 * A model without that would burn a run the first tick a machine drew through it, which is wrong
 * twice over: physically, because metal has mass and takes minutes to reach a steady temperature,
 * and as a game, because a hazard that fires before the warning can be read is the thing rule 7
 * exists to forbid.
 *
 * <p>So a wire has thermal mass, and the numbers are the metal's own. Iron at the standard gauge
 * carries about 14 J/K per metre and sheds a few hundredths of a watt per kelvin in vacuum, which
 * puts its time constant in the <strong>minutes</strong> — a long, obvious, glowing warning before
 * anything is lost. Thin wire has less mass and less surface, and it gets there sooner, which is
 * the correct relationship rather than a difficulty setting.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class ThermalMass {

    /**
     * What a metre of this conductor stores, in joules per kelvin.
     *
     * <p>Density times area times specific heat. Nothing here is tuned: it is the mass of a metre
     * of the metal and what that metal costs to warm.
     */
    public static double heatCapacityPerMetre(Conductor conductor) {
        double kgPerMetre = conductor.material().densityKgPerM3() * conductor.crossSectionM2();
        return kgPerMetre * conductor.material().specificHeatJPerKgK();
    }

    /**
     * How readily a metre sheds heat near this temperature, in watts per kelvin.
     *
     * <p>Taken as a slope rather than assumed constant, because radiation goes as T⁴ and a wire
     * near its limit sheds far more per kelvin than a cold one — which is exactly why an
     * overheating wire settles rather than running away.
     */
    public static double coolingWattsPerKelvin(Conductor conductor, double wireK, double ambientK,
                                               double airFraction) {
        double step = 1.0;
        double above = Ampacity.coolingWattsPerMetre(conductor, wireK + step, ambientK,
                airFraction);
        double below = Ampacity.coolingWattsPerMetre(conductor, wireK, ambientK, airFraction);
        return Math.max((above - below) / step, 1e-9);
    }

    /**
     * The time constant at this temperature, in seconds — mass over dissipation.
     *
     * <p>One of these is about 63 % of the way to the steady value, three of them is 95 %. For
     * iron at 4 mm² in vacuum that is several minutes, which is what makes the glow a warning
     * rather than a death rattle.
     */
    public static double timeConstantSeconds(Conductor conductor, double wireK, double ambientK,
                                             double airFraction) {
        return heatCapacityPerMetre(conductor)
                / coolingWattsPerKelvin(conductor, wireK, ambientK, airFraction);
    }

    /**
     * Where the wire is after {@code seconds}, heading toward {@code steadyK}.
     *
     * <p>First-order relaxation, which is what a lumped thermal mass does: the approach is
     * exponential, so the wire never overshoots and never quite arrives. Using the exponential
     * rather than a linear step matters because the caller's interval varies — a generator ticks
     * every twentieth of a second and the wire ticker every quarter, and a linear step would
     * heat the same wire at different rates depending on who asked.
     */
    public static double after(double currentK, double steadyK, double tauSeconds,
                               double seconds) {
        if (tauSeconds <= 0 || seconds <= 0) {
            return currentK;
        }
        double remaining = Math.exp(-seconds / tauSeconds);
        return steadyK + (currentK - steadyK) * remaining;
    }

    private ThermalMass() {}
}
