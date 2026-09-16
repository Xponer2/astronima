package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.entity.PurgeValveBlockEntity;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * The valve you open when the air in here can never be breathed again.
 *
 * <p><strong>You do not filter a toxic release; you dump it.</strong> A scrubber exists because
 * crew make carbon dioxide continuously and a bed of lithium hydroxide takes it out at the rate
 * they make it. Nothing on a spacecraft filters a <em>spill</em> — the contingency for a serious
 * atmospheric contamination is isolate the module, dump its atmosphere, repressurise from
 * stores, and every crewed vehicle ever flown carries the valve for it.
 *
 * <p>That is also why this is not the pump with a different label (rule 8). The pump
 * <em>stores</em> gas you intend to have back; this <em>discards</em> gas you must never
 * breathe. Different verbs, different situations, and the cost tells them apart: after the
 * oxygen tier an atmosphere is worth roughly twenty chlorate crystals, so choosing to throw one
 * away is a decision rather than a button.
 *
 * <p>Before this block, nine of the mod's eleven gases could not be got back out of a room at
 * all — see {@code design/contamination.md} §0.
 */
public class PurgeValveBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PurgeValveBlock> CODEC = simpleCodec(PurgeValveBlock::new);

    /** Open is dumping. Kept on the state so the block itself reads as open or shut (rule 9). */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /**
     * How fast an open valve empties the room behind it, per second.
     *
     * <p>Emptying a module in about fifteen seconds, which is the right order for an emergency
     * vent: fast enough that it is genuinely the contingency response, slow enough that a
     * player who opened it by mistake has a moment to shut it again.
     */
    public static final double CONDUCTANCE_PER_SECOND = 0.25;

    public PurgeValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Faces the way the player is looking, so "the outward face" is the one they aimed at.
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Opens or shuts it — and refuses, loudly, when there is nothing outside to dump into.
     *
     * <p><strong>Rule 18.</strong> A valve built into an interior wall has no vacuum on its far
     * side and can do nothing at all. Letting it sit there reading "open" while the pressure
     * did not move would be indistinguishable from a broken block, and the player would go
     * looking for the fault anywhere except the wall they chose. So the refusal names the
     * reason, at the moment they ask.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        boolean opening = !state.getValue(OPEN);
        if (opening && !facesVacuum(serverLevel, state, pos)) {
            player.sendSystemMessage(Component.translatable("astronima.purge.no_vacuum"));
            return InteractionResult.FAIL;
        }
        level.setBlock(pos, state.setValue(OPEN, opening), Block.UPDATE_ALL);
        player.sendSystemMessage(Component.translatable(
                opening ? "astronima.purge.opened" : "astronima.purge.shut"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether the outward face has somewhere to dump to.
     *
     * <p>Asked of the atmosphere rather than of the block in front, because "outside" is not a
     * block — it is <strong>the absence of a volume that is holding pressure</strong>. A valve
     * opening onto a second habitat would merely move the poison next door, which is worse
     * than useless; a valve opening onto rock can do nothing at all.
     *
     * <p><strong>The test is {@code sealed()}, not "is it a room".</strong> The first draft
     * asked for {@code openToSpace}, which is a stricter thing than it sounds: a vented pocket
     * or an over-cap tunnel maze is neither a sealed room nor formally open to space, and both
     * are perfectly good places to dump an atmosphere — they are not holding any. That draft
     * refused everywhere except a cell the scanner had specifically marked, which in a game
     * test is nowhere, and the scenario failed with the valve reporting itself open and doing
     * nothing. Which is, precisely, the symptom rule 18 says this block must never produce.
     */
    public static boolean facesVacuum(ServerLevel level, BlockState state, BlockPos pos) {
        BlockPos outward = pos.relative(state.getValue(FACING));
        if (!level.getBlockState(outward).isAir()) {
            return false;
        }
        Atmosphere.RoomReading reading = Atmosphere.get(level).readingNear(outward);
        return reading == null || !reading.sealed();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PurgeValveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.PURGE_VALVE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((PurgeValveBlockEntity) blockEntity).serverTick(tickLevel, pos);
    }

    /** True when this valve is dumping right now. */
    public static boolean isVenting(BlockState state) {
        return state.getBlock() instanceof PurgeValveBlock && state.getValue(OPEN);
    }
}
