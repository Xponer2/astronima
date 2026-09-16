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
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.menu.StorageDriveUiHolder;

import java.util.Locale;

/**
 * The drive's own panel and slot wells — see {@code design/data-cells.md} §14. The flat,
 * textureless grey the user reported was a plain vanilla {@code ChestMenu} screen, which never
 * draws any of this mod's own chrome at all; this is the same {@code MachineFrame.panel}/
 * {@code slot} treatment every other machine in the mod already gets. Six rows of real cell slots
 * (rule 130's own {@code canPlaceItem}-gating {@link net.minecraft.world.inventory.Slot} subclass,
 * built by {@link StorageDriveUi}), plus the player's own inventory.
 */
public final class StorageDriveBody extends UIElement {

    static final int WIDTH = 176;
    static final int HEIGHT = 226;

    static final int GRID_TOP = 18;
    private static final int DIVIDER_Y = 128;
    static final int INVENTORY_LABEL_Y = 132;
    static final int INVENTORY_Y = 144;
    static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;

    private final StorageDriveUiHolder holder;

    public StorageDriveBody(StorageDriveUiHolder holder) {
        this.holder = holder;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(WIDTH);
        getLayout().height(HEIGHT);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        // Before the real ItemSlot children StorageDriveUi adds, never after: the panel is
        // opaque and would otherwise paint straight over every slot and every item in it
        // (IncubatorBody's own doc comment on this exact ordering trap).
        MachineFrame.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        MachineFrame.value(graphics, font(), holder.getDisplayName().getString().toUpperCase(Locale.ROOT),
                leftPos + 8, topPos + 7, WIDTH - 16, MachineFrame.TEXT);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + GRID_TOP + row * 18);
            }
        }
        MachineFrame.divider(graphics, leftPos + 7, topPos + DIVIDER_Y, WIDTH - 14);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            MachineFrame.slot(graphics, leftPos + 8 + column * 18, topPos + HOTBAR_Y);
        }
    }

    @LDLRegisterClient(name = "storage_drive_body", registry = "ldlib2:ui_element_renderer")
    public static final class StorageDriveBodyRenderer
            extends DelegatingUIElementRenderer<StorageDriveBody, StorageDriveBodyRenderer> {
        @Override
        public Class<StorageDriveBody> type() {
            return StorageDriveBody.class;
        }

        @Override
        public void drawBackgroundAdditional(StorageDriveBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
