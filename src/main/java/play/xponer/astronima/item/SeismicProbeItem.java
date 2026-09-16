package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import play.xponer.astronima.atmosphere.AirBlockKinds;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.room.BlockKind;

/**
 * Acoustic sounding: tap a rock face and time the echo. Reports the distance to the
 * first cavity behind the face and whether it carries a trapped-gas signature —
 * informed risk instead of surprise, the rule-7 instrument for gas pockets.
 */
public class SeismicProbeItem extends Item {
    private static final int RANGE_BLOCKS = 12;
    private static final int GAS_SCAN_RADIUS = 3;
    private static final int COOLDOWN_TICKS = 30;

    public SeismicProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Direction inward = context.getClickedFace().getOpposite();
        BlockPos.MutableBlockPos cursor = context.getClickedPos().mutable();

        level.playSound(null, context.getClickedPos(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.8f);
        player.getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN_TICKS);

        for (int distance = 1; distance <= RANGE_BLOCKS; distance++) {
            cursor.move(inward);
            if (AirBlockKinds.classify(level.getBlockState(cursor), level, cursor) == BlockKind.OPEN) {
                boolean gas = hasGasSignature(level, cursor);
                player.sendSystemMessage(Component.translatable(
                                gas ? "astronima.probe.cavity_gas" : "astronima.probe.cavity", distance)
                        .withStyle(gas ? ChatFormatting.RED : ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
        }
        player.sendSystemMessage(Component.translatable("astronima.probe.solid", RANGE_BLOCKS)
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static boolean hasGasSignature(ServerLevel level, BlockPos cavity) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -GAS_SCAN_RADIUS; dx <= GAS_SCAN_RADIUS; dx++) {
            for (int dy = -GAS_SCAN_RADIUS; dy <= GAS_SCAN_RADIUS; dy++) {
                for (int dz = -GAS_SCAN_RADIUS; dz <= GAS_SCAN_RADIUS; dz++) {
                    probe.setWithOffset(cavity, dx, dy, dz);
                    if (level.getBlockState(probe).is(ModBlocks.GAS_POCKET_CORE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
