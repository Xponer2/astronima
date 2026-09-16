package play.xponer.astronima.wire;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.wire.FaceBasis;

/**
 * What a run is made of, and where that metal comes from.
 *
 * <p>Wire used to cost nothing, which made the whole conductor ladder a cosmetic menu: a player
 * could pick silver for a hundred-metre bus and pay the same as for iron. <strong>Now a metre of
 * trace is metal out of your pockets</strong>, and the choice between a short iron run and a long
 * aluminium one is the trade the tier was designed around.
 *
 * <p><strong>And a metal you cannot make yet cannot be laid.</strong> That turns the ladder from
 * a list into a progression without a single extra rule: iron is what you crash with, nickel
 * arrives with carbonyl refining, and the rest wait for the tiers that produce them. The panel
 * shows those rungs greyed rather than hiding them, because knowing what is coming is half of
 * why a ladder is worth climbing.
 */
public final class WireStock {

    /**
     * How much trace one item of stock draws out into, in pixels.
     *
     * <p>Sixty-four — four metres per rod. Drawing wire multiplies length enormously for the
     * same metal, which is what drawing <em>is</em>; the number is chosen so that wiring a room
     * costs a handful of rods rather than a chest of them, because the interesting cost here is
     * the decision, not the grind.
     */
    public static final int PIXELS_PER_ITEM = 64;

    /** Metres of trace one item makes, for a readout that wants to speak in metres. */
    public static final double METRES_PER_ITEM = PIXELS_PER_ITEM / (double) FaceBasis.GRID;

    /**
     * The item this conductor is drawn from, or null when the mod cannot make it yet.
     *
     * <p>Null is a real answer and not an omission: copper, aluminium, silver, tungsten and the
     * rest are genuine rungs of the ladder whose ores or reduction steps belong to tiers that do
     * not exist. Listing them with no stock is honest — the physics is already there and waiting
     * — where quietly leaving them out would make the ladder look shorter than it is.
     */
    public static @Nullable Item stockFor(ConductorMaterial material) {
        return switch (material) {
            case IRON -> ModItems.IRON_ROD.get();
            case NICKEL -> ModItems.PURE_NICKEL.get();
            // Not yet producible: their minerals or their reduction belong to later tiers.
            case COBALT, ALUMINIUM, COPPER, SILVER, GOLD, TUNGSTEN, TITANIUM -> null;
        };
    }

    public static boolean isAvailable(ConductorMaterial material) {
        return stockFor(material) != null;
    }

    /** How many items a run of this many pixels costs, rounding up — you cannot buy half a rod. */
    /** How many items a length of this gauge costs — thicker wire, more metal per metre. */
    public static int itemsFor(int pixels, WireGauge gauge) {
        int per = gauge.pixelsPerItem();
        return (pixels + per - 1) / per;
    }

    public static int itemsFor(int pixels) {
        return Math.max(0, (pixels + PIXELS_PER_ITEM - 1) / PIXELS_PER_ITEM);
    }

    /** How much wire a given number of items pays for. */
    public static int pixelsFrom(int items) {
        return Math.max(0, items) * PIXELS_PER_ITEM;
    }

    /** How many of that stock the player is carrying. */
    public static int carried(Player player, ConductorMaterial material) {
        Item stock = stockFor(material);
        if (stock == null) {
            return 0;
        }
        if (player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack held = player.getInventory().getItem(slot);
            if (held.is(stock)) {
                found += held.getCount();
            }
        }
        return found;
    }

    /**
     * Takes the stock a run costs, or takes nothing and says no.
     *
     * <p>All or nothing on purpose: half-paying for a route would leave a trace that stops in
     * mid-air somewhere the player did not choose, and choosing where a run ends is the entire
     * point of routing it.
     */
    public static boolean spend(Player player, ConductorMaterial material, int pixels,
                                WireGauge gauge) {
        return spend(player, material, scaled(pixels, gauge));
    }

    /** A length at one gauge, expressed as the standard-gauge length that costs the same metal. */
    private static int scaled(int pixels, WireGauge gauge) {
        return (int) Math.ceil(pixels
                * (double) WireGauge.STANDARD_PIXELS_PER_ITEM / gauge.pixelsPerItem());
    }

    public static void refund(Player player, ConductorMaterial material, int pixels,
                              WireGauge gauge) {
        refund(player, material, scaled(pixels, gauge));
    }

    public static boolean spend(Player player, ConductorMaterial material, int pixels) {
        int needed = itemsFor(pixels);
        if (needed <= 0) {
            return true;
        }
        if (player.isCreative()) {
            return true;
        }
        Item stock = stockFor(material);
        if (stock == null || carried(player, material) < needed) {
            return false;
        }
        int left = needed;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack held = player.getInventory().getItem(slot);
            if (!held.is(stock)) {
                continue;
            }
            int taken = Math.min(left, held.getCount());
            held.shrink(taken);
            left -= taken;
        }
        return true;
    }

    /**
     * Gives back what a cut run was made of.
     *
     * <p>Rounded <strong>down</strong>, and that asymmetry is the cost of changing your mind:
     * drawing wire and re-melting the offcut does not come out even in life either. It also
     * stops a player farming metal by laying and cutting the same metre.
     */
    public static void refund(Player player, ConductorMaterial material, int pixels) {
        Item stock = stockFor(material);
        int items = stock == null ? 0 : pixels / PIXELS_PER_ITEM;
        if (items <= 0) {
            return;
        }
        ItemStack returned = new ItemStack(stock, items);
        if (!player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
    }

    private WireStock() {}
}
