package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.client.screen.AtlasUi;

/**
 * Opens the atlas screen. Isolated in its own class so the item, which runs on both sides,
 * never touches a client-only screen type directly — the same shape {@link WireCoilOpener} and
 * {@code SuitRepairOpener} already use.
 *
 * <p>No {@code @OnlyIn}: NeoForge no longer strips members at runtime and warns loudly about
 * relying on it. What actually keeps this off a server is that nothing calls it except a branch
 * already known to be client-side.
 */
public final class AtlasOpener {
    public static void open(Player player) {
        Minecraft.getInstance().setScreen(AtlasUi.screen(player));
    }

    private AtlasOpener() {}
}
