package play.xponer.astronima.progression;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.Map;

/**
 * Grants a codex unlock the moment its real condition is actually true — see
 * design/codex-disclosure.md §3's two id kinds.
 *
 * <p><strong>{@code crafted:} is checked against current possession, not a persistent "ever
 * held" ledger</strong> — a real, named simplification against the design doc's own stated
 * intent ("obtained, not currently held"). Building a true ever-held record would mean hooking
 * every path an item can arrive by — crafting, mining, a machine's output slot, a hopper, a
 * command — and this mod does not have one central "the player now has this" event to hang that
 * on. Checking current possession every tick, forever, until granted is simpler, and wrong only
 * in the narrow case of an ore mined and fully spent (smelted, crushed, sold to a machine)
 * inside the same tick it arrived — which is not a case an early-game ore actually reaches.
 *
 * <p>Checked every tick rather than on an event, for the same reason {@code AsteroidGravity}
 * gives: an unlock that only fires from an event a player can miss is an unlock that
 * occasionally never fires. Cheap to be sure — three item checks and one position check.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class UnlockTriggers {

    /**
     * Close enough to the real rim ({@link AsteroidBody#EQUATORIAL_RADIUS}) that reaching here
     * means having walked most of the way out, not merely wandered.
     */
    private static final double RIM_STUDIED_FRACTION = 0.9;

    public static final String REACHED_THE_RIM = "studied:reached-the-rim";

    /** One id per ore whose real t-band threshold the World Generator page locks behind it. */
    private static final Map<String, java.util.function.Supplier<Item>> ORE_UNLOCKS = Map.of(
            "crafted:astronima:hematite_ore", () -> ModItems.HEMATITE_ORE.get(),
            "crafted:astronima:ilmenite_ore", () -> ModItems.ILMENITE_ORE.get(),
            "crafted:astronima:metal_rich_ore", () -> ModItems.METAL_RICH_ORE.get());

    @SubscribeEvent
    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Unlocks unlocks = player.getData(ModAttachments.UNLOCKS.get());
        Unlocks next = unlocks;

        if (!next.has(REACHED_THE_RIM) && reachedTheRim(player)) {
            next = next.with(REACHED_THE_RIM);
        }
        for (Map.Entry<String, java.util.function.Supplier<Item>> entry : ORE_UNLOCKS.entrySet()) {
            if (!next.has(entry.getKey()) && hasEverSoFar(player, entry.getValue().get())) {
                next = next.with(entry.getKey());
            }
        }

        if (next != unlocks) {
            player.setData(ModAttachments.UNLOCKS.get(), next);
        }
    }

    private static boolean reachedTheRim(Player player) {
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return false;
        }
        double r = Math.hypot(player.getX(), player.getZ());
        return r >= AsteroidBody.EQUATORIAL_RADIUS * RIM_STUDIED_FRACTION;
    }

    private static boolean hasEverSoFar(Player player, Item item) {
        return player.getInventory().contains(stack -> stack.is(item));
    }

    private UnlockTriggers() {}
}
