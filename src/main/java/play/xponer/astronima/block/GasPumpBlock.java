package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The part that lifts pressure.
 *
 * <p>A pipe only ever equalises — it moves gas down a pressure difference and stops when
 * both ends match. A pump does work: it moves gas <em>against</em> a gradient, which is
 * the only way to fill a tank above room pressure or pull a room down toward vacuum.
 *
 * <p>Positive displacement, so it sweeps a fixed volume per second at inlet conditions
 * and the moles it moves fall as the inlet empties. Past its rated outlet pressure the
 * swept volume no longer clears and it stalls — which is why the fix for a stalled pump
 * is a bigger pump rather than more patience.
 *
 * <p>Inline: it splits a run into an inlet side and an outlet side. It faces the way it
 * pushes.
 */
public class GasPumpBlock extends BaseEntityBlock {
    public static final MapCodec<GasPumpBlock> CODEC = simpleCodec(GasPumpBlock::new);

    /** The direction the pump pushes toward: its outlet face. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** True while it is actually moving gas, so a stall is visible without a menu. */
    public static final BooleanProperty RUNNING = BlockStateProperties.LIT;

    public GasPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(RUNNING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, RUNNING);
    }

    /** Placed pushing away from the player, which is how you point a pump at a tank. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Where the pump draws from, and where it pushes to. */
    public static BlockPos inletSide(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    public static BlockPos outletSide(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasPumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.GAS_PUMP.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((GasPumpBlockEntity) blockEntity).serverTick(tickLevel, pos, tickState);
    }
}
