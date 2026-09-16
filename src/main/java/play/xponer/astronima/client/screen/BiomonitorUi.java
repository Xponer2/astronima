package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.client.hud.BiomonitorLayout;

/**
 * The biomonitor, rebuilt on LDLib2 — {@code design/ui-ldlib2-machines.md}'s pattern, applied to
 * a screen with no menu and no slots at all. Opened straight from a keybind
 * ({@code ClientTickHandler.OPEN_BIOMONITOR}), the same way {@link CodexUi#screen} already is;
 * unlike the codex there is no session state to carry between opens — every frame reads the
 * player's live vitals fresh, so there is nothing here to rebuild on navigation because there is
 * no navigation.
 */
public final class BiomonitorUi {

    private BiomonitorUi() {}

    public static ModularUIScreen screen(Player player) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(BiomonitorLayout.WIDTH).height(BiomonitorLayout.HEIGHT));
        root.addChild(new BiomonitorBody());

        UI ui = UI.of(root);
        ModularUI modularUI = ModularUI.of(ui, player);
        return new ModularUIScreen(modularUI, Component.translatable("astronima.biomonitor.title"));
    }
}
