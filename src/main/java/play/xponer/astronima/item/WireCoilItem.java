package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireAim;
import play.xponer.astronima.wire.WireStock;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The coil: the tool the whole wire layer is operated with.
 *
 * <p>Wire is not placed a block at a time — it is <strong>pulled</strong>. Click a surface to
 * take hold of the loose end at exactly the pixel you are pointing at, walk to where it should
 * go, and click again: the route between the two draws itself as a ghost while you move and is
 * laid when you commit. The far end becomes the new anchor, so a run is built leg by leg and the
 * shape of it is the player's, not the router's.
 *
 * <p><strong>Two routing modes, and the second is not a lesser first.</strong> {@code PATHFIND}
 * goes around what is in the way. {@code STRAIGHT} lays straight legs and <em>refuses</em> rather
 * than detour — because a router that quietly diverted when the player asked for a straight run
 * would be overruling them, and "it stops here" tells them exactly which block to move.
 *
 * <h2>The controls, all on one item</h2>
 * <table>
 *   <tr><td>Right-click a surface</td><td>take the end / lay the leg to here</td></tr>
 *   <tr><td>Sneak + right-click a surface</td><td>let go of the end, or pull up wire if not holding one</td></tr>
 *   <tr><td>Right-click the air</td><td>switch between routing round obstacles and going straight</td></tr>
 *   <tr><td>Sneak + right-click the air</td><td>next insulation colour</td></tr>
 * </table>
 */
public class WireCoilItem extends Item {

    /** How far a single leg may run, in pixels, so one click cannot span a base by accident. */
    public static final int MAX_LEG_PIXELS = 2_048;

    public WireCoilItem(Properties properties) {
        super(properties);
    }

    public static WireCoil settingOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WIRE_COIL.get(), WireCoil.DEFAULT);
    }

    public static WireCoil.@Nullable Anchor anchorOf(ItemStack stack) {
        return stack.get(ModDataComponents.WIRE_ANCHOR.get());
    }

    /**
     * The exact pixel of the surface being pointed at.
     *
     * <p>This is what makes the choice the player's: the hit position inside the block face is
     * projected onto that face's own axes, so <em>where you aim is which of the sixteen lanes you
     * get</em>. The ghost renderer highlights it before the click, so it is a decision rather than
     * a lottery.
     */
    public static WirePixel pixelAt(net.minecraft.world.level.Level level, BlockHitResult hit) {
        return WireAim.at(level, hit, false).pixel();
    }

    public static WirePixel pixelOf(WireCoil.Anchor anchor) {
        return new WirePixel(anchor.cell().getX(), anchor.cell().getY(), anchor.cell().getZ(),
                Faces.of(anchor.face()), anchor.u(), anchor.v());
    }

    private static WireCoil.Anchor anchorFor(WirePixel pixel) {
        return new WireCoil.Anchor(new BlockPos(pixel.x(), pixel.y(), pixel.z()),
                Faces.of(pixel.face()), pixel.u(), pixel.v());
    }

    /**
     * What the world permits, asked one pixel at a time.
     *
     * <p>Shared by the client's ghost and the server's commit, so the route previewed is the
     * route laid — the same object answering the same question on both sides (rule 13).
     */
    public static WireRouter.Space spaceIn(Level level) {
        // The pixel-level question, which is one obstacle stricter than the face-level one: a
        // part's housing occupies pixels, and you cannot route a wire under a component.
        return pixel -> Wires.canPlace(level, pixel);
    }

    /**
     * The same, plus a dislike of brushing against somebody else's run of this colour.
     *
     * <p>A route must not make connections the player did not ask for. Charged rather than
     * forbidden — see {@link Wires#brushes} — so a one-lane corridor still gets wired, and the
     * ghost says what the router settled for.
     */
    public static WireRouter.Space spaceIn(Level level, DyeColor colour) {
        return new WireRouter.Space() {
            @Override
            public boolean canOccupy(WirePixel pixel) {
                return Wires.canPlace(level, pixel);
            }

            @Override
            public int penalty(WirePixel pixel) {
                return Wires.brushes(level, pixel, colour) ? Wires.BRUSH_PENALTY : 0;
            }
        };
    }

    // ---- using it --------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        WirePixel target = pixelAt(level, hit);

        if (!(level instanceof ServerLevel)) {
            // Sneak opens the panel wherever you are standing. It used to need a clear line of
            // sight to the air, which in a corridor there is not — reported as "в тесном
            // помещении некуда тыкать ПКМ в воздух".
            if (player.isSecondaryUseActive()) {
                play.xponer.astronima.client.WireCoilOpener.open(settingOf(stack));
            }
            // The client assumes it worked so the arm swings; the server decides for real.
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.SUCCESS; // the panel is a client screen; it asks by packet
        }

        WireCoil coil = settingOf(stack);
        WireCoil.Anchor anchor = anchorOf(stack);

        if (!Wires.canPlace(level, target)) {
            say(player, "astronima.wire.no_support", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        return anchor == null ? takeHold(stack, player, target)
                : layLeg(level, stack, player, coil, pixelOf(anchor), target);
    }

    /** First click: the loose end is now held at this pixel. */
    private static InteractionResult takeHold(ItemStack stack, Player player, WirePixel target) {
        stack.set(ModDataComponents.WIRE_ANCHOR.get(), anchorFor(target));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS, 0.6f, 1.6f);
        sayArgs(player, "astronima.wire.held", ChatFormatting.AQUA,
                Component.literal(target.u() + "," + target.v()));
        return InteractionResult.SUCCESS;
    }

    /** Second click: route from the held end to here, lay it, and hold the far end. */
    private static InteractionResult layLeg(Level level, ItemStack stack, Player player,
                                            WireCoil coil, WirePixel from, WirePixel to) {
        if (from.equals(to)) {
            say(player, "astronima.wire.same_pixel", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var route = WireRouter.route(from, to, spaceIn(level, coil.colour()), coil.mode(),
                MAX_LEG_PIXELS);
        if (route.isEmpty()) {
            say(player, coil.mode() == WireRouter.Mode.STRAIGHT
                    ? "astronima.wire.no_straight_run" : "astronima.wire.no_route",
                    ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        List<WirePixel> pixels = route.get();
        // Never lay a route that is not a real chain. The router is trusted to search well, not
        // to be right about adjacency — and the same check will stop a crafted path once the
        // ghost is a client proposing one (rule 13).
        for (int i = 1; i < pixels.size(); i++) {
            if (!PixelGeometry.neighbours(pixels.get(i - 1)).contains(pixels.get(i))) {
                say(player, "astronima.wire.bad_route", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
        }

        // Only the pixels that are not already there cost anything: retracing your own run to
        // reach past it must not charge you twice for the same metal.
        int fresh = 0;
        for (WirePixel point : pixels) {
            if (!Wires.has(level, point, coil.colour())) {
                fresh++;
            }
        }
        if (!WireStock.isAvailable(coil.material())) {
            sayArgs(player, "astronima.wire.no_stock_kind", ChatFormatting.RED,
                    Component.literal(coil.material().name().toLowerCase(Locale.ROOT)));
            return InteractionResult.SUCCESS;
        }
        if (!WireStock.spend(player, coil.material(), fresh, coil.gauge())) {
            sayArgs(player, "astronima.wire.no_stock", ChatFormatting.RED,
                    Component.literal(String.valueOf(WireStock.itemsFor(fresh, coil.gauge()))));
            return InteractionResult.SUCCESS;
        }

        int laid = Wires.placeAll(level, pixels, coil.colour(), coil.material(), coil.gauge());
        stack.set(ModDataComponents.WIRE_ANCHOR.get(), anchorFor(to));
        level.playSound(null, player.blockPosition(), SoundEvents.COPPER_BULB_TURN_ON,
                SoundSource.BLOCKS, 0.5f, 1.2f);
        sayArgs(player, "astronima.wire.laid", ChatFormatting.AQUA,
                Component.literal(String.valueOf(laid)),
                Component.literal(String.format(Locale.ROOT, "%.2f",
                        pixels.size() / (double) FaceBasis.GRID)));
        return InteractionResult.SUCCESS;
    }

    /** Sneak with the end held: drop it, so the next click starts a fresh run. */
    private static InteractionResult letGo(ItemStack stack, Player player) {
        stack.remove(ModDataComponents.WIRE_ANCHOR.get());
        say(player, "astronima.wire.let_go", ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    /** Sneak with nothing held: pull this pixel's conductor back out of the wall. */
    private static InteractionResult pullUp(Level level, Player player, WirePixel target,
                                            WireCoil coil) {
        if (Wires.remove(level, target, coil.colour())) {
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.6f, 1.0f);
            say(player, "astronima.wire.pulled", ChatFormatting.GRAY);
        } else {
            say(player, "astronima.wire.nothing_here", ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * In the air: open the panel, or sneak for the one setting worth changing without looking.
     *
     * <p>The panel is where a coil is configured, because choosing a conductor is a decision with
     * numbers attached and a cycle-through-nine-metals button cannot show them. Sneaking still
     * flips the colour on the spot — that is the one change made mid-run, over and over, and
     * opening a screen for it would be worse than the button it replaced.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel)) {
            if (player.isSecondaryUseActive()) {
                play.xponer.astronima.client.WireCoilOpener.open(settingOf(stack));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Left click: let go of the end.
     *
     * <p>Called from {@code WireEvents}, which also stops the coil breaking anything — a tool
     * you hold for minutes on end while aiming at walls must not chew through them by reflex.
     */
    public static boolean letGoOf(ItemStack stack, Player player) {
        if (anchorOf(stack) == null) {
            return false;
        }
        stack.remove(ModDataComponents.WIRE_ANCHOR.get());
        say(player, "astronima.wire.let_go", ChatFormatting.GRAY);
        return true;
    }

    /**
     * The coil says what it is set to, because none of it is visible on the model.
     *
     * <p>A tool with four modes and no way to read them is the trap {@code WrenchItem} already
     * learned to avoid: a mode the player cannot see changes what their next click does and gives
     * them nothing to blame when it surprises them.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        WireCoil coil = settingOf(stack);
        lines.accept(Component.translatable("astronima.wire.tip_setting",
                        Component.literal(coil.material().name().toLowerCase(Locale.ROOT)),
                        Component.literal(coil.colour().getSerializedName()))
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable(coil.mode() == WireRouter.Mode.STRAIGHT
                        ? "astronima.wire.tip_straight" : "astronima.wire.tip_pathfind")
                .withStyle(ChatFormatting.AQUA));
        if (anchorOf(stack) != null) {
            lines.accept(Component.translatable("astronima.wire.tip_holding")
                    .withStyle(ChatFormatting.GREEN));
        }
        lines.accept(Component.translatable("astronima.wire.tip_controls")
                .withStyle(ChatFormatting.DARK_GRAY));
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
