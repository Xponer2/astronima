package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.metal.LaserSintering;

/**
 * Selective laser sintering: fuses a bed of iron powder into a printed part, track by track.
 *
 * <p><strong>The first machine in the mod whose control is a plane, not a line</strong>
 * ({@code design/sls.md} §2). Every sibling machine's dial was removed in favour of a value the
 * machine computes for itself, because the right answer moved on its own — the retort's mirror
 * aims itself, the refiner's vessel finds its own equilibrium, the bed's drum brackets its own
 * feed. None of that applies here: there is no formula that hands back the one correct
 * ({@link #powerW}, {@link #speedMmS}) pair from the feed or the power network, because the real
 * process this machine models has no such formula either — mapping the plane by hand is what
 * additive-manufacturing process development *is*. So this is the one machine in the mod that
 * keeps a real, player-set control, and it needs two independent axes rather than one because the
 * same energy density reached fast versus slow prints a different part (rule 8).
 *
 * <p><strong>Grid power still only buys rate, never quality</strong> (`machines.md` §7, same as
 * every powered machine here): the electrical draw this class inherits from
 * {@link ProcessingBlockEntity} decides how many batches a minute this printer gets through.
 * {@link #powerW} is a completely different number — the laser's own output, a process
 * parameter the player sets on the plane — and nothing here ever lets one substitute for the
 * other.
 */
public class SlsPrinterBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Iron powder spent per printed part. */
    public static final int FEED_PER_BATCH = 4;

    /** Work for one part. Slow and precise, matching a laser tracing a bed one line at a time. */
    public static final int BATCH_WORK = 500;

    /** A safe, working starting point: comfortably inside the sound pocket. */
    public static final double DEFAULT_POWER_W = 60.0;
    public static final double DEFAULT_SPEED_MMS = 200.0;

    private double powerW = DEFAULT_POWER_W;
    private double speedMmS = DEFAULT_SPEED_MMS;

    public SlsPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SLS_PRINTER.get(), pos, state, 2);
    }

    /** No drift, no wrench: the plane is a real control, moved by the player directly. */
    @Override
    public Calibration.Drift drift() {
        return Calibration.Drift.NONE;
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.SLS;
    }

    public double powerW() {
        return powerW;
    }

    public double speedMmS() {
        return speedMmS;
    }

    /**
     * Moves the marker on the plane. Both axes together, from the two fractions
     * {@link play.xponer.astronima.network.SlsControlPayload} carries — a click or a drag always
     * sets power and speed in the same motion, the same way dragging a point on a real plane
     * always sets both its coordinates at once.
     */
    public void setFromDials(double power01, double speed01) {
        powerW = LaserSintering.MIN_POWER_W + Math.clamp(power01, 0, 1)
                * (LaserSintering.MAX_POWER_W - LaserSintering.MIN_POWER_W);
        speedMmS = LaserSintering.MIN_SPEED_MMS + Math.clamp(speed01, 0, 1)
                * (LaserSintering.MAX_SPEED_MMS - LaserSintering.MIN_SPEED_MMS);
        setChanged();
    }

    /** Which corner of the process plane the current dials land in. */
    public LaserSintering.Regime regime() {
        return LaserSintering.regime(powerW, speedMmS);
    }

    /** How sound a part printed right now would come out, 0..1. */
    public double soundness() {
        return LaserSintering.soundness(powerW, speedMmS);
    }

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        ItemStack feed = getItem(SLOT_INPUT);
        return feed.is(ModItems.IRON_POWDER.get()) && feed.getCount() >= FEED_PER_BATCH;
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, new ItemStack(ModItems.SINTERED_FRAME.get()));
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    /**
     * Whatever the plane is set to when the batch finishes, not when it started — the same
     * "read the dial at the moment it matters" rule every calibration-free machine here follows.
     * A part is a snapshot of one operating point, never an average of a run the player was
     * still adjusting.
     */
    @Override
    protected void finishBatch() {
        ItemStack feed = getItem(SLOT_INPUT);
        if (!feed.is(ModItems.IRON_POWDER.get()) || feed.getCount() < FEED_PER_BATCH) {
            return;
        }
        ItemStack part = new ItemStack(ModItems.SINTERED_FRAME.get());
        part.set(ModDataComponents.SINTER_SOUNDNESS.get(), (float) soundness());
        pushOutput(SLOT_OUTPUT, part);
        feed.shrink(FEED_PER_BATCH);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("power_w", powerW);
        output.putDouble("speed_mms", speedMmS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        powerW = input.getDoubleOr("power_w", DEFAULT_POWER_W);
        speedMmS = input.getDoubleOr("speed_mms", DEFAULT_SPEED_MMS);
    }
}
