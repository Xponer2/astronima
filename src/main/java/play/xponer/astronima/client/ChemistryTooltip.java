package play.xponer.astronima.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.chem.Chemistry;
import play.xponer.astronima.sim.chem.Element;

import java.util.Comparator;
import java.util.Map;

/**
 * What a thing is made of, on the thing itself.
 *
 * <p>{@code sim/chem/Formula} and {@code sim/chem/Element} have parsed real chemistry since the
 * ore chain needed it, and none of it ever reached a tooltip. This is {@code TierTooltip}'s own
 * shape reused for a different roster: look the item up in {@link Chemistry}, skip it silently if
 * it has no entry — exactly as a tool or a machine should, since neither is one substance.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ChemistryTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                event.getItemStack().getItem());
        if (id == null) {
            return;
        }
        // Not namespace-filtered any more: a vanilla item this mod's own tree actually produces
        // (an iron ingot smelted from real iron powder, a torch struck from real tholins) is just
        // as real a fact as any astronima: item's own chemistry, and Chemistry.roster() covers it
        // now (Chemistry class doc).
        Chemistry.Entry chem = Chemistry.of(id.toString()).orElse(null);
        if (chem == null) {
            return;
        }

        if (chem.isNoteOnly()) {
            event.getToolTip().add(Component.translatable(chem.noteKey())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }

        if (chem.formulaText() != null) {
            double molarMass = Math.round(chem.molarMass() * 10) / 10.0;
            event.getToolTip().add(Component.translatable("astronima.chem.formula",
                    subscripted(chem.formulaText()), molarMass).withStyle(ChatFormatting.AQUA));
        }
        event.getToolTip().add(Component.translatable("astronima.chem.composition", breakdown(chem))
                .withStyle(ChatFormatting.GRAY));
    }

    /** {@code "Fe 72%, O 28%"}, heaviest element first — what an ore chain actually asks. */
    private static String breakdown(Chemistry.Entry chem) {
        StringBuilder text = new StringBuilder();
        chem.elementMassFractions().entrySet().stream()
                .sorted(Comparator.<Map.Entry<Element, Double>>comparingDouble(Map.Entry::getValue).reversed())
                .forEach(entry -> {
                    if (!text.isEmpty()) {
                        text.append(", ");
                    }
                    text.append(entry.getKey().symbol()).append(' ')
                            .append(Math.round(entry.getValue() * 100)).append('%');
                });
        if (chem.organicResidueFraction() > 0.01) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(I18n.get("astronima.chem.organics")).append(' ')
                    .append(Math.round(chem.organicResidueFraction() * 100)).append('%');
        }
        return text.toString();
    }

    /** {@code Fe3O4} reads as {@code Fe₃O₄} — ordinary Unicode subscript digits, no font work. */
    private static String subscripted(String formulaText) {
        StringBuilder out = new StringBuilder(formulaText.length());
        for (char c : formulaText.toCharArray()) {
            out.append(c >= '0' && c <= '9' ? (char) ('₀' + (c - '0')) : c);
        }
        return out.toString();
    }

    private ChemistryTooltip() {}
}
