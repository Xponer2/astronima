package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.magic.CapturedSpectrum;
import play.xponer.astronima.sim.magic.SpectralLine;
import play.xponer.astronima.sim.magic.Spectrum;

import java.util.Locale;
import java.util.SortedSet;
import java.util.function.Consumer;

/**
 * A blank plate, or an exposed one — one item id, exactly as a culture dish or a tool head
 * carries its own state on the stack rather than needing a second item registered for every
 * condition it can be in (rule 8). Astral spectroscopy, design/astra-incognita.md §6, §8.1.
 *
 * <p><strong>What is not built yet, named rather than hidden:</strong> the darkroom
 * "development" step and the plate's own generated texture (§9.1) — an exposed plate is
 * immediately readable by tooltip, which is the honest interim this design's rule 8 asks for
 * rather than a silently absent mechanic.
 */
public class SpectralPlateItem extends Item {

    public SpectralPlateItem(Properties properties) {
        super(properties);
    }

    public static boolean isExposed(ItemStack stack) {
        return stack.has(ModDataComponents.CAPTURED_SPECTRUM.get());
    }

    /** A new, single exposed plate — the blank is consumed by whoever calls this. */
    public static ItemStack exposedCopy(ItemStack blank, CapturedSpectrum captured) {
        ItemStack exposed = blank.copyWithCount(1);
        exposed.set(ModDataComponents.CAPTURED_SPECTRUM.get(), captured);
        return exposed;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        CapturedSpectrum captured = stack.get(ModDataComponents.CAPTURED_SPECTRUM.get());
        if (captured == null) {
            lines.accept(Component.translatable("astronima.spectral_plate.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.accept(Component.literal(captured.target().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.AQUA));
        double peakNm = Spectrum.peakWavelengthNm(captured.target().temperatureK());
        if (!Double.isInfinite(peakNm)) {
            lines.accept(Component.literal(
                            String.format(Locale.ROOT, "peak %.0f nm", peakNm))
                    .withStyle(ChatFormatting.GRAY));
        }
        SortedSet<SpectralLine> observed = Spectrum.capture(
                captured.target().temperatureK(), captured.target().lines(), captured.vacuumKPa());
        if (observed.isEmpty()) {
            lines.accept(Component.translatable("astronima.spectral_plate.no_lines")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (SpectralLine line : observed) {
            lines.accept(Component.literal(String.format(Locale.ROOT, "%.1f nm  %s",
                            line.wavelengthNm(), line.name().toLowerCase(Locale.ROOT)))
                    .withStyle(line.forbidden() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
        }
    }
}
