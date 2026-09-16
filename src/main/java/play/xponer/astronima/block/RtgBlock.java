package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.RtgBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.Terminated;

import java.util.ArrayList;
import java.util.List;

/**
 * A radioisotope thermoelectric generator's block: placement and ticking only, the physics is
 * all in {@link RtgBlockEntity} (design/radiation.md).
 */
public class RtgBlock extends BaseEntityBlock implements PowerConnectable, Terminated {

    @Override
    public List<Terminal> terminals(BlockState state) {
        List<Terminal> found = new ArrayList<>(8);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            found.add(new Terminal(side, Terminated.STRIP_POWER_U, 8, Terminal.Kind.POWER));
            found.add(new Terminal(side, Terminated.STRIP_OUT_U, 8, Terminal.Kind.SIGNAL_OUT));
        }
        return found;
    }

    public static final MapCodec<RtgBlock> CODEC = simpleCodec(RtgBlock::new);

    public RtgBlock(Properties properties) {
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
        return new RtgBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.RTG.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((RtgBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
