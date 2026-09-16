package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.FuelCellBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The clean generator: three times cheaper in oxygen than the burner, and a fifth as hot.
 *
 * <p>See {@link FuelCellBlockEntity} for why it draws from vessels rather than breathing the
 * room, and {@code design/power.md} §P9 for why it is a different machine from the combustion
 * generator rather than the same one with a fuel switch.
 */
public class FuelCellBlock extends BaseEntityBlock implements PowerConnectable, play.xponer.astronima.wire.Terminated {

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

    public static final MapCodec<FuelCellBlock> CODEC = simpleCodec(FuelCellBlock::new);

    public FuelCellBlock(Properties properties) {
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
        return new FuelCellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FUEL_CELL.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((FuelCellBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
