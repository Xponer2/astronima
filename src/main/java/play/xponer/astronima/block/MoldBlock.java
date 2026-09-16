package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.entity.MoldBlockEntity;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.MoldGrowth;
import play.xponer.astronima.sim.RoomState;

/**
 * Black mold — the housekeeping problem every real space station fights.
 *
 * <p>Colonies take hold on floors, walls <em>and</em> ceilings in rooms that stay warm and damp
 * (>70 % RH) — real mold is often worse on a wall or ceiling than a floor, at the cold-surface
 * condensation points {@code sim/Humidity}'s own Magnus formula already predicts. {@link AttachFace}
 * (this block extends {@link FaceAttachedHorizontalDirectionalBlock}, buttons' and levers' own base
 * class) is what lets one colony be on any of the three; growth through {@link
 * BlockStateProperties#AGE_3}'s four visible stages, spread to a neighbouring cell once mature, and
 * staged die-back are all unchanged from the floor-only version ({@code design/mold-growth.md} §2's
 * real vegetative-growth-then-sporulation split, in {@link MoldGrowth}) and now simply consider every
 * face of a neighbouring cell rather than only the floor beneath it. Breathing mold spores irritates
 * the lungs. The permanent fix is engineering rather than scrubbing: run a dehumidifier.
 */
public class MoldBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<MoldBlock> CODEC = simpleCodec(MoldBlock::new);

    private static final VoxelShape FLOOR_SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape CEILING_SHAPE = Block.box(0, 15, 0, 16, 16, 16);
    // The wall this coating clings to is on the side opposite FACING (FACING points away from
    // it, into the room - FaceAttachedHorizontalDirectionalBlock's own convention), so the thin
    // film sits flush against that far side, not the near one.
    private static final VoxelShape WALL_NORTH_SHAPE = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape WALL_SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape WALL_EAST_SHAPE = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape WALL_WEST_SHAPE = Block.box(15, 0, 0, 16, 16, 16);

    private static final float SPREAD_CHANCE = 0.3f;
    private static final float DIE_BACK_CHANCE = 0.5f;
    private static final float GROWTH_CHANCE = 0.2f;

    public MoldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.AGE_3, 0));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, BlockStateProperties.AGE_3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> switch (state.getValue(FACING)) {
                case NORTH -> WALL_NORTH_SHAPE;
                case SOUTH -> WALL_SOUTH_SHAPE;
                case EAST -> WALL_EAST_SHAPE;
                case WEST -> WALL_WEST_SHAPE;
                default -> FLOOR_SHAPE; // FACING is horizontal-only for this block
            };
        };
    }

    // canSurvive, getStateForPlacement and the auto-removal-on-neighbour-change in updateShape
    // all come from FaceAttachedHorizontalDirectionalBlock unchanged - it already answers
    // "is there a solid surface behind this FACE+FACING" for all three attachments, which is
    // exactly what a floor-only custom canSurvive used to hand-roll for one of the three.

    /**
     * Which real-world direction points away from whatever surface this colony clings to —
     * {@code getConnectedDirection} is {@code protected static} on the vanilla base class, so
     * {@code MoldBlockEntityRenderer} (a different class entirely) cannot call it directly; this
     * is that one fact, exposed. {@code MoldBranching}'s own local +Y axis (its "up, away from
     * the surface" direction) maps onto exactly this world direction.
     */
    public static Direction awayDirection(BlockState state) {
        return getConnectedDirection(state);
    }

    /**
     * No block model at all: the real geometry is a live, per-position branching cluster
     * ({@code sim/MoldBranching}), submitted every frame by {@code MoldBlockEntityRenderer}
     * rather than baked to any file (design/mold-growth.md §3c).
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Exists purely to give the renderer somewhere to cache the branch shape it computes for
     *  this position, instead of re-walking it every frame - nothing here is persisted (rule 5
     *  does not apply: there is no real state here that blockstate does not already carry). */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoldBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null; // growth/spread/die-back all run through randomTick, not a per-tick ticker
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RoomState room = Atmosphere.get(level).roomTouching(pos);
        int age = state.getValue(BlockStateProperties.AGE_3);
        if (room == null || !Humidity.moldFavourable(room)) {
            if (random.nextFloat() < DIE_BACK_CHANCE) {
                int thinner = MoldGrowth.diedBack(age);
                if (thinner < 0) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                } else {
                    level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.AGE_3, thinner));
                }
            }
            return;
        }
        if (random.nextFloat() < GROWTH_CHANCE && age < MoldGrowth.MAX_AGE) {
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.AGE_3, MoldGrowth.grown(age)));
        }
        if (!MoldGrowth.canSpread(age) || random.nextFloat() >= SPREAD_CHANCE) {
            return;
        }
        // Any of the six face-adjacent cells, not just the floor's own 3x3 - a real colony
        // spreads along a continuous damp surface in any direction, corner onto wall onto
        // ceiling included, reported live as "it need to be not only on floor".
        Direction[] directions = Direction.values();
        Direction direction = directions[random.nextInt(directions.length)];
        tryEstablish(level, pos.relative(direction));
    }

    /** Seeds a fresh, age-0 colony at {@code pos} when a room has been damp too long. */
    public static void trySeed(ServerLevel level, BlockPos pos) {
        tryEstablish(level, pos);
    }

    /**
     * Runs one real {@link #randomTick} against whatever is at {@code pos} right now, if it is
     * still mold — the debug-command rule's own "read and set its state" taken to its honest
     * limit: {@code /astronima mold rush} does not force an age, it replays the exact same dice
     * {@code randomTick} rolls, just as many times as asked instead of waiting for vanilla's own
     * random-tick lottery (~once per 68 real seconds per block) to hand them out one at a time.
     * A prior call may have died back to nothing, so this re-checks the block is still mold
     * before touching it, rather than assuming the caller's loop still has a target.
     */
    public static void simulateFavourableTick(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.MOLD.get())) {
            ((MoldBlock) state.getBlock()).randomTick(state, level, pos, random);
        }
    }

    /**
     * Tries every attachment an empty cell could actually hold a colony on - floor, ceiling,
     * then each wall - and places the first one whose backing surface is real. A spore does not
     * know in advance which surface it will land against.
     */
    private static boolean tryEstablish(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockState mold = ModBlocks.MOLD.get().defaultBlockState();
        BlockState floor = mold.setValue(FACE, AttachFace.FLOOR);
        if (floor.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, floor);
            return true;
        }
        BlockState ceiling = mold.setValue(FACE, AttachFace.CEILING);
        if (ceiling.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, ceiling);
            return true;
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState wall = mold.setValue(FACE, AttachFace.WALL).setValue(FACING, facing);
            if (wall.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, wall);
                return true;
            }
        }
        return false;
    }
}
