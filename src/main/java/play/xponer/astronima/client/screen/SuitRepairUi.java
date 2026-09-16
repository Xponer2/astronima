package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * The repair bench, rebuilt on LDLib2 — {@link BiomonitorUi}'s own "screen with no menu" shape.
 */
public final class SuitRepairUi {

    private SuitRepairUi() {}

    public static ModularUIScreen screen(SuitSubsystem subsystem) {
        UIElement root = new UIElement();
        root.layout(l -> l.width(SuitRepairBody.WIDTH).height(SuitRepairBody.HEIGHT));
        root.addChild(new SuitRepairBody(subsystem));

        UI ui = UI.of(root);
        ModularUI modularUI = ModularUI.of(ui, Minecraft.getInstance().player);
        return new ModularUIScreen(modularUI,
                Component.translatable("astronima.suit.repairing",
                        Component.literal(subsystem.displayName())));
    }
}
