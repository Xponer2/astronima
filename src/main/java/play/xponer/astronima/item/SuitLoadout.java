package play.xponer.astronima.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.suit.SuitCondition;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * What a wearer is actually equipped with.
 *
 * <p>Single point of truth for the rest of the mod: breathing, decompression and the
 * suit panel all ask this rather than rummaging through an inventory. Everything is
 * read from worn slots, never from carried items, because the whole point of the slot
 * model is that the loadout is visible and finite — a tank in your backpack is cargo,
 * not life support.
 */
public final class SuitLoadout {
    public static final String SLOT_SUIT = "suit";
    public static final String SLOT_TANK = "tank";
    public static final String SLOT_CARTRIDGE = "cartridge";

    /** The worn suit, or empty. */
    public static ItemStack suit(LivingEntity wearer) {
        return worn(wearer, SLOT_SUIT);
    }

    /** The fitted tank, or empty. */
    public static ItemStack tank(LivingEntity wearer) {
        return worn(wearer, SLOT_TANK);
    }

    /** The fitted scrubber cartridge, or empty. */
    public static ItemStack cartridge(LivingEntity wearer) {
        return worn(wearer, SLOT_CARTRIDGE);
    }

    private static ItemStack worn(LivingEntity wearer, String slot) {
        return CuriosApi.getCuriosInventory(wearer)
                .flatMap(inventory -> inventory.getStacksHandler(slot))
                .map(handler -> handler.getStacks().getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Swaps what is fitted in a slot — used when a consumable is spent, so the wearer
     * is left holding the empty shell rather than nothing. An empty tank is not
     * rubbish; it is the thing you refill.
     */
    public static void fit(LivingEntity wearer, String slot, ItemStack replacement) {
        CuriosApi.getCuriosInventory(wearer)
                .flatMap(inventory -> inventory.getStacksHandler(slot))
                .ifPresent(handler -> handler.getStacks().setStackInSlot(0, replacement));
    }

    /** Repair state of the worn suit; a wrecked mask when nothing is worn. */
    public static int condition(LivingEntity wearer) {
        ItemStack suit = suit(wearer);
        return suit.isEmpty() ? SuitCondition.WRECKED : EvaSuitItem.conditionOf(suit);
    }

    /**
     * True when the wearer is inside a suit that can actually hold pressure — sealed
     * helmet and a working tank mount, with a tank fitted that still has gas.
     *
     * <p><strong>This is capability, not current state.</strong> It says nothing about
     * whether the visor is closed right now — a fully repaired, fully tanked suit
     * standing in breathable air answers {@code true} here with the visor wide open.
     * Barotrauma mitigation and the suit HUD's seal lamp both need the instantaneous
     * question instead: see {@link play.xponer.astronima.sim.suit.SuitCondition#isCurrentlySealed}.
     */
    public static boolean isSealed(LivingEntity wearer) {
        if (!SuitCondition.canHoldPressure(condition(wearer))) {
            return false;
        }
        ItemStack tank = tank(wearer);
        return tank.is(ModItems.OXYGEN_TANK.get()) && tank.getDamageValue() < tank.getMaxDamage();
    }

    private SuitLoadout() {}
}
