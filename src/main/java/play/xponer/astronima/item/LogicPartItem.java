package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.BlockHitResult;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.wire.PartPlacement;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.Wires;

import java.util.function.Consumer;

/**
 * A logic part you bolt to a wall, in the wire layer, sized like the wire.
 *
 * <p>One class for all eight — six gates, the switch, the button and the plate — because the only
 * thing that differs between them is a {@link PartType}, and rule 20 is emphatic that a list of
 * near-identical registrations is a checklist and checklists get missed.
 *
 * <p><strong>These used to be blocks, and a block was the wrong size.</strong> A conductor one
 * pixel across feeding a gate a metre on a side is a scale error with three costs a player
 * actually meets: two gates set side by side leave nowhere for the wire between them, a circuit
 * cannot be buried in a wall the way the whole tier promises, and a NOT gate costs the same sealed
 * floor area as a scrubber. See {@code design/wire-parts.md}.
 *
 * <p>Placement points the output the way the player is looking, which is the direction they are
 * about to run the wire from it; the wrench turns it afterwards.
 */
public class LogicPartItem extends Item {

    private final PartType type;

    public LogicPartItem(Properties properties, PartType type) {
        super(properties);
        this.type = type;
    }

    public PartType type() {
        return type;
    }

    /** How far the player has turned this one before placing it — see the R key. */
    public static int rotationOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.PART_ROTATION.get(), 0);
    }

    /** What a plate is carrying, or an empty circuit for everything else. */
    public static Circuit circuitOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CIRCUIT.get(), Circuit.empty());
    }

    /**
     * A plate's own custom name, or "" — the vanilla rename (anvil, or the editor's own field),
     * read off the stack so it can be carried into the {@link WirePart} it becomes on the wall.
     */
    public static String customNameOf(ItemStack stack) {
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return custom == null ? "" : custom.getString();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        // Sneaking with a plate opens its editor rather than mounting it — otherwise a player
        // standing in front of a wall could never open one, because the wall always wins.
        if ((type == PartType.PLATE || type == PartType.MACRO_PLATE) && player.isSecondaryUseActive()) {
            if (!(level instanceof ServerLevel)) {
                play.xponer.astronima.client.CircuitEditorOpener.open(circuitOf(stack), type,
                        customNameOf(stack));
            }
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        // The same call the ghost renderer makes, so what was drawn is what is placed (rule 20).
        WirePart part = PartPlacement.proposed(player, hit, type, circuitOf(stack),
                rotationOf(stack), customNameOf(stack));

        if (!PartPlacement.allowed(serverLevel, part)) {
            say(player, "astronima.part.no_room");
            return InteractionResult.SUCCESS;
        }
        Wires.mount(serverLevel, part);
        stack.consume(1, player);
        serverLevel.playSound(null, part.cell(), SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS,
                0.5f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    /** Right-clicking nothing with a plate opens its editor; the other parts do nothing. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (type != PartType.PLATE && type != PartType.MACRO_PLATE) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel)) {
            ItemStack held = player.getItemInHand(hand);
            play.xponer.astronima.client.CircuitEditorOpener.open(
                    circuitOf(held), type, customNameOf(held));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A plate says what it computes without being opened.
     *
     * <p>A box whose contents can only be learned by opening it is a box you have to open every
     * time you pick one up — and an inventory of identical-looking plates is exactly what this
     * item creates.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("astronima.part.tip")
                .withStyle(ChatFormatting.GRAY));
        if (type != PartType.PLATE && type != PartType.MACRO_PLATE) {
            return;
        }
        Circuit circuit = circuitOf(stack);
        if (circuit.isBlank()) {
            lines.accept(Component.translatable("astronima.circuit.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.accept(Component.translatable("astronima.circuit.gates", circuit.nodes().size())
                .withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            Circuit.Source source = circuit.outputs().get(i);
            if (source.isOff()) {
                continue;
            }
            lines.accept(Component.literal("  " + (char) ('W' + i) + " = "
                    + describe(circuit, source)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** One output, written out as an expression, so a plate reads as what it computes. */
    public static String describe(Circuit circuit, Circuit.Source source) {
        return describe(circuit, source, new java.util.HashSet<>(), 0);
    }

    private static String describe(Circuit circuit, Circuit.Source source,
                                   java.util.Set<Integer> visited, int depth) {
        if (!source.isNode()) {
            return source.label();
        }
        int idx = source.node();
        if (visited.contains(idx) || depth > 8) {
            return "#" + (idx + 1);
        }
        java.util.Set<Integer> nextVisited = new java.util.HashSet<>(visited);
        nextVisited.add(idx);
        Circuit.Node node = circuit.nodes().get(idx);
        if (node.isSubcircuit()) {
            StringBuilder args = new StringBuilder();
            for (int p = 0; p < node.inputCount(); p++) {
                if (p > 0) {
                    args.append(", ");
                }
                args.append(describe(circuit, node.input(p), nextVisited, depth + 1));
            }
            String pin = source.output() == 0 ? "" : "." + (char) ('V' + source.output());
            String label = node.name().isEmpty() ? "IC" : node.name();
            return label + pin + "(" + args + ")";
        }
        String a = describe(circuit, node.a(), nextVisited, depth + 1);
        if (node.gate().isSingleInput()) {
            return node.gate().label() + " " + a;
        }
        String b = describe(circuit, node.b(), nextVisited, depth + 1);
        return "(" + a + " " + node.gate().label() + " " + b + ")";
    }

    /** Action bar: a one-off event tied to a click, never chat (PLAN §0.5.0). */
    private static void say(Player player, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }
}
