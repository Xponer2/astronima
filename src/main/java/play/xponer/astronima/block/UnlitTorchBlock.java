package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;

/**
 * A torch that has gone out.
 *
 * <p>Snuffing a torch out of existence would be both wasteful and confusing, so a
 * starved flame leaves this behind: the torch is still on the wall, it simply gives
 * no light. It is a genuinely separate block and item rather than a state flag, so
 * anything that reacts to "is this a torch" — dynamic lighting mods included — sees
 * an unlit object and treats it as one.
 *
 * <p>Relight it with a striker once there is oxygen to burn.
 */
public class UnlitTorchBlock extends TorchBlock {
    public UnlitTorchBlock(Properties properties) {
        super(net.minecraft.core.particles.ParticleTypes.SMOKE, properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!isIgniter(stack)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && !relight(serverLevel, pos, state)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /** True for items that can throw a spark: a ferrocerium striker or flint and steel. */
    public static boolean isIgniter(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)
                || stack.is(play.xponer.astronima.registry.ModItems.STRIKER.get());
    }

    /**
     * Puts the flame back if the air can support one.
     *
     * @return false when the atmosphere still cannot sustain a flame
     */
    public static boolean relight(ServerLevel level, BlockPos pos, BlockState state) {
        RoomState room = Atmosphere.get(level).roomAt(pos);
        if (room != null && room.partialPressureKPa(Gas.OXYGEN) <= Flammability.MIN_O2_KPA) {
            return false;
        }
        BlockState lit = state.is(ModBlocks.UNLIT_WALL_TORCH.get())
                ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                        state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                : Blocks.TORCH.defaultBlockState();
        level.setBlockAndUpdate(pos, lit);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        return true;
    }

    /** Wall-mounted variant, so a snuffed wall torch keeps its orientation. */
    public static class Wall extends WallTorchBlock {
        public Wall(Properties properties) {
            super(net.minecraft.core.particles.ParticleTypes.SMOKE, properties);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
            if (!isIgniter(stack)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel && !relight(serverLevel, pos, state)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }

    }

}
