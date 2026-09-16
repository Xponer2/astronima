package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.block.entity.IncubatorBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/** A warm box that holds one plate at a temperature you set. */
public class IncubatorBlock extends Block implements EntityBlock, MenuProvider {

    public IncubatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof IncubatorBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.IncubatorUiHolder(machine, pos),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.incubator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IncubatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.INCUBATOR.get()
                ? (world, pos, blockState, entity) -> IncubatorBlockEntity.tick(world, pos,
                        blockState, (IncubatorBlockEntity) entity)
                : null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        if (level.getBlockEntity(pos) instanceof IncubatorBlockEntity machine) {
            net.minecraft.world.Containers.dropContents(level, pos,
                    (net.minecraft.world.Container) machine.container());
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }
}
