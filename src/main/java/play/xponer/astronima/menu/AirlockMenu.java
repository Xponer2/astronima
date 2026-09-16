package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * A one-line subclass of {@link ModularUIContainerMenu}, for the same reason {@code
 * StorageTerminalMenu} exists: {@code AbstractContainerMenu#addDataSlots(ContainerData)} is
 * {@code protected}, and {@code ModularUIContainerMenu}'s own constructor never calls it —
 * {@link AirlockUiHolder} (an unrelated class, not a subclass) has no way to reach it otherwise.
 * Without this, the status ints {@link AirlockUiHolder#data()} carries (every gauge
 * {@link AirlockUiHolder#status()} decodes) never actually reached a real client — PLAN.md rule
 * 135 found the gap, rule 136 closes it here.
 */
public final class AirlockMenu extends ModularUIContainerMenu {
    public AirlockMenu(MenuType<ModularUIContainerMenu> menuType, int windowId,
                        Inventory inventory, AirlockUiHolder holder) {
        super(menuType, windowId, inventory, holder);
        addDataSlots(holder.data());
    }
}
