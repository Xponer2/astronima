package play.xponer.astronima.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.SuitModels;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.sim.suit.SuitCondition;
import top.theillusivec4.curios.api.SlotContext;

/**
 * The pressure suit, drawn in the condition it is actually in.
 *
 * <p>A rebuilt suit and the wreck you crawled out of should not look the same. This is
 * the cheapest honest instrument in the mod: no gauge, no readout, just the fact that
 * you can see the patched-together version on your own body until you have finished
 * fixing it. It also means another player can tell at a glance whether the person
 * about to follow them into vacuum is actually ready to.
 */
public class EvaSuitRenderer extends WornCurioRenderer {
    private static final Identifier DAMAGED = Identifier.fromNamespaceAndPath(
            Astronima.MODID, "textures/entity/eva_suit_damaged.png");

    public EvaSuitRenderer() {
        super(SuitModels.SUIT, "eva_suit");
    }

    @Override
    public Identifier getModelTexture(ItemStack stack, SlotContext slotContext) {
        // Effective condition, so a subsystem that has *worn out* shows as damage too
        // rather than only ones that were never repaired.
        return SuitCondition.isFullyRepaired(EvaSuitItem.conditionOf(stack))
                ? super.getModelTexture(stack, slotContext)
                : DAMAGED;
    }
}
