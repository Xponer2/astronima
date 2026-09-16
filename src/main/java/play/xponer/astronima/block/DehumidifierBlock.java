package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import play.xponer.astronima.block.entity.DehumidifierBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Condensing dehumidifier: chills a coil below the room's dew point so exhaled water
 * vapor condenses out, then collects it. The ISS recovers most of its water this way.
 * Without one, a sealed habitat drifts toward saturation — fog, frost on cold walls,
 * and eventually mold.
 */
public class DehumidifierBlock extends Block implements EntityBlock {
    public DehumidifierBlock(Properties properties) {
        super(properties);
    }

    /**
     * Draws off whatever whole bottles have condensed.
     *
     * <p>The click is a <em>withdrawal</em>, not a status request — the status is the gauge
     * — so it answers in the two ways a tap answers: bottles and the sound of them filling,
     * or the hollow knock of a reservoir with nothing in it yet. Never a chat line, which
     * is what it used to do (rule 9).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DehumidifierBlockEntity unit) {
            int bottles = unit.collectBottles(player);
            level.playSound(null, pos,
                    bottles > 0 ? SoundEvents.BOTTLE_FILL : SoundEvents.BARREL_CLOSE,
                    SoundSource.BLOCKS, 0.7F, bottles > 0 ? 1.0F : 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new DehumidifierBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DEHUMIDIFIER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((DehumidifierBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }
}
