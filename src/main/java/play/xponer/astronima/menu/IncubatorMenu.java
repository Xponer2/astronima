package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * A one-line subclass of {@link ModularUIContainerMenu}, for the same reason {@code
 * StorageTerminalMenu} exists: {@code AbstractContainerMenu#addDataSlots(ContainerData)} is
 * {@code protected}, and {@code ModularUIContainerMenu}'s own constructor never calls it —
 * {@link IncubatorUiHolder} (an unrelated class, not a subclass) has no way to reach it
 * otherwise. Without this, the thermostat setpoint {@link IncubatorUiHolder#data()} carries (the
 * door-window/dial rendering's own live value) never actually reached a real client — PLAN.md
 * rule 135 found the gap, rule 136 closes it here.
 */
public final class IncubatorMenu extends ModularUIContainerMenu {
    public IncubatorMenu(MenuType<ModularUIContainerMenu> menuType, int windowId,
                          Inventory inventory, IncubatorUiHolder holder) {
        super(menuType, windowId, inventory, holder);
        addDataSlots(holder.data());
    }
}
