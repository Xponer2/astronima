package play.xponer.astronima.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Opens the atlas — design/astra-research.md's interface for the research layer, revised in
 * that document's ritual note to ride on a personal item rather than a station block alone.
 *
 * <h2>Deliberately unreachable, and named rather than hidden (rule 8)</h2>
 * No recipe, no ritual, no loot table places this in the world yet. That is not an oversight:
 * design/astra-research.md's new ritual section describes how a player is meant to earn one —
 * found as a written note somewhere in the world, its actual steps generated differently for
 * every save so no wiki page can describe "the" procedure — and none of that exists yet. This
 * is the item and its screen, built first and honestly inert, so the interface has something
 * real to open once the ritual does.
 *
 * <p>{@code CraftingTree}'s reachability guard (rule 6) does not fire on this: the guard proves
 * every <em>declared</em> recipe reaches from bare hands, and this item declares none. An item
 * with no source is not a broken promise the way a recipe with an unreachable ingredient would
 * be — it is simply not promised yet.
 */
public class CelestialAtlasItem extends Item {

    public CelestialAtlasItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            // Fires once per click, on the hand it is actually in - not once per hand, which
            // would double-open the screen for a single press (SpectrographItem's own scar).
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel)) {
            play.xponer.astronima.client.AtlasOpener.open(player);
        }
        return InteractionResult.SUCCESS;
    }
}
