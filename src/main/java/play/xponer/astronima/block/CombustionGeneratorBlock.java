package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.phys.BlockHitResult;
import play.xponer.astronima.block.entity.CombustionGeneratorBlockEntity;
import play.xponer.astronima.power.PowerConnectable;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The night source, and mostly a stove.
 *
 * <p>Burns methane out of the room it stands in and hands back carbon dioxide, water and — three
 * quarters of the fuel's energy — heat. See {@link CombustionGeneratorBlockEntity} for why
 * breathing the room is the point rather than a shortcut.
 */
public class CombustionGeneratorBlock extends BaseEntityBlock implements PowerConnectable, play.xponer.astronima.wire.Terminated {

    /** Power terminals on all four sides, so it can be wired from whichever side is free. */
    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(
            net.minecraft.world.level.block.state.BlockState state) {
        java.util.List<play.xponer.astronima.wire.Terminal> found = new java.util.ArrayList<>(8);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_POWER_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.POWER));
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_OUT_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT));
        }
        return found;
    }

    public static final MapCodec<CombustionGeneratorBlock> CODEC =
            simpleCodec(CombustionGeneratorBlock::new);

    public CombustionGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Digging the soot out with a shovel.
     *
     * <p>A shovel rather than a bare hand or a menu, because the tool says what the job is: this
     * is not a machine you reconfigure, it is one you clean. And it refuses to say nothing —
     * scraping a clean engine tells you it is clean, so the player can stop wondering.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(stack.getItem() instanceof ShovelItem)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel)
                || !(level.getBlockEntity(pos) instanceof CombustionGeneratorBlockEntity generator)) {
            return InteractionResult.SUCCESS;
        }
        int dug = generator.shovelOut();
        if (dug <= 0) {
            player.sendSystemMessage(Component.translatable("astronima.generator.clean"));
            return InteractionResult.FAIL;
        }
        ItemStack sludge = new ItemStack(ModItems.SLUDGE.get(), dug);
        if (!player.getInventory().add(sludge)) {
            player.drop(sludge, false);
        }
        player.sendSystemMessage(Component.translatable("astronima.generator.cleaned", dug));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CombustionGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.COMBUSTION_GENERATOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CombustionGeneratorBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
