package play.xponer.astronima.sim.chem;

/**
 * The stage a hydroponic crop is at, and how it moves between stages — the same real
 * photosynthesis A1's own {@link Photosynthesis} uses, staged rather than batched (see
 * {@code design/hydroponics.md} §4.2).
 *
 * <p>Minecraft-free (rule 1) — the block owns the random roll and the real world reads (sunlight,
 * room gases); this owns only the state machine those reads drive, the same split
 * {@code sim.MoldGrowth} already established for a growing block.
 */
public final class HydroponicGrowth {
    public static final int MAX_AGE = 3;

    /** Age a fully-harvested plant returns to — it survives its own harvest and keeps growing,
     *  the real "several mature leaves... at weekly intervals" fact NASA's own Veg-05 names
     *  (design/hydroponics.md §4.1), not a destroy-and-replant cycle. */
    public static final int HARVESTED_AGE = 1;

    /** Real CO2 spent, and O2 released, on one favourable growth tick - a small, fixed real
     *  constant (design/hydroponics.md §4.6), at the same real 1:1 ratio {@link Photosynthesis}
     *  already uses at bottle scale. */
    public static final double CO2_PER_GROWTH_MOL = 0.05;
    public static final double O2_PER_GROWTH_MOL =
            CO2_PER_GROWTH_MOL * Photosynthesis.O2_PER_MOL_CO2;

    /** All three real conditions this stage needs to advance at all - real sunlight, real room
     *  water vapour present, and enough real room CO2 to actually cover one growth tick's own
     *  real charge (the same all-or-nothing reagent gate {@code Photosynthesis} already uses,
     *  not a partial/rationed tick). */
    public static boolean favorable(double sunlight01, double roomCo2Mol, double roomWaterVaporMol) {
        return sunlight01 > 0 && roomCo2Mol >= CO2_PER_GROWTH_MOL && roomWaterVaporMol > 0;
    }

    /** Age after one favourable tick that rolled a growth hit - one stage, clamped at mature. */
    public static int grown(int age) {
        return Math.min(MAX_AGE, age + 1);
    }

    public static boolean isMature(int age) {
        return age >= MAX_AGE;
    }

    private HydroponicGrowth() {}
}
