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
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;
import play.xponer.astronima.menu.StorageTerminalUiHolder;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The real browsing screen for whatever {@code storage_drive}(s) this block directly touches —
 * see {@code design/data-cells.md} §14. Right-click opens {@link StorageTerminalBlockEntity}'s
 * own live, sorted view through {@link StorageTerminalUiHolder}, this mod's own LDLib2 chrome;
 * the block itself only opens the menu, the same as every other storage block in this family.
 */
public class StorageTerminalBlock extends Block implements EntityBlock {
    public StorageTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal) {
            // The client's own menu reconstruction has no block entity to ask for its position
            // (a fresh disconnected container, the same split ProcessingUiHolder's own two
            // constructors use) - the sort buttons need it to address their own server packet.
            serverPlayer.openMenu(new StorageTerminalUiHolder(terminal, pos),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageTerminalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.STORAGE_TERMINAL.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                StorageTerminalBlockEntity.tick(tickLevel, pos, tickState,
                        (StorageTerminalBlockEntity) blockEntity);
    }
}
