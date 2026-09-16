package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.menu.AirlockUiHolder;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

/**
 * The chrome around {@link AirlockBody}: no item slots at all, just the click/hover overlays for
 * the CYCLE button, the SCAN button and the four terminals — the same "plain hit-tested
 * {@code UIElement} calling back into the picture element" move {@code ProcessingUi} uses for its
 * own dial and SLS-plane overlays.
 */
public final class AirlockUi {

    private AirlockUi() {}

    public static ModularUI build(AirlockUiHolder holder, Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(AirlockBody.WIDTH).height(AirlockBody.HEIGHT));

        AirlockBody body = new AirlockBody(holder);
        root.addChild(body);

        addOverlay(root, AirlockBody.BUTTON_X, AirlockBody.BUTTON_Y, AirlockBody.BUTTON_W,
                AirlockBody.BUTTON_H, e -> body.onCycleClicked(), null);
        addOverlay(root, AirlockBody.SCAN_X, AirlockBody.SCAN_Y, AirlockBody.SCAN_W,
                AirlockBody.SCAN_H, e -> body.onScanClicked(),
                event -> event.hoverTooltips = HoverTooltips.create(body.scanTooltip().toArray()));

        for (var entry : AirlockBody.TERMINAL_BOXES.entrySet()) {
            Role role = entry.getKey();
            int[] box = entry.getValue();
            addOverlay(root, box[0] - 3, box[1] - 3, box[2] + 6, box[3] + 6,
                    e -> body.onTerminalClicked(role, e.button == 1),
                    event -> event.hoverTooltips = HoverTooltips.create(
                            body.terminalTooltip(role).toArray()));
        }

        UI ui = UI.of(root);
        return ModularUI.of(ui, player);
    }

    private static void addOverlay(UIElement root, int x, int y, int width, int height,
                                   UIEventListener onClick, UIEventListener onHover) {
        UIElement overlay = new UIElement();
        overlay.getLayout().positionType(TaffyPosition.ABSOLUTE);
        overlay.getLayout().left(x);
        overlay.getLayout().top(y);
        overlay.getLayout().width(width);
        overlay.getLayout().height(height);
        overlay.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        overlay.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 || event.button == 1) {
                onClick.handleEvent(event);
            }
        });
        if (onHover != null) {
            overlay.addEventListener(UIEvents.HOVER_TOOLTIPS, onHover);
        }
        root.addChild(overlay);
    }
}
