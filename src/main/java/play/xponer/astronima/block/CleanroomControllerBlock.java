package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.CleanroomControllerBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;

/**
 * A real HEPA blower/positive-pressure unit — design/halogens.md §33 (Part C3). Loaded with a
 * fresh filter, it holds the room it touches at a real positive pressure until the filter
 * saturates.
 */
public class CleanroomControllerBlock extends Block implements EntityBlock {
    public CleanroomControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.HEPA_FILTER.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CleanroomControllerBlockEntity controller) {
            if (controller.tryLoadFilter(player)) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Nothing to set bare-handed — the gauge is the readout, the same reasoning
     *  {@code ScrubberBlock.useWithoutItem} already gives. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new CleanroomControllerBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CLEANROOM_CONTROLLER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CleanroomControllerBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
