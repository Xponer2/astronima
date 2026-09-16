package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.StorageTerminalBlockEntity;

/**
 * A one-line subclass of {@link ModularUIContainerMenu}, for exactly one reason at first: {@code
 * AbstractContainerMenu#addDataSlots(ContainerData)} is {@code protected}, and
 * {@code ModularUIContainerMenu}'s own constructor never calls it — {@link StorageTerminalUiHolder}
 * (an unrelated class, not a subclass) has no way to reach it otherwise. Every other menu class in
 * this project shares the one plain {@code ModularUIContainerMenu}; this is the one screen that
 * needs a synced {@code ContainerData} (the scrollbar's own row/total-rows — design/data-cells.md
 * §18) and therefore the one that needed this.
 *
 * <p>Not to be confused with the old vanilla {@code StorageTerminalMenu} this replaced (design doc
 * §17) — same name, same package, an entirely different, LDLib2-based class built for a different
 * reason.
 *
 * <p><strong>Now also the one real safety exception display rows still need.</strong> §25's own
 * rework made display rows plain, ordinary {@code Slot}s — real vanilla single-click pickup and
 * placement both go through {@code Slot#remove}/{@code Slot#set}, which
 * {@link StorageTerminalBlockEntity#removeItem}/{@code #setItem} already handle safely. Shift-click
 * (quick-move) is the one path that does not: {@code ModularUIContainerMenu#quickMoveStack}
 * mutates whatever {@code Slot#getItem()} returns *directly* (rule 133's own finding, extending
 * rule 131 to LDLib2), which would hand the player a real item while a display row's own
 * aggregated total — recomputed from scratch every tick — never actually lost anything.
 * {@link #quickMoveStack} intercepts display-row indices explicitly and routes them through
 * {@link StorageTerminalBlockEntity#withdraw} directly instead, the one real, safe operation this
 * whole system was built around; every other index (the player's own inventory — there is no
 * separate input slot anymore, §27) still falls through to the generic implementation, unchanged;
 * for a deposit landing on a display row, that generic path reaches {@code Slot#safeInsert}
 * directly, the same safe destination-side call single-click placement already uses.
 */
public final class StorageTerminalMenu extends ModularUIContainerMenu {
    private final StorageTerminalUiHolder holder;

    public StorageTerminalMenu(MenuType<ModularUIContainerMenu> menuType, int windowId,
                               Inventory inventory, StorageTerminalUiHolder holder) {
        super(menuType, windowId, inventory, holder);
        this.holder = holder;
        addDataSlots(holder.data());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (player.level().isClientSide()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(slotIndex);
        if (slot.container == holder.container()
                && slot.getSlotIndex() >= StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT
                && holder.container() instanceof StorageTerminalBlockEntity terminal) {
            ItemStack shown = slot.getItem();
            if (shown.isEmpty()) {
                return ItemStack.EMPTY;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(shown.getItem()).toString();
            long withdrawn = terminal.withdraw(itemId, shown.getMaxStackSize());
            if (withdrawn <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack given = new ItemStack(shown.getItem(), (int) withdrawn);
            if (!inventory.add(given)) {
                player.drop(given, false);
            }
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, slotIndex);
    }
}
