package play.xponer.astronima.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ToolMaterial;

/**
 * Native meteoric iron, cold worked.
 *
 * <p>Sits between stone and vanilla iron, and that is the honest place for it. It is
 * genuinely iron — a nickel-iron alloy, tougher and far more corrosion-resistant than
 * bloomery iron, which is why meteoric blades survive millennia in graves. But it has
 * never been melted, refined or alloyed deliberately: it is worked as it came out of
 * the rock, so it carries whatever the rock gave it and cannot be made better by
 * technique alone.
 *
 * <p>Better metal is what the chemical and electrolytic tiers are for. This is what
 * you can have in the first hour, with a hammer and a vacuum.
 */
public final class MeteoricToolMaterial {
    /**
     * Durability here is a floor: the actual tool's is scaled by how well its head was
     * forged, so the same metal in better hands lasts longer.
     */
    public static final ToolMaterial METEORIC = new ToolMaterial(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            220,
            5.5F,
            2.0F,
            8,
            ItemTags.IRON_TOOL_MATERIALS);

    private MeteoricToolMaterial() {}
}
