package play.xponer.astronima.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.lab.Culture;

import java.util.List;
import java.util.function.Consumer;

/**
 * A Petri dish, and everything anybody has found out about what is on it.
 *
 * <h2>The plate is the notebook</h2>
 * This is the item that makes the laboratory a chain rather than a box. It goes from the incubator
 * to the microscope to the disc reader collecting observations, and each machine writes what it
 * found onto the dish it hands on — so a plate arriving at the disc reader already knows it holds a
 * Gram-positive coccus in clusters.
 *
 * <p><strong>It reads its own label.</strong> Hover it and you get exactly what a real dish's side
 * would tell you: how long it has been growing, whether there are colonies worth picking, and every
 * test that has been run on it. Not the answer — the observations. Whether they add up to an
 * organism is the player's problem, and the point.
 *
 * <p>A dish carrying a botched Gram stain says so faithfully. It does not know it is wrong.
 */
public class PetriDishItem extends Item {

    /**
     * How a dish is written down.
     *
     * <p>Here rather than on {@link Culture}, and a test made that call rather than taste: a codec
     * drags Mojang's serialization library in with it, and {@code sim/} knows nothing about
     * Minecraft (rule 1). The unit tests run without that library on the classpath, so the moment
     * the codec went into the model, fourteen laboratory tests stopped being able to load the class
     * they were testing. The boundary is not a style preference — it is load-bearing.
     *
     * <p>Every field optional and defaulting to "not yet done", because a dish is picked up
     * part-way through a workflow far more often than it is finished (rule 5).
     */
    /** Whether this plate has been wiped across a surface, and whether anything came off. */
    public static boolean isSpentSwab(ItemStack stack) {
        return Boolean.FALSE.equals(stack.get(ModDataComponents.SWABBED.get()));
    }

    public static void setSwabbed(ItemStack stack, boolean grew) {
        stack.set(ModDataComponents.SWABBED.get(), grew);
    }

    public static final com.mojang.serialization.Codec<Culture> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("organism", "")
                            .forGetter(Culture::organism),
                    com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("grown_hours", 0.0)
                            .forGetter(Culture::grownHours),
                    com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("streak", 1.0)
                            .forGetter(Culture::streak),
                    named(play.xponer.astronima.sim.lab.GramStain.Result.values())
                            .optionalFieldOf("gram")
                            .forGetter(culture -> java.util.Optional.ofNullable(culture.gram())),
                    named(play.xponer.astronima.sim.lab.Organism.Shape.values())
                            .optionalFieldOf("shape")
                            .forGetter(culture -> java.util.Optional.ofNullable(culture.shape())),
                    named(play.xponer.astronima.sim.lab.Organism.Arrangement.values())
                            .optionalFieldOf("arrangement")
                            .forGetter(culture ->
                                    java.util.Optional.ofNullable(culture.arrangement())),
                    com.mojang.serialization.Codec.BOOL.optionalFieldOf("catalase")
                            .forGetter(culture ->
                                    java.util.Optional.ofNullable(culture.catalase())),
                    com.mojang.serialization.Codec.BOOL.optionalFieldOf("oxidase")
                            .forGetter(culture -> java.util.Optional.ofNullable(culture.oxidase()))
            ).apply(instance, (organism, grown, streak, gram, shape, arrangement, catalase,
                               oxidase) -> new Culture(organism, grown, streak,
                    gram.orElse(null), shape.orElse(null), arrangement.orElse(null),
                    catalase.orElse(null), oxidase.orElse(null))));

    /** By name, so a save stays readable and survives an enum being reordered. */
    private static <E extends Enum<E>> com.mojang.serialization.Codec<E> named(E[] values) {
        return com.mojang.serialization.Codec.STRING.xmap(
                text -> {
                    for (E value : values) {
                        if (value.name().equalsIgnoreCase(text)) {
                            return value;
                        }
                    }
                    return values[0];
                },
                value -> value.name().toLowerCase(java.util.Locale.ROOT));
    }

    public PetriDishItem(Properties properties) {
        super(properties);
    }

    /** What is on that dish, or a blank plate. */
    public static Culture cultureOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CULTURE.get(), Culture.BLANK);
    }

    public static void setCulture(ItemStack stack, Culture culture) {
        stack.set(ModDataComponents.CULTURE.get(), culture);
    }

    /**
     * Opens the hand bench.
     *
     * <p>Client-side, because everything on that screen is something you do with your hands to an
     * item you are holding — there is no world state to change and nothing for a server to
     * arbitrate. The screen writes back onto the stack, which is where the culture lives.
     */
    /**
     * Swabbing a surface: the step that makes the laboratory answer a question about the habitat.
     *
     * <p>Until now every plate was one the player was handed. This one they collect, off a bench
     * they suspect, and what grows on it is what <em>their own base</em> has been cultivating. The
     * microscope stops being a puzzle about an isolate and starts being an answer about a room.
     *
     * <p>No new item, deliberately. A dedicated swab would be a second thing to craft, carry and
     * lose in order to do something the dish is already for.
     *
     * <p><strong>A blank plate is a real outcome.</strong> Sample a nearly clean surface and
     * nothing grows, and you have spent a plate and a night to learn that. That is the cost of
     * guessing, and it is what makes the goggles worth wearing.
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ItemStack held = context.getItemInHand();
        if (!cultureOf(held).isBlank()) {
            return InteractionResult.PASS; // a plate already carrying something is not a swab
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        var surface = play.xponer.astronima.physio.Surfaces.at(context.getLevel(),
                context.getClickedPos());
        boolean grew = play.xponer.astronima.sim.lab.Swab.willGrow(surface.load());
        if (grew) {
            setCulture(held, play.xponer.astronima.sim.lab.Culture.freshly(
                    play.xponer.astronima.sim.pathogen.Pathogens.of(surface.source()).designator(),
                    play.xponer.astronima.sim.lab.Swab.SWAB_STREAK));
        }
        play.xponer.astronima.physio.Surfaces.set(context.getLevel(), context.getClickedPos(),
                play.xponer.astronima.sim.lab.Swab.remaining(surface));
        // Marked either way: a plate that was used on a sterile bench is spent, and finding that
        // out is the information you paid for.
        setSwabbed(held, grew);
        context.getLevel().playSound(null, context.getClickedPos(),
                net.minecraft.sounds.SoundEvents.SLIME_BLOCK_STEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(net.minecraft.world.level.Level level,
                                 net.minecraft.world.entity.player.Player player,
                                 net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openBench(held);
        } else {
            // Working with a grown plate is the first hop of the chain, and it happens here
            // rather than on pickup: carrying a dish in a crate is not handling a culture.
            play.xponer.astronima.physio.ContaminationEvents.handledCulture(player,
                    cultureOf(held));
        }
        return InteractionResult.SUCCESS;
    }

    private static void openBench(ItemStack held) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                play.xponer.astronima.client.screen.DishUi.screen(cultureOf(held),
                        culture -> setCulture(held, culture)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        Culture culture = cultureOf(stack);
        for (String line : culture.label()) {
            lines.accept(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (culture.isBlank()) {
            // A plate that was wiped across a surface and grew nothing is not the same thing as a
            // fresh plate, and the difference is the whole information the swab bought. It has to
            // be readable on the item - a chat line would have scrolled away before the eighteen
            // hours of incubation the player is about to not need.
            if (isSpentSwab(stack)) {
                lines.accept(Component.translatable("astronima.dish.sterile_swab")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            return;
        }
        // What is left to do, rather than what it is. A dish that named the organism would be a
        // dish that had done the player's work for them.
        List<String> outstanding = culture.key().outstanding();
        if (!culture.key().isSelfConsistent()) {
            lines.accept(Component.literal("results contradict each other")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        } else if (!outstanding.isEmpty() && culture.hasColonies()) {
            lines.accept(Component.literal("to run: " + String.join(", ", outstanding))
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
