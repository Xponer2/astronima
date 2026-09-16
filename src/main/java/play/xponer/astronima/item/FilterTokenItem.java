package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.sim.magic.SpectralLine;

import java.util.function.Consumer;

/**
 * A piece of glass ground for one real wavelength (design/astra-research-m4b.md §1/§3) — held
 * while reading a captured object's spectrum strip in the atlas, applied to a marked candidate
 * position to test it against that line. One item per {@link SpectralLine} catalogue entry,
 * carrying which line it is as a plain constructor field baked in at registration — the same
 * shape {@code LogicPartItem}/{@code PartType} already uses for an enum-keyed item family, since
 * a token's line never changes once crafted (no per-stack state to carry, unlike
 * {@link SpectralPlateItem}'s blank/exposed distinction).
 *
 * <p><strong>Reusable, not consumed</strong> (design/astra-research.md §4a.2 step 5, found by
 * building it the other way first and hitting the wall that shape produces): a filter is
 * equipment a player owns, not ammunition, since the same physical line has to be placeable on
 * more than one object for a shared-line claim to ever be provable.
 */
public class FilterTokenItem extends Item {

    private final SpectralLine line;

    public FilterTokenItem(Properties properties, SpectralLine line) {
        super(properties);
        this.line = line;
    }

    public SpectralLine line() {
        return line;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal(line.displayName())
                .withStyle(line.forbidden() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        lines.accept(Component.literal(String.format(java.util.Locale.ROOT, "%.1f nm", line.wavelengthNm()))
                .withStyle(ChatFormatting.GRAY));
    }
}
