package play.xponer.astronima.sim;

/**
 * Gas species tracked by the atmosphere simulation.
 *
 * <p>The set mirrors the volatiles actually found in carbonaceous (C-type) asteroids
 * plus the species produced by life support processes. Quantities are always tracked
 * in moles; partial pressures fall out of the ideal gas law (see {@link GasMixture}).
 */
public enum Gas {
    OXYGEN("O2", 0.032),
    NITROGEN("N2", 0.028),
    CARBON_DIOXIDE("CO2", 0.044),
    CARBON_MONOXIDE("CO", 0.028),
    METHANE("CH4", 0.016),
    HYDROGEN("H2", 0.002),
    HYDROGEN_SULFIDE("H2S", 0.034),
    SULFUR_DIOXIDE("SO2", 0.064),
    AMMONIA("NH3", 0.017),
    WATER_VAPOR("H2O", 0.018),

    /**
     * Chlorine. What comes off a chlorate bed heated past the point where it simply gives
     * up its oxygen — and one of the most dangerous things that can happen in a sealed
     * habitat, because it is heavier than air, it attacks the lungs, and a chlorate charge
     * is something the player is deliberately cooking a few metres away.
     *
     * <p>Also the halogen the plan's v0.7 chain eventually wants for biocide and PVC, so it
     * arrives here early rather than being invented twice.
     */
    CHLORINE("Cl2", 0.071),

    /**
     * Nickel tetracarbonyl — the Mond process's carrier, and the worst thing in this game.
     *
     * <p>Dangerous at parts per million and notorious for a <em>delayed</em> onset: the
     * exposure that kills does not feel like anything at the time. It exists here because the
     * refiner that makes ultrapure nickel makes it on the way, and a machine held at the wrong
     * temperature fills the room with it.
     */
    NICKEL_CARBONYL("Ni(CO)4", 0.171),

    /**
     * The cracking tower's own product: two carbons, the monomer the polymerizer strings into
     * polyethylene. Real thermal cracking of a long-chain hydrocarbon really does yield a shorter
     * alkene like this one (see {@code design/petrochemicals.md} §2).
     */
    ETHYLENE("C2H4", 0.02805);

    private final String symbol;
    private final double molarMassKgPerMol;

    Gas(String symbol, double molarMassKgPerMol) {
        this.symbol = symbol;
        this.molarMassKgPerMol = molarMassKgPerMol;
    }

    /** Chemical formula, e.g. {@code "CO2"}. Used for analyzer readouts and serialization keys. */
    public String symbol() {
        return symbol;
    }

    /** Molar mass in kg/mol, e.g. 0.032 for O2. */
    public double molarMassKgPerMol() {
        return molarMassKgPerMol;
    }
}
