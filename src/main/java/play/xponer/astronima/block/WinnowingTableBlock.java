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
import play.xponer.astronima.block.entity.WinnowingTableBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A winnowing table: crushed powder in, a gas stream up through the bed, heavies and
 * lights out.
 *
 * <p>Tier 1's second separation axis. The magnet sorts on magnetism and is deliberately
 * unable to touch pentlandite; this sorts on density, which is what pentlandite has. The two
 * together are a progression rather than two flavours of one idea — see design/winnowing.md.
 *
 * <p>It has no control of its own. How well it separates is decided by how finely the feed
 * was ground and by whether the room it stands in holds enough air to carry a particle, and
 * neither of those is a dial on this block.
 */
public class WinnowingTableBlock extends Block implements EntityBlock, MenuProvider, PowerConnectable {
    public WinnowingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WinnowingTableBlockEntity machine) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.ProcessingUiHolder(machine, pos),
                    buffer -> {
                buffer.writeVarInt(ProcessingMenu.Kind.WINNOWER.ordinal());
                buffer.writeVarInt(machine.getContainerSize());
                buffer.writeBlockPos(pos);
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.winnowing_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null; // the per-instance Provider above is what actually opens
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WinnowingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.WINNOWING_TABLE.get()) {
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
