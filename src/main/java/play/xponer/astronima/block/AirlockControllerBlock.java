package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.item.SuitLoadout;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.airlock.AirlockCycle;

/**
 * The panel that runs a two-door airlock.
 *
 * <p>Placed on a chamber wall facing into it, it sequences the chamber the player built —
 * pumping its air into a tank before opening to space and letting it back in on the way
 * home — so leaving the habitat spends stored air and time rather than venting it. It does
 * no simulation itself; {@link AirlockControllerBlockEntity} finds and drives the port,
 * pump, tank and doors that do.
 */
public class AirlockControllerBlock extends BaseEntityBlock
        implements play.xponer.astronima.wire.Terminated,
        play.xponer.astronima.wire.SignalSource {

    /**
     * Drives its status stud while a cycle is actually running.
     *
     * <p>The one fact everything else in a habitat wants to interlock on: <em>the lock is
     * busy</em>. A lamp over the door, a second lock held shut, a pump inhibited — all of them
     * are this signal, and none of them needs the airlock to know they exist.
     */
    @Override
    public boolean isDriving(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                             play.xponer.astronima.wire.Terminal terminal) {
        if (terminal.kind() != play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller)) {
            return false;
        }
        AirlockCycle.Phase phase = controller.phase();
        return phase != AirlockCycle.Phase.SEALED && phase != AirlockCycle.Phase.VACUUM;
    }

    /**
     * Control inputs on the three sides that are not the panel.
     *
     * <p>Not the panel's own face: that is where the player reads and presses, and a terminal
     * there would put a stud in the middle of the instrument.
     */
    @Override
    public java.util.List<play.xponer.astronima.wire.Terminal> terminals(BlockState state) {
        java.util.List<play.xponer.astronima.wire.Terminal> found = new java.util.ArrayList<>(6);
        net.minecraft.core.Direction panel = state.getValue(FACING);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (side == panel) {
                continue;
            }
            // Named, though it is the only input this block has. An unnamed stud is one a read
            // can only find by asking "any input", and that question stops being sound the day a
            // second input arrives - which is exactly how the machines' enable nearly broke when
            // the servo turned up (PLAN rule 44). Naming it costs a word now and a bug later.
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_IN_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.SIGNAL_IN, CYCLE));
            found.add(new play.xponer.astronima.wire.Terminal(side,
                    play.xponer.astronima.wire.Terminated.STRIP_OUT_U, 8,
                    play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT));
        }
        return found;
    }

    /** What the controller's input stud does: one rising edge presses Cycle. */
    public static final String CYCLE = "cycle";

    public static final MapCodec<AirlockControllerBlock> CODEC = simpleCodec(AirlockControllerBlock::new);

    /** The direction the panel faces — into the chamber it controls. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public AirlockControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Faces into the room the player is standing in when they set it on the wall. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Opens the panel.
     *
     * <p>It used to cycle the airlock on the click itself, which is how it came back from
     * play as *"pressing on the block is stupid, nothing is comprehensible"* — a machine
     * with a chamber, two doors, a pump and a tank bound to it cannot say any of that
     * through a right-click. The click now opens the schematic, which shows what it bound
     * to, and the Cycle control lives there where the consequences are visible.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // A real airlock refuses to depressurise on an unsuited crew; this one warns
            // and obeys — killing yourself is your decision, but never a silent one.
            if (controller.phase() == AirlockCycle.Phase.SEALED && !SuitLoadout.isSealed(player)) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("astronima.airlock.no_suit")
                                .withStyle(ChatFormatting.RED), true);
            }
            openPanel(player, controller, pos);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Opens the schematic for this controller.
     *
     * <p>Shared so the wrench can bring the player straight back to it after landing a
     * device on a terminal: commissioning is arm → walk → click, and dropping them in the
     * world with no confirmation would make the whole loop feel like it had not worked.
     */
    public static void openPanel(@Nullable Player player,
                                 AirlockControllerBlockEntity controller, BlockPos pos) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new play.xponer.astronima.menu.AirlockUiHolder(controller, pos),
                    buffer -> buffer.writeBlockPos(pos));
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirlockControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.AIRLOCK_CONTROLLER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                ((AirlockControllerBlockEntity) blockEntity).serverTick(tickLevel, pos, tickState);
    }
}
