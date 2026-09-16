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
import play.xponer.astronima.block.entity.ScrubberBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;

/**
 * CO2 scrubber cabinet. Loaded with lithium-hydroxide cartridges
 * (2 LiOH + CO2 → Li2CO3 + H2O — the chemistry that kept Apollo 13 alive), it
 * chemically binds CO2 out of the room it touches until the cartridge saturates.
 */
public class ScrubberBlock extends Block implements EntityBlock {
    public ScrubberBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScrubberBlockEntity scrubber) {
            double remaining = 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage();
            if (scrubber.tryLoadCartridge(player, remaining)) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A scrubber has nothing to set, so a bare-handed click does nothing — deliberately.
     *
     * <p>It used to print the cartridge charge into chat, which rule 9 rejects for a
     * readout that matters: chat scrolls away, cannot be glanced at, and buries the one
     * number you wanted under everything else that happened. The charge is a gauge now —
     * the lamp on the block and the panel under the crosshair, both reading the same
     * {@code Scrubber} constants the machine consumes.
     *
     * <p>{@code PASS} rather than {@code SUCCESS}: consuming the click to do nothing at
     * all would be worse than not handling it, because the arm swings and the player is
     * left to wonder what they just did.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ScrubberBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SCRUBBER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((ScrubberBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
