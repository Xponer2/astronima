package play.xponer.astronima.sim.metal;

/**
 * Real melting thermodynamics: sensible heat to reach the melting point, plus the latent heat
 * of fusion to actually change phase there — see {@code design/induction-furnace.md}. The whole
 * point of the machine this feeds is that neither of these terms touches room oxygen at all,
 * unlike {@code AbstractFurnaceBlockEntityMixin}'s own vanilla furnace, which burns real air to
 * do the identical job.
 */
public final class InductionMelting {

    /** Real iron constants — the one metal this mod already smelts. */
    public static final double IRON_SPECIFIC_HEAT_J_PER_KG_K = 449.0;
    public static final double IRON_MELTING_POINT_K = 1811.0;
    public static final double IRON_LATENT_HEAT_FUSION_J_PER_KG = 247_000.0;

    /** A heated habitat's own real room temperature — the furnace sits indoors, not in the
     *  asteroid's own cold vacuum. */
    public static final double AMBIENT_ROOM_K = 293.0;

    /**
     * Real energy to melt {@code massKg} of a metal from {@code startK} to {@code meltingPointK}
     * and then through the phase change itself: sensible heat plus latent heat of fusion, two
     * genuinely separate terms rather than one folded constant.
     */
    public static double energyJoules(double massKg, double specificHeatJPerKgK,
                                      double meltingPointK, double latentHeatFusionJPerKg,
                                      double startK) {
        if (massKg <= 0) {
            return 0.0;
        }
        double sensible = specificHeatJPerKgK * Math.max(0, meltingPointK - startK);
        return massKg * (sensible + latentHeatFusionJPerKg);
    }

    /** The real figure for this mod's one already-smelted metal, at habitat ambient. */
    public static double ironEnergyJoules(double massKg) {
        return energyJoules(massKg, IRON_SPECIFIC_HEAT_J_PER_KG_K, IRON_MELTING_POINT_K,
                IRON_LATENT_HEAT_FUSION_J_PER_KG, AMBIENT_ROOM_K);
    }

    private InductionMelting() {}
}
