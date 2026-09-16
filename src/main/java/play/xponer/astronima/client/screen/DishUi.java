package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.sim.lab.Culture;

import java.util.function.Consumer;

/**
 * The hand bench, rebuilt on LDLib2 — {@link BiomonitorUi}'s own "screen with no menu" shape.
 */
public final class DishUi {

    private DishUi() {}

    public static ModularUIScreen screen(Culture culture, Consumer<Culture> save) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(DishBody.WIDTH).height(DishBody.HEIGHT));
        root.addChild(new DishBody(culture, save));

        UI ui = UI.of(root);
        ModularUI modularUI = ModularUI.of(ui, Minecraft.getInstance().player);
        return new ModularUIScreen(modularUI, Component.translatable("astronima.dish.title"));
    }
}
