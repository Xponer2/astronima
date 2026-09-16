package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import play.xponer.astronima.item.WrenchItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.GasValveBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.pipe.Valve;

import java.util.EnumMap;
import java.util.Map;

/**
 * A bore you can narrow, set in line with a run.
 *
 * <p>A valve is not a percentage applied to a rate — it is a smaller hole, and a smaller
 * hole obeys the same fourth-power law as a pipe. Half open passes a sixteenth of the
 * flow, so nearly all of the useful range is in the last part of the travel. That is
 * what throttling a real line feels like, and it comes out of the physics rather than
 * from a curve chosen to feel right.
 *
 * <p>Shaped like what it is: a pipe-diameter body in line with the run, with a handwheel
 * on top, rather than a solid cube. It takes an {@link #AXIS} the way a pipe fitting does,
 * so it sits flush between the pipes on either side and the bore can be read by looking
 * down the line. Right-click to step the setting; the wheel turns to match and the bore
 * narrows, so a run's settings can be read by walking it rather than opening five menus.
 */
public class GasValveBlock extends BaseEntityBlock {
    public static final MapCodec<GasValveBlock> CODEC = simpleCodec(GasValveBlock::new);

    /** 0 is shut, {@link Valve#SETTINGS}-1 is full bore. */
    public static final IntegerProperty SETTING =
            IntegerProperty.create("setting", 0, Valve.SETTINGS - 1);

    /** Which way the run passes through it — the valve is an inline fitting, not a cube. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** The slim body, one per axis, so it reads and collides as a pipe rather than a wall. */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.Axis.Z, Block.box(5, 5, 0, 11, 11, 16),
            Direction.Axis.X, Block.box(0, 5, 5, 16, 11, 11),
            Direction.Axis.Y, Block.box(5, 0, 5, 11, 16, 11)));

    public GasValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(SETTING, Valve.SETTINGS - 1)
                .setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SETTING, AXIS);
    }

    /**
     * Lines up with the run it is placed into: if exactly one axis has plumbing on it, take
     * that axis, so dropping a valve into a straight line orients it without thought. When
     * that is ambiguous (a junction, or open air), fall back to the way the player is
     * facing.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = axisFromNeighbours(context);
        if (axis == null) {
            axis = context.getHorizontalDirection().getAxis();
        }
        return defaultBlockState().setValue(SETTING, Valve.SETTINGS - 1).setValue(AXIS, axis);
    }

    private static Direction.@Nullable Axis axisFromNeighbours(BlockPlaceContext context) {
        return runAxisAround(context.getLevel(), context.getClickedPos());
    }

    /**
     * The single axis a run passes through this position on, or null when that is
     * ambiguous (a junction) or there is no plumbing at all.
     */
    private static Direction.@Nullable Axis runAxisAround(LevelReader level, BlockPos pos) {
        Direction.Axis chosen = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (joinedOn(level, pos, axis)) {
                if (chosen != null) {
                    return null; // plumbing on more than one axis — let the player decide
                }
                chosen = axis;
            }
        }
        return chosen;
    }

    private static boolean joinedOn(LevelReader level, BlockPos pos, Direction.Axis axis) {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        return GasPipeBlock.connectsTo(level.getBlockState(pos.relative(positive)))
                || GasPipeBlock.connectsTo(level.getBlockState(pos.relative(positive.getOpposite())));
    }

    /**
     * Snaps a stranded valve into line with the run being built around it.
     *
     * <p>Reported from play: <em>"the pump doesn't understand a valve connected directly
     * without pipes."</em> A valve placed in open air takes the axis the player happened to
     * be facing; build the run across that later and the valve sits broadside, severing it
     * — and nothing said so, which is the silent failure this project keeps promising not
     * to ship.
     *
     * <p>Only realigns when the axis it currently has is joined to <em>nothing</em>, so a
     * valve deliberately turned with the wrench and actually in use is never overruled. A
     * stranded valve snaps to the run; a working one is left alone.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        if (joinedOn(level, pos, state.getValue(AXIS))) {
            return state;
        }
        Direction.Axis run = runAxisAround(level, pos);
        return run == null ? state : state.setValue(AXIS, run);
    }

    /**
     * Keep drawing the bore model. {@link BaseEntityBlock} defaults to an invisible shape
     * that expects the renderer to draw everything; here the renderer only adds the
     * turning wheel and the block's body is still an ordinary model.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasValveBlockEntity(pos, state);
    }

    /**
     * Client-side only: the wheel is a display and the setting it chases already lives on
     * the blockstate the server owns, so the server has nothing to tick.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.GAS_VALVE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((GasValveBlockEntity) blockEntity).clientTick(tickState);
    }

    /**
     * Yields to the wrench.
     *
     * <p>A right-click normally steps the setting (via {@link #useWithoutItem}), and the
     * vanilla interaction runs that <em>before</em> a held item's own {@code useOn}. So a
     * valve held with a wrench would step its setting instead of turning — the wrench could
     * never re-aim the one block that also responds to a bare click. Returning {@code PASS}
     * here, only for the wrench, lets the interaction fall through to the wrench's rotate;
     * every other hand still steps the setting as before.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Steps the setting and wraps at full bore.
     *
     * <p>Wrapping rather than reversing so a valve can always be shut from any position
     * in a fixed number of clicks — a control you have to hunt for is worse than one
     * that goes the long way round.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        int next = (state.getValue(SETTING) + 1) % Valve.SETTINGS;
        level.setBlock(pos, state.setValue(SETTING, next), Block.UPDATE_ALL);
        level.playSound(player, pos,
                next == 0 ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.5F, 1.2F);
        return InteractionResult.SUCCESS;
    }
}
