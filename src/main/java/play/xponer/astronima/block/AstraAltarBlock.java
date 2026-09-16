package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.AstraAltarBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The ritual's focus block — design/astra-ritual-grammar.md §1/§2. See
 * {@link AstraAltarBlockEntity} for the real structure detection and live draw; this class is
 * the standard {@code BaseEntityBlock} scaffolding, mirroring {@code SolarArrayBlock}'s own
 * minimal shape (no menu, no wire terminals — a right-click and a tick are the whole interface).
 */
public class AstraAltarBlock extends BaseEntityBlock {

    public static final MapCodec<AstraAltarBlock> CODEC = simpleCodec(AstraAltarBlock::new);

    public AstraAltarBlock(Properties properties) {
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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AstraAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ASTRA_ALTAR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((AstraAltarBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof AstraAltarBlockEntity altar)) {
            return InteractionResult.SUCCESS;
        }

        AstraAltarBlockEntity.ActivationResult result = altar.activate(serverLevel);
        switch (result) {
            case STARTED -> player.sendSystemMessage(altar.statusMessage());
            case ALREADY_RUNNING -> player.sendSystemMessage(
                    Component.translatable("astronima.astra_altar.already_running")
                            .withStyle(ChatFormatting.RED));
            case NO_ARMS -> player.sendSystemMessage(
                    Component.translatable("astronima.astra_altar.no_arms")
                            .withStyle(ChatFormatting.RED));
            case NO_BOUNDARY -> player.sendSystemMessage(
                    Component.translatable("astronima.astra_altar.no_boundary")
                            .withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }
}
