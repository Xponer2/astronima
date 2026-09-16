package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.network.CircuitPlatePayload;
import play.xponer.astronima.sim.logic.Circuit;

import java.util.Optional;

/**
 * The macro plate editor — {@link BoardBody} again, five pins each side instead of four (the
 * macro plate's own footprint, {@code PartType.MACRO_PLATE.pads()}), plus the side panel of
 * carried chips {@link CircuitEditorUi.Screen}'s {@code sidePanel} flag turns on. Reuses
 * {@link CircuitEditorUi.Screen} directly — the old {@code MacroPlateEditorScreen extends
 * CircuitEditorScreen} relationship, as composition instead of inheritance.
 */
public final class MacroPlateEditorUi {

    private MacroPlateEditorUi() {}

    public static ModularUIScreen screen(Circuit circuit, Optional<CircuitPlatePayload.At> mounted,
                                         String name) {
        return new CircuitEditorUi.Screen(circuit, mounted, name,
                Component.translatable("astronima.macro_plate.title"), 5, 5, true);
    }
}
