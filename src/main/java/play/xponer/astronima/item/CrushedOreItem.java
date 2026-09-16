package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.chem.Chemistry;
import play.xponer.astronima.sim.chem.Element;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.OreGrade;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Crushed ore, which carries the whole reason the crusher has a dial.
 *
 * <p>Without this tooltip the machine appeared to do nothing: a player moved the jaw
 * gap, crushed more rock, and got back items that looked identical, because two
 * batches ground at different settings differ only in a data component nothing was
 * displaying. The processing was working perfectly and was completely invisible,
 * which is worse than it not working — you cannot even tell there is a system there.
 *
 * <p>So the batch says what it is: which rock it came from, how finely it was ground,
 * and how much of its mineral that freed. Liberation is the number that actually
 * decides what the separator can recover, so it is the one shown in colour.
 */
public class CrushedOreItem extends Item {
    public CrushedOreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);

        Integer packed = stack.get(ModDataComponents.ORE_BATCH.get());
        if (packed == null) {
            lines.accept(Component.translatable("astronima.crushed.unprocessed")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        OreGrade grade = OreGrade.gradeOf(packed);
        double microns = Comminution.particleSizeMicrons(OreGrade.finenessOf(packed));
        double liberation = Comminution.liberation(microns);

        lines.accept(Component.literal(grade.displayName()).withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("astronima.crushed.size", Math.round(microns))
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("astronima.crushed.liberation",
                        Math.round(liberation * 100))
                .withStyle(colourFor(liberation)));
        lines.accept(Component.translatable("astronima.crushed.hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        Chemistry.Entry chemistry = Chemistry.Entry.mixture(grade.body());
        lines.accept(Component.translatable("astronima.chem.composition", breakdown(chemistry))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The same thresholds the machine screen uses, so one colour means one thing. */
    private static ChatFormatting colourFor(double liberation) {
        if (liberation >= 0.6) {
            return ChatFormatting.GREEN;
        }
        return liberation >= 0.3 ? ChatFormatting.GOLD : ChatFormatting.RED;
    }

    /**
     * {@code "Fe 72%, O 28%"} — the same aggregate {@link Chemistry} derives for the raw ore
     * blocks, computed here per stack instead of statically because which grade a batch is is a
     * data component, not a fixed fact of the item ({@code ChemistryTooltip}'s own doc explains
     * why the two paths differ).
     */
    private static String breakdown(Chemistry.Entry chemistry) {
        StringBuilder text = new StringBuilder();
        chemistry.elementMassFractions().entrySet().stream()
                .sorted(Comparator.<Map.Entry<Element, Double>>comparingDouble(Map.Entry::getValue).reversed())
                .forEach(entry -> {
                    if (!text.isEmpty()) {
                        text.append(", ");
                    }
                    text.append(entry.getKey().symbol()).append(' ')
                            .append(Math.round(entry.getValue() * 100)).append('%');
                });
        if (chemistry.organicResidueFraction() > 0.01) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(I18n.get("astronima.chem.organics")).append(' ')
                    .append(Math.round(chemistry.organicResidueFraction() * 100)).append('%');
        }
        return text.toString();
    }
}
