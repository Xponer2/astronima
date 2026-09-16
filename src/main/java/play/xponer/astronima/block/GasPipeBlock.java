package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * A run of gas line.
 *
 * <p>Connects to its neighbours on all six faces, so the player builds the topology by
 * placing blocks and the game reads it — the same bargain room detection already makes
 * with air. There is no menu in which to declare what is joined to what, because a pipe
 * you can see is a better interface than a list you have to maintain.
 *
 * <p>The block itself holds no gas and does no simulation. It is the shape of the
 * network; {@code sim/pipe/GasNetwork} is the behaviour of it.
 */
public class GasPipeBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final Map<Direction, BooleanProperty> BY_DIRECTION =
            new EnumMap<>(Map.of(
                    Direction.NORTH, NORTH,
                    Direction.EAST, EAST,
                    Direction.SOUTH, SOUTH,
                    Direction.WEST, WEST,
                    Direction.UP, UP,
                    Direction.DOWN, DOWN));

    /** A slim core, so a run reads as plumbing rather than as a wall. */
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5),
            Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11),
            Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11),
            Direction.UP, Block.box(5, 11, 5, 11, 16, 11)));

    public GasPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    public static BooleanProperty property(Direction direction) {
        return BY_DIRECTION.get(direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /**
     * True when a pipe should join to whatever is on this side.
     *
     * <p>Joins to other pipes, and to anything that holds or moves gas. Deliberately
     * <em>not</em> to arbitrary solid blocks: a pipe that silently connected to a wall
     * would make the network's shape something the player cannot see.
     */
    public static boolean connectsTo(BlockState neighbour) {
        return neighbour.getBlock() instanceof GasPipeBlock
                || neighbour.getBlock() instanceof GasPortBlock
                || neighbour.getBlock() instanceof GasValveBlock
                || neighbour.getBlock() instanceof GasTankBlock
                || neighbour.getBlock() instanceof GasPumpBlock;
    }

    /**
     * True when a pipe should join {@code neighbour} reached by moving {@code toward} it.
     *
     * <p>Adds the directional rules the plain {@link #connectsTo} cannot express: a valve
     * is an inline fitting with two ports along its axis, and a pump has exactly one
     * suction and one discharge along its facing. A run joins either end-on and not
     * against the flank. Without this a pipe stuck on a valve's side drew an arm into
     * solid body and — worse — conducted through it; on a pump's side the arm joined a
     * face that takes nothing, which reads as plumbing and works as decoration.
     */
    public static boolean connectsToward(BlockState neighbour, Direction toward) {
        if (neighbour.getBlock() instanceof GasValveBlock) {
            return neighbour.getValue(GasValveBlock.AXIS) == toward.getAxis();
        }
        if (neighbour.getBlock() instanceof GasPumpBlock) {
            return neighbour.getValue(GasPumpBlock.FACING).getAxis() == toward.getAxis();
        }
        return connectsTo(neighbour);
    }

    private BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            boolean joined = connectsToward(level.getBlockState(pos.relative(direction)), direction);
            result = result.setValue(BY_DIRECTION.get(direction), joined);
        }
        return result;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        return state.setValue(BY_DIRECTION.get(directionToNeighbour),
                connectsToward(neighbourState, directionToNeighbour));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(BY_DIRECTION.get(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }
}
