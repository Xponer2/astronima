package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import play.xponer.astronima.client.screen.SuitRepairUi;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Opens the repair bench. Isolated in its own class so the item, which runs on both
 * sides, never touches a client-only screen type directly.
 */
public final class SuitRepairOpener {
    public static void open(SuitSubsystem subsystem) {
        Minecraft.getInstance().setScreen(SuitRepairUi.screen(subsystem));
    }

    private SuitRepairOpener() {}
}
