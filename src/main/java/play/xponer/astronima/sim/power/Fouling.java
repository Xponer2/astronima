package play.xponer.astronima.sim.power;

/**
 * What an engine leaves behind, and why it is your fault.
 *
 * <p>Taken literally, "machines accumulate waste and jam" is a durability bar with a shovel.
 * What makes this something else is that <strong>the rate is set by two decisions the player is
 * already making</strong>, so the sludge is information rather than a timer.
 *
 * <table>
 *   <tr><td><strong>Running rich</strong> — not enough oxygen for complete combustion</td>
 *       <td>soot, and it is why a starved engine cokes up</td></tr>
 *   <tr><td><strong>Sour gas</strong> — sulfur compounds in the charge</td>
 *       <td>corrosion and deposits, and it is why gas is <em>sweetened</em> before use
 *           anywhere on Earth</td></tr>
 * </table>
 *
 * <p>So feeding clean methane and plenty of air runs an engine for a long time; piping a
 * hydrogen-sulfide pocket into the burner room and keeping the oxygen low means shovelling it
 * out constantly. <strong>Neither is wrong</strong> — the second is getting power out of a
 * pocket the first would have had to vent — and that is what makes it a trade rather than a
 * chore.
 *
 * <p>Minecraft-free (rule 1): mixtures in, a fouling rate out.
 */
public final class Fouling {

    /**
     * Sludge a perfectly-fed engine still leaves, per mole of fuel.
     *
     * <p>Not zero. Every real engine deposits something, and a machine that never needed
     * touching would make the shovel a mechanic only careless players ever meet — so the
     * careful player is on a long schedule rather than on none.
     *
     * <p><strong>Scaled against the intervals the design asked for, not typed.</strong> With
     * these three figures and a twelve-unit sump, a well-fed engine on clean gas chokes after
     * about <strong>fifteen hours</strong> of running and a starved one on sour gas after
     * <strong>half an hour</strong> — which is the spread that makes the sludge a consequence
     * rather than a clock. The first draft was a tenth of this and would have taken twenty
     * hours even at its filthiest: a mechanic nobody would ever have met.
     */
    public static final double BASE_PER_FUEL_MOL = 0.2;

    /**
     * Extra sludge per mole of fuel when the charge is entirely sulfurous.
     *
     * <p>Twenty times the clean rate, because sour gas really is that much worse for an engine
     * — and because the decision has to be visible within one session rather than statistically.
     */
    public static final double SOUR_PER_FUEL_MOL = 4.0;

    /**
     * Extra sludge per mole of fuel when running as rich as the model allows.
     *
     * <p>Half the sour penalty: starving an engine of air is bad for it, and not as bad as
     * feeding it sulfur.
     */
    public static final double RICH_PER_FUEL_MOL = 2.0;

    /**
     * Oxygen-to-fuel ratio at or above which combustion is complete.
     *
     * <p>Twice stoichiometric. An engine wants excess air to burn clean — running at exactly
     * the stoichiometric ratio is already sooty, which is a fact about combustion rather than a
     * penalty invented here.
     */
    public static final double CLEAN_EXCESS_AIR = 2.0;

    /**
     * How rich the charge is, 0 (plenty of air) to 1 (as starved as it gets).
     *
     * @param oxygenMol available oxygen
     * @param fuelMol   available fuel
     * @param o2PerFuel moles of oxygen one mole of this fuel wants
     */
    public static double richness(double oxygenMol, double fuelMol, double o2PerFuel) {
        if (fuelMol <= 0 || o2PerFuel <= 0) {
            return 0;
        }
        double wanted = fuelMol * o2PerFuel * CLEAN_EXCESS_AIR;
        if (wanted <= 0) {
            return 0;
        }
        return Math.clamp(1.0 - Math.max(oxygenMol, 0) / wanted, 0.0, 1.0);
    }

    /**
     * How sour the charge is, 0 (clean) to 1 (all sulfur).
     *
     * <p>Measured as the sulfur species' share of everything combustible in the mixture,
     * because that is what actually reaches the burner.
     */
    public static double sourness(double sulfurMol, double fuelMol) {
        double total = Math.max(sulfurMol, 0) + Math.max(fuelMol, 0);
        return total <= 0 ? 0 : Math.max(sulfurMol, 0) / total;
    }

    /**
     * Sludge produced by burning this much fuel under these conditions.
     *
     * <p>The two penalties add rather than multiply: they are separate physical processes —
     * unburnt carbon and sulfur deposits — and a product would make a clean-but-starved engine
     * behave as though it were spotless.
     */
    public static double sludgeFrom(double fuelMol, double richness, double sourness) {
        if (fuelMol <= 0) {
            return 0;
        }
        double perMol = BASE_PER_FUEL_MOL
                + RICH_PER_FUEL_MOL * Math.clamp(richness, 0.0, 1.0)
                + SOUR_PER_FUEL_MOL * Math.clamp(sourness, 0.0, 1.0);
        return fuelMol * perMol;
    }

    /**
     * What fraction of its rated output a machine this fouled still manages, 1 down to 0.
     *
     * <p><strong>Linear and gradual, deliberately.</strong> A machine that worked perfectly and
     * then stopped would be a trap — nothing warns you and nothing can be scheduled. One that
     * gets visibly worse is a maintenance interval the player sets for themselves.
     */
    public static double output(double sludge, double capacity) {
        if (capacity <= 0) {
            return 0;
        }
        return Math.clamp(1.0 - sludge / capacity, 0.0, 1.0);
    }

    /** True when it is packed solid and will not run at all until it is dug out. */
    public static boolean jammed(double sludge, double capacity) {
        return output(sludge, capacity) <= 0;
    }

    private Fouling() {}
}
