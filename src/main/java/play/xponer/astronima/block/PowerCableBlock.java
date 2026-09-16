package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import play.xponer.astronima.power.PowerConnectable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A run of conductor, and the only thing in this tier that turns power into heat on purpose.
 *
 * <p>It is not the gas pipe with electrons in it. A pipe is limited by pressure and throttled
 * by a valve; a cable is limited by <strong>length</strong>, and what a long one takes it does
 * not merely lose — it deposits along its own body as heat. See {@code sim/circuit/Delivery}
 * for the arithmetic and {@code design/power.md} §P7 for why that is the point rather than a
 * penalty.
 *
 * <p>No block entity: a cable holds nothing. The run is resolved by whatever is drawing through
 * it, the same way a gas run is resolved by the pump rather than by the pipe.
 *
 * <p><strong>Shape.</strong> The same connected shape the gas pipe uses — a slim core with an
 * arm toward each neighbour it joins — because a run the player laid should read as a run and
 * not as a wall (rule 15). What differs is only where "joined" comes from: a cable connects to
 * everything on the electrical network, marked by {@link PowerConnectable}, rather than to the
 * gas fittings. Thinner than the pipe on purpose, so a cable and a pipe are never confused at a
 * glance.
 */
public class PowerCableBlock extends Block implements PowerConnectable {
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

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

    /** A four-pixel core: a wire, and visibly thinner than the pipe's six-pixel conduit. */
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6),
            Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 6, 10, 10),
            Direction.EAST, Block.box(10, 6, 6, 16, 10, 10),
            Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10),
            Direction.UP, Block.box(6, 10, 6, 10, 16, 10)));

    public PowerCableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static BooleanProperty property(Direction direction) {
        return BY_DIRECTION.get(direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /**
     * True when a cable should join, and reach an arm toward, whatever is on this side.
     *
     * <p>Joins to everything on the electrical network — other cables, the store, the sources
     * and the machines that draw — and to nothing else, so a run's shape is the shape of the
     * wiring and not of the wall it is stapled to. What is on the network is declared by the
     * blocks themselves via {@link PowerConnectable}, which is the one place that answer lives.
     */
    public static boolean connectsTo(BlockState neighbour) {
        return neighbour.getBlock() instanceof PowerConnectable;
    }

    private BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            result = result.setValue(BY_DIRECTION.get(direction),
                    connectsTo(level.getBlockState(pos.relative(direction))));
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
                connectsTo(neighbourState));
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
