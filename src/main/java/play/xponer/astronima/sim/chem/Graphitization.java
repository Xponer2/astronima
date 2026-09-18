package play.xponer.astronima.sim.chem;

/**
 * Real high-temperature carbon chemistry, shared by two feeds and one vessel (see
 * {@code design/carbon-fiber.md}): amorphous carbon annealed into crystalline graphite (the
 * Acheson process, no reagent, ~2500-3000 °C), and a stabilized pitch fiber carbonized/graphitized
 * the same way. Both are the same real fact at the vessel's high setpoint - carbon reordering
 * itself under heat - so one model serves both feeds, and rule 8's single verb stays intact.
 *
 * <p><strong>Oxygen present at that heat does not block the anneal - it wins a race against
 * it.</strong> Hot carbon burns in air (C + O2 -> CO2) far faster than solid carbon reorders its
 * own lattice, so any real oxygen in the room is consumed by combustion instead of the intended
 * reaction: the same charge, a different real reaction on the other end of the same heat. That is
 * why {@link #stepHighSetpoint} is a per-tick step rather than a one-shot judgement - the room's
 * own oxygen can arrive or run out mid-batch, and the charge should answer to whichever is true
 * <em>right now</em>, the same real-time-actionable shape {@code CentrifugalBed}'s own entrainment
 * already is for the fluidized bed.
 *
 * <p>The low setpoint is the opposite real requirement: oxidative stabilization of a green pitch
 * fiber (~250-350 °C) is real cross-linking that <em>needs</em> oxygen to proceed at all, and
 * carries no combustion risk of its own at that temperature - starved of oxygen it simply does not
 * react, so {@link #stepLowSetpoint} has no burn branch to model.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Graphitization {

    /** Acheson graphitization / pitch-fiber carbonization-graphitization, real industrial range
     *  2500-3000 °C - see design/carbon-fiber.md §1. One shared number for both real processes
     *  (named simplification there). */
    public static final double HIGH_SETPOINT_K = 2773.15;

    /** Real oxidative stabilization window for a green pitch fiber, ~250-350 °C. */
    public static final double LOW_SETPOINT_K = 573.15;

    /** C + O2 -> CO2: one mole of oxygen burns one mole of carbon into one mole of carbon dioxide. */
    public static final double O2_PER_MOL_CARBON = 1.0;
    public static final double CO2_PER_MOL_CARBON = 1.0;

    /** A charge partway through the vessel: carbon still to process, and product already made. */
    public record Charge(double carbonMol, double productMol) {
        public static Charge of(double carbonMol) {
            return new Charge(Math.max(0, carbonMol), 0);
        }

        public boolean isSpent() {
            return carbonMol <= 0;
        }
    }

    /** One tick's worth of work at the high setpoint: how much burned instead of converting, and
     *  what that cost/made in the room. */
    public record Step(Charge charge, double o2ConsumedMol, double co2ProducedMol) {}

    /**
     * Steps a charge at the high setpoint by up to {@code rateMol} of carbon, burning instead of
     * converting for as much of that step as the room's own oxygen can actually sustain.
     *
     * <p>Combustion is capped by whichever is scarcer - the step itself, or the oxygen on hand -
     * so a charge outlasting a small O2 leak finishes the leak off and starts converting cleanly
     * again, while a room that keeps replenishing its own oxygen (an unfixed life-support leak)
     * keeps burning the charge away for as long as the player lets it run.
     *
     * @param charge     what is left to process, and what has already been made
     * @param roomO2Mol  real free oxygen in the room touching the vessel right now
     * @param rateMol    how much carbon this tick could process at most
     */
    public static Step stepHighSetpoint(Charge charge, double roomO2Mol, double rateMol) {
        double advance = Math.min(charge.carbonMol(), Math.max(0, rateMol));
        if (advance <= 0) {
            return new Step(charge, 0, 0);
        }
        double burned = Math.min(advance, Math.max(0, roomO2Mol) / O2_PER_MOL_CARBON);
        double converted = advance - burned;
        Charge next = new Charge(charge.carbonMol() - advance, charge.productMol() + converted);
        return new Step(next, burned * O2_PER_MOL_CARBON, burned * CO2_PER_MOL_CARBON);
    }

    /**
     * Steps a charge at the low setpoint by up to {@code rateMol}, real oxidative stabilization -
     * needs the room's oxygen to proceed at all, and simply does not advance without it (no
     * combustion risk at this temperature, so nothing is lost by waiting).
     */
    public static Charge stepLowSetpoint(Charge charge, double roomO2Mol, double rateMol) {
        if (roomO2Mol <= 0) {
            return charge;
        }
        double advance = Math.min(charge.carbonMol(), Math.max(0, rateMol));
        return new Charge(charge.carbonMol() - advance, charge.productMol() + advance);
    }

    private Graphitization() {}
}
