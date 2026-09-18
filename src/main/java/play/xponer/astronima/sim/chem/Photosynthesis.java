package play.xponer.astronima.sim.chem;

/**
 * Real photosynthesis, run in a box: {@code 6 CO2 + 6 H2O + light -> C6H12O6 + 6 O2}, real and
 * balanced (see {@code design/hydroponics.md} §1.1). One water bottle is this mod's own real
 * batch-charge convention (a fixed, discrete real charge, the same abstraction every other batch
 * machine here already uses); the room's own CO2 either covers that whole charge or the batch
 * holds rather than running on a shortfall, the same all-or-nothing reagent gate
 * {@code TroiliteRoasterBlockEntity} already uses for its own single room reagent.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Photosynthesis {

    /** Real 1:1:1:6 molar ratio, read as 1:1 between CO2 consumed and water reacted, and 1:1
     *  between O2 released and CO2 consumed. */
    public static final double CO2_PER_MOL_WATER = 1.0;
    public static final double O2_PER_MOL_CO2 = 1.0;

    /** Real water in one bottle, treated as this mod's own batch-charge convention. */
    public static final double WATER_MOL_PER_BOTTLE = 3.0;

    /** Real CO2 one water-bottle charge needs, at the balanced ratio. */
    public static final double CO2_PER_BOTTLE_MOL = WATER_MOL_PER_BOTTLE * CO2_PER_MOL_WATER;

    /** What one batch actually achieves: nothing at all unless the room's own CO2 covers the
     *  whole bottle's real charge. */
    public record Batch(double co2ConsumedMol, double o2ProducedMol) {
        public boolean ran() {
            return co2ConsumedMol > 0;
        }
    }

    /** Runs one water-bottle charge against whatever CO2 the room actually has right now. */
    public static Batch run(double roomCo2Mol) {
        if (roomCo2Mol < CO2_PER_BOTTLE_MOL) {
            return new Batch(0, 0);
        }
        return new Batch(CO2_PER_BOTTLE_MOL, CO2_PER_BOTTLE_MOL * O2_PER_MOL_CO2);
    }

    private Photosynthesis() {}
}
