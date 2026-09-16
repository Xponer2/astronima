package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.power.PowerBalance;

/**
 * Joules, kept somewhere.
 *
 * <p>The point of a store in this tier is not capacity, it is <strong>the night</strong>. An
 * array in the belt makes 37 W a panel and makes nothing at all for half the day, so a cell is
 * what turns an intermittent trickle into a machine that runs when you are there to use it.
 *
 * <p>No cables yet, deliberately (see {@code design/power.md} §5): a cell feeds what it is
 * touching. That is honest for a first electrical tier — you bolt the cell to the machine — and
 * it leaves the network for a slice that can reuse the gas network's shape rather than
 * inventing a second one in a hurry.
 */
public class PowerCellBlockEntity extends ReadableBlockEntity {

    /**
     * What one cell holds, in joules.
     *
     * <p>Sized against the thing it exists for: a quarter of an hour of one machine at its
     * rated 250 W. Enough that a night is survivable with a few cells and an array; not so
     * much that one cell makes the array's feebleness stop mattering.
     */
    public static final double CAPACITY_J = 250.0 * 900.0;

    private double storedJ;

    public PowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CELL.get(), pos, state);
    }

    public double storedJ() {
        return storedJ;
    }

    /** How full it is, 0..1 — what the indicator on the block draws (rule 9). */
    public float charge() {
        return (float) Math.clamp(storedJ / CAPACITY_J, 0.0, 1.0);
    }

    /**
     * Puts energy in, and returns how much would not fit.
     *
     * <p>The remainder is handed back rather than swallowed, so a caller can tell "the cell
     * took it" from "the cell was full" — for a solar array those are the same outcome and
     * for a cell charging another they are not, and a method that hid the difference would
     * make the second one impossible to write correctly later.
     */
    public double charge(double offeredJ) {
        double taken = PowerBalance.acceptable(storedJ, CAPACITY_J, offeredJ);
        if (taken > 0) {
            storedJ += taken;
            setChanged();
        }
        return offeredJ - taken;
    }

    /** Takes energy out, and returns how much there actually was to take. */
    public double draw(double wantedJ) {
        double taken = PowerBalance.drawn(storedJ, wantedJ);
        if (taken > 0) {
            storedJ -= taken;
            setChanged();
        }
        return taken;
    }

    /**
     * The cell touching this position, or null when nothing is.
     *
     * <p>Shared by everything that generates and everything that draws, so a machine and an
     * array can never disagree about what counts as "connected" — which with no cables is the
     * entire wiring rule and therefore worth having exactly one copy of.
     */
    public static @Nullable PowerCellBlockEntity adjacentTo(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side))
                    instanceof PowerCellBlockEntity cell) {
                return cell;
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("stored_j", storedJ);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedJ = Math.clamp(input.getDoubleOr("stored_j", 0.0), 0.0, CAPACITY_J);
    }
}
