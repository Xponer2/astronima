package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.StorageDriveBlockEntity;
import play.xponer.astronima.menu.StorageDriveUiHolder;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Where a player puts real {@code data_cell} items in, and reads the drive's own screen — this
 * mod's own LDLib2 chrome ({@link StorageDriveUiHolder}, see {@code design/data-cells.md} §14),
 * not the plain vanilla {@code ChestMenu} E2 originally shipped with.
 *
 * <p>How many of the drive's 54 real slots actually accept a cell right now depends on how much
 * connected {@code storage_frame} the player has built around it — see
 * {@link StorageDriveBlockEntity#usableSlots()}. This block itself has no opinion on that; it
 * only opens the menu, the same as {@link CargoCrateBlock}.
 */
public class StorageDriveBlock extends Block implements EntityBlock {
    public StorageDriveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StorageDriveBlockEntity drive) {
            serverPlayer.openMenu(new StorageDriveUiHolder(drive, pos),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageDriveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.STORAGE_DRIVE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                StorageDriveBlockEntity.tick(tickLevel, pos, tickState,
                        (StorageDriveBlockEntity) blockEntity);
    }

    // No drop-on-break override here, deliberately, for the same reason CargoCrateBlock has
    // none: BlockEntity.preRemoveSideEffects already drops any Container's contents before the
    // block's own removal hook runs.
}
