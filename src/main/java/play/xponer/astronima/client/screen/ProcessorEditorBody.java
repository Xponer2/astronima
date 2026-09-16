package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The processor editor's chrome — panel, title, divider and the assembly errors — ported from
 * the old {@code ProcessorEditorScreen} verbatim. The one thing this element does <em>not</em>
 * own: the code itself. {@code MultiLineEditBox} has no LDLib2 equivalent, and porting the whole
 * text-editing machinery to a custom element for one screen was not worth the risk of a subtly
 * different caret/selection/scroll feel — {@link ProcessorEditorUi}'s own {@code ModularUIScreen}
 * subclass keeps the real vanilla {@code MultiLineEditBox} and {@code Button}s exactly as they
 * were, added the same way the old {@code Screen} added them
 * ({@code addRenderableWidget}), just from {@code init()} on a screen backed by a {@code
 * ModularUI} instead of a bare one.
 */
public final class ProcessorEditorBody extends UIElement {

    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    static final int MARGIN = 10;

    private static final int ERROR = 0xFFE0A840;
    private static final int LABEL = 0xFF8FA89F;

    private final Component title;
    private final Supplier<List<String>> errors;

    public ProcessorEditorBody(Component title, Supplier<List<String>> errors) {
        this.title = title;
        this.errors = errors;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top) {
        play.xponer.astronima.client.hud.MachineFrame.panel(graphics, left, top, WIDTH, HEIGHT);
        play.xponer.astronima.client.hud.MachineFrame.value(graphics, font(), title,
                left + MARGIN, top + 9, WIDTH - MARGIN * 2, LABEL);
        play.xponer.astronima.client.hud.MachineFrame.divider(graphics, left + MARGIN, top + 22,
                WIDTH - MARGIN * 2);

        List<String> current = errors.get();
        if (current.isEmpty()) {
            return;
        }
        // The editor already reserves this strip (HEIGHT - 76 stops well short of the button
        // row), so an error never covers the text the player is trying to fix.
        int y = top + HEIGHT - 50;
        for (String error : current) {
            if (y > top + HEIGHT - 32) {
                break;
            }
            graphics.textWithWordWrap(font(), Component.literal(error), left + MARGIN, y,
                    WIDTH - MARGIN * 2, ERROR);
            y += 10;
        }
    }

    @LDLRegisterClient(name = "processor_editor_body", registry = "ldlib2:ui_element_renderer")
    public static final class ProcessorEditorBodyRenderer
            extends DelegatingUIElementRenderer<ProcessorEditorBody, ProcessorEditorBodyRenderer> {
        @Override
        public Class<ProcessorEditorBody> type() {
            return ProcessorEditorBody.class;
        }

        @Override
        public void drawBackgroundAdditional(ProcessorEditorBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
