package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;

/**
 * The rule that fire obeys everywhere: <em>flames need oxygen</em>.
 *
 * <p>Two consequences the vanilla game does not model. A flame carried into a
 * flammable atmosphere sets it off — the classic mine disaster, and not something you
 * have to deliberately place a block to trigger. And a flame in vacuum or thin air
 * simply goes out, which is why lighting an unpressurized tunnel needs chemistry
 * (light sticks) or electricity rather than fire.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class CombustionEvents {
    /** Checked a few times a second: cheap, and the danger should feel immediate. */
    private static final int CHECK_INTERVAL_TICKS = 5;

    @SubscribeEvent
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.tickCount % CHECK_INTERVAL_TICKS != 0
                || !carriesOpenFlame(player)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        RoomState room = Atmosphere.get(level).roomAt(pos);
        if (room != null && Flammability.ignitable(room)) {
            // Carrying fire into a flammable mixture is the disaster, not placing it.
            Ignition.tryIgnite(level, pos);
            return;
        }
        snuffCarriedTorches(player, room);
    }

    private static boolean carriesOpenFlame(ServerPlayer player) {
        return isOpenFlame(player.getMainHandItem()) || isOpenFlame(player.getOffhandItem());
    }

    private static boolean isOpenFlame(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)
                || stack.is(Items.CAMPFIRE) || stack.is(Items.LANTERN)
                || stack.is(Items.CANDLE) || stack.is(Items.FLINT_AND_STEEL);
    }

    /**
     * Puts out a placed flame the surrounding air cannot feed. A torch is converted
     * to its unlit twin rather than destroyed — it stays exactly where it was mounted
     * and can be relit — while fire itself simply disappears.
     *
     * @return true when the flame was put out
     */
    public static boolean snuffIfStarved(ServerLevel level, BlockPos pos, BlockState state) {
        RoomState room = Atmosphere.get(level).roomAt(pos);
        if (room != null && room.partialPressureKPa(Gas.OXYGEN) >= Flammability.MIN_O2_KPA) {
            return false;
        }
        BlockState replacement;
        if (state.is(Blocks.TORCH)) {
            replacement = ModBlocks.UNLIT_TORCH.get().defaultBlockState();
        } else if (state.is(Blocks.WALL_TORCH)) {
            replacement = ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,
                            state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        } else {
            replacement = Blocks.AIR.defaultBlockState();
        }
        level.setBlockAndUpdate(pos, replacement);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                6, 0.1, 0.1, 0.1, 0.01);
        return true;
    }

    /**
     * A torch held in air that cannot sustain a flame becomes an unlit torch in the
     * hand. Without this the item stays "a torch" to everything else — and a dynamic
     * lighting mod would happily light the way through hard vacuum.
     */
    private static void snuffCarriedTorches(ServerPlayer player, RoomState room) {
        if (room != null && room.partialPressureKPa(Gas.OXYGEN) >= Flammability.MIN_O2_KPA) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.TORCH)) {
                player.setItemInHand(hand,
                        new ItemStack(ModBlocks.UNLIT_TORCH.get(), held.getCount()));
                player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.3f, 1.6f);
            }
        }
    }

    private CombustionEvents() {}
}
