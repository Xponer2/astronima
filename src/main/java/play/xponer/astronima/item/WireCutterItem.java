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
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireStock;
import play.xponer.astronima.wire.WireTrace;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Cutters: the way wire comes back out, and the metal with it.
 *
 * <p>Wire is not a block, so it cannot be mined — and once laying it started costing metal, being
 * unable to reclaim any of that metal made every mistake permanent. This is the other half of the
 * loop.
 *
 * <p><strong>Two cuts, because a run and a length of run are different mistakes.</strong>
 *
 * <table>
 *   <tr><td>Right-click a trace</td><td><strong>the whole circuit</strong> — everything
 *       electrically joined to that pixel comes out at once. What you want when a route was
 *       wrong from the start</td></tr>
 *   <tr><td>Sneak + right-click</td><td><strong>just that surface</strong> — one block's worth,
 *       for trimming a corner or freeing a face you want to build on</td></tr>
 * </table>
 *
 * <p>Cutting the whole circuit is the headline because that is the operation that is otherwise
 * <em>impossible</em>: a run of four hundred pixels cannot reasonably be picked off one at a
 * time, and having to would make routing a long run a decision nobody would ever revisit.
 *
 * <p>What comes back is rounded down (see {@link WireStock#refund}) — drawing wire and remelting
 * the offcut does not come out even in life either, and the shortfall is what stops laying and
 * cutting the same metre being a way to make metal.
 */
public class WireCutterItem extends Item {

    /** How much of a circuit one cut will take, in pixels. */
    public static final int LIMIT = 20_000;

    public WireCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);

        // A part first, because it is the thing under the crosshair and it is the only way one
        // comes off a wall short of breaking the wall. Wire under a part's housing cannot exist
        // (Wires.canMount refuses it), so there is nothing here for this to shadow.
        play.xponer.astronima.wire.WirePart part =
                play.xponer.astronima.wire.PartInteraction.under(level, hit);
        if (part != null) {
            Wires.unmount(serverLevel, part);
            if (!player.getInventory().add(
                    play.xponer.astronima.wire.WireEvents.asItem(part))) {
                player.drop(play.xponer.astronima.wire.WireEvents.asItem(part), false);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.7f, 1.1f);
            return InteractionResult.SUCCESS;
        }

        // preferWire: the cutters are looking for conductor, so a trace running under a stud
        // is reachable rather than shadowed by the terminal on top of it.
        WirePixel target = play.xponer.astronima.wire.WireAim.at(level, hit, true).pixel();
        BlockPos cell = Wires.cellOf(target);
        Direction face = Faces.of(target.face());

        WireTrace trace = traceAt(serverLevel, cell, face, target);
        if (trace == null) {
            say(player, "astronima.wire.cut_nothing", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        return player.isSecondaryUseActive()
                ? cutSurface(serverLevel, player, cell, face)
                : cutCircuit(serverLevel, player, target, trace);
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
     * The whole circuit, in one cut.
     *
     * <p>Walked first and removed after: taking pixels out while walking would cut the network
     * under the walk's own feet and leave most of it behind.
     */
    private static InteractionResult cutCircuit(ServerLevel level, Player player,
                                                WirePixel start, WireTrace trace) {
        DyeColor colour = trace.colour();
        Wires.Network network = Wires.network(level, start, colour, LIMIT);
        ConductorMaterial metal = network.weakest().orElse(trace.material());

        List<WirePixel> pixels = network.pixels();
        for (WirePixel pixel : pixels) {
            Wires.remove(level, pixel, colour);
        }
        // Refunded at the gauge that was actually laid: cutting a busbar has to hand back a
        // busbar's worth of metal, or thick wire would be a way to destroy iron.
        WireStock.refund(player, metal, pixels.size(), trace.gauge());
        level.playSound(null, player.blockPosition(), SoundEvents.SHEARS_SNIP,
                SoundSource.BLOCKS, 0.8f, 1.0f);
        sayArgs(player, "astronima.wire.cut_run", ChatFormatting.AQUA,
                Component.literal(String.valueOf(pixels.size())),
                Component.literal(metal.name().toLowerCase(Locale.ROOT)));
        return InteractionResult.SUCCESS;
    }

    /** One surface's worth, for trimming rather than tearing out. */
    private static InteractionResult cutSurface(ServerLevel level, Player player, BlockPos cell,
                                                Direction face) {
        // Refund each colour in its own metal: a bundle on one wall may be several materials,
        // and handing back the wrong one would quietly launder metal.
        for (WireTrace trace : Wires.bundleOn(level, cell, face)) {
            WireStock.refund(player, trace.material(), trace.pixelCount(), trace.gauge());
        }
        int pulled = Wires.removeAll(level, cell, face);
        level.playSound(null, player.blockPosition(), SoundEvents.SHEARS_SNIP,
                SoundSource.BLOCKS, 0.7f, 1.3f);
        sayArgs(player, "astronima.wire.cut_face", ChatFormatting.AQUA,
                Component.literal(String.valueOf(pulled)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("astronima.wire.tip_cutter")
                .withStyle(ChatFormatting.GRAY));
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
