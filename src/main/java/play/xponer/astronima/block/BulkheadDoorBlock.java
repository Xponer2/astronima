package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.airlock.DoorLatches;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModDataComponents;

/**
 * A machined pressure door: the only door that is airtight when closed (open it and
 * gas moves freely; ordinary doors leak around the frame in any state — see
 * {@link play.xponer.astronima.atmosphere.AirBlockKinds}). Uses the copper set type
 * for hand-openable metal behavior.
 */
public class BulkheadDoorBlock extends DoorBlock {

    /**
     * The interlock latch: thrown, this door does not open by any means.
     *
     * <p>A real state rather than a controller that shuts the door again afterwards, which
     * is what this used to be. The old arrangement let the door swing and pulled it back on
     * the next controller tick — so the invariant was <em>repaired</em> rather than
     * enforced, gas moved through the gap, redstone and pistons bypassed it entirely, and
     * the player experienced a machine fighting them instead of a hatch that would not
     * turn. Reported as <em>"могу дёргать двери и ничего"</em>.
     *
     * <p>Vanilla's own property, so a redstone-minded player already has a mental model for
     * a locked door, and so the blockstate JSON names something recognisable.
     */
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public BulkheadDoorBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.COPPER, properties);
        registerDefaultState(defaultBlockState().setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOCKED);
    }

    /** True when the interlock is holding this door. */
    public static boolean isLocked(BlockState state) {
        return state.getBlock() instanceof BulkheadDoorBlock && state.getValue(LOCKED);
    }

    /**
     * Throws or releases the latch on both halves of a door, given either half.
     *
     * <p>Both halves, always: a door whose top is locked and whose bottom is not would open
     * anyway, and the two halves are one door to everything except the block grid.
     *
     * @return true when anything actually changed, so the caller can avoid a block update
     */
    public static boolean setLocked(Level level, BlockPos anyHalf, boolean locked) {
        BlockState state = level.getBlockState(anyHalf);
        if (!(state.getBlock() instanceof BulkheadDoorBlock)) {
            return false;
        }
        BlockPos foot = state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? anyHalf.below() : anyHalf;
        boolean changed = false;
        for (BlockPos half : new BlockPos[] {foot, foot.above()}) {
            BlockState at = level.getBlockState(half);
            if (at.getBlock() instanceof BulkheadDoorBlock && at.getValue(LOCKED) != locked) {
                // UPDATE_CLIENTS without neighbour notification: the latch changes nothing
                // about what the door seals, and re-running the room scan on every phase of
                // every airlock cycle would be pure waste.
                level.setBlock(half, at.setValue(LOCKED, locked), Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        if (changed && level instanceof ServerLevel serverLevel) {
            if (locked) {
                // The latch starts asking, from now on, whether anyone still holds it.
                serverLevel.scheduleTick(foot, serverLevel.getBlockState(foot).getBlock(),
                        DoorLatches.CHECK_INTERVAL_TICKS);
            } else {
                applySignalAfterRelease(serverLevel, foot);
            }
        }
        return changed;
    }

    /**
     * A door released onto a live redstone line does what the line says.
     *
     * <p>Found by a test's own self-check rather than by reasoning, which is why it is
     * worth writing down. A locked door ignores the signal, so a lever held on the whole
     * time never produces a <em>change</em> for {@code neighborChanged} to react to — and
     * the door would come out of the interlock shut, and stay shut, until the player went
     * and toggled the lever. It would look exactly like the latch never released.
     *
     * <p>So the release re-reads the line once and applies it. If the wiring says open, the
     * door opens the moment the interlock lets go, which is what the player wired it to do.
     */
    private static void applySignalAfterRelease(ServerLevel level, BlockPos foot) {
        boolean signal = level.hasNeighborSignal(foot) || level.hasNeighborSignal(foot.above());
        for (BlockPos half : new BlockPos[] {foot, foot.above()}) {
            BlockState at = level.getBlockState(half);
            if (!(at.getBlock() instanceof BulkheadDoorBlock)) {
                continue;
            }
            if (at.getValue(POWERED) != signal || at.getValue(OPEN) != signal) {
                level.setBlock(half, at.setValue(POWERED, signal).setValue(OPEN, signal),
                        Block.UPDATE_ALL);
            }
        }
        if (signal) {
            Atmosphere.get(level).invalidate(foot);
        }
    }

    /**
     * A locked door asking whether anyone still holds it, and letting go if not.
     *
     * <p>This is the whole softlock guarantee, and it is here rather than in a destructor on
     * the controller because a controller can vanish in ways nothing gets told about: mined,
     * blown up, chunk unloaded, the server killed mid-cycle. None of those is handled. They
     * all simply stop the renewals, and a latch nobody renews opens.
     *
     * <p>Scheduled ticks survive a save, so a world that reloads with a door locked gets
     * this check within a second — and after a reload the register is empty by construction,
     * so the door frees itself unless a live controller has already claimed it back.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                        net.minecraft.util.RandomSource random) {
        if (!state.getValue(LOCKED)) {
            return;
        }
        BlockPos foot = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (DoorLatches.held(level, foot)) {
            level.scheduleTick(pos, this, DoorLatches.CHECK_INTERVAL_TICKS);
            return;
        }
        setLocked(level, foot, false);
    }

    /**
     * The hand: a locked hatch does not turn.
     *
     * <p>It refuses rather than opening-and-being-corrected, which is the whole difference
     * between an interlock and a machine that fights you. The clunk is the answer — a
     * latched door you try gives you a sound and no movement, and one attempt teaches the
     * whole mechanic.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (state.getValue(LOCKED)) {
            level.playSound(player, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                    0.6F, 0.55F);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /**
     * Redstone: a locked door does not follow the signal.
     *
     * <p>The route that was never covered at all. The old arrangement only shut the door on
     * the controller's own tick, so a lever or comparator wired to an airlock door was not
     * interlocked in any sense — and wiring a base up is exactly what a player does once
     * the airlock works.
     *
     * <p>The signal is deliberately <em>not</em> recorded in {@code POWERED} while locked:
     * the release re-reads the line and applies it in one place
     * ({@link #applySignalAfterRelease}), and having two places decide what a live line
     * means is how a door ends up shut with its own wiring saying open.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.getValue(LOCKED)) {
            return;
        }
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
    }

    /**
     * Pistons: a locked door cannot be shoved out of the way either.
     *
     * <p>Not thoroughness for its own sake — pushing the door out of its frame would leave
     * the chamber open with the controller still believing it is sealed, which is the same
     * hole the hand and redstone routes are closed for.
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return state.getValue(LOCKED) ? PushReaction.BLOCK : super.getPistonPushReaction(state);
    }

    /**
     * A wrench armed for a terminal binds this door instead of opening it.
     *
     * <p>Interaction order is {@code useItemOn} → {@code useWithoutItem} → the item's
     * {@code useOn}, and a door's {@code useWithoutItem} swings it — so without this, a
     * player commissioning an airlock would click their outer door and watch it open while
     * the terminal stayed empty. Same shape as the valve, which passes the click through so
     * a wrench turns its axis rather than stepping its setting.
     *
     * <p>Only while <em>armed</em>: a wrench in hand is not otherwise a reason a door
     * should refuse to open, and a tool that stopped doors working would be worse than the
     * problem it solves.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (stack.getItem() instanceof play.xponer.astronima.item.WrenchItem
                && stack.get(ModDataComponents.TERMINAL_ARM.get()) != null) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Doors toggle with the don't-notify-neighbors flag, so no block event fires and
     * rooms would keep reading the pre-toggle sealing. This hook runs on every state
     * change (hand, redstone, pistons alike) and is the authoritative invalidation
     * point for the mod's one airtight door.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel
                && oldState.getBlock() == this
                && oldState.getValue(OPEN) != state.getValue(OPEN)) {
            Atmosphere.get(serverLevel).invalidate(pos);
        }
    }
}
