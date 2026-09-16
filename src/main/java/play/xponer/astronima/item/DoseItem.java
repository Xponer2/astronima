package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import play.xponer.astronima.physio.Infections;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.lab.Antibiotic;

import java.util.function.Consumer;

/**
 * One dose of one antimicrobial.
 *
 * <h2>One item, not three</h2>
 * A broad-spectrum dose and a narrow-spectrum dose are the same idea with a different target, and
 * rule 8 refuses three item ids for that — three ids is three chances to grab the wrong vial and no
 * lesson at all. Which drug it is rides on the stack, exactly as a culture rides on a dish.
 *
 * <h2>Taking it is a commitment</h2>
 * A dose starts or continues a <strong>course</strong>. Finishing the course is what cures; walking
 * away from it halfway is what breeds a strain that drug will never touch again. The item cannot
 * express that on its own — the infection can, and does.
 */
public class DoseItem extends Item {

    public DoseItem(Properties properties) {
        super(properties);
    }

    /** Which drug is in the vial, or null for a blank one the synthesizer has not programmed. */
    public static Antibiotic drugOf(ItemStack stack) {
        String name = stack.get(ModDataComponents.DOSE.get());
        if (name == null) {
            return null;
        }
        return Antibiotic.all().stream()
                .filter(drug -> drug.name().equals(name)).findFirst().orElse(null);
    }

    public static void setDrug(ItemStack stack, Antibiotic drug) {
        stack.set(ModDataComponents.DOSE.get(), drug.name());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Antibiotic drug = drugOf(held);
        if (drug == null) {
            return InteractionResult.PASS;      // a blank vial does nothing; go and fill it
        }
        if (!level.isClientSide() && Infections.dose(player, drug)) {
            held.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        Antibiotic drug = drugOf(stack);
        if (drug == null) {
            lines.accept(Component.translatable("astronima.dose.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.accept(Component.literal(drug.name()).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("astronima.dose.course")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
