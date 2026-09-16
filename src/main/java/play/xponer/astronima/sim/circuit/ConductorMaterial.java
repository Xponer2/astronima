package play.xponer.astronima.sim.circuit;

import play.xponer.astronima.sim.chem.Formula;

/**
 * What a wire is made of — and therefore what it costs you to send power down it.
 *
 * <p><strong>Resistivity is a fixed physical property of a substance.</strong> Nothing in this
 * file is a balance figure: iron really is six times worse than copper, and that ratio is why
 * the conductor ladder is a progression rather than a tier list. A player who upgrades a run
 * from iron to aluminium cuts its losses by 3.7× because ρ says so.
 *
 * <p>This is what makes the electrical tier fall out of the <em>mineral</em> tier instead of
 * being bolted beside it. The mod has no copper and no aluminium on day one; it has native
 * iron-nickel. So the first wiring in the game is made of the worst common conductor there is,
 * and every better one has to be earned out of the rock — which is exactly the shape
 * {@code design/electrical.md} §3.1 argues for, and it needed no invention at all.
 *
 * <h2>Temperature matters, and it is a feedback loop</h2>
 * A metal's resistivity <em>rises</em> with temperature. So an overloaded wire gets hotter, and
 * being hotter makes it more resistive, which makes it hotter still. That is real positive
 * feedback and it is the reason a fuse is not optional — a run that is merely warm is fine, and
 * a run past its limit runs away.
 *
 * <p>Minecraft-free (rule 1).
 */
public enum ConductorMaterial {

    /**
     * Native iron-nickel is what you crawl out of the wreck with, and iron is a poor conductor.
     * Every tech mod hands the player copper in the first hour; here the good conductor is the
     * reward rather than the starting point.
     */
    IRON("Fe", 9.71e-8, 0.00651, 1811, 7874, 449),

    /** Better than iron, and the carbonyl tier already produces it pure (v0.55). */
    NICKEL("Ni", 6.99e-8, 0.0060, 1728, 8908, 444),

    /** Sits with iron-nickel in meteorites; a slightly better conductor than either. */
    COBALT("Co", 6.24e-8, 0.0070, 1768, 8900, 421),

    /**
     * What real transmission lines are made of, and the prize of this ladder. It takes
     * molten-salt electrolysis to win it out of a silicate — power to make the thing that
     * carries power, which is the chicken-and-egg every real ISRU plan has.
     */
    ALUMINIUM("Al", 2.65e-8, 0.00429, 933, 2700, 897),

    /** The conductor everyone else starts with. Here it needs a copper sulfide first. */
    COPPER("Cu", 1.68e-8, 0.00393, 1358, 8960, 385),

    /** The best conductor there is, and far too valuable to run down a corridor. */
    SILVER("Ag", 1.59e-8, 0.0038, 1235, 10490, 235),

    /** Worse than copper and it does not corrode — which is why real contacts are plated. */
    GOLD("Au", 2.44e-8, 0.0034, 1337, 19300, 129),

    /**
     * Not for carrying power. Tungsten is here because it melts at 3695 K, which is what a
     * thermionic filament needs — the vacuum tube's cathode (design/electrical.md §7).
     */
    TUNGSTEN("W", 5.28e-8, 0.0045, 3695, 19250, 132),

    /**
     * A cautionary entry. The mod already produces titanium, and a player may reasonably assume
     * a strong metal is a good wire. It is <strong>seventeen times worse than aluminium</strong>
     * — and the meter is how they find that out.
     */
    TITANIUM("Ti", 4.20e-7, 0.0038, 1941, 4506, 523);

    /**
     * Below this fraction of its room-temperature value, resistivity stops falling.
     *
     * <p>A pure metal's resistivity does not go to zero as it cools — it flattens at a residual
     * value set by impurities and defects. Without this floor the linear model would hand a
     * cold-side wire <em>negative</em> resistance, which is not merely wrong but would appear as
     * a run generating energy. A tenth is an ordinary residual-resistivity ratio for the
     * unglamorous metal a survivor smelts.
     */
    public static final double RESIDUAL_FRACTION = 0.10;

    /** Where the linear coefficients are quoted from: 20 °C. */
    public static final double REFERENCE_K = 293.15;

    private final String formulaText;
    private final double densityKgPerM3;
    private final double specificHeatJPerKgK;
    private final double resistivity20C;
    private final double temperatureCoefficient;
    private final double meltingPointK;

    ConductorMaterial(String formulaText, double resistivity20C, double temperatureCoefficient,
                      double meltingPointK, double densityKgPerM3, double specificHeatJPerKgK) {
        this.densityKgPerM3 = densityKgPerM3;
        this.specificHeatJPerKgK = specificHeatJPerKgK;
        this.formulaText = formulaText;
        this.resistivity20C = resistivity20C;
        this.temperatureCoefficient = temperatureCoefficient;
        this.meltingPointK = meltingPointK;
    }

    /**
     * What it is made of.
     *
     * <p>Parsed rather than stored as a string, so a conductor is chemistry the rest of the mod
     * can reason about — which is what lets the future chemistry tier ask a wire what it would
     * recover from melting one down, instead of a lookup table saying so separately.
     */
    public Formula formula() {
        return Formula.parse(formulaText);
    }

    /** Resistivity at 20 °C, Ω·m. A property of the substance. */
    public double resistivity20C() {
        return resistivity20C;
    }

    /** Fractional change in resistivity per kelvin, near room temperature. */
    public double temperatureCoefficient() {
        return temperatureCoefficient;
    }

    /** Where the conductor itself stops being a conductor and becomes a puddle. */
    /**
     * Density, kg/m3 — one half of what decides how long a wire takes to heat up.
     *
     * <p>Here rather than in a table beside it because it is a property of the metal in exactly
     * the way resistivity is, and a second list keyed by the same names is a second chance to
     * disagree with the first (rule 20).
     */
    public double densityKgPerM3() {
        return densityKgPerM3;
    }

    /** Specific heat, J per kg per K — the other half. */
    public double specificHeatJPerKgK() {
        return specificHeatJPerKgK;
    }

    public double meltingPointK() {
        return meltingPointK;
    }

    /**
     * Resistivity at a temperature, Ω·m — {@code ρ(T) = ρ₂₀ · (1 + α·(T − 293.15))}.
     *
     * <p>Linear, which is accurate to a few per cent across the range a wire lives in and is
     * the form every engineering table is quoted in. Floored per {@link #RESIDUAL_FRACTION}.
     */
    public double resistivity(double temperatureK) {
        double scaled = resistivity20C * (1.0 + temperatureCoefficient * (temperatureK - REFERENCE_K));
        return Math.max(scaled, resistivity20C * RESIDUAL_FRACTION);
    }
}
