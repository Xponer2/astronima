package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.MagneticSeparation;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.ore.OreGrade;

/**
 * A drum magnet: crushed ore in, concentrate and tailings out.
 *
 * <p>Two output streams is the entire point. A machine with one output is a furnace; a
 * machine that divides a stream into a good half and a bad half is beneficiation, and
 * watching the split is what teaches the recovery-versus-grade curve that the crusher's
 * jaw gap controls.
 *
 * <p>It costs nothing to run but motion — no oxygen, no heat, no reagents — which is
 * the only reason an extraction step is possible at all on a rock where fire is not.
 * What it cannot do matters as much: no field strength will pull nickel out of
 * pentlandite, because pentlandite is not magnetic. Watching good nickel ore go
 * straight to the tailings is the intended way to discover that the chemical tier has
 * to exist.
 */
public class MagneticSeparatorBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_CONCENTRATE = 1;
    public static final int SLOT_TAILINGS = 2;

    /** Grams of recovered metal per grain item. */
    private static final double GRAMS_PER_GRAIN = 55.0;

    /** Tailings are bulk: one item stands for a lot of spoil. */
    private static final double GRAMS_PER_TAILING = 1200.0;

    private float lastRecovery;
    private float lastGrade;

    public MagneticSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGNETIC_SEPARATOR.get(), pos, state, 3);
    }

    /**
     * Field strength as the drum is actually running it.
     *
     * <p>Set with a wrench on the machine and drifting down from there as the magnet warms, so
     * this is not what anybody dialled — see {@code design/calibration.md}.
     */
    public double field() {
        return MagneticSeparation.fieldFor(calibratedSetting());
    }

    /** A drum out of the crate is wound fairly hard: recovery first, grade second. */
    @Override
    protected double defaultSetting() {
        return MagneticSeparation.dialForField(0.75);
    }

    /**
     * The magnet warms with use, so the field falls away from where it was set.
     *
     * <p>Field strength trades throughput against selectivity — a stronger field catches more of
     * the magnetic fraction but takes longer to pull it through the drum — and that is a decision
     * an operator makes once for the ore they are running, which is exactly the kind of setting
     * that belongs on the machine rather than in a window.
     */
    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.SEPARATOR;
    }

    public float lastRecovery() {
        return lastRecovery;
    }

    public float lastGrade() {
        return lastGrade;
    }

    @Override
    public play.xponer.astronima.menu.ProcessingMenu.Kind kind() {
        return play.xponer.astronima.menu.ProcessingMenu.Kind.SEPARATOR;
    }

    @Override
    public int workRequired() {
        // Winding the field harder means turning the drum slower.
        return MagneticSeparation.BASE_WORK + (int) Math.round(field() * 90);
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.CRUSHED_ORE.get());
    }

    /** Both halves must land: a full tailings bin stops the drum as surely as a full one of ore. */
    @Override
    public boolean hasRoomForProduct() {
        ItemStack input = getItem(SLOT_INPUT);
        if (!input.is(ModItems.CRUSHED_ORE.get())) {
            return true;
        }
        Split split = split(input);
        return hasRoom(SLOT_CONCENTRATE, split.grains()) && hasRoom(SLOT_TAILINGS, split.tailings());
    }

    @Override
    protected void finishBatch() {
        ItemStack input = getItem(SLOT_INPUT);
        Split split = split(input);
        lastRecovery = (float) split.recovery();
        lastGrade = (float) split.grade();

        pushOutput(SLOT_CONCENTRATE, split.grains());
        pushOutput(SLOT_TAILINGS, split.tailings());
        input.shrink(1);
    }

    /** The two streams plus the numbers the screen reports. */
    public record Split(ItemStack grains, ItemStack tailings, double recovery, double grade) {
        public static Split nothing() {
            return new Split(ItemStack.EMPTY, ItemStack.EMPTY, 0, 0);
        }
    }

    public Split split(ItemStack batch) {
        return run(batch, field());
    }

    /** Runs the model and converts its two streams into items. */
    public static Split run(ItemStack batch, double fieldStrength) {
        if (!batch.is(ModItems.CRUSHED_ORE.get())) {
            return Split.nothing();
        }
        int packed = batch.getOrDefault(ModDataComponents.ORE_BATCH.get(), 0);
        OreGrade grade = OreGrade.gradeOf(packed);
        double fineness = OreGrade.finenessOf(packed);

        OreBody feed = grade.body();
        double liberation = Comminution.liberationFromSetting(fineness);
        MagneticSeparation.Result result =
                MagneticSeparation.separate(feed, liberation, fineness, fieldStrength);

        // Only native metal leaves this tier as usable metal. Magnetite reports to the
        // concentrate too, but its iron is bonded to oxygen and stays that way until
        // something can break the bond — which is what tier three is for.
        double nativeMetal = result.concentrate().massOf(Mineral.KAMACITE)
                * Mineral.KAMACITE.metalMassFraction();

        int grains = (int) Math.floor(nativeMetal / GRAMS_PER_GRAIN);
        int spoil = (int) Math.floor(result.tailings().totalMass() / GRAMS_PER_TAILING);

        return new Split(
                grains > 0 ? new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), grains) : ItemStack.EMPTY,
                spoil > 0 ? new ItemStack(ModItems.TAILINGS.get(), spoil) : ItemStack.EMPTY,
                result.recovery(), result.grade());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && stack.is(ModItems.CRUSHED_ORE.get());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("last_recovery", lastRecovery);
        output.putFloat("last_grade", lastGrade);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // A separator saved before the magnet could warm keeps the field it was left at.
        double legacyField = legacyValue(input, "field");
        if (!Double.isNaN(legacyField)) {
            setCalibratedTo(MagneticSeparation.dialForField(legacyField));
        }
        lastRecovery = input.getFloatOr("last_recovery", 0f);
        lastGrade = input.getFloatOr("last_grade", 0f);
    }
}
