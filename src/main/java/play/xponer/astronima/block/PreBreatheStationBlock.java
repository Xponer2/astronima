package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

/**
 * Pre-breathe mask station: breathe pure oxygen from the room's supply to wash
 * dissolved nitrogen out of your tissue before an EVA — exactly the protocol NASA
 * uses before a spacewalk. Costs the room real oxygen, so it is a resource decision,
 * and it is the only fast cure for a heavy nitrogen load.
 */
public class PreBreatheStationBlock extends Block {
    /** Nitrogen washed out per use, kPa of tissue tension. */
    private static final float PURGE_KPA = 25.0f;
    /** Oxygen drawn from the room per use, mol. */
    private static final double O2_COST_MOL = 1.5;

    public PreBreatheStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(pos);
        if (room == null || room.gases().get(Gas.OXYGEN) < O2_COST_MOL) {
            player.sendSystemMessage(Component.translatable("astronima.prebreathe.no_supply"));
            return InteractionResult.FAIL;
        }
        float tissue = serverPlayer.getData(ModAttachments.TISSUE_N2);
        if (tissue <= 1.0f) {
            player.sendSystemMessage(Component.translatable("astronima.prebreathe.already_clear"));
            return InteractionResult.FAIL;
        }
        room.removeGas(Gas.OXYGEN, O2_COST_MOL);
        float purged = Math.max(0f, tissue - PURGE_KPA);
        serverPlayer.setData(ModAttachments.TISSUE_N2.get(), purged);
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.sendSystemMessage(Component.translatable("astronima.prebreathe.purged",
                String.format("%.0f", purged)));
        return InteractionResult.SUCCESS;
    }
}
