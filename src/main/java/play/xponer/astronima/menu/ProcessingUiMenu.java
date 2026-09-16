package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * A one-line subclass of {@link ModularUIContainerMenu}, for the same reason {@code
 * StorageTerminalMenu} exists: {@code AbstractContainerMenu#addDataSlots(ContainerData)} is
 * {@code protected}, and {@code ModularUIContainerMenu}'s own constructor never calls it —
 * {@link ProcessingUiHolder} (an unrelated class, not a subclass) has no way to reach it
 * otherwise.
 *
 * <p>Named {@code ProcessingUiMenu} rather than {@code ProcessingMenu} — that name already
 * belongs to the pure-static domain class ({@link ProcessingMenu.Kind}, {@code feedAccepts},
 * {@link ProcessingMenu#dataFor}, and the rest), which this class has nothing to do with beyond
 * calling into it via {@link ProcessingUiHolder#data()}.
 *
 * <p>Without this, every one of the ~16 processing-machine kinds sharing {@code
 * ModMenus.PROCESSING} — the progress bar, the setting dial, every reading {@link
 * ProcessingMenu#dataFor} builds — never actually reached a real client. PLAN.md rule 135 found
 * the gap, rule 136 closes it here.
 */
public final class ProcessingUiMenu extends ModularUIContainerMenu {
    public ProcessingUiMenu(MenuType<ModularUIContainerMenu> menuType, int windowId,
                             Inventory inventory, ProcessingUiHolder holder) {
        super(menuType, windowId, inventory, holder);
        addDataSlots(holder.data());
    }
}
