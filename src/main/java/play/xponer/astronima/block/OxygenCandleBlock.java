package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import play.xponer.astronima.atmosphere.Ignition;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.OxygenCandleBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A chlorate oxygen candle — the real "Vika" generator flown on Mir and carried as
 * ISS backup. Sodium chlorate mixed with iron powder burns self-sustained (the
 * chlorate provides its own oxidizer), releasing hot oxygen. Strike it once and it
 * runs to completion: real candles cannot be throttled or put out, and neither can
 * this one — plan the room size before lighting it.
 */
public class OxygenCandleBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");

    public OxygenCandleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, SPENT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (state.getValue(LIT) || state.getValue(SPENT)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            // The striker cap is a spark source: in a flammable atmosphere the strike
            // itself sets off the room — and the candle likely doesn't survive it.
            if (Ignition.tryIgnite(serverLevel, pos)) {
                return InteractionResult.SUCCESS;
            }
            level.setBlockAndUpdate(pos, state.setValue(LIT, true));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new OxygenCandleBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.OXYGEN_CANDLE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((OxygenCandleBlockEntity) blockEntity).serverTick(tickLevel, pos, tickState);
    }
}
