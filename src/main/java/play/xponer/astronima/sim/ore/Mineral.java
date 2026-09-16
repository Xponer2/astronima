package play.xponer.astronima.sim.ore;

/**
 * The minerals actually present in a carbonaceous chondrite.
 *
 * <p>This is not a fantasy rock. CI and CM chondrites are among the best characterised
 * materials in the solar system, and every species here is one that is really in them,
 * with its real formula and roughly its real abundance.
 *
 * <p>The fact that drives the entire early game is {@link #KAMACITE}: chondrites
 * contain <em>native metal</em>. Meteoric iron is metal already, which is why it was
 * worked thousands of years before anyone could smelt anything. A castaway with no
 * oxygen cannot run a furnace, but they can absolutely swing a hammer at a lump of
 * iron-nickel alloy — so the progression starts with a magnet, not a fire.
 */
public enum Mineral {
    /**
     * Native iron-nickel alloy. Already metal, strongly magnetic, cold-workable.
     * The reason a survivable first hour exists.
     */
    KAMACITE("Kamacite", "(Fe,Ni)", 7.9, 1.0, 0.93, Tier.MECHANICAL),

    /** Magnetite. Iron oxide, strongly magnetic, but the iron is bonded to oxygen. */
    MAGNETITE("Magnetite", "Fe3O4", 5.2, 0.55, 0.72, Tier.ELECTROLYTIC),

    /** Iron sulfide. Weakly magnetic at best; the sulfur has to go somewhere. */
    TROILITE("Troilite", "FeS", 4.7, 0.008, 0.63, Tier.CHEMICAL),

    /** The nickel ore. A magnet does essentially nothing to it, which is the point. */
    PENTLANDITE("Pentlandite", "(Fe,Ni)9S8", 4.8, 0.005, 0.34, Tier.CHEMICAL),

    /** Hydrated silicate. Not a metal ore at all — it is where the water lives. */
    SERPENTINE("Serpentine", "Mg3Si2O5(OH)4", 2.6, 0.001, 0.0, Tier.ELECTROLYTIC),

    /** Anhydrous silicate gangue, and the late-game oxygen feedstock. */
    OLIVINE("Olivine", "(Mg,Fe)2SiO4", 3.3, 0.002, 0.0, Tier.ELECTROLYTIC),

    /** Carbonate. Decomposes to CO2 — a feedstock for the carbonyl tier, not waste. */
    CARBONATE("Breunnerite", "(Mg,Fe)CO3", 3.0, 0.002, 0.0, Tier.CHEMICAL),

    /** Complex organics. The carbon that makes carbonyl chemistry possible at all. */
    THOLIN("Organics", "kerogen", 1.4, 0.0, 0.0, Tier.CHEMICAL),

    /**
     * Iron-titanium oxide — the Apollo-famous lunar/ISRU mineral, and the whole reason the
     * fluidized-bed reactor exists. A magnet does nothing to it; the iron and the titanium are
     * both bonded to oxygen, and only hydrogen reduction at ~1000 °C splits them
     * ({@code FeTiO₃ + H₂ → Fe + TiO₂ + H₂O}). Its density 4.79 g/cm³ is the very figure
     * {@link play.xponer.astronima.sim.metal.CentrifugalBed#SOLID_DENSITY_KGM3} assumes, so the
     * fluidization window and the mineral agree by construction. Not in the chondrite assemblage:
     * it is its own worldgen ore.
     */
    ILMENITE("Ilmenite", "FeTiO3", 4.79, 0.002, 0.368, Tier.THERMOCHEMICAL),

    /**
     * The platinum-group metals — platinum, palladium, iridium, osmium, ruthenium, rhodium —
     * modelled as one mineral because real iron meteorites report them as a group: they occur
     * together, as native alloy inclusions in the metal phase, not as six separately findable
     * minerals. Present only in metal-rich seams ({@code OreBody.metalRich()}) — chondrite's
     * silicate-dominant assemblage barely has a metal phase for them to concentrate in.
     *
     * <p>Barely magnetic, unlike kamacite: real platinum is only weakly paramagnetic, which is
     * <em>why</em> a magnet does essentially nothing to it and the winnowing table's density
     * separation — real placer platinum's real extraction method — is what finds it
     * ({@code design/platinum-group.md} §1.1/§2.1). Native metal already, exactly like kamacite:
     * no oxide or sulfide bond to break, so the metal mass fraction is 1.0 and the tier is the
     * same {@link Tier#MECHANICAL} kamacite gets.
     */
    PLATINUM_GROUP("Platinum group", "(Pt,Pd,Ir,Os,Ru,Rh)", 19.0, 0.003, 1.0, Tier.MECHANICAL);

    /** Which physical principle can actually process a mineral. */
    public enum Tier {
        /** Crushing and magnets. No inputs but motion. */
        MECHANICAL,
        /** Carbonyl chemistry. Costs carbon monoxide and precise heat. */
        CHEMICAL,
        /**
         * Hydrogen reduction in a spun bed. Costs hydrogen — consumed, not a carrier — and the
         * ~1000 °C heat to run it. The fluidized-bed reactor's tier; distinct from
         * {@link #CHEMICAL} because the gas is a reagent spent, not a carrier returned (rule 8).
         */
        THERMOCHEMICAL,
        /** Molten oxide electrolysis. Costs power, yields oxygen. */
        ELECTROLYTIC
    }

    private final String displayName;
    private final String formula;
    private final double densityGPerCm3;
    private final double magneticSusceptibility;
    private final double metalMassFraction;
    private final Tier tier;

    Mineral(String displayName, String formula, double densityGPerCm3,
            double magneticSusceptibility, double metalMassFraction, Tier tier) {
        this.displayName = displayName;
        this.formula = formula;
        this.densityGPerCm3 = densityGPerCm3;
        this.magneticSusceptibility = magneticSusceptibility;
        this.metalMassFraction = metalMassFraction;
        this.tier = tier;
    }

    public String displayName() {
        return displayName;
    }

    public String formula() {
        return formula;
    }

    public double densityGPerCm3() {
        return densityGPerCm3;
    }

    /**
     * Magnetic susceptibility, relative to native iron at 1.0.
     *
     * <p>The spread here is the point. Kamacite is ferromagnetic and magnetite
     * ferrimagnetic, while troilite, pentlandite and the silicates are merely
     * paramagnetic — in reality that is a difference of orders of magnitude, not a
     * factor of a few. Compressing it made every mineral respond to the field at
     * roughly the same rate, which is what left the separator's field dial with no
     * trade-off in it.
     */
    public double magneticSusceptibility() {
        return magneticSusceptibility;
    }

    /** Fraction of the mineral's mass that is recoverable metal. */
    public double metalMassFraction() {
        return metalMassFraction;
    }

    /** The lowest tier that can extract anything from this mineral. */
    public Tier tier() {
        return tier;
    }

    /** True when a magnet will pull this out of a crushed powder. */
    public boolean isMagnetic() {
        return magneticSusceptibility >= MagneticSeparation.MAGNETIC_THRESHOLD;
    }

    /**
     * True when the metal is already metal and needs no chemistry to free it.
     * Exactly one mineral qualifies, and that is the whole design.
     */
    public boolean isNativeMetal() {
        return this == KAMACITE;
    }
}
