package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;
import play.xponer.astronima.power.PowerConnectable;

/**
 * Joules, kept somewhere — and the reason an array in the belt is usable at all.
 *
 * <p>What a store is for here is the <em>night</em>, not capacity: the panel makes nothing for
 * half the day, so the cell is what turns a trickle into a machine that runs when you are
 * there to use it. With no cables in this slice it feeds whatever it is touching.
 */
public class PowerCellBlock extends BaseEntityBlock implements PowerConnectable, play.xponer.astronima.wire.Terminated,
        play.xponer.astronima.wire.SignalSource {

    /**
     * Drives its status stud while it holds any charge at all.
     *
     * <p>The most useful thing a battery can say, and the one a NOT gate turns into the thing you
     * actually want an alarm on: <em>flat</em>. Reporting a percentage would need a threshold
     * invented here rather than chosen by the player; reporting empty-or-not needs none.
     */
    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level,
                             net.minecraft.core.BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        return terminal.kind() == play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT
                && level.getBlockEntity(pos)
                        instanceof play.xponer.astronima.block.entity.PowerCellBlockEntity cell
                && cell.storedJ() > 0;
    }

    /** Power terminals on all four sides, so it can be wired from whichever side is free. */
    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(
            net.minecraft.world.level.block.state.BlockState state) {
        java.util.List<play.xponer.astronima.wire.Terminal> found = new java.util.ArrayList<>(8);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_POWER_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.POWER));
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_OUT_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT));
        }
        return found;
    }

    public static final MapCodec<PowerCellBlock> CODEC = simpleCodec(PowerCellBlock::new);

    public PowerCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCellBlockEntity(pos, state);
    }
}
