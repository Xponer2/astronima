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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.block.entity.MicroscopeBlockEntity;

/** A microscope: a stage for one slide and a fine focus you have to work. */
public class MicroscopeBlock extends Block implements EntityBlock, MenuProvider {

    public MicroscopeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MicroscopeBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.MicroscopeUiHolder(machine, pos),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.microscope");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicroscopeBlockEntity(pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,
                                               net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        if (level.getBlockEntity(pos) instanceof MicroscopeBlockEntity machine) {
            net.minecraft.world.Containers.dropContents(level, pos, machine.container());
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }
}
