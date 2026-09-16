package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A salvaged component that fixes one or more suit subsystems.
 *
 * <p>Hold the suit in the other hand and use the part: no crafting grid, no menu —
 * repairing a suit should feel like working on it. Most parts fix exactly one fault,
 * so rebuilding the suit is a sequence of small, legible wins. A part can name more
 * than one subsystem when the physical fix really is the same material — insulation
 * weave patches both the thermal layer and torn gloves — in which case it opens the
 * bench for whichever of them is still broken (PLAN.md rule 57), rather than the two
 * subsystems needing two separately-modelled items.
 */
public class SuitRepairItem extends Item {
    private final List<SuitSubsystem> subsystems;

    public SuitRepairItem(Properties properties, SuitSubsystem... subsystems) {
        super(properties);
        this.subsystems = List.of(subsystems);
    }

    /** The subsystem this part primarily repairs — for callers that only need one. */
    public SuitSubsystem subsystem() {
        return subsystems.get(0);
    }

    /** True when this part is a candidate to repair the given subsystem. */
    public boolean handles(SuitSubsystem subsystem) {
        return subsystems.contains(subsystem);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return openBench(level, player, hand, true);
    }

    /**
     * Opens the repair bench for this part, if the other hand holds a suit that needs
     * it.
     *
     * @param announce whether to explain why nothing happened. Parts that exist only
     *                 to repair should say so; an item with another purpose — the LiOH
     *                 cartridge is also a consumable — should stay quiet and let its
     *                 other behaviour run instead.
     * @return SUCCESS when the bench opened, PASS when this was not a repair attempt
     */
    protected InteractionResult openBench(Level level, Player player, InteractionHand hand,
                                          boolean announce) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack suit = player.getItemInHand(otherHand);
        if (!suit.is(ModItems.EVA_SUIT.get())) {
            if (announce && !level.isClientSide()) {
                player.sendSystemMessage(
                        Component.translatable("astronima.suit.hold_suit").withStyle(ChatFormatting.GRAY));
            }
            return announce ? InteractionResult.FAIL : InteractionResult.PASS;
        }
        Optional<SuitSubsystem> target = SuitCondition.firstFault(EvaSuitItem.conditionOf(suit), subsystems);
        if (target.isEmpty()) {
            // Everything this part covers already works, so which name the message uses
            // does not change the fact — named against the first for a single simple line.
            if (announce && !level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("astronima.suit.already_working",
                        Component.literal(subsystem().displayName())).withStyle(ChatFormatting.GRAY));
            }
            return announce ? InteractionResult.FAIL : InteractionResult.PASS;
        }
        // Fitting a part is a procedure, not a click: the client opens the bench and
        // the server applies the result once the work is actually done.
        if (level.isClientSide()) {
            play.xponer.astronima.client.SuitRepairOpener.open(target.get());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        for (SuitSubsystem subsystem : subsystems) {
            lines.accept(Component.translatable("astronima.suit.repairs",
                    Component.literal(subsystem.displayName())).withStyle(ChatFormatting.GRAY));
        }
    }
}
