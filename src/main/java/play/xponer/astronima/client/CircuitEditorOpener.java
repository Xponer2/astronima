package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import play.xponer.astronima.client.screen.CircuitEditorUi;
import play.xponer.astronima.client.screen.MacroPlateEditorUi;
import play.xponer.astronima.network.CircuitPlatePayload;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;

import java.util.Optional;

/**
 * Opens the plate editor. Isolated so the item and the wire layer, which both run on either side,
 * never touch a client-only screen type directly — the same shape {@code SuitRepairOpener} has
 * always had.
 *
 * <p><strong>The dispatch on {@link PartType} lives here and only here</strong> (rule 20): every
 * caller that opens an editor — the item in hand, a plate bolted to a wall — has to route a macro
 * plate to {@link MacroPlateEditorUi}, and before this class did it once, none of them did:
 * all three opened the plain 4-in/4-out screen regardless of which part was actually clicked,
 * which is why the macro editor never showed its fifth pin no matter what the screen itself drew.
 */
public final class CircuitEditorOpener {

    /** The plate in the player's hand. */
    public static void open(Circuit circuit, PartType type, String name) {
        open(circuit, Optional.empty(), type, name);
    }

    /** A plate bolted to a wall. */
    public static void open(Circuit circuit, CircuitPlatePayload.At at, PartType type, String name) {
        open(circuit, Optional.of(at), type, name);
    }

    private static void open(Circuit circuit, Optional<CircuitPlatePayload.At> mounted, PartType type,
                             String name) {
        Minecraft.getInstance().setScreen(type == PartType.MACRO_PLATE
                ? MacroPlateEditorUi.screen(circuit, mounted, name)
                : CircuitEditorUi.screen(circuit, mounted, name));
    }

    private CircuitEditorOpener() {}
}
