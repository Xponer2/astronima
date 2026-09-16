package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import play.xponer.astronima.block.entity.ColdForgeBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A cold forge: an anvil worked in vacuum, where fresh metal surfaces weld to each
 * other because there is no atmosphere to grow an oxide film between them.
 *
 * <p>Right-clicking opens the machine rather than performing a step of the work. That
 * distinction is the whole reason this class exists in this shape: a block you click
 * to convert an item is a crafting recipe, while a block that holds items, runs on a
 * clock and exposes a control is a machine you can inspect, tune and automate.
 */
public class ColdForgeBlock extends Block implements EntityBlock, MenuProvider, PowerConnectable, play.xponer.astronima.wire.Terminated,
        play.xponer.astronima.wire.SignalSource {

    /**
     * Drives its status stud while it is actually turning.
     *
     * <p>"Working" rather than "done", because working is the state a machine <em>has</em> — a
     * one-shot "finished" pulse would need somewhere to remember that it had fired. An idle
     * signal is this one through a NOT gate, which is exactly what the NOT gate is for.
     *
     * <p>And a second, separate line for <em>held</em> — out of feed, product backed up, work
     * ruined. Folding that into "not working" would tell a base that a finished machine and a
     * jammed one are the same event, and they are not: one wants patience and the other wants a
     * person.
     */
    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level,
                             net.minecraft.core.BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        if (terminal.kind() != play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT) {
            return false;
        }
        if (!(level.getBlockEntity(pos)
                instanceof play.xponer.astronima.block.entity.ProcessingBlockEntity machine)) {
            return false;
        }
        // Two outputs, told apart by which row the stud is on: the working line while it turns,
        // and the held line while it is stopped for a reason a person has to fix.
        // Told apart by the name on the stud, not by which row it is on. The row was a second
        // way of saying the same thing, and it stopped being unambiguous the moment another stud
        // moved into that row - which is precisely how the servo arrived (PLAN rule 44).
        //
        // Both named, and anything else drives nothing. An `else` covering the working line would
        // also cover every output added after this was written, and it would report them all as
        // "the machine is running" - plausibly, and wrongly.
        String stud = terminal.label();
        if (play.xponer.astronima.wire.Terminated.STUCK.equals(stud)) {
            return machine.workState().isHolding();
        }
        if (play.xponer.astronima.wire.Terminated.WORKING.equals(stud)) {
            return machine.workState().isWorking();
        }
        return false;
    }

    /** A terminal strip on every side: enable in, power, status out. */
    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(
            net.minecraft.world.level.block.state.BlockState state) {
        return play.xponer.astronima.wire.Terminated.machineStrip();
    }

    public ColdForgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ColdForgeBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.ProcessingUiHolder(machine, pos),
                    buffer -> {
                buffer.writeVarInt(ProcessingMenu.Kind.FORGE.ordinal());
                buffer.writeVarInt(machine.getContainerSize());
                buffer.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.cold_forge");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null; // the per-instance Provider above is what actually opens
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColdForgeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.COLD_FORGE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((ProcessingBlockEntity) blockEntity).serverTick();
    }

    // No drop-on-break override, deliberately: BlockEntity.preRemoveSideEffects already
    // drops the contents of any Container block entity, and it runs before this hook would
    // — by which point the block entity is detached, so an override here finds nothing and
    // does nothing. One lived here looking load-bearing until a mutation proved otherwise.
}
