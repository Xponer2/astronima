package play.xponer.astronima.sim.ore;

/**
 * Crushing, and the only thing it really controls: how free the mineral grains are.
 *
 * <p>Ore does not arrive as separate minerals. It arrives as composite particles, each
 * holding several minerals locked together, and no separator can sort a particle that
 * is half valuable and half waste — it has to go somewhere, and wherever it goes it is
 * wrong. Crushing exists to break those composites apart, which is called
 * <em>liberation</em>, and it is the single most important variable in mineral
 * processing.
 *
 * <p>Liberation improves as particle size falls, but with sharply diminishing returns:
 * halving the size again and again buys less each time, while the energy cost keeps
 * climbing. Real plants live on this curve. So does this one.
 */
public final class Comminution {
    /** Coarsest useful product, in micrometres — barely broken at all. */
    public static final double COARSEST_MICRONS = 2000.0;

    /** Finest a hand-worked mill will reach. Below this you are wasting effort. */
    public static final double FINEST_MICRONS = 20.0;

    /**
     * Grain size of the minerals themselves. Liberation is essentially complete once
     * particles are smaller than the grains they are made of, which is why this — and
     * not some tuned constant — sets where the curve flattens.
     */
    public static final double GRAIN_SIZE_MICRONS = 120.0;

    /**
     * Particle size produced by a crusher setting.
     *
     * @param setting 0 for the coarsest the machine will run, 1 for the finest
     */
    public static double particleSizeMicrons(double setting) {
        double t = Math.clamp(setting, 0.0, 1.0);
        // Geometric rather than linear: crusher settings are ratios, not offsets, and
        // a linear dial would spend most of its travel in sizes nobody wants.
        return COARSEST_MICRONS * Math.pow(FINEST_MICRONS / COARSEST_MICRONS, t);
    }

    /**
     * Fraction of mineral grains freed from composite particles, 0..1.
     *
     * <p>The curve is the real one: liberation rises as particle size approaches the
     * mineral grain size and then flattens, because once a particle is smaller than a
     * grain there is nothing left to liberate.
     */
    public static double liberation(double particleSizeMicrons) {
        double ratio = GRAIN_SIZE_MICRONS / Math.max(1e-6, particleSizeMicrons);
        return ratio / (1.0 + ratio);
    }

    /** Convenience: liberation straight from a crusher setting. */
    public static double liberationFromSetting(double setting) {
        return liberation(particleSizeMicrons(setting));
    }

    /**
     * Work needed to reach a size, in arbitrary effort units per kilogram.
     *
     * <p>Bond's law: energy scales with the reciprocal square root of product size, so
     * fine grinding gets expensive fast. This is why "just grind everything to powder"
     * is not a strategy, and why the dial is a decision rather than a slider you push
     * to the end.
     */
    /**
     * How many size grades a crusher's product is sorted into.
     *
     * <p>A screen deck grades material into <em>fractions</em> — minus-60 plus-100, and so on — and
     * nobody stamps a sack with the exact micron it happened to come out at. So the product carries
     * a band rather than a reading, and two sacks from the same band are the same sack.
     *
     * <p>The count is not free. Once the jaws drift, every batch comes out at a slightly different
     * gap, and an exactly-stamped product would refuse to stack with the one before it — a machine
     * that jams its own output slot after one batch, which is what actually happened the first time
     * these two systems met. So a band has to be at least as wide as the drift a machine is allowed
     * to accumulate before the panel says it is worth re-calibrating, and {@code CalibrationTest}
     * holds the two numbers against each other.
     */
    public static final int GRADES = 9;

    /**
     * The band a jaw gap falls in, as a setting in the middle of that band.
     *
     * <p>Only the <em>label</em> is graded. The grind, the liberation and the work are all read
     * from where the jaws really are, because the rock does not round itself off.
     */
    public static double graded(double setting) {
        int steps = GRADES - 1;
        return Math.clamp(Math.round(Math.clamp(setting, 0.0, 1.0) * steps) / (double) steps,
                0.0, 1.0);
    }

    /** How wide one band is, as a fraction of the dial. */
    public static double gradeWidth() {
        return 1.0 / (GRADES - 1);
    }

    public static double workPerKilogram(double particleSizeMicrons) {
        return 10.0 * (1.0 / Math.sqrt(Math.max(1e-6, particleSizeMicrons))
                - 1.0 / Math.sqrt(COARSEST_MICRONS));
    }

    /**
     * Work for one batch at the widest gap, before Bond's law adds to it.
     *
     * <p>Was a literal duplicated in {@code OreCrusherBlockEntity}, {@code MachineBody}
     * and {@code ProcessingRecipe} — three copies of the same formula, which is exactly the
     * scar rule 46 exists for. Kept here as the one the crusher, the codex calculator
     * ({@code Calculators}) and any future reader now share.
     */
    public static final int BASE_WORK = 90;

    /** How many effort units one full batch costs at this product size, {@link #BASE_WORK}
     *  included. */
    public static int workPerBatch(double particleSizeMicrons) {
        return BASE_WORK + (int) Math.round(workPerKilogram(particleSizeMicrons) * 45);
    }

    private Comminution() {}
}
