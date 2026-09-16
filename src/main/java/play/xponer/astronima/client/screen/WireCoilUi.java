package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.item.WireCoil;

/**
 * The wire coil panel, rebuilt on LDLib2 — {@link BiomonitorUi}'s own "screen with no menu" shape.
 * Client-side only, same as the old screen: it sends one validated packet and the server decides
 * whether the sender is actually holding a coil.
 */
public final class WireCoilUi {

    private WireCoilUi() {}

    public static ModularUIScreen screen(WireCoil coil) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(WireCoilBody.WIDTH).height(WireCoilBody.HEIGHT));
        root.addChild(new WireCoilBody(coil));

        UI ui = UI.of(root);
        ModularUI modularUI = ModularUI.of(ui, Minecraft.getInstance().player);
        return new ModularUIScreen(modularUI, Component.translatable("astronima.wire.panel_title"));
    }
}
