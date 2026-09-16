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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.SynthesiserLayout;
import play.xponer.astronima.item.DoseItem;
import play.xponer.astronima.menu.SynthesiserUiHolder;
import play.xponer.astronima.network.MachineSettingPayload;
import play.xponer.astronima.sim.lab.Antibiotic;

import java.util.List;

/**
 * The synthesiser's own picture and rows, ported from the old {@code SynthesiserScreen}
 * verbatim — the {@code MachineBody} pattern again. Three (or however many
 * {@link Antibiotic#all()} says) clickable rows, drawn here and hit-tested by
 * {@link SynthesiserUi}'s own per-row overlays.
 */
public final class SynthesiserBody extends UIElement {

    static final int WIDTH = SynthesiserLayout.WIDTH;
    static final int HEIGHT = SynthesiserLayout.HEIGHT;

    private static final int TEXT_WIDTH = 150;
    private static final int TEXT = 0xFFC9D2DA;
    private static final int DIM = 0xFF7E8894;
    private static final int HEAD = 0xFFF2F6FA;
    private static final int ACCENT = 0xFF6FB0EE;

    static final int ROW_X = SynthesiserLayout.ROW_X;
    static final int ROW_Y = SynthesiserLayout.ROW_Y;
    static final int ROW_W = SynthesiserLayout.ROW_W;
    static final int ROW_H = SynthesiserLayout.ROW_H;

    private final SynthesiserUiHolder holder;

    public SynthesiserBody(SynthesiserUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos,
                           int mouseX, int mouseY) {
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(),
                holder.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT),
                leftPos + 8, topPos + 7, WIDTH - 16, MachineFrame.TEXT);
        MachineFrame.divider(graphics, leftPos + 7,
                topPos + SynthesiserLayout.INVENTORY_LABEL_Y - 6, WIDTH - 14);
        MachineFrame.feedSlot(graphics, leftPos + 80, topPos + 32);

        MachineFrame.value(graphics, font(), Component.translatable(holder.vial().isEmpty()
                        ? "astronima.synthesiser.empty"
                        : DoseItem.drugOf(holder.vial()) == null
                                ? "astronima.synthesiser.blank"
                                : "astronima.synthesiser.filled"),
                leftPos + 8, topPos + 22, TEXT_WIDTH, holder.vial().isEmpty() ? DIM : TEXT);

        List<Antibiotic> drugs = Antibiotic.all();
        for (int i = 0; i < drugs.size(); i++) {
            int y = topPos + ROW_Y + i * (ROW_H + 2);
            boolean chosen = i == holder.chosenIndex();
            boolean over = mouseX >= leftPos + ROW_X && mouseX <= leftPos + ROW_X + ROW_W
                    && mouseY >= y && mouseY < y + ROW_H;
            graphics.fill(leftPos + ROW_X, y, leftPos + ROW_X + ROW_W, y + ROW_H,
                    chosen ? 0x336FB0EE : over ? 0x28FFFFFF : 0x18FFFFFF);
            if (chosen) {
                graphics.fill(leftPos + ROW_X, y, leftPos + ROW_X + 2, y + ROW_H, ACCENT);
            }
            MachineFrame.value(graphics, font(), Component.literal(drugs.get(i).name()),
                    leftPos + ROW_X + 6, y + 3, TEXT_WIDTH, chosen ? HEAD : TEXT);
        }
    }

    /** Called by {@link SynthesiserUi}'s per-row overlay. */
    void onRowClicked(int index) {
        List<Antibiotic> drugs = Antibiotic.all();
        float part = drugs.size() <= 1 ? 0 : index / (float) (drugs.size() - 1);
        ClientPacketDistributor.sendToServer(new MachineSettingPayload(holder.pos(), part));
    }

    @LDLRegisterClient(name = "synthesiser_body", registry = "ldlib2:ui_element_renderer")
    public static final class SynthesiserBodyRenderer
            extends DelegatingUIElementRenderer<SynthesiserBody, SynthesiserBodyRenderer> {
        @Override
        public Class<SynthesiserBody> type() {
            return SynthesiserBody.class;
        }

        @Override
        public void drawBackgroundAdditional(SynthesiserBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY(), guiContext.mouseX, guiContext.mouseY);
        }
    }
}
