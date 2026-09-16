package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * A one-line subclass of {@link ModularUIContainerMenu}, for the same reason {@code
 * StorageTerminalMenu} exists: {@code AbstractContainerMenu#addDataSlots(ContainerData)} is
 * {@code protected}, and {@code ModularUIContainerMenu}'s own constructor never calls it —
 * {@link SynthesiserUiHolder} (an unrelated class, not a subclass) has no way to reach it
 * otherwise. Without this, the selected-recipe index {@link SynthesiserUiHolder#data()} carries
 * never actually reached a real client — PLAN.md rule 135 found the gap, rule 136 closes it here.
 */
public final class SynthesiserMenu extends ModularUIContainerMenu {
    public SynthesiserMenu(MenuType<ModularUIContainerMenu> menuType, int windowId,
                            Inventory inventory, SynthesiserUiHolder holder) {
        super(menuType, windowId, inventory, holder);
        addDataSlots(holder.data());
    }
}
