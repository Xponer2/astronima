package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.sim.ore.Comminution;

import java.util.function.Consumer;

/**
 * Crushed ilmenite, which carries the grind the fluidized bed's spin window slides on.
 *
 * <p>The reactor's one control is the drum speed, and the speed that fluidizes a bed depends on
 * how finely the ore was ground — a fine dust must be spun fast to be pinned, a coarse grind
 * packs solid if spun fast. So the crusher setting is not decoration on this item: it is the
 * number that decides where the good band sits. Without showing it, two batches ground at
 * different gaps would look identical and the machine would appear to behave differently for no
 * visible reason — the same invisible-processing trap {@link CrushedOreItem} was written for.
 *
 * <p>So the batch says its grain size, and hints which way to trim the spin.
 */
public class CrushedIlmeniteItem extends Item {
    public CrushedIlmeniteItem(Properties properties) {
        super(properties);
    }

    /** The crusher setting a batch was ground at, 0..1, from the {@code GRIND_FINENESS} component. */
    public static double finenessOf(ItemStack stack) {
        Integer permille = stack.get(ModDataComponents.GRIND_FINENESS.get());
        if (permille == null) {
            return 0.5;
        }
        return Math.clamp(permille / (double) ProcessingMenu.SETTING_SCALE, 0.0, 1.0);
    }

    /** The grain size a batch represents, µm — what the bed reads. */
    public static double micronsOf(ItemStack stack) {
        return Comminution.particleSizeMicrons(finenessOf(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);

        if (stack.get(ModDataComponents.GRIND_FINENESS.get()) == null) {
            lines.accept(Component.translatable("astronima.crushed.unprocessed")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        double microns = micronsOf(stack);
        lines.accept(Component.translatable("astronima.crushed.size", Math.round(microns))
                .withStyle(ChatFormatting.GRAY));
        // The lesson from the design: finer must be spun faster, coarser slower. Pointing the
        // player at the trade is the tooltip's job — the exact rpm is the bed's to find.
        lines.accept(Component.translatable(microns <= Comminution.GRAIN_SIZE_MICRONS
                        ? "astronima.crushed_ilmenite.fine"
                        : "astronima.crushed_ilmenite.coarse")
                .withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("astronima.crushed_ilmenite.hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
