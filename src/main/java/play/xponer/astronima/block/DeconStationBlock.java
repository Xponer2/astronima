package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.block.entity.DeconStationBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Stand on it and it cleans you: lamp first, then water for what the light could not see.
 *
 * <p>No menu, deliberately. The thing being cleaned is <em>you</em>, and the instrument that shows
 * it already exists — the biomonitor's contamination bars, which fall while you stand here. A
 * second screen showing the same two numbers would be a duplicate that can disagree with itself.
 */
public class DeconStationBlock extends Block implements EntityBlock {

    /** Lit while a cycle is running, so the booth reads as working from across the room. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public DeconStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                          BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (!held.is(Items.POTION) && !held.is(Items.WATER_BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof DeconStationBlockEntity booth)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // A bottle is a third of a litre and a bucket is the whole tank, which is the same
        // arithmetic every other water job in this mod uses.
        double litres = held.is(Items.WATER_BUCKET) ? DeconStationBlockEntity.TANK_LITRES : 0.33;
        if (!booth.addWater(litres)) {
            return InteractionResult.CONSUME;
        }
        if (!player.hasInfiniteMaterials()) {
            player.setItemInHand(hand, held.is(Items.WATER_BUCKET)
                    ? new ItemStack(Items.BUCKET) : new ItemStack(Items.GLASS_BOTTLE));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()
                || !(level.getBlockEntity(pos) instanceof DeconStationBlockEntity booth)) {
            return InteractionResult.SUCCESS;
        }
        if (booth.isRunning()) {
            booth.stop();
        } else {
            booth.start();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeconStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.DECON_STATION.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> {
            if (!(entity instanceof DeconStationBlockEntity booth)) {
                return;
            }
            if (world instanceof net.minecraft.server.level.ServerLevel server) {
                if (world.getGameTime() % 10 != 0) {
                    return;
                }
                booth.serverTick(server, 0.5);
                if (blockState.getValue(RUNNING) != booth.isRunning()) {
                    world.setBlock(pos, blockState.setValue(RUNNING, booth.isRunning()), 3);
                }
            } else if (blockState.getValue(RUNNING)) {
                // Something to look at: the lamp throws a haze, the rinse throws droplets.
                var random = world.getRandom();
                for (int i = 0; i < 3; i++) {
                    world.addParticle(booth.isRinsing()
                                    ? ParticleTypes.FALLING_WATER : ParticleTypes.END_ROD,
                            pos.getX() + random.nextDouble(),
                            pos.getY() + 0.9 + random.nextDouble() * 0.8,
                            pos.getZ() + random.nextDouble(), 0, 0, 0);
                }
            }
        };
    }
}
