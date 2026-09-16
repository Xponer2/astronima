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
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.RibbonGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireAim;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.WireStock;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The ribbon: N ordinary traces laid, routed and coloured together, so a bus is one gesture
 * instead of N repetitions of the coil. {@code design/bus.md} is the design this follows.
 *
 * <p><strong>Nothing this lays is new.</strong> Lane 0 is routed exactly as the coil would route
 * it; every other lane is {@link RibbonGeometry#laneRoutes} replaying that same route from a
 * parallel start, so what ends up in the world is indistinguishable from N runs laid by hand —
 * {@code Wires.network}, the ticker, the snips and every save/load path need nothing new to know
 * about a bus, because there is no bus object, only ordinary traces (§2.1).
 *
 * <h2>The controls</h2>
 * <table>
 *   <tr><td>Right-click a surface</td><td>take hold of lane 0's end / lay the leg to here</td></tr>
 *   <tr><td>Right-click a part's pad</td><td>land the lanes on that many consecutive pads, if the
 *       approach lines up (§3.2) — refused, and named, otherwise</td></tr>
 *   <tr><td>Sneak + right-click a surface</td><td>let go, or pull up whichever lane is there</td></tr>
 *   <tr><td>Right-click the air</td><td>routing mode: round obstacles, or straight and refuse</td></tr>
 *   <tr><td>Sneak + right-click the air</td><td>lane count: four or eight</td></tr>
 * </table>
 */
public class WireRibbonItem extends Item {

    /** Every lane's colour, fixed rather than chosen — lane 0 is always the marked stripe (§2.3). */
    public static final DyeColor[] LANE_COLOURS = {
            DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME,
            DyeColor.CYAN, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA
    };

    /** A bus is control and address lines, never a power run — signal gauge, and iron, always. */
    private static final ConductorMaterial MATERIAL = ConductorMaterial.IRON;
    private static final WireGauge GAUGE = WireGauge.SIGNAL;

    public WireRibbonItem(Properties properties) {
        super(properties);
    }

    public static WireRibbon settingOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WIRE_RIBBON.get(), WireRibbon.DEFAULT);
    }

    public static WireCoil.@Nullable Anchor anchorOf(ItemStack stack) {
        return stack.get(ModDataComponents.RIBBON_ANCHOR.get());
    }

    private static WirePixel pixelOf(WireCoil.Anchor anchor) {
        return new WirePixel(anchor.cell().getX(), anchor.cell().getY(), anchor.cell().getZ(),
                Faces.of(anchor.face()), anchor.u(), anchor.v());
    }

    private static WireCoil.Anchor anchorFor(WirePixel pixel) {
        return new WireCoil.Anchor(new BlockPos(pixel.x(), pixel.y(), pixel.z()),
                Faces.of(pixel.face()), pixel.u(), pixel.v());
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

        if (!(level instanceof ServerLevel)) {
            if (player.isSecondaryUseActive()) {
                return InteractionResult.SUCCESS; // handled server-side below
            }
            return InteractionResult.SUCCESS;
        }

        WireAim.Aim aim = WireAim.at(level, hit, false);
        if (player.isSecondaryUseActive()) {
            WireCoil.Anchor anchor = anchorOf(stack);
            return anchor == null ? pullUp(level, player, aim.pixel()) : letGo(stack, player);
        }
        if (!Wires.canPlace(level, aim.pixel())) {
            say(player, "astronima.ribbon.no_support", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        WireCoil.Anchor anchor = anchorOf(stack);
        return anchor == null ? takeHold(stack, player, aim.pixel())
                : layLeg(level, stack, player, settingOf(stack), pixelOf(anchor), aim);
    }

    private static InteractionResult takeHold(ItemStack stack, Player player, WirePixel target) {
        stack.set(ModDataComponents.RIBBON_ANCHOR.get(), anchorFor(target));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS, 0.6f, 1.4f);
        sayArgs(player, "astronima.ribbon.held", ChatFormatting.AQUA,
                Component.literal(target.u() + "," + target.v()));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult layLeg(Level level, ItemStack stack, Player player,
                                            WireRibbon setting, WirePixel from, WireAim.Aim aim) {
        WirePixel to = aim.pixel();
        if (from.equals(to)) {
            say(player, "astronima.ribbon.same_pixel", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        int width = setting.width();
        Optional<List<WirePixel>> lane0Route = WireRouter.route(from, to,
                WireCoilItem.spaceIn(level, LANE_COLOURS[0]), setting.mode(), WireCoilItem.MAX_LEG_PIXELS);
        if (lane0Route.isEmpty()) {
            say(player, setting.mode() == WireRouter.Mode.STRAIGHT
                    ? "astronima.wire.no_straight_run" : "astronima.wire.no_route", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        Optional<List<List<WirePixel>>> derived = RibbonGeometry.laneRoutes(lane0Route.get(), width);
        if (derived.isEmpty()) {
            say(player, "astronima.ribbon.no_room", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        List<List<WirePixel>> lanes = derived.get();

        if (aim.pad() != null) {
            InteractionResult landing = checkLanding(player, aim.part(), aim.pad(), lanes, width);
            if (landing != null) {
                return landing;
            }
        }

        for (List<WirePixel> lane : lanes) {
            for (WirePixel point : lane) {
                if (!Wires.canPlace(level, point)) {
                    say(player, "astronima.ribbon.blocked", ChatFormatting.RED);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        int fresh = 0;
        for (int lane = 0; lane < width; lane++) {
            DyeColor colour = LANE_COLOURS[lane];
            for (WirePixel point : lanes.get(lane)) {
                if (!Wires.has(level, point, colour)) {
                    fresh++;
                }
            }
        }
        if (!WireStock.isAvailable(MATERIAL)) {
            say(player, "astronima.wire.no_stock_kind", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!WireStock.spend(player, MATERIAL, fresh, GAUGE)) {
            sayArgs(player, "astronima.wire.no_stock", ChatFormatting.RED,
                    Component.literal(String.valueOf(WireStock.itemsFor(fresh, GAUGE))));
            return InteractionResult.SUCCESS;
        }

        int laid = 0;
        for (int lane = 0; lane < width; lane++) {
            laid += Wires.placeAll(level, lanes.get(lane), LANE_COLOURS[lane], MATERIAL, GAUGE);
        }
        WirePixel newEnd = lanes.get(0).get(lanes.get(0).size() - 1);
        stack.set(ModDataComponents.RIBBON_ANCHOR.get(), anchorFor(newEnd));
        level.playSound(null, player.blockPosition(), SoundEvents.COPPER_BULB_TURN_ON,
                SoundSource.BLOCKS, 0.5f, 1.4f);
        sayArgs(player, "astronima.ribbon.laid", ChatFormatting.AQUA,
                Component.literal(String.valueOf(width)), Component.literal(String.valueOf(laid)));
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether the target pad group lines up with where the lanes actually land.
     *
     * @return an {@link InteractionResult} to return immediately (a refusal), or {@code null} to
     *         mean "not landing on a pad group at all — lay the freeform route instead"
     */
    private static @Nullable InteractionResult checkLanding(Player player, WirePart part,
                                                             PartType.Pad clicked,
                                                             List<List<WirePixel>> lanes, int width) {
        Optional<List<PartType.Pad>> group = padGroupFrom(part, clicked, width);
        if (group.isEmpty()) {
            say(player, "astronima.ribbon.no_pad_group", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        List<PartType.Pad> pads = group.get();
        for (int lane = 0; lane < width; lane++) {
            WirePixel arrival = lanes.get(lane).get(lanes.get(lane).size() - 1);
            WirePixel expected = part.padPixel(pads.get(lane));
            if (!arrival.equals(expected)) {
                say(player, "astronima.ribbon.misaligned", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
        }
        return null;
    }

    /**
     * {@code width} consecutive pads starting at {@code clicked}, in {@link PartType#pads()}'s own
     * declared order, verified to actually form a straight pitch-2 row rather than crossing into an
     * unrelated group — {@code design/bus.md} §3.2's "N consecutive pads" rule, checked
     * geometrically rather than by parsing pad labels, so it works for any part's layout.
     */
    private static Optional<List<PartType.Pad>> padGroupFrom(WirePart part, PartType.Pad clicked,
                                                              int width) {
        List<PartType.Pad> pads = part.type().pads();
        int index = pads.indexOf(clicked);
        if (index < 0 || index + width > pads.size()) {
            return Optional.empty();
        }
        List<PartType.Pad> slice = pads.subList(index, index + width);
        for (PartType.Pad pad : slice) {
            if (pad.drives() != clicked.drives()) {
                return Optional.empty();
            }
        }
        if (width == 1) {
            return Optional.of(slice);
        }
        int du = slice.get(1).u() - slice.get(0).u();
        int dv = slice.get(1).v() - slice.get(0).v();
        boolean onePitchStep = (du == 0 && Math.abs(dv) == RibbonGeometry.LANE_PITCH)
                || (dv == 0 && Math.abs(du) == RibbonGeometry.LANE_PITCH);
        if (!onePitchStep) {
            return Optional.empty();
        }
        for (int i = 2; i < slice.size(); i++) {
            if (slice.get(i).u() != slice.get(0).u() + du * i
                    || slice.get(i).v() != slice.get(0).v() + dv * i) {
                return Optional.empty();
            }
        }
        return Optional.of(slice);
    }

    private static InteractionResult letGo(ItemStack stack, Player player) {
        stack.remove(ModDataComponents.RIBBON_ANCHOR.get());
        say(player, "astronima.wire.let_go", ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    /** Whichever of the ribbon's own fixed colours is actually sitting on that pixel comes out. */
    private static InteractionResult pullUp(Level level, Player player, WirePixel target) {
        for (DyeColor colour : LANE_COLOURS) {
            if (Wires.remove(level, target, colour)) {
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                        SoundSource.BLOCKS, 0.6f, 1.0f);
                say(player, "astronima.wire.pulled", ChatFormatting.GRAY);
                return InteractionResult.SUCCESS;
            }
        }
        say(player, "astronima.wire.nothing_here", ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    /** In the air: routing mode, or (sneaking) lane count — both simple enough to need no panel. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        WireRibbon current = settingOf(stack);
        if (player.isSecondaryUseActive()) {
            WireRibbon next = current.toggleWidth();
            stack.set(ModDataComponents.WIRE_RIBBON.get(), next);
            sayArgs(player, "astronima.ribbon.width_set", ChatFormatting.AQUA,
                    Component.literal(String.valueOf(next.width())));
        } else {
            WireRibbon next = current.toggleMode();
            stack.set(ModDataComponents.WIRE_RIBBON.get(), next);
            say(player, next.mode() == WireRouter.Mode.STRAIGHT
                    ? "astronima.wire.mode_straight" : "astronima.wire.mode_pathfind", ChatFormatting.AQUA);
        }
        return InteractionResult.SUCCESS;
    }

    /** Left click: let go of the end, same reason the coil never breaks anything either. */
    public static boolean letGoOf(ItemStack stack, Player player) {
        if (anchorOf(stack) == null) {
            return false;
        }
        stack.remove(ModDataComponents.RIBBON_ANCHOR.get());
        say(player, "astronima.wire.let_go", ChatFormatting.GRAY);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        WireRibbon setting = settingOf(stack);
        lines.accept(Component.translatable("astronima.ribbon.tip_setting",
                        Component.literal(String.valueOf(setting.width())))
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable(setting.mode() == WireRouter.Mode.STRAIGHT
                        ? "astronima.wire.tip_straight" : "astronima.wire.tip_pathfind")
                .withStyle(ChatFormatting.AQUA));
        if (anchorOf(stack) != null) {
            lines.accept(Component.translatable("astronima.wire.tip_holding").withStyle(ChatFormatting.GREEN));
        }
        lines.accept(Component.translatable("astronima.ribbon.tip_controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void say(@Nullable Player player, String key, ChatFormatting colour) {
        sayArgs(player, key, colour);
    }

    private static void sayArgs(@Nullable Player player, String key, ChatFormatting colour, Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable(key, args).withStyle(colour), true);
        }
    }
}
