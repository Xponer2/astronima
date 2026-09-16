package play.xponer.astronima.sim.codex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.MagneticSeparation;
import play.xponer.astronima.sim.ore.OreGrade;

/**
 * Every {@link Calculator} the codex can embed, published once (rule 20 — a hand-written list
 * in three places is how the winnowing table shipped with no JEI page at all).
 *
 * <p>Each calculator is a nested class here rather than its own file. Rule 13's own guard scans
 * per <em>file</em> under {@code sim/} for an outside consumer — a separate
 * {@code ComminutionCalculator.java} would need one of its own, and the only real consumer a
 * single-calculator file could ever gain is this registry, which is not "outside" in the sense
 * the guard means. Nesting keeps the file count honest: one file, one outside consumer
 * ({@code CodexUi}), one registry.
 */
public final class Calculators {
    private static final Map<String, Calculator> REGISTRY = new LinkedHashMap<>();

    static {
        register(new ComminutionCalculator());
        register(new SeparationCalculator());
    }

    private static void register(Calculator calculator) {
        REGISTRY.put(calculator.id(), calculator);
    }

    /** Every registered calculator, in registration order. */
    public static List<Calculator> all() {
        return List.copyOf(REGISTRY.values());
    }

    /** The calculator for a {@code {calc:id}} block, or {@code null} — an unknown id is the
     *  caller's problem to draw loudly (rule 18), not this registry's to hide. */
    public static Calculator get(String id) {
        return REGISTRY.get(id);
    }

    /**
     * {@code {calc:ore/comminution}} — the dial a wrench sets against the work since it was
     * last set right. Both inputs, and the gap between them, are
     * {@code sim/machine/Calibration}'s own mechanic; see
     * {@code design/codex-calculator-c4a.md} §2.3 for why these two and not the textbook
     * "feed size, product size, work index" the parent design first reached for.
     */
    private static final class ComminutionCalculator implements Calculator {
        private static final double PRESET_DIAL = 0.5;
        private static final double LIBERATION_GOOD = 0.5;
        private static final double LIBERATION_MARGINAL = 0.3;
        private static final double PRESET_WORK_PER_KG =
                Comminution.workPerKilogram(Comminution.particleSizeMicrons(PRESET_DIAL));

        private static final Input DIAL = new Input("dial", "astronima.calc.comminution.dial", "",
                0.0, 1.0, Comminution.gradeWidth(), PRESET_DIAL);
        private static final Input WORK = new Input("work", "astronima.calc.comminution.work", "",
                0.0, Calibration.Drift.CRUSHER.workToService() * 2.0, Comminution.BASE_WORK, 0.0);

        @Override
        public String id() {
            return "ore/comminution";
        }

        @Override
        public List<Input> inputs() {
            return List.of(DIAL, WORK);
        }

        @Override
        public List<Output> compute(List<Double> values) {
            double dial = Calculator.clampFinite(values.get(0), DIAL);
            double work = Calculator.clampFinite(values.get(1), WORK);

            double actual = Calibration.after(dial, Calibration.Drift.CRUSHER, work);
            double size = Comminution.particleSizeMicrons(actual);
            double liberation = Comminution.liberation(size);
            double workPerKg = Comminution.workPerKilogram(size);
            double stamped = Comminution.graded(actual);

            Verdict actualVerdict = Calibration.isWorthResetting(dial, actual)
                    ? Verdict.MARGINAL : Verdict.GOOD;
            Verdict liberationVerdict = liberation >= LIBERATION_GOOD ? Verdict.GOOD
                    : liberation >= LIBERATION_MARGINAL ? Verdict.MARGINAL : Verdict.BAD;
            Verdict workVerdict = workPerKg <= PRESET_WORK_PER_KG ? Verdict.GOOD
                    : workPerKg <= PRESET_WORK_PER_KG * 2 ? Verdict.MARGINAL : Verdict.BAD;

            return List.of(
                    new Output("actual", "astronima.calc.comminution.actual", "",
                            actual, Calculator.format("%.2f", actual), actualVerdict, ""),
                    new Output("size", "astronima.calc.comminution.size", "um",
                            size, Calculator.format("%.0f", size), Verdict.INFO, ""),
                    new Output("liberation", "astronima.calc.comminution.liberation", "%",
                            liberation, Calculator.format("%.0f", liberation * 100),
                            liberationVerdict, ""),
                    new Output("work_per_kg", "astronima.calc.comminution.work_per_kg", "",
                            workPerKg, Calculator.format("%.2f", workPerKg), workVerdict, ""),
                    new Output("stamped", "astronima.calc.comminution.stamped", "",
                            stamped, Calculator.format("%.3f", stamped), Verdict.INFO, ""));
        }
    }

    /**
     * {@code {calc:ore/separation}} — same shape as {@link ComminutionCalculator}: a wrench dial
     * plus work since it was last set right, {@code Drift.SEPARATOR}'s own mechanic. Two
     * departures from the parent design's "field strength, feed grade, particle size" roster,
     * checked against the real machine before writing this:
     *
     * <ul>
     * <li><b>Feed grade is fixed to {@code OreGrade.CHONDRITE}, not a player input.</b>
     * {@code MagneticSeparatorBlockEntity.run} reads grade and fineness off the crushed-ore
     * <em>item</em>'s own stamped data component, not a dial on the separator - there is nowhere
     * on the real machine a player picks a grade. {@link Calculator.Input} is a continuous
     * slider; a discrete ore-grade choice does not fit it, and widening the contract to carry
     * one is a bigger, cross-cutting change this leaf does not make on its own. Chondrite is the
     * ore the truth gametest already builds a crusher around.</li>
     * <li><b>"Particle size" is exposed as {@code fineness}</b> (0..1, the same dial-space
     * {@link ComminutionCalculator} uses), not a raw micron figure - it is standing in for
     * "however finely your crusher happened to be set", the actual thing that reaches the
     * separator on the item, and dial-space is what a player already reads off the crusher's
     * own panel.</li>
     * </ul>
     *
     * <p>{@code MIN_FIELD}/{@code MAX_FIELD}/{@code BASE_WORK}/{@code fieldFor}/
     * {@code dialForField} moved from {@code MagneticSeparatorBlockEntity} into
     * {@code MagneticSeparation} itself while building this (rule 46 again — this class could
     * not reach them without pulling a Minecraft-touching block entity into {@code sim/}, which
     * rule 1 refuses); {@code MagneticSeparatorBlockEntity} now calls the moved versions, and
     * {@code tools/textures.py}'s own port was pointed at the new file.
     */
    private static final class SeparationCalculator implements Calculator {
        private static final double PRESET_FINENESS = 0.5;
        private static final double PRESET_DIAL = MagneticSeparation.dialForField(0.75);
        private static final double RECOVERY_GOOD = 0.6;
        private static final double RECOVERY_MARGINAL = 0.35;
        private static final double GRADE_GOOD = 0.5;
        private static final double GRADE_MARGINAL = 0.3;

        private static final Input FINENESS = new Input("fineness", "astronima.calc.separation.fineness", "",
                0.0, 1.0, Comminution.gradeWidth(), PRESET_FINENESS);
        private static final Input DIAL = new Input("dial", "astronima.calc.separation.dial", "",
                0.0, 1.0, 0.05, PRESET_DIAL);
        private static final Input WORK = new Input("work", "astronima.calc.separation.work", "",
                0.0, Calibration.Drift.SEPARATOR.workToService() * 2.0,
                MagneticSeparation.BASE_WORK, 0.0);

        @Override
        public String id() {
            return "ore/separation";
        }

        @Override
        public List<Input> inputs() {
            return List.of(FINENESS, DIAL, WORK);
        }

        @Override
        public List<Output> compute(List<Double> values) {
            double fineness = Calculator.clampFinite(values.get(0), FINENESS);
            double dial = Calculator.clampFinite(values.get(1), DIAL);
            double work = Calculator.clampFinite(values.get(2), WORK);

            double actualDial = Calibration.after(dial, Calibration.Drift.SEPARATOR, work);
            double field = MagneticSeparation.fieldFor(actualDial);
            double liberation = Comminution.liberationFromSetting(fineness);
            MagneticSeparation.Result result = MagneticSeparation.separate(
                    OreGrade.CHONDRITE.body(), liberation, fineness, field);

            Verdict actualVerdict = Calibration.isWorthResetting(dial, actualDial)
                    ? Verdict.MARGINAL : Verdict.GOOD;
            Verdict recoveryVerdict = result.recovery() >= RECOVERY_GOOD ? Verdict.GOOD
                    : result.recovery() >= RECOVERY_MARGINAL ? Verdict.MARGINAL : Verdict.BAD;
            Verdict gradeVerdict = result.grade() >= GRADE_GOOD ? Verdict.GOOD
                    : result.grade() >= GRADE_MARGINAL ? Verdict.MARGINAL : Verdict.BAD;

            return List.of(
                    new Output("actual", "astronima.calc.separation.actual", "",
                            actualDial, Calculator.format("%.2f", actualDial), actualVerdict, ""),
                    new Output("field", "astronima.calc.separation.field", "",
                            field, Calculator.format("%.2f", field), Verdict.INFO, ""),
                    new Output("recovery", "astronima.calc.separation.recovery", "%",
                            result.recovery(), Calculator.format("%.0f", result.recovery() * 100),
                            recoveryVerdict, ""),
                    new Output("grade", "astronima.calc.separation.grade", "%",
                            result.grade(), Calculator.format("%.0f", result.grade() * 100),
                            gradeVerdict, ""));
        }
    }

    private Calculators() {}
}
