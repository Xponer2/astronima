package play.xponer.astronima.sim.chem;

/**
 * Real anaerobic digestion: bacteria break organic waste down, in the absence of oxygen, into
 * biogas and digestate (design/anaerobic-digestion.md §1). Real biogas runs roughly 50-70%
 * methane by volume, the rest mostly CO2 — a real representative point in that range, not a
 * per-batch-measured number.
 */
public final class AnaerobicDigestion {
    public static final double METHANE_FRACTION = 0.6;
    public static final double CO2_FRACTION = 1.0 - METHANE_FRACTION;

    /** A fixed real biogas yield per real waste item - the same abstraction level
     *  {@link Photosynthesis#CO2_PER_BOTTLE_MOL} already picks, not a literal gram-for-gram mass
     *  balance this project has no real crop-waste mass figure to back honestly. */
    public static final double BIOGAS_MOL_PER_WASTE_ITEM = 3.0;
    public static final double METHANE_MOL_PER_WASTE_ITEM =
            BIOGAS_MOL_PER_WASTE_ITEM * METHANE_FRACTION;
    public static final double CO2_MOL_PER_WASTE_ITEM = BIOGAS_MOL_PER_WASTE_ITEM * CO2_FRACTION;

    public record Batch(double methaneMol, double co2Mol) {}

    /** One real waste item digested - the only batch shape there is, since crop waste is either
     *  a whole item present or not, with no rationed/partial reagent case to gate on. */
    public static Batch run() {
        return new Batch(METHANE_MOL_PER_WASTE_ITEM, CO2_MOL_PER_WASTE_ITEM);
    }

    private AnaerobicDigestion() {}
}
