package play.xponer.astronima.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ToolMaterial;

/**
 * A length of structural member off the wreck.
 *
 * <p>Weaker than stone on purpose. It is spacecraft aluminium or a composite strut —
 * strong in tension along its length, which is what makes it a good lever, and quite
 * unsuited to being hammered into rock. It gets you into panels and through the softest
 * material, and it makes wanting a real tool the first thing you feel.
 */
public final class PryBarToolMaterial {
    public static final ToolMaterial SALVAGE = new ToolMaterial(
            BlockTags.INCORRECT_FOR_STONE_TOOL,
            0,
            2.0F,
            1.0F,
            5,
            ItemTags.STONE_TOOL_MATERIALS);

    private PryBarToolMaterial() {}
}
