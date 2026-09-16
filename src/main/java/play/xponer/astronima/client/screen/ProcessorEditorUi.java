package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.network.ProcessorProgramPayload;
import play.xponer.astronima.sim.processor.Assembler;

import java.util.List;

/**
 * A processor's code editor, rebuilt on LDLib2 — {@code design/processor.md} §5.1: text in, and
 * saving assembles it.
 *
 * <p>{@link ProcessorEditorBody} draws everything that <em>is</em> hand-drawn (the panel, the
 * title, the errors); the real code editing stays on vanilla's own {@code MultiLineEditBox}, kept
 * exactly as it was rather than ported to a custom element — see {@link ProcessorEditorBody}'s
 * class doc for why. {@link Screen}, the subclass below, is what makes that possible: {@code
 * ModularUIScreen} is a real, extendable {@code Screen}, so adding the vanilla widgets from its
 * own {@code init()} — after calling {@code super.init()} so {@code getLeftPos()}/
 * {@code getTopPos()} are already the panel's real position — works exactly the way the old plain
 * {@code Screen} added them.
 */
public final class ProcessorEditorUi {

    private ProcessorEditorUi() {}

    public static ModularUIScreen screen(String program, ProcessorProgramPayload.At mounted) {
        return new Screen(program, mounted);
    }

    private static final class Screen extends ModularUIScreen {
        private static final int MARGIN = ProcessorEditorBody.MARGIN;
        private static final int WIDTH = ProcessorEditorBody.WIDTH;
        private static final int HEIGHT = ProcessorEditorBody.HEIGHT;

        private final String initialProgram;
        private final ProcessorProgramPayload.At mounted;
        /** The same list instance {@link ProcessorEditorBody}'s supplier closed over in
         *  {@link #build} — {@code save()} mutates it in place, so the body reads it live rather
         *  than a snapshot taken before any error ever existed. */
        private final List<String> errors;
        private MultiLineEditBox editor;

        Screen(String program, ProcessorProgramPayload.At mounted) {
            this(program, mounted, new java.util.ArrayList<>());
        }

        private Screen(String program, ProcessorProgramPayload.At mounted, List<String> errors) {
            super(build(errors), Component.translatable("astronima.processor.title"));
            this.initialProgram = program;
            this.mounted = mounted;
            this.errors = errors;
        }

        private static ModularUI build(List<String> errors) {
            UIElement root = new UIElement();
            root.layout(l -> l.width(WIDTH).height(HEIGHT));
            root.addChild(new ProcessorEditorBody(
                    Component.translatable("astronima.processor.title"), () -> errors));
            UI ui = UI.of(root);
            return ModularUI.of(ui, Minecraft.getInstance().player);
        }

        @Override
        public void init() {
            super.init();
            int left = getLeftPos();
            int top = getTopPos();

            editor = MultiLineEditBox.builder()
                    .setX(left + MARGIN)
                    .setY(top + 28)
                    .setPlaceholder(Component.translatable("astronima.processor.placeholder"))
                    .build(font, WIDTH - MARGIN * 2, HEIGHT - 76,
                            Component.translatable("astronima.processor.title"));
            editor.setValue(initialProgram);
            addRenderableWidget(editor);

            addRenderableWidget(Button.builder(Component.translatable("astronima.processor.save"),
                            button -> save())
                    .bounds(left + MARGIN, top + HEIGHT - 28, 80, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                            button -> onClose())
                    .bounds(left + MARGIN + 86, top + HEIGHT - 28, 80, 20)
                    .build());
        }

        /**
         * {@code design/processor.md} §5.1/§8: a refused assembly changes nothing — the errors
         * are shown and the screen stays open with whatever the player typed still in it, and
         * nothing is ever sent to the server unless this local assembly already succeeded.
         */
        private void save() {
            Assembler.Result result = Assembler.assemble(editor.getValue());
            if (!result.ok()) {
                errors.clear();
                errors.addAll(result.errors());
                return;
            }
            errors.clear();
            ClientPacketDistributor.sendToServer(new ProcessorProgramPayload(editor.getValue(), mounted));
            onClose();
        }
    }
}
