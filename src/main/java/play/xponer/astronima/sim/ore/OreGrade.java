package play.xponer.astronima.sim.ore;

/**
 * What kind of rock a mined block was, and how much of it one item represents.
 *
 * <p>An item stack cannot carry a full mineral assemblage without becoming unique:
 * two stacks whose gram counts differ by a rounding error would refuse to stack, and
 * an inventory full of one-item piles is a worse outcome than any fidelity it buys.
 * So a crushed-ore item carries its <em>grade</em> and its <em>fineness</em>, and the
 * assemblage is reconstructed from those on demand.
 *
 * <p>This is a deliberate simplification and worth naming: it means all ore of one
 * grade is identical, so you cannot high-grade a particularly rich block. In exchange
 * the process stays legible and the inventory stays usable, and every number the
 * player sees still comes from the real model.
 */
public enum OreGrade {
    /** Ordinary carbonaceous chondrite: a few percent native metal, mostly silicate. */
    CHONDRITE("Chondrite"),

    /** A metal-rich seam. Rarer, and the reason prospecting is worth the walk. */
    METAL_RICH("Metal-rich seam");

    /** Grams of rock one mined block yields. A block is a lot of rock. */
    public static final double GRAMS_PER_BLOCK = 4000.0;

    private final String displayName;

    OreGrade(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The mineral assemblage one item of this grade represents. */
    public OreBody body() {
        return body(GRAMS_PER_BLOCK);
    }

    public OreBody body(double grams) {
        return switch (this) {
            case CHONDRITE -> OreBody.chondrite(grams);
            case METAL_RICH -> OreBody.metalRich(grams);
        };
    }

    /**
     * Packs a grade and a crusher setting into one integer for a data component.
     *
     * <p>Fineness is stored as whole percent. That is coarser than the crusher dial
     * but far finer than anyone can act on, and it means two batches crushed at the
     * same setting stack together — which is the entire point of not storing the
     * assemblage itself.
     */
    public static int pack(OreGrade grade, double fineness) {
        int percent = Math.clamp((int) Math.round(fineness * 100), 0, 100);
        return (grade.ordinal() << 8) | percent;
    }

    public static OreGrade gradeOf(int packed) {
        int index = (packed >> 8) & 0xF;
        OreGrade[] values = values();
        return index < values.length ? values[index] : CHONDRITE;
    }

    /** The crusher setting this batch was ground at, 0..1. */
    public static double finenessOf(int packed) {
        return Math.clamp(packed & 0xFF, 0, 100) / 100.0;
    }
}
