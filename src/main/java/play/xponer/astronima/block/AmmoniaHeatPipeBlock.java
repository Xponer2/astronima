package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.AmmoniaHeatPipeBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

import java.util.EnumMap;
import java.util.Map;

/**
 * A sealed, passive conductor between whatever two rooms sit on either end of its own axis.
 * See {@link AmmoniaHeatPipeBlockEntity}. Shaped like a pipe segment, not a cube, the same
 * "visible and slim" convention every other fitting in the plumbing set already carries.
 */
public class AmmoniaHeatPipeBlock extends Block implements EntityBlock {
    /** Which way the pipe runs — the two rooms it joins sit at either end of this axis. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.Axis.Z, Block.box(5, 5, 0, 11, 11, 16),
            Direction.Axis.X, Block.box(0, 5, 5, 16, 11, 11),
            Direction.Axis.Y, Block.box(5, 0, 5, 11, 16, 11)));

    public AmmoniaHeatPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoniaHeatPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.AMMONIA_HEAT_PIPE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((AmmoniaHeatPipeBlockEntity) blockEntity).serverTick(tickLevel, pos, tickState);
    }
}
