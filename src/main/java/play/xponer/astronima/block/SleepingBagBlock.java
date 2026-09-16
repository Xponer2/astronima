package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Polymer bedding — the mod's first respawn point. Nothing today lets a player set one at all;
 * see {@code design/petrochemicals.md} §3 for why this is built for real rather than shipped as
 * a decoration named after a mechanic it does not have.
 *
 * <p><strong>Real vanilla, not reinvented.</strong> {@code Player.startSleepInBed} only runs its
 * distance/obstruction/respawn logic when the block state carries {@link #FACING} — a modded
 * bed with no facing is treated as an automatic, no-op success and <em>skips the respawn write
 * entirely</em> (verified in {@code ServerPlayer.startSleepInBed}'s own guard, rule 2). So this
 * is a real {@link HorizontalDirectionalBlock}, the same shape {@link PurgeValveBlock} already
 * uses, rather than a plain block that would silently fail to do the one thing it is named for.
 *
 * <p>No block entity: there is no state here beyond which way it faces.
 */
public class SleepingBagBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SleepingBagBlock> CODEC = simpleCodec(SleepingBagBlock::new);

    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.box(
            0.0, 0.0, 0.0, 1.0, 0.1875, 1.0);

    public SleepingBagBlock(Properties properties) {
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Vanilla's own sleep flow: distance, obstruction and monster-safety checks, and — the whole
     * point — a real respawn point on success. Nothing here duplicates that logic; duplicating a
     * safety check is how it drifts from the one vanilla actually enforces.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSleeping()) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.startSleepInBed(pos).ifLeft(problem -> {
            if (problem.message() != null) {
                player.sendSystemMessage(problem.message());
            }
        });
        return InteractionResult.SUCCESS;
    }
}
