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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.item.OxygenTanks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;

/**
 * A pressure vessel: gas you have put somewhere, rather than gas you are breathing.
 *
 * <p>A room holds air at habitat pressure because that is what a room is for. A tank
 * holds far more in far less space because it is built to take the pressure, and that
 * is the whole point of having one — a buffer that survives the habitat depressurising.
 *
 * <p>It is a node on a pipe network like a room is, but it is not a room: nothing
 * breathes in here. Its contents reach the player two ways: through plumbing, and through
 * the <strong>fill port</strong> — hold an empty bottle against it and it charges.
 *
 * <p><strong>The fill port is what makes vacuum work possible.</strong> Before it, the only
 * way to charge a suit bottle was {@code EmptyOxygenTankItem} drawing 12 mol out of the room
 * you were breathing, so every hour spent outside was taken directly out of the habitat's
 * air — and mining, which is the one job that must be done in vacuum, was cheaper to do
 * indoors at 6.6 mol of oxygen per block. That is backwards, and it is what a bank fixes: a
 * charged cylinder is oxygen that is not also your atmosphere.
 */
public class GasTankBlock extends BaseEntityBlock {
    public static final MapCodec<GasTankBlock> CODEC = simpleCodec(GasTankBlock::new);

    public GasTankBlock(Properties properties) {
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
        return new GasTankBlockEntity(pos, state);
    }

    /**
     * Charging a suit bottle off the vessel.
     *
     * <p>No pressure floor, unlike filling from a room — see
     * {@link OxygenTanks#MIN_ROOM_PPO2_AFTER_FILL}. The only refusal is the honest one:
     * there is not enough oxygen in here to fill a bottle.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(ModItems.OXYGEN_TANK_EMPTY.get())) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel)
                || !(level.getBlockEntity(pos) instanceof GasTankBlockEntity vessel)) {
            return InteractionResult.SUCCESS;
        }
        if (vessel.contents().gases().get(Gas.OXYGEN) < OxygenTanks.TANK_O2_MOL) {
            player.sendSystemMessage(Component.translatable("astronima.tank.vessel_empty"));
            return InteractionResult.FAIL;
        }
        vessel.contents().removeGas(Gas.OXYGEN, OxygenTanks.TANK_O2_MOL);
        vessel.setChanged();
        // One bottle per press, and the stack is consumed one at a time, so a handful of
        // empties fills into a handful of fulls without the player having to split them.
        stack.shrink(1);
        ItemStack filled = new ItemStack(ModItems.OXYGEN_TANK.get());
        if (!player.getInventory().add(filled)) {
            player.drop(filled, false);
        }
        player.sendSystemMessage(Component.translatable("astronima.tank.filled"));
        return InteractionResult.SUCCESS;
    }
}
