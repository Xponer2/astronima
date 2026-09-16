package play.xponer.astronima.physio;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.pathogen.Strain;

/**
 * The two hops of the chain that are player actions rather than time passing.
 *
 * <p><strong>Both are verbs the player already has.</strong> That was a constraint of
 * {@code design/transmission.md} and it is worth naming: a hazard you have to learn a new control
 * to avoid is a hazard a player meets once and then resents.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class ContaminationEvents {

    /**
     * How filthy a grown culture is. High, because that is what a plate of colonies <em>is</em>.
     *
     * <p>The point of a laboratory is that the dangerous thing is on purpose, in your hand, and
     * labelled. Nothing about it should be subtle.
     */
    public static final double CULTURE_LOAD = 0.9;

    /**
     * Eating or drinking with a dirty glove: the face-touch, and the most important hop.
     *
     * <p>There is no "scratch your nose" control in Minecraft, and inventing one would have put
     * the step that matters most behind a key nobody presses. Hand to mouth is the same gesture
     * and the same mistake, and every player already does it several times an hour without
     * thinking — which is exactly the behaviour this is supposed to make them think about.
     */
    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof Player player)
                || !event.getItem().has(net.minecraft.core.component.DataComponents.CONSUMABLE)) {
            return;
        }
        Contaminations.handToMouth(player);
        if (Contaminations.deliversDose(player)) {
            Infections.infect(player, Contaminations.of(player).source());
        }
    }

    /**
     * Working with a culture: it goes onto your gloves, or onto your hands if you have none.
     *
     * <p>Called from the dish bench rather than from a use event, because opening the bench is
     * what "working with it" means — picking the item up off the floor is not handling a culture,
     * and charging for that would teach the wrong lesson.
     */
    public static void handledCulture(Player player,
                                      play.xponer.astronima.sim.lab.Culture culture) {
        if (player.level().isClientSide() || !culture.hasColonies()) {
            return; // a blank plate of agar is not a hazard, and charging for one would teach
                    // a player to avoid the machine rather than to handle it properly
        }
        Contaminations.handle(player, Strain.Source.COMMENSAL, CULTURE_LOAD);
    }

    /**
     * Using any block: what is on it comes off onto you, and what is on you goes onto it.
     *
     * <p>Every block, not a list of laboratory ones. A list would be a thing to forget to add to,
     * and it would also be wrong — the door handle between the laboratory and the galley is
     * exactly the surface that matters, and nobody would ever have thought to register it.
     *
     * <p>Almost every block in a habitat is clean and stays clean, so this costs a map lookup that
     * misses. The store keeps only what somebody has actually dirtied.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        CarriedContamination carried = Contaminations.of(player);
        if (carried.isClean() && Surfaces.at(player.level(), event.getPos()).isClean()) {
            return; // nothing on either side: the overwhelmingly common case
        }
        Surfaces.work(player, event.getPos());
    }

    private ContaminationEvents() {}
}
