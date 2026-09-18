package play.xponer.astronima.physio;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.Astronima;

/**
 * The one player action in {@code design/macronutrients.md}: finishing a real food item. No new
 * key — the same finish-eating hook {@link ContaminationEvents#onFinishUsingItem} already uses.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class NutritionEvents {

    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof Player player)
                || !event.getItem().has(DataComponents.CONSUMABLE)) {
            return;
        }
        Nutrition.consumed(player, event.getItem());
    }

    private NutritionEvents() {}
}
