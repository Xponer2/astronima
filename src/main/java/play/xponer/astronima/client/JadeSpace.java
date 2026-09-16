package play.xponer.astronima.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.fml.ModList;
import play.xponer.astronima.client.hud.HudPanels;

/**
 * Holds the space Jade's overlay is using, while it is using it.
 *
 * <h2>Not "is it installed" — "is it on screen"</h2>
 * The obvious version reserves a corner whenever the mod is present, which is wrong twice over: it
 * gives away space on every frame the player is looking at nothing, and it says nothing about
 * where the player has actually put the thing. Jade answers both questions itself —
 * {@code OverlayRenderer.shown} is whether it is drawing right now, and its config carries the
 * position and anchor the player chose — so this asks rather than assumes.
 *
 * <p><strong>The size is an estimate and that is the honest limit of this.</strong> A tooltip's
 * height depends on what it is describing and Jade does not publish the box, so the space held is
 * a generous band at the right place rather than the exact rectangle. Reserving slightly too much
 * costs one panel a better seat; reserving too little would let ours be drawn through Jade's,
 * which is the failure worth avoiding.
 *
 * <p>Every Jade type is touched only inside {@link Present}, which is never loaded when the mod is
 * absent — a class that mentions a missing type is a crash at first use, not a graceful skip.
 */
public final class JadeSpace {

    private static final Boolean INSTALLED = ModList.get().isLoaded("jade");

    /** A typical Jade tooltip, in its own scaled pixels. Generous rather than exact. */
    private static final int TYPICAL_WIDTH = 200;
    private static final int TYPICAL_HEIGHT = 60;

    /** Called every frame; holds the space only while Jade is actually drawing. */
    public static void hold(GuiGraphicsExtractor graphics, double now) {
        if (!Boolean.TRUE.equals(INSTALLED)) {
            return;
        }
        try {
            Present.hold(graphics, now);
        } catch (Throwable jadeChanged) {
            // Its internals are not our API. If a future Jade moves them, the worst outcome is
            // that we go back to not knowing about it, rather than taking the game down.
        }
    }

    private static final class Present {
        static void hold(GuiGraphicsExtractor graphics, double now) {
            if (!snownee.jade.overlay.OverlayRenderer.shown) {
                return;
            }
            var overlay = snownee.jade.api.config.IWailaConfig.get().overlay();
            float scale = Math.max(0.5f, overlay.getOverlayScale());
            int width = Math.round(TYPICAL_WIDTH * scale);
            int height = Math.round(TYPICAL_HEIGHT * scale);

            // Jade places by a fraction of the screen, then pulls back by its own anchor.
            int x = Math.round(graphics.guiWidth() * overlay.getOverlayPosX()
                    - width * overlay.getAnchorX());
            int y = Math.round(graphics.guiHeight() * (1 - overlay.getOverlayPosY())
                    - height * overlay.getAnchorY());

            HudPanels.reserve("jade", x, y, width, height, now);
        }

        private Present() {}
    }

    private JadeSpace() {}
}
