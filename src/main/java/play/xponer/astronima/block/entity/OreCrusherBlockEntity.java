package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.OreGrade;

/**
 * A jaw crusher: ore in, crushed ore out, and one setting that decides everything
 * downstream.
 *
 * <p>The jaw gap is the whole tier. Crushing frees mineral grains from the composite
 * particles they are locked into, and how far you close the jaws decides what the
 * magnet can sort afterwards:
 *
 * <ul>
 *   <li><strong>Wide</strong> is quick, but grains stay bonded to silicate and leave
 *       with the tailings. Metal is lost.</li>
 *   <li><strong>Narrow</strong> frees nearly everything, but non-magnetic dust travels
 *       with the concentrate whatever it is. Purity is lost.</li>
 * </ul>
 *
 * <p>No setting avoids both, because opposite things cause them, and the cost of
 * closing the jaws is real: work per batch follows Bond's law, so the finest grind
 * takes roughly twice the turns of the coarsest. The player sets the gap on the
 * screen and watches liberation and cost move together.
 */
public class OreCrusherBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Work for one batch at the widest gap, before Bond's law adds to it. Re-exported from
     *  {@link Comminution#BASE_WORK} - that is the canonical value now (rule 46: this constant
     *  and the formula in {@link #workRequired()} used to be duplicated in three other files),
     *  kept as a field here because external code already reads it by this name. */
    public static final int BASE_WORK = Comminution.BASE_WORK;

    public OreCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_CRUSHER.get(), pos, state, 2);
    }

    /**
     * Liner wear opens the jaws.
     *
     * <p>The textbook case, and the one the whole mechanic was built from: real plants measure the
     * closed side setting on a schedule by crushing a lead ball between the jaws and re-shimming.
     * See {@code design/calibration.md}.
     */
    @Override
    public Calibration.Drift drift() {
        return Calibration.Drift.CRUSHER;
    }

    /**
     * Where the jaws actually are.
     *
     * <p>Everything downstream — the grind, the liberation, the work per batch — reads this rather
     * than what was set, because the rock does not know what anybody intended.
     */
    public double setting() {
        return calibratedSetting();
    }

    public double particleSizeMicrons() {
        return Comminution.particleSizeMicrons(setting());
    }

    public double liberation() {
        return Comminution.liberation(particleSizeMicrons());
    }

    /** Bond's law: energy scales with the reciprocal square root of product size. */
    @Override
    public play.xponer.astronima.menu.ProcessingMenu.Kind kind() {
        return play.xponer.astronima.menu.ProcessingMenu.Kind.CRUSHER;
    }

    @Override
    public int workRequired() {
        return Comminution.workPerBatch(particleSizeMicrons());
    }

    @Override
    public boolean hasFeed() {
        return gradeOf(getItem(SLOT_INPUT)) != null || isIlmenite(getItem(SLOT_INPUT));
    }

    /** True when the feed is ilmenite ore, which crushes to its own pure-mineral product. */
    public static boolean isIlmenite(ItemStack feed) {
        return feed.is(ModItems.ILMENITE_ORE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        // With nothing to crush there is no batch to place, so nothing is in its way.
        return !hasFeed() || hasRoom(SLOT_OUTPUT, crushedResult());
    }

    @Override
    protected void finishBatch() {
        ItemStack result = crushedResult();
        if (result.isEmpty()) {
            return;
        }
        pushOutput(SLOT_OUTPUT, result);
        getItem(SLOT_INPUT).shrink(1);
    }

    /**
     * The gap this batch is <em>labelled</em> with, which is a band rather than a reading.
     *
     * <p>Stamping the exact gap made the crusher jam its own output slot: once the jaws drift,
     * every batch comes out a hair different and the product refuses to stack with the batch
     * before it. Screen decks grade material into fractions for the same reason people do
     * (see {@link Comminution#GRADES}). The grind itself is still read from where the jaws really
     * are - only the label rounds.
     */
    private double stampedGap() {
        return Comminution.graded(setting());
    }

    private ItemStack crushedResult() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (isIlmenite(feed)) {
            // A pure mineral, not an assemblage: it carries only the grind, which is the grain
            // size the fluidized bed's spin window slides on — the same jaw gap read a third way.
            ItemStack crushed = new ItemStack(ModItems.CRUSHED_ILMENITE.get());
            crushed.set(ModDataComponents.GRIND_FINENESS.get(),
                    (int) Math.round(stampedGap()
                            * play.xponer.astronima.menu.ProcessingMenu.SETTING_SCALE));
            return crushed;
        }
        OreGrade grade = gradeOf(feed);
        if (grade == null) {
            return ItemStack.EMPTY;
        }
        ItemStack crushed = new ItemStack(ModItems.CRUSHED_ORE.get());
        // Stamped with where the jaws ACTUALLY were, not where somebody meant them to be - to the
        // nearest size band, so a drifting machine still fills a sack rather than one item.
        crushed.set(ModDataComponents.ORE_BATCH.get(), OreGrade.pack(grade, stampedGap()));
        return crushed;
    }

    /** Which assemblage a feed item represents, or null when it is not ore. */
    public static OreGrade gradeOf(ItemStack feed) {
        if (feed.is(ModItems.METAL_RICH_ORE.get())) {
            return OreGrade.METAL_RICH;
        }
        // Plain asteroid rock is ore too. Only a few percent metal, but the point of
        // the tier is that ordinary ground is worth processing at all.
        return feed.is(ModItems.ASTEROID_ROCK.get()) ? OreGrade.CHONDRITE : null;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && (gradeOf(stack) != null || isIlmenite(stack));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    }
}
