package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.block.entity.TelescopeBlockEntity;
import play.xponer.astronima.registry.ModEntityTypes;
import play.xponer.astronima.telescope.TelescopeMountEntity;

/**
 * The instrument, design/astra-telescope.md §2 (v3 — a directly-driven camera, not a ridden seat).
 * Right-clicking an unoccupied telescope starts a server-tracked observation session
 * ({@link TelescopeMountEntity#beginObserving}) without moving or repossessing the player's own
 * entity at all — riding was tried across three separate rounds (PLAN.md rules 77-79) and each
 * round found a new way vanilla's own passenger machinery quietly did not do what it looked like
 * it did; direct correction, "тебе надо позицию камеры просто менять а не как на лошади ездить."
 * There is still no menu, no inventory, and no per-instance state stored on the block itself — the
 * mount entity is found by searching a small volume around the block, not tracked in a block
 * entity (§7's registry table).
 *
 * <p>Placed facing away from the player, so the eyepiece silhouette points the direction it was
 * planted to look — the same convention {@code GasPumpBlock} already uses for a directional
 * instrument. Facing does not yet constrain which way it can actually be aimed
 * (design/astra-telescope.md §9, open question 2); today every telescope is a ground tripod with
 * the same reach regardless of which way it is placed.
 */
public class TelescopeBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TelescopeBlock> CODEC = simpleCodec(TelescopeBlock::new);

    /**
     * A low, wide plinth plus a raised column — reads as a mounted instrument rather than a full
     * cube from any facing (this mod's own standing rule against slabless plumbing/instrument
     * cubes), and left facing-independent on purpose: a real, per-facing eyepiece silhouette needs
     * to be judged by eye once this is in game, not guessed at blind.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0),
            Block.box(6.0, 6.0, 6.0, 10.0, 14.0, 10.0));

    public TelescopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** Exists only to remember the last aim across a rider leaving (§9 open question 4, answered
     * by direct request after playtest) — no menu, no per-tick logic, nothing else the block's
     * own long-standing "no per-instance state for interaction" claim would be broken by. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelescopeBlockEntity(pos, state);
    }

    /**
     * Not riding any more (design/astra-telescope.md §2.1, PLAN.md rules 77-79's own account of
     * why): the player's own entity is completely untouched by this interaction. The client side
     * of "which entity does my camera actually point at" is handled entirely by
     * {@code client.TelescopeCamera}, which listens for this exact same right-click independently
     * — this method only ever runs the server-authoritative half (who may start or end a session).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        TelescopeMountEntity mount = findMount(level, pos);
        if (mount != null) {
            if (mount.isObservedBy(player)) {
                mount.stopObserving();
                return InteractionResult.SUCCESS;
            }
            // Someone else is already observing here — refused, not queued (design/
            // astra-telescope.md §8's T1 test plan: "one already occupied by another player").
            return InteractionResult.FAIL;
        }
        TelescopeBlockEntity blockEntity =
                level.getBlockEntity(pos) instanceof TelescopeBlockEntity te ? te : null;
        float[] restoredAim = blockEntity != null && blockEntity.hasSavedAim()
                ? new float[] {blockEntity.savedYaw(), blockEntity.savedPitch()}
                : TelescopeMountEntity.restRotation();
        if (eyeWouldBeInsideSolidBlock(level, pos.immutable(), restoredAim[0], restoredAim[1])) {
            // design/astra-telescope.md §2.3.3 step 6, direct report: "разрешает зайти в телескоп
            // даже если блоки вокруг и над ним" — checked against the exact aim a session would
            // actually restore to, not the telescope's own rest angle regardless of what it was
            // last left aimed at.
            return InteractionResult.FAIL;
        }
        TelescopeMountEntity spawned = TelescopeMountEntity.spawnAt(level, pos.immutable(),
                blockEntity, ModEntityTypes.TELESCOPE_MOUNT.get());
        spawned.beginObserving(player);
        if (!(level instanceof ServerLevelAccessor serverLevel) || !serverLevel.addFreshEntity(spawned)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean eyeWouldBeInsideSolidBlock(Level level, BlockPos pos, float yaw, float pitch) {
        net.minecraft.world.phys.Vec3 eye = TelescopeMountEntity.eyePositionFor(pos, yaw, pitch);
        BlockPos eyeBlock = BlockPos.containing(eye);
        if (eyeBlock.equals(pos)) {
            // The telescope's own post/plinth (a real collision shape, SHAPE above) occupies part
            // of this same block — at rest (near the zenith dead zone) the eye has barely moved
            // away from the instrument's own base yet, which is expected proximity to the
            // telescope itself, not an obstruction to refuse entry over.
            return false;
        }
        return !level.getBlockState(eyeBlock).getCollisionShape(level, eyeBlock).isEmpty();
    }

    private static TelescopeMountEntity findMount(Level level, BlockPos pos) {
        return TelescopeMountEntity.findAt(level, pos);
    }
}
