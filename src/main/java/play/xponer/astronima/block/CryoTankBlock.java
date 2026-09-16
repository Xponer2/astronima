package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.CryoTankBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;

/**
 * A dewar: {@link CryoTankBlockEntity} for the physics, this class for placement, ticking and
 * the aerogel-wrap upgrade (design/cryogenics.md §4/§5).
 *
 * <p>Extends {@link GasTankBlock} rather than {@link BaseEntityBlock} directly, so its headspace
 * inherits the same fill-port interaction: a LOX dewar's boiled-off vapor is ordinary
 * {@code Gas.OXYGEN}, so an empty suit bottle held against it tops off exactly the way it would
 * against a plain gas tank — a real, free consequence of the two vessels sharing a gas species,
 * not a special case written for it. An LN2/LH2 dewar's headspace simply is not oxygen, so the
 * inherited check refuses harmlessly and other interactions (namely, this class's own aerogel
 * wrap below) still run.
 */
public class CryoTankBlock extends GasTankBlock {
    public static final MapCodec<CryoTankBlock> CODEC = simpleCodec(CryoTankBlock::new);

    public CryoTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CRYO_TANK.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((CryoTankBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.is(ModItems.SILICA_AEROGEL.get())) {
            if (!(level instanceof ServerLevel)
                    || !(level.getBlockEntity(pos) instanceof CryoTankBlockEntity tank)) {
                return InteractionResult.SUCCESS;
            }
            if (!tank.applyAerogelWrap()) {
                player.sendSystemMessage(Component.translatable("astronima.cryo_tank.already_wrapped"));
                return InteractionResult.FAIL;
            }
            stack.shrink(1);
            player.sendSystemMessage(Component.translatable("astronima.cryo_tank.wrapped"));
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
