package play.xponer.astronima.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;

/**
 * Filling an empty tank compresses {@link OxygenTanks#TANK_O2_MOL} mol of oxygen out
 * of the surrounding sealed room — the room really loses that oxygen, so topping up
 * tanks in a small cabin is a decision, not a free action. The fill is refused if it
 * would drive the room below breathable ppO2.
 */
public class EmptyOxygenTankItem extends Item {
    public EmptyOxygenTankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(eyePos);
        // Any enclosed volume with enough oxygen will do — the pump does not care
        // whether the space is small enough to hold pressure indefinitely.
        if (reading == null || reading.openToSpace()) {
            player.sendSystemMessage(Component.translatable("astronima.tank.no_air"));
            return InteractionResult.FAIL;
        }
        RoomState room = reading.state();
        double remainingO2Mol = room.gases().get(Gas.OXYGEN) - OxygenTanks.TANK_O2_MOL;
        double remainingPpO2 = remainingO2Mol * GasMixture.R * room.temperatureK()
                / room.volumeM3() / 1000.0;
        if (remainingPpO2 < OxygenTanks.MIN_ROOM_PPO2_AFTER_FILL) {
            player.sendSystemMessage(Component.translatable("astronima.tank.would_deplete"));
            return InteractionResult.FAIL;
        }
        room.removeGas(Gas.OXYGEN, OxygenTanks.TANK_O2_MOL);
        player.setItemInHand(hand, new ItemStack(ModItems.OXYGEN_TANK.get()));
        player.sendSystemMessage(Component.translatable("astronima.tank.filled"));
        return InteractionResult.SUCCESS;
    }
}
