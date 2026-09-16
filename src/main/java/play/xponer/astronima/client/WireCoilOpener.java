package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import play.xponer.astronima.client.screen.WireCoilUi;
import play.xponer.astronima.item.WireCoil;

/**
 * Opens the coil's panel. Isolated in its own class so the item, which runs on both sides,
 * never touches a client-only screen type directly.
 *
 * <p>No {@code @OnlyIn}: NeoForge no longer strips members at runtime and warns loudly about
 * relying on it. What actually keeps this off a server is that nothing calls it except a branch
 * already known to be client-side — which is how {@code SuitRepairOpener} has always done it.
 */
public final class WireCoilOpener {
    public static void open(WireCoil coil) {
        Minecraft.getInstance().setScreen(WireCoilUi.screen(coil));
    }

    private WireCoilOpener() {}
}
