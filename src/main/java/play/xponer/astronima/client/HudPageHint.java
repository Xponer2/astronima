package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudPanels;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.MachineFrame;

/**
 * Says so when a readout did not fit.
 *
 * <p>The HUD module sends a panel it cannot seat to another page rather than dropping it, and that
 * is only better than dropping it <strong>if the player is told</strong>. A readout that is
 * missing without saying so and a readout that is missing with a keypress next to it are two
 * completely different things.
 *
 * <p>Deliberately not a module panel itself. It is furniture, like the hotbar: it has one fixed
 * place, nothing ever contends for it, and — the part that matters — a hint about pages that could
 * itself be paged would be a joke at the player's expense.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class HudPageHint {

    /** How far above the hotbar it sits. Out of the way, and where a status line belongs. */
    private static final int ABOVE_HOTBAR = 62;

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "hud_page_hint"),
                HudPageHint::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || !HudPanels.hasOtherPages()) {
            return;
        }
        String key = ModKeybinds.HUD_PAGE.getTranslatedKeyMessage().getString();
        String text = (HudPanels.page() + 1) + "/" + (HudPanels.pageCount()) + "  [" + key + "]";

        int width = minecraft.font.width(text) + 10;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - ABOVE_HOTBAR;

        // Holds its own line. It is the thing that explains the key, so a readout drawn over it
        // would hide the only instruction the player has.
        HudPanels.reserve("page hint", x, y, width, 12, System.currentTimeMillis() / 1000.0);

        graphics.fill(x, y, x + width, y + 12, 0xB0121619);
        graphics.fill(x, y, x + width, y + 1, HudScale.COLOR_INFO);
        MachineFrame.overlay(graphics, minecraft.font, text, x + 5, y + 2, width - 10,
                HudScale.COLOR_LABEL);
    }

    private HudPageHint() {}
}
