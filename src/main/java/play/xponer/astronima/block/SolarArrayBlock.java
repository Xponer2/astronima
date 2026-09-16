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
import play.xponer.astronima.block.entity.SolarArrayBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A square metre of photovoltaic, making about as much as a bright lamp.
 *
 * <p>See {@link SolarArrayBlockEntity} for why that is the right amount and not a nerf. Its
 * placement problem is the retort's — open sky — because it is the same physical fact.
 */
public class SolarArrayBlock extends BaseEntityBlock implements PowerConnectable, play.xponer.astronima.wire.Terminated,
        play.xponer.astronima.wire.SignalSource {

    /**
     * Drives its status stud while the sun is actually on it.
     *
     * <p>So a base can know it is daytime without a clock, which is the honest way round: the
     * panel is the thing that can see the sky, and half the belt's problem is that it cannot see
     * it for very long.
     */
    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level,
                             net.minecraft.core.BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        return terminal.kind() == play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT
                && level.getBlockEntity(pos)
                        instanceof play.xponer.astronima.block.entity.SolarArrayBlockEntity array
                && array.sunlight() > 0.05f;
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

    public static final MapCodec<SolarArrayBlock> CODEC = simpleCodec(SolarArrayBlock::new);

    public SolarArrayBlock(Properties properties) {
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
        return new SolarArrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SOLAR_ARRAY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((SolarArrayBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
