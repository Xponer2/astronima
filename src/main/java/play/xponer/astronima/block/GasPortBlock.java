package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.GasPortBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Where a pipe run opens into a room.
 *
 * <p>A pipe on its own connects nothing to nothing — it conducts, but only between
 * things that hold gas. A port is the opening: set it in a wall, run pipe to it, and
 * the room on its open face becomes a node in the network.
 *
 * <p>It is a block rather than an inference for a reason. A pipe end could simply vent
 * into whatever air it happened to touch, which would be less to build and would make
 * every leak invisible: gas would cross between pipe and room at places the player
 * never chose and cannot see. With a port, if gas is crossing there is a block saying
 * so, and breaking it stops it.
 *
 * <p>The port itself moves nothing. It is a boundary, not a mover — see
 * design/plumbing.md §2.5 for why that distinction is kept sharp.
 */
public class GasPortBlock extends BaseEntityBlock {
    /** The face that opens into the room; the opposite side takes the pipe. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public GasPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Placed opening away from the face clicked, so it faces into the room you are
     * standing in when you set it into a wall.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    public static final MapCodec<GasPortBlock> CODEC = simpleCodec(GasPortBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** An ordinary block model, not the invisible default a BaseEntityBlock assumes. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.GAS_PORT.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((GasPortBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }

    /** The block space this port reads its room from. */
    public static BlockPos roomSide(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING));
    }
}
