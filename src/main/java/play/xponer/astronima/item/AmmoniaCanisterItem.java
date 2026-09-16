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
import play.xponer.astronima.sim.RoomState;

/**
 * Filling an empty canister compresses {@link #CANISTER_NH3_MOL} mol of ammonia out of the
 * surrounding sealed room — the room really loses that ammonia, the same honest cost
 * {@link EmptyOxygenTankItem} already charges for oxygen. The only real source is a mined
 * ammonia pocket ({@code GasPockets}) vented into a room; this is the one step that turns a
 * rare hazard find into a real crafting ingredient (see {@code design/ammonia-heat-pipes.md}
 * §3).
 */
public class AmmoniaCanisterItem extends Item {
    /** One canister's worth — enough to charge one heat pipe, real and finite. */
    public static final double CANISTER_NH3_MOL = 20.0;

    public AmmoniaCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(eyePos);
        if (reading == null || reading.openToSpace()) {
            player.sendSystemMessage(Component.translatable("astronima.ammonia_canister.no_air"));
            return InteractionResult.FAIL;
        }
        RoomState room = reading.state();
        if (room.gases().get(Gas.AMMONIA) < CANISTER_NH3_MOL) {
            player.sendSystemMessage(Component.translatable("astronima.ammonia_canister.not_enough"));
            return InteractionResult.FAIL;
        }
        room.removeGas(Gas.AMMONIA, CANISTER_NH3_MOL);
        player.setItemInHand(hand, new ItemStack(ModItems.AMMONIA_CANISTER.get()));
        player.sendSystemMessage(Component.translatable("astronima.ammonia_canister.filled"));
        return InteractionResult.SUCCESS;
    }
}
