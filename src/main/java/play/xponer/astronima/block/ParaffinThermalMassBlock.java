package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * A real thermal mass — see {@link ParaffinThermalMassBlockEntity} for the mechanism,
 * {@code design/phase-change-blocks.md} for why. A plain cube, not plumbing: furniture a player
 * places in a room, not a fitting a flow runs through, the same call {@code AcousticFoam} and
 * {@code PackedTailings} already made for their own equipment blocks.
 */
public class ParaffinThermalMassBlock extends Block implements EntityBlock {

    public ParaffinThermalMassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ParaffinThermalMassBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.PARAFFIN_THERMAL_MASS.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((ParaffinThermalMassBlockEntity) blockEntity).serverTick(tickLevel, pos, tickState);
    }
}
