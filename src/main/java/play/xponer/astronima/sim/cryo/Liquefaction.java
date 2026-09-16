package play.xponer.astronima.sim.cryo;

/**
 * The Stirling cryocooler's own physics (design/cryogenics.md §1.1) — a reversed heat engine,
 * so the absolute floor on what it costs to liquefy a gas is the Carnot coefficient of
 * performance, and a real machine only ever reaches a fraction of that floor.
 *
 * <p>Minecraft-free (rule 1): every method here is a pure function of temperatures, a species'
 * real constants and a wattage, so the whole model is checkable against a hand-worked value.
 */
public final class Liquefaction {

    /**
     * A real small Stirling cryocooler reaches roughly 10-30% of its own Carnot floor —
     * friction, imperfect regeneration and real-gas behaviour all take a cut. This mod declares
     * the low-to-middle of that published range, because PLAN.md calls this machine
     * "power-hungry" and the honest refrigeration floor earns that word better than a tuned one.
     */
    public static final double REAL_EFFICIENCY_FRACTION = 0.15;

    /** The cooler's rated electrical draw when fully supplied, W — the upper end of this mod's
     * existing machine wattages (design/power.md's generator/fuel cell top out near 250 W),
     * because PLAN.md names this machine power-hungry and a cryocooler earns that rating. */
    public static final double RATED_ELECTRICAL_W = 300.0;

    /**
     * The Carnot coefficient of performance of a refrigerator moving heat from {@code coldK} to
     * {@code hotK} — the theoretical best any reversed heat engine can do, real or not.
     *
     * @return 0 if {@code hotK <= coldK} (nothing to cool against; asking to liquefy a gas
     *         already at or below its own boiling point needs no refrigeration at all)
     */
    public static double carnotCop(double hotK, double coldK) {
        if (hotK <= coldK) {
            return 0.0;
        }
        return coldK / (hotK - coldK);
    }

    /**
     * Total heat that must leave one mole of {@code cryogen}'s gas, starting at {@code hotK}, to
     * fully liquefy it: sensible heat down to the boiling point, plus the latent heat of
     * condensing there. Zero (nothing to remove) if the gas starts at or below its own boiling
     * point already.
     */
    public static double heatToRemoveJPerMol(Cryogen cryogen, double hotK) {
        double sensible = Math.max(0.0, cryogen.gasMolarHeatCapacityJPerMolK()
                * (hotK - cryogen.boilingPointK()));
        return sensible + cryogen.latentHeatVaporizationJPerMol();
    }

    /**
     * The real electrical energy needed to liquefy one mole of {@code cryogen} from a room at
     * {@code hotK}, at {@link #REAL_EFFICIENCY_FRACTION} of the Carnot floor.
     *
     * @return {@link Double#POSITIVE_INFINITY} if the room offers no temperature gradient to
     *         refrigerate against ({@code hotK <= coldK}, i.e. {@link #carnotCop} is zero) — an
     *         infinite cost is the honest answer, not zero moles for zero energy
     */
    public static double electricalEnergyPerMoleJ(Cryogen cryogen, double hotK) {
        double cop = carnotCop(hotK, cryogen.boilingPointK()) * REAL_EFFICIENCY_FRACTION;
        if (cop <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return heatToRemoveJPerMol(cryogen, hotK) / cop;
    }

    /**
     * How many moles of {@code cryogen} a real delivery of {@code electricalJoules} liquefies
     * from a room at {@code hotK}. The inverse of {@link #electricalEnergyPerMoleJ} — this is
     * what a machine actually calls each tick, given whatever watts the cable network delivered.
     */
    public static double molesLiquefiedFor(Cryogen cryogen, double hotK, double electricalJoules) {
        if (electricalJoules <= 0.0) {
            return 0.0;
        }
        double perMole = electricalEnergyPerMoleJ(cryogen, hotK);
        if (Double.isInfinite(perMole)) {
            return 0.0;
        }
        return electricalJoules / perMole;
    }

    private Liquefaction() {}
}
