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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireAim;
import play.xponer.astronima.wire.WireStock;
import play.xponer.astronima.wire.WireTrace;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Snips: take one length out of a run without tearing out the whole circuit.
 *
 * <p>{@link WireCutterItem} only ever answers "the whole thing" or "one surface" — exactly right
 * for a route that was wrong from the start, and no help at all for a run that is <em>mostly</em>
 * right. Splicing a corrected middle section back in used to mean pulling every pixel between two
 * studs, which meant most re-routes were a full cutter pass and a full redraw even when only a
 * short stretch was wrong.
 *
 * <h2>Two clicks, the coil's own shape</h2>
 * <table>
 *   <tr><td>Right-click a wire pixel</td><td>mark the cut's start</td></tr>
 *   <tr><td>Right-click another pixel of the <strong>same run</strong></td><td>cut everything
 *       between them, including both marked pixels, and refund the metal</td></tr>
 *   <tr><td>Sneak + right-click</td><td>let go of the marked start</td></tr>
 * </table>
 *
 * <p><strong>Deliberately two clicks, not three.</strong> An earlier shape of this tool
 * (`design` note, Phase B) had the second click only preview the cut and a third commit it — but
 * the ghost the second click would have previewed is already on screen continuously the moment a
 * start is marked (see the wire-snips ghost renderer), exactly the way the coil's own route ghost
 * already follows the crosshair before that tool's second click lands anything. A confirmation
 * step the player cannot see anything new from is not caution, it is friction, so this keeps the
 * coil's own established rhythm: mark, then commit.
 *
 * <p><strong>Two refusals named rather than guessed</strong> (`design` note): the two marked
 * pixels have to be on one run at all ({@link Wires#pathBetween} coming back empty means they are
 * not), and cutting the shortest path between them must not leave the two "sides" still joined by
 * some other route — a loop between the two points, which {@link Wires#wouldStillBeJoinedAfter}
 * exists to catch. Both refuse with a stated reason and leave the marked start alone, so a
 * mis-click costs nothing but another click.
 */
public class WireSnipsItem extends Item {

    public WireSnipsItem(Properties properties) {
        super(properties);
    }

    private static WirePixel anchorPixel(ItemStack stack) {
        WireCoil.Anchor anchor = stack.get(ModDataComponents.SNIPS_ANCHOR.get());
        return anchor == null ? null : WireCoilItem.pixelOf(anchor);
    }

    private static WireCoil.Anchor anchorFor(WirePixel pixel) {
        return new WireCoil.Anchor(new BlockPos(pixel.x(), pixel.y(), pixel.z()),
                Faces.of(pixel.face()), pixel.u(), pixel.v());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            letGoOf(stack, player);
            return InteractionResult.SUCCESS;
        }

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        WirePixel target = WireAim.at(level, hit, true).pixel();
        BlockPos cell = Wires.cellOf(target);
        Direction face = Faces.of(target.face());
        WireTrace targetTrace = traceAt(serverLevel, cell, face, target);
        if (targetTrace == null) {
            say(player, "astronima.wire.snip_nothing", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }

        WirePixel start = anchorPixel(stack);
        return start == null ? markStart(stack, player, target)
                : commitCut(serverLevel, stack, player, start, target);
    }

    /** First click: mark the cut's start. */
    private static InteractionResult markStart(ItemStack stack, Player player, WirePixel target) {
        stack.set(ModDataComponents.SNIPS_ANCHOR.get(), anchorFor(target));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS, 0.6f, 1.4f);
        sayArgs(player, "astronima.wire.snip_held", ChatFormatting.AQUA,
                Component.literal(target.u() + "," + target.v()));
        return InteractionResult.SUCCESS;
    }

    /** Second click: cut the run between the marked start and here. */
    private static InteractionResult commitCut(ServerLevel level, ItemStack stack, Player player,
                                               WirePixel start, WirePixel target) {
        if (start.equals(target)) {
            say(player, "astronima.wire.snip_same_pixel", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        WireTrace startTrace = traceAt(level, Wires.cellOf(start), Faces.of(start.face()), start);
        if (startTrace == null) {
            stack.remove(ModDataComponents.SNIPS_ANCHOR.get());
            say(player, "astronima.wire.snip_start_gone", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        DyeColor colour = startTrace.colour();

        List<WirePixel> path = Wires.pathBetween(level, start, target, colour, Wires.DEFAULT_LIMIT);
        if (path.isEmpty()) {
            say(player, "astronima.wire.snip_not_joined", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (Wires.wouldStillBeJoinedAfter(level, path, colour, Wires.DEFAULT_LIMIT)) {
            say(player, "astronima.wire.snip_cyclic", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        ConductorMaterial metal = worstMaterial(level, path, colour, startTrace.material());
        for (WirePixel pixel : path) {
            Wires.remove(level, pixel, colour);
        }
        WireStock.refund(player, metal, path.size(), startTrace.gauge());
        stack.remove(ModDataComponents.SNIPS_ANCHOR.get());
        level.playSound(null, player.blockPosition(), SoundEvents.SHEARS_SNIP,
                SoundSource.BLOCKS, 0.8f, 1.4f);
        sayArgs(player, "astronima.wire.snip_cut", ChatFormatting.AQUA,
                Component.literal(String.valueOf(path.size())),
                Component.literal(metal.name().toLowerCase(Locale.ROOT)));
        return InteractionResult.SUCCESS;
    }

    /**
     * The worst conductor along the path being cut — the same "a chain is its weakest link"
     * honesty {@link Wires.Network#weakest()} already applies to a whole-circuit cut, scoped to
     * just the pixels this cut actually takes.
     */
    private static ConductorMaterial worstMaterial(ServerLevel level, List<WirePixel> path,
                                                    DyeColor colour, ConductorMaterial fallback) {
        ConductorMaterial worst = fallback;
        for (WirePixel pixel : path) {
            var trace = Wires.trace(level, pixel, colour);
            if (trace.isPresent()
                    && trace.get().material().resistivity20C() > worst.resistivity20C()) {
                worst = trace.get().material();
            }
        }
        return worst;
    }

    /** Whichever trace actually occupies the pixel being pointed at. */
    private static @Nullable WireTrace traceAt(ServerLevel level, BlockPos cell, Direction face,
                                               WirePixel pixel) {
        for (WireTrace trace : Wires.bundleOn(level, cell, face)) {
            if (trace.has(pixel.u(), pixel.v())) {
                return trace;
            }
        }
        return null;
    }

    /**
     * Left click and sneak+click both funnel here: let go of whatever is marked. Called from
     * {@code WireEvents}, which also stops the snips breaking anything while they are held.
     */
    public static boolean letGoOf(ItemStack stack, Player player) {
        if (stack.get(ModDataComponents.SNIPS_ANCHOR.get()) == null) {
            return false;
        }
        stack.remove(ModDataComponents.SNIPS_ANCHOR.get());
        say(player, "astronima.wire.snip_let_go", ChatFormatting.GRAY);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("astronima.wire.tip_snips")
                .withStyle(ChatFormatting.GRAY));
        if (stack.get(ModDataComponents.SNIPS_ANCHOR.get()) != null) {
            lines.accept(Component.translatable("astronima.wire.tip_snips_holding")
                    .withStyle(ChatFormatting.GREEN));
        }
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
}
