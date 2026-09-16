package play.xponer.astronima.vrpod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;

/**
 * Nothing that would strand a player in the VR Simulation Pod's own dimension for good is
 * allowed to happen there, gated on the level rather than on the specific hazard.
 * "Explosions reset instead of killing" (design/vr-simulation-pod.md §3/§4) is one instance of
 * that rule; {@link #onItemToss} is a second one the design doc did not anticipate: dropping
 * (Q) the one {@code vr_simulation_pod} item while already inside voids it into the level's real
 * void ({@code min_y: -64}), and {@link VrPodSafety}'s own death-cancel then keeps the player
 * alive forever with no item left to place an exit and no permission to run the op-only
 * {@code /astronima vrpod exit} debug command - a genuine, permanent softlock on a server where
 * the player isn't opped (game-design audit #2, finding B). Both handlers can never be exercised
 * by this project's gametest harness (no harness player takes real damage or reliably tosses
 * items through a real network path, see reference-gametest-harness-limits) —
 * {@code VrPodSafetyWiringTest} is the structural guard that keeps the one thing that matters in
 * each, the dimension check, honest instead.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class VrPodSafety {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().dimension().equals(ModDimensions.VR_LEVEL)) {
            return;
        }
        event.setCanceled(true);
        entity.setHealth(entity.getMaxHealth());
        entity.clearFire();
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(player.level(), ModDimensions.VR_SPAWN_X, ModDimensions.VR_SPAWN_Y,
                    ModDimensions.VR_SPAWN_Z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    /**
     * Cancelling {@link ItemTossEvent} alone only stops the dropped stack from entering the
     * world - it does not undo its removal from the inventory (the event's own doc comment says
     * so directly), so a bare cancel here would still void the pod. The stack has to be handed
     * back explicitly.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().level().dimension().equals(ModDimensions.VR_LEVEL)) {
            return;
        }
        ItemStack tossed = event.getEntity().getItem();
        if (!tossed.is(ModItems.VR_SIMULATION_POD.get())) {
            return;
        }
        event.setCanceled(true);
        event.getPlayer().getInventory().add(tossed.copy());
    }

    private VrPodSafety() {}
}
