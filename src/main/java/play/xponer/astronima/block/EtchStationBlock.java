package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import play.xponer.astronima.block.entity.EtchStationBlockEntity;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.physio.ChemicalBurn;

/**
 * Real wet oxide etching. See {@link EtchStationBlockEntity}, and {@code design/halogens.md}
 * §39/§42 for why hand-loading HF here runs the exact same real contact event
 * {@code HydrofluoricAcidItem#use} does, rather than a second, weaker copy of it.
 */
public class EtchStationBlock extends Block implements EntityBlock, MenuProvider, PowerConnectable,
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

    public EtchStationBlock(Properties properties) {
        super(properties);
    }

    /**
     * The one real door into the HF slot (design/halogens.md §42). Every other item, including
     * the wafer, falls through to vanilla ({@code PASS}) so the normal slot/menu path still
     * works for them.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.HYDROFLUORIC_ACID.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EtchStationBlockEntity station) {
            ItemStack already = station.getItem(EtchStationBlockEntity.SLOT_HF);
            if (!already.isEmpty() && !already.is(ModItems.HYDROFLUORIC_ACID.get())) {
                return InteractionResult.SUCCESS;
            }
            double before = player.getData(ModAttachments.CHEMICAL_BURN_DOSE);
            double after = ChemicalBurn.contact(before,
                    play.xponer.astronima.item.HydrofluoricAcidItem.CONTACT_GRAMS_PER_ITEM);
            player.setData(ModAttachments.CHEMICAL_BURN_DOSE.get(), (float) after);

            int moved = stack.getCount();
            ItemStack toLoad = already.isEmpty()
                    ? stack.copyWithCount(moved)
                    : already.copyWithCount(already.getCount() + moved);
            stack.shrink(moved);
            station.setItem(EtchStationBlockEntity.SLOT_HF, toLoad);

            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.7f);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable(
                        "astronima.etch_station.hand_loaded"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof EtchStationBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.ProcessingUiHolder(machine, pos),
                    buffer -> {
                buffer.writeVarInt(ProcessingMenu.Kind.ETCH_STATION.ordinal());
                buffer.writeVarInt(machine.getContainerSize());
                buffer.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.etch_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EtchStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ETCH_STATION.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((ProcessingBlockEntity) blockEntity).serverTick();
    }
}
