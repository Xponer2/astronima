package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.airlock.AirlockCommissioning;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.AirlockControllerBlock;
import play.xponer.astronima.block.BulkheadDoorBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.pipe.PipeSurvey;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;
import play.xponer.astronima.sim.pipe.RunDiagnosis;

import java.util.function.Consumer;

/**
 * Turns the block you use it on, in place.
 *
 * <p>A directional block is easy to build facing the wrong way and tedious to fix by
 * breaking and replacing. The wrench is the standard answer: hold it, click the block, and
 * it rotates where it stands. It works on the orientation <em>property</em> rather than on
 * a list of blocks, so it turns the pump and port (a six-way {@code FACING}), the valve (an
 * {@code AXIS}), and any vanilla directional block for free.
 *
 * <p>With the wrench in hand a right-click means <em>rotate</em>, which is why it consumes
 * the interaction — a valve stepped with an empty hand still changes its setting, but a
 * valve wrenched changes only its axis. On a block with nothing to turn it passes the click
 * straight through, so it never gets in the way of a block's ordinary use.
 */
public class WrenchItem extends Item {
    /**
     * How far from its panel a terminal may be filled.
     *
     * <p>Generous — a pump and tank sit at the far end of a pipe run by design — but not
     * unbounded: binding a device on the other side of the base to a panel you cannot see
     * is not commissioning, it is a way to build an airlock that can never be understood.
     */
    private static final double BIND_RANGE = 32.0;

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /**
     * Calibration, before the block gets the click.
     *
     * <p>Every one of these machines opens a menu on right-click and <em>consumes</em> the
     * interaction doing it, so an ordinary {@code useOn} never runs on them: the panel opened and
     * the wrench in the player's hand did nothing at all. That is exactly the silent kind of
     * nothing this project keeps finding, and it survived because no test had ever put a wrench
     * on a machine — the crusher's calibration had shipped unreachable.
     *
     * <p>{@code onItemUseFirst} is the hook for a tool whose business is with the block rather
     * than with its menu, and it is one site rather than an override in five machine blocks
     * (rule 20). A machine that does not drift returns PASS and opens its panel as before.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // The client cannot know whether this machine drifts without asking, and guessing
            // SUCCESS here would swallow the panel-opening click on machines that do not.
            return InteractionResult.PASS;
        }
        // A machine whose setting drifts is calibrated with this, not from a menu. One turn of the
        // wrench per click, several turns to the job - the cost of the mechanic is minutes, never
        // a resource, so a machine can always be put right however far it has gone
        // (design/calibration.md).
        //
        // Asked of the machine, not written as a list of machines. Every drifting machine is
        // reachable the moment its block entity names a drift, which is the difference between a
        // mechanic and five copies of one (rule 20).
        if (level.getBlockEntity(context.getClickedPos())
                instanceof play.xponer.astronima.block.entity.ProcessingBlockEntity machine
                && machine.drift().drifts()) {
            boolean reversed = context.getPlayer() != null
                    && context.getPlayer().isSecondaryUseActive();
            machine.turnTheAdjuster(reversed ? -1 : +1);
            level.playSound(null, context.getClickedPos(), SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS, 0.5f, reversed ? 1.3f : 1.6f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // Assume success on the client so the arm swings; the server decides for real.
            return InteractionResult.SUCCESS;
        }
        TerminalArm arm = context.getItemInHand().get(ModDataComponents.TERMINAL_ARM.get());
        if (arm != null) {
            return bind(level, context, arm);
        }

        BlockState state = level.getBlockState(context.getClickedPos());
        boolean reverse = context.getPlayer() != null && context.getPlayer().isSecondaryUseActive();
        BlockState rotated = rotate(state, reverse);
        if (rotated == null || rotated == state) {
            // Nothing to turn here. On plumbing that is not a dead end — it is the survey
            // (plumbing-rebuild.md D3): a tank and a straight pipe have no orientation, and
            // "tell me about this line" is exactly what a player holding a wrench at one of
            // them wants. Anything else still passes the click through untouched.
            if (survey(level, context)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        level.setBlock(context.getClickedPos(), rotated, Block.UPDATE_ALL);
        // A rotated port opens into a different room, and a rotated pump reverses which
        // face draws; re-resolve the atmosphere here. Cheap for a manual, one-off action.
        Atmosphere.get(level).invalidate(context.getClickedPos());
        level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                SoundSource.BLOCKS, 0.7f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    /**
     * An armed wrench says so in the hand.
     *
     * <p>Binding mode changes what the next click does, and a mode the player cannot see is
     * a trap — they would put the wrench away, come back later, and find that clicking a
     * pump no longer turns it.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        TerminalArm arm = stack.get(ModDataComponents.TERMINAL_ARM.get());
        if (arm != null) {
            lines.accept(Component.translatable("astronima.wrench.armed", arm.role().label())
                    .withStyle(ChatFormatting.AQUA));
            lines.accept(Component.translatable("astronima.wrench.armed_cancel")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * The second half of commissioning: the player armed a terminal on a panel, walked to a
     * device, and clicked it.
     *
     * <p>Everything that can go wrong is said <em>at the moment of the click</em>, which is
     * the one rule design/commissioning.md §C3 insists on: a click that quietly does
     * nothing is indistinguishable from a click that did not register, and the player will
     * stand there clicking. So a tank offered as a door is told it is a tank, and a click
     * too far from the panel is told the panel is out of reach — and the wrench stays armed
     * either way, because the player is plainly still trying to fill that terminal.
     */
    private static InteractionResult bind(ServerLevel level, UseOnContext context,
                                          TerminalArm arm) {
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        // The way out. A mode with no exit is a trap: without this, a player who armed a
        // terminal and changed their mind would have a wrench that refuses to rotate
        // anything until they bind something they do not want bound.
        if (player != null && player.isSecondaryUseActive()) {
            context.getItemInHand().remove(ModDataComponents.TERMINAL_ARM.get());
            say(player, "astronima.airlock.disarmed", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(arm.controller())
                instanceof AirlockControllerBlockEntity controller)) {
            // The panel was broken while the player walked over: disarm rather than leave
            // a wrench that will never bind anything.
            context.getItemInHand().remove(ModDataComponents.TERMINAL_ARM.get());
            say(player, "astronima.airlock.panel_gone", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!clicked.closerThan(arm.controller(), BIND_RANGE)) {
            say(player, "astronima.airlock.too_far", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        Role role = arm.role();
        BlockState state = level.getBlockState(clicked);
        if (!accepts(role, state)) {
            sayArgs(player, "astronima.airlock.wrong_kind", ChatFormatting.RED,
                    state.getBlock().getName(), Component.literal(role.words()));
            return InteractionResult.SUCCESS;
        }

        // A door is two blocks and a player clicks whichever is at eye height, so the
        // binding is normalised now — otherwise the same door bound from two heights
        // would read as two devices and "same door as the other" could never fire.
        BlockPos device = isDoorRole(role)
                ? AirlockCommissioning.doorFoot(level, clicked) : clicked.immutable();
        controller.bind(role, device);
        context.getItemInHand().remove(ModDataComponents.TERMINAL_ARM.get());
        level.playSound(null, clicked, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS,
                0.8f, 1.4f);
        sayArgs(player, "astronima.airlock.bound", ChatFormatting.AQUA,
                Component.literal(role.label()), state.getBlock().getName());
        // Straight back to the panel, so the player sees the terminal land rather than
        // having to go and check.
        AirlockControllerBlock.openPanel(player, controller, arm.controller());
        return InteractionResult.SUCCESS;
    }

    /**
     * Reports what the run this fitting belongs to is doing, or why it is doing nothing.
     *
     * <p>The wrench's diagnostic mode, and the last unbuilt half of it
     * ({@code plumbing-rebuild.md} D3). Every fitting in the mod carries a gauge; the
     * <em>run</em> never did, and the run is what people get wrong — a line joined to one
     * room, a line with a shut valve in the middle. All of them look identical from outside:
     * a pipe, doing nothing. Reported from play three separate ways, every time with the
     * plumbing behaving exactly as built and no way to see it.
     *
     * <p>On the action bar rather than as a gauge, because a run is not a block: there is
     * nowhere to hang a lamp, and this is a one-off answer to a deliberate question, which is
     * what PLAN §0.5.0 reserves the action bar for. The <em>fittings</em> keep their own
     * gauges; this is about the line between them.
     *
     * @return true when this was plumbing and something was said
     */
    private static boolean survey(ServerLevel level, UseOnContext context) {
        RunDiagnosis.Survey survey = PipeSurvey.of(level, context.getClickedPos());
        if (survey == null) {
            return false;
        }
        RunDiagnosis.Verdict verdict = RunDiagnosis.verdict(survey);
        sayArgs(context.getPlayer(), "astronima.wrench.survey",
                verdict.isFault() ? ChatFormatting.RED : ChatFormatting.AQUA,
                Component.literal(RunDiagnosis.label(survey)));
        level.playSound(null, context.getClickedPos(), SoundEvents.COPPER_BULB_TURN_ON,
                SoundSource.BLOCKS, 0.5f, verdict.isFault() ? 0.7f : 1.4f);
        return true;
    }

    /** Which blocks may fill which terminal — the only check the click itself makes. */
    private static boolean accepts(Role role, BlockState state) {
        return switch (role) {
            case INNER_DOOR, OUTER_DOOR -> state.getBlock() instanceof BulkheadDoorBlock;
            case PUMP -> state.getBlock() instanceof GasPumpBlock;
            case TANK -> state.getBlock() instanceof GasTankBlock;
        };
    }

    private static boolean isDoorRole(Role role) {
        return role == Role.INNER_DOOR || role == Role.OUTER_DOOR;
    }

    private static void say(@Nullable Player player, String key, ChatFormatting colour) {
        sayArgs(player, key, colour);
    }

    /** Action bar: a one-off event tied to a click, never chat (PLAN §0.5.0). */
    private static void sayArgs(@Nullable Player player, String key, ChatFormatting colour,
                                Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.translatable(key, args).withStyle(colour), true);
        }
    }

    /**
     * True when the wrench may turn this block in place.
     *
     * <p>A block is turnable when it carries a single-block orientation the wrench can step
     * <em>and</em> turning it cannot break an invariant something else depends on. Doors are
     * refused on both counts, and this method exists because of the bug that made it necessary:
     * a bulkhead door carries {@code HORIZONTAL_FACING} like a pump does, so the old "turn
     * whatever has an orientation" wrench happily rotated one half of it. That desynced the two
     * halves — the door read <em>physically open</em> while its {@code OPEN} state stayed false,
     * so the chamber stood open while the controller believed it sealed. That is rule 19 exactly
     * (an answer the player acts on — "is this door shut" — derived from something that moves),
     * and rule 22: a wrench must not rotate a block whose orientation is load-bearing for a
     * sealing or safety invariant, or that is a multi-block structure it can only half-turn. A
     * door is both.
     */
    public static boolean rotates(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return false;
        }
        return state.hasProperty(BlockStateProperties.FACING)
                || state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.hasProperty(BlockStateProperties.AXIS);
    }

    /**
     * The block turned one step, or null when it has no orientation to turn — or when turning it
     * would break something, per {@link #rotates}.
     *
     * <p>Chosen by which property the block carries, so one method covers every
     * directional block. Six-way facing steps through all six so repeated clicks reach any
     * of them; an axis cycles the three; a horizontal block turns round the compass.
     */
    private static @Nullable BlockState rotate(BlockState state, boolean reverse) {
        if (!rotates(state)) {
            return null;
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return state.setValue(BlockStateProperties.FACING, step6(facing, reverse));
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            Direction next = reverse ? facing.getCounterClockWise() : facing.getClockWise();
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, next);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.setValue(BlockStateProperties.AXIS,
                    stepAxis(state.getValue(BlockStateProperties.AXIS), reverse));
        }
        return null;
    }

    /** Next of the six directions by 3D data value, so repeated clicks visit all of them. */
    private static Direction step6(Direction facing, boolean reverse) {
        int v = facing.get3DDataValue();
        int next = Math.floorMod(reverse ? v - 1 : v + 1, 6);
        return Direction.from3DDataValue(next);
    }

    private static Direction.Axis stepAxis(Direction.Axis axis, boolean reverse) {
        Direction.Axis[] axes = Direction.Axis.values();
        int next = Math.floorMod(reverse ? axis.ordinal() - 1 : axis.ordinal() + 1, axes.length);
        return axes[next];
    }
}
