package play.xponer.astronima.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.crafting.Tiers;

/**
 * Which rung of the ladder a thing is on, on the thing itself.
 *
 * <p>Tiers were a shape in a roadmap and nowhere a player could see. A roster in a file is better
 * than nothing and still requires somebody to go and read it; the tier belongs on the item, where
 * the question is actually asked — <em>can I build this yet, and if not, what comes first?</em>
 *
 * <p>One event rather than a line in every item class. There are ninety-odd things in the mod and
 * a per-item override would be ninety places to forget (rule 20).
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TierTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                event.getItemStack().getItem());
        if (id == null || !id.getNamespace().equals(Astronima.MODID)) {
            return;
        }
        Tiers.Tier tier = Tiers.of(id.toString());
        if (tier == null) {
            return;
        }
        event.getToolTip().add(Component.translatable("astronima.tier",
                        Component.translatable("astronima.tier." + tier.name().toLowerCase(
                                java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private TierTooltip() {}
}
