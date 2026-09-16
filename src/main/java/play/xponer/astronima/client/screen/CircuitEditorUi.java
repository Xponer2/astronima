package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.network.CircuitPlatePayload;
import play.xponer.astronima.sim.logic.Circuit;

import java.util.Optional;

/**
 * The plain circuit editor, rebuilt on LDLib2 — {@link BiomonitorUi}'s "screen with no menu"
 * shape, since a circuit plate opens with no server-side container at all.
 *
 * <p>{@link BoardBody} draws and hit-tests everything hand-drawn; the one real vanilla widget the
 * old screen had — the plate's name field — is added the same hybrid way
 * {@link ProcessorEditorUi} adds its {@code MultiLineEditBox}: a {@code ModularUIScreen} subclass
 * whose {@code init()} calls {@code super.init()} first, then adds the {@code EditBox} at the
 * real panel position.
 */
public final class CircuitEditorUi {

    private CircuitEditorUi() {}

    public static ModularUIScreen screen(Circuit circuit, Optional<CircuitPlatePayload.At> mounted,
                                         String name) {
        return screen(circuit, mounted, name, Component.translatable("astronima.circuit.title"));
    }

    static ModularUIScreen screen(Circuit circuit, Optional<CircuitPlatePayload.At> mounted,
                                  String name, Component title) {
        return new Screen(circuit, mounted, name, title, 4, 4, false);
    }

    /** Package-private: {@link MacroPlateEditorUi} builds the same screen shape with different
     *  pin counts and the chip side panel turned on. */
    static class Screen extends ModularUIScreen {
        private final BoardBody body;

        Screen(Circuit circuit, Optional<CircuitPlatePayload.At> mounted, String name,
              Component title, int maxInputs, int maxOutputs, boolean sidePanel) {
            this(build(circuit, mounted, name, title, maxInputs, maxOutputs, sidePanel), title);
        }

        private Screen(Built built, Component title) {
            super(built.modularUI, title);
            this.body = built.body;
        }

        private record Built(ModularUI modularUI, BoardBody body) {}

        private static Built build(Circuit circuit, Optional<CircuitPlatePayload.At> mounted,
                                   String name, Component title, int maxInputs, int maxOutputs,
                                   boolean sidePanel) {
            BoardBody body = new BoardBody(circuit, mounted, name, title, maxInputs, maxOutputs,
                    sidePanel);
            UIElement root = new UIElement();
            int width = sidePanel ? BoardBody.WIDTH + 8 + 108 : BoardBody.WIDTH;
            root.layout(l -> l.width(width).height(BoardBody.HEIGHT));
            root.addChild(body);
            UI ui = UI.of(root);
            return new Built(ModularUI.of(ui, Minecraft.getInstance().player), body);
        }

        @Override
        public void init() {
            super.init();
            EditBox nameBox = new EditBox(font, getLeftPos() + BoardBody.WIDTH - 138, getTopPos() + 7,
                    96, 12, Component.translatable("astronima.circuit.name"));
            nameBox.setMaxLength(32);
            nameBox.setBordered(false);
            nameBox.setTextColor(0xFFD9C9A8);
            nameBox.setValue(body.plateName());
            nameBox.setHint(Component.translatable("astronima.circuit.unnamed"));
            nameBox.setResponder(body::setPlateName);
            addRenderableWidget(nameBox);
        }
    }
}
