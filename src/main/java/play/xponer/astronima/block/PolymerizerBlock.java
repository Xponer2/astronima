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
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.PolymerizerBlockEntity;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Addition polymerization: titania catalyst plus the room's own ethylene, strung into
 * polyethylene. See {@link PolymerizerBlockEntity}.
 */
public class PolymerizerBlock extends Block implements EntityBlock, MenuProvider, PowerConnectable,
        play.xponer.astronima.wire.Terminated, play.xponer.astronima.wire.SignalSource {

    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level,
                             net.minecraft.core.BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        if (terminal.kind() != play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof ProcessingBlockEntity machine)) {
            return false;
        }
        String stud = terminal.label();
        if (play.xponer.astronima.wire.Terminated.STUCK.equals(stud)) {
            return machine.workState().isHolding();
        }
        if (play.xponer.astronima.wire.Terminated.WORKING.equals(stud)) {
            return machine.workState().isWorking();
        }
        return false;
    }

    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(BlockState state) {
        return play.xponer.astronima.wire.Terminated.machineStrip();
    }

    public PolymerizerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PolymerizerBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.ProcessingUiHolder(machine, pos),
                    buffer -> {
                buffer.writeVarInt(ProcessingMenu.Kind.POLYMERIZER.ordinal());
                buffer.writeVarInt(machine.getContainerSize());
                buffer.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.polymerizer");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PolymerizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.POLYMERIZER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((ProcessingBlockEntity) blockEntity).serverTick();
    }
}
