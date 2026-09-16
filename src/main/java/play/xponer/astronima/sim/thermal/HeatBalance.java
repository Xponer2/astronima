package play.xponer.astronima.sim.thermal;

import play.xponer.astronima.sim.power.PowerBalance;

/**
 * How a sealed volume loses heat when the only way out is to radiate, and gains it back
 * from the one thing outside a habitat that puts real watts in for free: the sun.
 *
 * <p>There is no convection to space and nothing to conduct into. A habitat on a rock in
 * vacuum can only throw heat away as light, and radiated power goes as the <em>fourth
 * power</em> of temperature against a sky at 2.7 K — so a warm hull sheds heat fast and a
 * cold one barely at all. That is why spacecraft equilibrate cold rather than freezing
 * solid, and it is the whole reason this is a curve worth modelling rather than a constant
 * drain.
 *
 * <h2>What a surface can see is most of the answer</h2>
 * A face looking at open sky radiates freely. A face pressed against rock a metre away
 * exchanges with the rock and nets almost nothing. This is the <em>view factor</em>, it is
 * the least intuitive thing about spacecraft thermal design, and here it is the mechanic:
 * <strong>burying a habitat keeps it warm and exposing it makes it cold</strong>, which gives
 * the shape of a base a second meaning on top of the one it already had.
 *
 * <p>Minecraft-free (rule 1). Nothing in here knows what a block is; it takes an area, a
 * temperature and a number between 0 and 1 for how much of that area can see the sky.
 */
public final class HeatBalance {

    /** Stefan–Boltzmann constant, W/m²K⁴. */
    public static final double SIGMA = 5.670374419e-8;

    /**
     * The sky an asteroid habitat radiates against, in kelvin.
     *
     * <p>The cosmic microwave background. Nothing can cool below it, which is the floor the
     * model needs so a room does not chase absolute zero — and it is a real floor, not a
     * clamp invented to keep the arithmetic tidy.
     */
    public static final double SKY_K = 2.7;

    /**
     * How well a bare hull plate radiates, 0..1.
     *
     * <p>Bare metal is a poor emitter — polished aluminium is around 0.05, and that is
     * exactly why spacecraft radiators are painted and why insulation is foil. A machined
     * hull sits well below a blackbody, which is the difference between a habitat that
     * equilibrates cold and one that freezes while you watch.
     */
    public static final double HULL_EMISSIVITY = 0.35;

    /**
     * Heat capacity of the shell, J/K per square metre of hull.
     *
     * <p>Three millimetres of steel: about 23 kg per square metre at roughly 500 J/kg·K.
     *
     * <p><strong>Per square metre, not per block of volume</strong>, and the first version
     * of this got it wrong. The gas is not what cools — a habitat's shell holds hundreds of
     * times the thermal mass of the air inside it — so the thermal mass is the <em>hull</em>,
     * and a hull is a surface. Scaling it with volume made a large habitat absurdly slow to
     * cool and gave a 125-block room a three-hour time constant, which is not a mechanic, it
     * is a rounding error with a temperature attached.
     *
     * <p>The consequence of getting it right is worth stating: loss and capacity now both
     * scale with area, so <em>the rate of cooling barely depends on the size of the base</em>.
     * A cupboard and a hangar cool at about the same speed. That is correct, it is not
     * obvious, and it means the answer to cold is never "build bigger".
     */
    public static final double SHELL_CAPACITY_J_PER_K_PER_M2 = 11_700.0;

    /**
     * Exposed surface area of a room, m², from its volume in blocks.
     *
     * <p>A cube's surface grows as the two-thirds power of its volume, which is the reason
     * big habitats are thermally easier than small ones per unit of space — the same
     * square-cube law that makes a mouse cold and an elephant hot. Six faces of a unit cube
     * is the coefficient.
     */
    public static double hullAreaM2(double volumeBlocks) {
        return volumeBlocks <= 0 ? 0 : 6.0 * Math.cbrt(volumeBlocks * volumeBlocks);
    }

    /**
     * Heat radiated away, in watts.
     *
     * @param temperatureK  the shell's temperature
     * @param areaM2        its total outer area
     * @param skyFraction   how much of that area can see open sky, 0 (buried) .. 1 (exposed)
     */
    public static double radiatedWatts(double temperatureK, double areaM2, double skyFraction) {
        double seeing = Math.clamp(skyFraction, 0.0, 1.0);
        if (areaM2 <= 0 || seeing <= 0) {
            return 0;
        }
        double t = Math.max(temperatureK, SKY_K);
        double t4 = t * t * t * t;
        double sky4 = SKY_K * SKY_K * SKY_K * SKY_K;
        return HULL_EMISSIVITY * SIGMA * areaM2 * seeing * (t4 - sky4);
    }

    /**
     * Heat conducted into the rock a buried hull is pressed against, in watts.
     *
     * <p>The other loss, and the one that decides whether burying is actually a strategy.
     * A buried wall stops radiating — but it starts conducting, into an asteroid whose
     * interior sits at {@code rockK} and has effectively infinite thermal mass. So burying
     * does not stop the loss, it <em>changes its character</em>: from a fourth-power term
     * against a 2.7 K sky to a linear one against a merely cold rock. That is a far smaller
     * number, which is why burying works, and it is not zero, which is why insulation is not
     * optional.
     *
     * @param uValue watts per square metre per kelvin through the hull — the thing
     *               insulation changes, and the only lever with the range to matter
     */
    public static double conductedWatts(double temperatureK, double rockK, double areaM2,
                                        double buriedFraction, double uValue) {
        double touching = Math.clamp(buriedFraction, 0.0, 1.0);
        if (areaM2 <= 0 || touching <= 0) {
            return 0;
        }
        return uValue * areaM2 * touching * (temperatureK - rockK);
    }

    /**
     * Heat a person puts into the room they are in, in watts.
     *
     * <p>A human is a poor engine: about a fifth of the chemical energy taken in becomes
     * work and the rest leaves as heat. At rest that is roughly 100 W — the reason a crowded
     * room warms up — and hard physical work takes it to three or four times that.
     *
     * <p><strong>Driven by the same exertion the breathing model uses</strong>, deliberately.
     * The oxygen a player burns and the heat they give off are two consequences of one
     * metabolic rate, and letting them be set by two separate numbers is how a game ends up
     * with someone gasping for air while contributing nothing to the room's warmth.
     *
     * @param exertion the multiplier over resting metabolism, 1 at rest
     */
    public static double metabolicWatts(double exertion) {
        return RESTING_METABOLIC_W * Math.max(exertion, 1.0);
    }

    /** A resting adult, in watts of heat. Roughly a filament lamp, and not a coincidence. */
    public static final double RESTING_METABOLIC_W = 100.0;

    /**
     * Heat a hand-worked machine puts into the room, in watts.
     *
     * <p>Everything the operator puts in comes back out as heat: crushing rock, working
     * metal and turning a drum all end as friction and deformation, and none of it leaves
     * the room. So a machine being cranked is worth about what the person cranking it is
     * worth — which is the honest figure, and it means a workshop is warm because somebody
     * is working in it rather than because a block was placed.
     */
    public static double machineWatts(int machinesBeingWorked) {
        return Math.max(machinesBeingWorked, 0) * WORKED_MACHINE_W;
    }

    /** One machine under the handle, in watts. */
    public static final double WORKED_MACHINE_W = 250.0;

    /**
     * A bare machined hull plate against rock, W/m2/K.
     *
     * <p>Metal is a poor wall to put between yourself and an asteroid: it conducts, and the
     * rock behind it has effectively infinite thermal mass. At this figure a 150 m2 buried
     * habitat sheds 12 kW holding shirtsleeve temperature - which is the number that makes
     * insulation part of the counter rather than a later polish.
     */
    public static final double BARE_HULL_U = 1.0;

    /**
     * The same plate with an evacuated powder jacket, W/m2/K.
     *
     * <p>A twentyfold reduction, which sounds generous and is not: evacuated powder and
     * multi-layer insulation both do far better than this in reality. It is held at twenty
     * deliberately, because insulation alone must not be the whole answer - the design wants
     * bury, insulate <em>and</em> be in there working, and an insulator that reduced the loss
     * to nothing would collapse those three decisions into one purchase.
     */
    public static final double INSULATED_HULL_U = 0.05;

    /**
     * The U-value of a shell that is insulated over part of its area.
     *
     * <p>Area-weighted, because the two kinds of wall are heat paths in <em>parallel</em>:
     * insulating half a room halves its conducted loss and no more. That is worth being
     * exact about, because it is the one place a player will suspect the game of cheating -
     * doing half the job and getting most of the benefit is what an averaged-in-series
     * formula would wrongly promise.
     */
    public static double uValue(double insulatedFraction) {
        double insulated = Math.clamp(insulatedFraction, 0.0, 1.0);
        return BARE_HULL_U * (1 - insulated) + INSULATED_HULL_U * insulated;
    }

    /**
     * How much of the sun a bare, machined hull plate actually absorbs, 0..1.
     *
     * <p>A real, cited mid-figure for oxidized/machined aluminum-like plate (real range
     * 0.3-0.9 depending on treatment) — this is the property that decides how much of
     * {@link PowerBalance#SOLAR_FLUX_W_PER_M2} a bare wall actually takes in, and it is a
     * different question from {@link #HULL_EMISSIVITY}: how a
     * surface answers visible sunlight and how it answers its own thermal infrared are two
     * separate material properties, not one knob wearing two names.
     */
    public static final double BARE_SOLAR_ABSORPTIVITY = 0.5;

    /**
     * The same figure for a plate painted white with TiO2 pigment, 0..1.
     *
     * <p>Real spacecraft white thermal-control paints commonly cite solar absorptance around
     * 0.12-0.25 — this is why they are painted <em>white</em>, not a colour choice for its own
     * sake. Deliberately not paired with a raised emissivity here — see {@code
     * design/albedo-paint.md} §2 for why paint's real second effect is named rather than
     * modelled, so nothing in this file quietly claims a benefit PLAN.md never asked for.
     */
    public static final double PAINTED_SOLAR_ABSORPTIVITY = 0.2;

    /**
     * The shell's own real solar absorptivity, area-weighted the same way {@link
     * #uValue(double)} already blends two wall kinds in parallel rather than in series.
     */
    public static double solarAbsorptivity(double paintedFraction) {
        double painted = Math.clamp(paintedFraction, 0.0, 1.0);
        return BARE_SOLAR_ABSORPTIVITY * (1 - painted) + PAINTED_SOLAR_ABSORPTIVITY * painted;
    }

    /**
     * Heat the sun puts into an exposed shell, in watts.
     *
     * <p>Real sunlight at this belt's own distance, absorbed only by the fraction of the
     * shell that both faces open space at all ({@code skyFraction}) and is actually lit right
     * now ({@code sunFraction}) — buried, night, or a fully reflective shell each zero this on
     * their own, because each of those genuinely means no sunlight is landing on anything.
     *
     * @param solarAbsorptivity the shell's own, from {@link #solarAbsorptivity(double)}
     * @param sunFraction       how much of the asteroid's own sun is up right now, 0..1 — the
     *                          day/night and occultation state, the same figure the solar
     *                          array and the retort's mirror already answer to
     */
    public static double solarWatts(double areaM2, double skyFraction, double solarAbsorptivity,
                                    double sunFraction) {
        double seeing = Math.clamp(skyFraction, 0.0, 1.0);
        double sun = Math.clamp(sunFraction, 0.0, 1.0);
        if (areaM2 <= 0 || seeing <= 0 || sun <= 0 || solarAbsorptivity <= 0) {
            return 0;
        }
        return solarAbsorptivity * PowerBalance.SOLAR_FLUX_W_PER_M2 * areaM2 * seeing * sun;
    }

    /**
     * The room's temperature one step later, given what it radiates and what is put back.
     *
     * <p>Stepped explicitly rather than solved, because the step is small against the time
     * constant and an explicit step is a thing that can be read. It is clamped at the sky's
     * own temperature: nothing radiates itself below what it is radiating against.
     *
     * @param heaterWatts heat supplied from anything that puts it back — zero until the
     *                    counter exists (design/thermal.md T3)
     */
    public static double step(double temperatureK, double volumeBlocks, double skyFraction,
                              double heaterWatts, double dtSeconds) {
        return step(temperatureK, volumeBlocks, skyFraction, 0, BARE_HULL_U, SKY_K,
                heaterWatts, dtSeconds);
    }

    /**
     * The full step: what a room radiates, what it conducts into the rock, and what is put
     * back into it.
     *
     * <p>The overload above drops the conductive term, which is only ever right for a room
     * touching no rock at all. This is the one the world calls, and it has to agree with
     * {@link #equilibriumK} - a stepper that omitted a loss the equilibrium solver included
     * would walk a room steadily past the temperature the instrument promised it would
     * settle at, which is the sort of disagreement a player reads as the game lying.
     */
    public static double step(double temperatureK, double volumeBlocks, double skyFraction,
                              double buriedFraction, double uValue, double rockK,
                              double heaterWatts, double dtSeconds) {
        double area = hullAreaM2(volumeBlocks);
        double capacity = Math.max(SHELL_CAPACITY_J_PER_K_PER_M2 * area, 1e-9);
        double net = heaterWatts
                - radiatedWatts(temperatureK, area, skyFraction)
                - conductedWatts(temperatureK, rockK, area, buriedFraction, uValue);
        return Math.max(SKY_K, temperatureK + net * dtSeconds / capacity);
    }

    /**
     * The heat a room needs, in watts, to hold the temperature it is at.
     *
     * <p>What a heater has to supply, and therefore what the counter has to be sized
     * against. Also the number an instrument should show: "this room is losing 900 W" is
     * something a player can act on in a way that "this room is at 287 K" is not.
     */
    public static double holdingWatts(double temperatureK, double volumeBlocks,
                                      double skyFraction) {
        return radiatedWatts(temperatureK, hullAreaM2(volumeBlocks), skyFraction);
    }

    /**
     * Where a room settles: the temperature at which everything put in equals everything
     * lost.
     *
     * <p>The number that decides whether this phase is playable at all, so it is worth being
     * able to ask directly rather than by simulating until it stops moving. Solved by
     * bisection because the radiative term is quartic and the conductive one is linear, so
     * there is no tidy closed form — and a numeric answer that is obviously right beats an
     * algebraic one that is subtly wrong.
     */
    public static double equilibriumK(double volumeBlocks, double skyFraction,
                                      double buriedFraction, double uValue, double rockK,
                                      double heatWatts) {
        double area = hullAreaM2(volumeBlocks);
        double low = SKY_K;
        double high = 1000.0;
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (low + high);
            double net = heatWatts
                    - radiatedWatts(mid, area, skyFraction)
                    - conductedWatts(mid, rockK, area, buriedFraction, uValue);
            if (net > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return 0.5 * (low + high);
    }

    private HeatBalance() {}
}
