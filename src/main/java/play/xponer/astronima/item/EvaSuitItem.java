package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitWear;
import play.xponer.astronima.sim.suit.SuitSubsystem;

import java.util.function.Consumer;

/**
 * The pressure suit you crawl out of the wreck wearing.
 *
 * <p>It is not a stat bonus but a machine with six subsystems, each independently
 * broken and independently repairable. Restoring them is the opening arc of the game:
 * every fix hands back a capability you can immediately feel.
 */
public class EvaSuitItem extends Item {
    public EvaSuitItem(Properties properties) {
        super(properties);
    }

    /**
     * What actually works right now: a subsystem counts only if it was repaired
     * <em>and</em> has life left. Folding wear in here means every caller — breathing,
     * pressure, the panel — respects degradation without having to know it exists.
     */
    public static int conditionOf(ItemStack stack) {
        int mask = repairedMask(stack);
        int wear = wearOf(stack);
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (SuitWear.isWornOut(wear, subsystem)) {
                mask = SuitCondition.damage(mask, subsystem);
            }
        }
        return mask;
    }

    /** Which subsystems have ever been repaired, ignoring how worn they are. */
    public static int repairedMask(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SUIT_CONDITION.get(), SuitCondition.WRECKED);
    }

    public static int wearOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SUIT_WEAR.get(), SuitWear.NONE);
    }

    public static void setWear(ItemStack stack, int packed) {
        stack.set(ModDataComponents.SUIT_WEAR.get(), packed);
    }

    public static long stressOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SUIT_STRESS.get(), SuitWear.NO_STRESS);
    }

    public static void setStress(ItemStack stack, long packed) {
        stack.set(ModDataComponents.SUIT_STRESS.get(), packed);
    }

    public static void setCondition(ItemStack stack, int mask) {
        stack.set(ModDataComponents.SUIT_CONDITION.get(), mask);
    }

    /** A suit straight out of the wreck: nothing works. */
    public static ItemStack wrecked(Item suit) {
        ItemStack stack = new ItemStack(suit);
        setCondition(stack, SuitCondition.WRECKED);
        return stack;
    }

    /**
     * Repairs one subsystem.
     *
     * @return false when that subsystem already works, so the part is not wasted
     */
    public static boolean repair(ItemStack stack, SuitSubsystem subsystem) {
        return repair(stack, subsystem, 1.0f);
    }

    /**
     * Repairs one subsystem, recording how well the job was done.
     *
     * <p>Quality becomes the subsystem's starting life, so a rushed field fix works
     * now and fails sooner — the decision has a tail rather than a score.
     *
     * @return false when that subsystem already works, so the part is not wasted
     */
    public static boolean repair(ItemStack stack, SuitSubsystem subsystem, float quality) {
        int mask = conditionOf(stack);
        if (SuitCondition.isWorking(mask, subsystem)) {
            return false;
        }
        setCondition(stack, SuitCondition.repair(repairedMask(stack), subsystem));
        setWear(stack, SuitWear.withLife(wearOf(stack), subsystem,
                SuitWear.startingLife(quality)));
        return true;
    }

    /**
     * Names anything that is working but nearly spent, so a suit can be serviced
     * before it fails rather than after — which is the whole point of knowing.
     */
    private static void appendWearWarnings(ItemStack stack, int mask, Consumer<Component> lines) {
        int wear = wearOf(stack);
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (!SuitCondition.isWorking(mask, subsystem)) {
                continue;
            }
            float remaining = SuitWear.fraction(wear, subsystem);
            if (remaining <= 0.34f) {
                lines.accept(Component.translatable("astronima.suit.wearing",
                                Component.literal(subsystem.displayName()),
                                Math.round(remaining * 100))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        int mask = conditionOf(stack);
        if (SuitCondition.isFullyRepaired(mask)) {
            lines.accept(Component.translatable("astronima.suit.intact").withStyle(ChatFormatting.GREEN));
            appendWearWarnings(stack, mask, lines);
            return;
        }
        lines.accept(Component.translatable("astronima.suit.faults",
                        SuitCondition.faults(mask).size(), SuitSubsystem.values().length)
                .withStyle(ChatFormatting.RED));
        for (SuitSubsystem fault : SuitCondition.faults(mask)) {
            lines.accept(Component.literal(" • " + fault.displayName() + " — needs " + fault.repairPart())
                    .withStyle(ChatFormatting.GRAY));
        }
        appendWearWarnings(stack, mask, lines);
    }
}
